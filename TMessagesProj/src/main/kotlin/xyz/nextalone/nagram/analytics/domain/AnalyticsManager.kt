package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.telegram.messenger.*
import xyz.nextalone.nagram.analytics.data.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) : NotificationCenter.NotificationCenterDelegate {

    companion object {
        fun getEntryPoint(context: Context): AnalyticsEntryPoint {
            return dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                AnalyticsEntryPoint::class.java
            )
        }

        fun get(context: Context): AnalyticsManager {
            return getEntryPoint(context).analyticsManager()
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface AnalyticsEntryPoint {
        fun analyticsManager(): AnalyticsManager
        fun chatLockManager(): ChatLockManager
        fun focusModeController(): FocusModeController
        fun addictionController(): AddictionController
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastUpdateTime: Long = 0
    private var currentChatId: Long = 0
    private var chatStartTime: Long = 0

    fun startTracking() {
        lastUpdateTime = System.currentTimeMillis()
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveNewMessages)
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagesRead)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            val messages = args[1] as ArrayList<MessageObject>
            scope.launch {
                messages.forEach { msg ->
                    trackMessage(msg)
                }
            }
        }
    }

    private suspend fun trackMessage(msg: MessageObject) {
        val today = getTodayTimestamp()
        val usage = dao.getChatUsage(msg.getDialogId(), today) ?: ChatUsageRecord(chatId = msg.getDialogId(), date = today)
        
        val isSent = msg.isOut()
        val isMedia = msg.hasPhoto() || msg.hasVideo() || msg.hasDocument() || msg.hasAudio() || msg.hasVoice()
        
        val updated = usage.copy(
            messagesSent = usage.messagesSent + if (isSent) 1 else 0,
            messagesReceived = usage.messagesReceived + if (!isSent) 1 else 0,
            textCount = usage.textCount + if (!isMedia) 1 else 0,
            mediaCount = usage.mediaCount + if (isMedia) 1 else 0
        )
        dao.insertChatUsage(updated)
    }

    fun onAppForeground() {
        lastUpdateTime = System.currentTimeMillis()
        scope.launch {
            val today = getTodayTimestamp()
            val usage = dao.getAppUsage(today) ?: AppUsageRecord(date = today)
            dao.insertAppUsage(usage.copy(sessionCount = usage.sessionCount + 1))
        }
    }

    fun onAppBackground() {
        val duration = (System.currentTimeMillis() - lastUpdateTime) / 1000
        if (duration > 0) {
            scope.launch {
                val today = getTodayTimestamp()
                val usage = dao.getAppUsage(today) ?: AppUsageRecord(date = today)
                dao.insertAppUsage(usage.copy(totalTimeSeconds = usage.totalTimeSeconds + duration))
            }
        }
    }

    fun onChatStarted(chatId: Long) {
        currentChatId = chatId
        chatStartTime = System.currentTimeMillis()
    }

    fun onChatEnded(chatId: Long) {
        if (currentChatId == chatId) {
            val duration = (System.currentTimeMillis() - chatStartTime) / 1000
            if (duration > 0) {
                scope.launch {
                    val today = getTodayTimestamp()
                    val usage = dao.getChatUsage(chatId, today) ?: ChatUsageRecord(chatId = chatId, date = today)
                    dao.insertChatUsage(usage.copy(timeSpentSeconds = usage.timeSpentSeconds + duration))
                }
            }
            currentChatId = 0
        }
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
