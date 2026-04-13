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
        refreshCache()
    }

    private fun refreshCache() {
        scope.launch {
            val allAccountLimits = mutableMapOf<Int, AnalyticsLimit?>()
            for (i in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
                // type 0 = App Global Limit, targetId = 0
                val limit = dao.getLimit(i, 0, 0)
                allAccountLimits[i] = limit
                _appLimit.value = allAccountLimits.toMap()
                isCacheWarm = true
            }
        }
    }

    /** 
     * Fail-safe synchronous check for app start.
     * Uses currently active account.
     */
    fun isLimitExceeded(): Boolean {
        val account = UserConfig.selectedAccount
        val limit = if (!isCacheWarm) {
            // COLD CACHE FALLBACK
            runBlocking(Dispatchers.IO) { dao.getLimit(account, 0, 0) }
        } else {
            _appLimit.value[account]
        } ?: return false

        if (!limit.isEnabled) return false

        val todayUsage = getTodaySeconds()

        return todayUsage >= limit.dailyLimitSeconds
    }

    /** Helper for UI: Get today's usage in seconds for the active account */
    fun getTodaySeconds(): Long {
        val account = UserConfig.selectedAccount
        return runBlocking(Dispatchers.IO) {
            dao.getAppUsage(account, getTodayTimestamp())?.totalTimeSeconds ?: 0L
        }
    }

    /** Helper for UI: Get current limit in seconds for the active account */
    fun getLimitSeconds(): Long {
        val account = UserConfig.selectedAccount
        return if (!isCacheWarm) {
            runBlocking(Dispatchers.IO) { dao.getLimit(account, 0, 0) }
        } else {
            _appLimit.value[account]
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
