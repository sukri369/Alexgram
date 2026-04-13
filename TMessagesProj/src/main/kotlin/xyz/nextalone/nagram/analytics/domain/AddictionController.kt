package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xyz.nextalone.nagram.analytics.data.AnalyticsDao
import xyz.nextalone.nagram.analytics.data.AnalyticsLimit
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddictionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── In-memory state for sync access from Java ──────────────────────────────
    @Volatile private var limitEnabled: Boolean = false
    @Volatile private var limitSeconds: Long = 0L
    @Volatile private var todaySeconds: Long = 0L

    companion object {
        fun get(context: Context): AddictionController {
            return AnalyticsManager.getEntryPoint(context).addictionController()
        }
    }

    init {
        // Keep in-memory state in sync with DB changes
        scope.launch {
            dao.getAllLimitsFlow().collect { limits ->
                val appLimit = limits.firstOrNull { it.type == 0 && it.targetId == 0L }
                limitEnabled = appLimit?.isEnabled ?: false
                limitSeconds = appLimit?.dailyLimitSeconds ?: 0L
            }
        }
        scope.launch {
            dao.getAppUsageFlow(1).collect { list ->
                todaySeconds = list.firstOrNull()?.totalTimeSeconds ?: 0L
            }
        }
    }

    /** True if the user has set and enabled a limit that is currently exceeded */
    fun isLimitExceeded(): Boolean {
        return limitEnabled && limitSeconds > 0L && todaySeconds >= limitSeconds
    }

    /** True if a limit is set and enabled (regardless of whether exceeded) */
    fun isLimitActive(): Boolean = limitEnabled && limitSeconds > 0L

    /** How many seconds the user has used today */
    fun getTodaySeconds(): Long = todaySeconds

    /** The configured daily limit in seconds */
    fun getLimitSeconds(): Long = limitSeconds

    // ── Suspend-based checks (for internal use) ───────────────────────────────

    suspend fun checkAppLimitReached(): Boolean {
        val today = getTodayTimestamp()
        val limit = dao.getLimit(0, 0) ?: return false
        if (!limit.isEnabled) return false
        val currentUsage = dao.getAppUsage(today)?.totalTimeSeconds ?: 0
        return currentUsage >= limit.dailyLimitSeconds
    }

    suspend fun checkChatLimitReached(chatId: Long): Boolean {
        val today = getTodayTimestamp()
        val limit = dao.getLimit(1, chatId) ?: return false
        if (!limit.isEnabled) return false
        val currentUsage = dao.getChatUsage(chatId, today)?.timeSpentSeconds ?: 0
        return currentUsage >= limit.dailyLimitSeconds
    }

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
