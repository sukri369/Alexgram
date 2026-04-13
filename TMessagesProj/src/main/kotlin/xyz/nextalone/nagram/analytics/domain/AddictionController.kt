package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xyz.nextalone.nagram.analytics.data.AnalyticsDao
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddictionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
