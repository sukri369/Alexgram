package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.telegram.messenger.UserConfig
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
    companion object {
        fun get(context: Context): AddictionController {
            return AnalyticsManager.getEntryPoint(context).addictionController()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Per-account limit state: Map<AccountIndex, AnalyticsLimit?>
    private val _appLimit = MutableStateFlow<Map<Int, AnalyticsLimit?>>(emptyMap())
    
    @Volatile
    private var isCacheWarm = false

    init {
        startObserving()
    }

    private fun startObserving() {
        scope.launch {
            val universalAccount = 0
            // Observe the universal App Limit (type 0) from account 0
            dao.getLimitsByTypeFlow(universalAccount, 0).collect { limits ->
                val allAccountLimits = mutableMapOf<Int, AnalyticsLimit?>()
                allAccountLimits[universalAccount] = limits.firstOrNull()
                _appLimit.value = allAccountLimits.toMap()
                isCacheWarm = true
            }
        }
    }

    /** 
     * Fail-safe synchronous check for app start.
     * App limit is universal (Global).
     */
    @JvmOverloads
    fun isLimitExceeded(sessionDurationSeconds: Long = 0): Boolean {
        val universalAccount = 0
        val limit = if (!isCacheWarm) {
            // COLD CACHE FALLBACK
            runBlocking(Dispatchers.IO) { dao.getLimit(universalAccount, 0, 0) }
        } else {
            _appLimit.value[universalAccount]
        } ?: return false

        if (!limit.isEnabled) return false

        val todayUsage = getTodaySeconds()

        return (todayUsage + sessionDurationSeconds) >= limit.dailyLimitSeconds
    }

    /** Helper for UI: Get today's usage in seconds (Universal for app) */
    fun getTodaySeconds(): Long {
        val universalAccount = 0
        return runBlocking(Dispatchers.IO) {
            dao.getAppUsage(universalAccount, getTodayTimestamp())?.totalTimeSeconds ?: 0L
        }
    }

    /** Helper for UI: Get current app limit in seconds (Universal for app) */
    fun getLimitSeconds(): Long {
        val universalAccount = 0
        return if (!isCacheWarm) {
            runBlocking(Dispatchers.IO) { dao.getLimit(universalAccount, 0, 0) }
        } else {
            _appLimit.value[universalAccount]
        }?.dailyLimitSeconds ?: 0L
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
