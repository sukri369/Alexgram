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
    private var currentAccount: Int = 0
    private var chatStartTime: Long = 0

    // ── Global Enable/Disable Logic ───────────────────────────────────────────
    
    private val prefs = MessagesController.getGlobalMainSettings()
    
    var isEnabled: Boolean
        get() = prefs.getBoolean("nagram_analytics_enabled", true)
        set(value) {
            prefs.edit().putBoolean("nagram_analytics_enabled", value).apply()
        }

    fun startTracking() {
        lastUpdateTime = System.currentTimeMillis()
        // Universal observer for theme etc can stay on global
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveNewMessages)
        
        // Per-Account observers are required for messages (multi-account support)
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.didReceiveNewMessages)
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagesRead)
        }
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (!isEnabled) return
        
        if (id == NotificationCenter.didReceiveNewMessages) {
            val messages = args[1] as ArrayList<MessageObject>
            scope.launch {
                messages.forEach { msg ->
                    trackMessage(account, msg)
                }
            }
        }
    }

    private suspend fun trackMessage(accountIndex: Int, msg: MessageObject) {
        val today = getTodayTimestamp()
        val usage = dao.getChatUsage(accountIndex, msg.getDialogId(), today) 
            ?: ChatUsageRecord(accountIndex = accountIndex, chatId = msg.getDialogId(), date = today)
        
        val isSent = msg.isOut()
        val isMedia = msg.isPhoto() || msg.isVideo() || msg.isDocument() || msg.isMusic() || msg.isVoice() || msg.isRoundVideo()
        
        val updated = usage.copy(
            messagesSent = usage.messagesSent + if (isSent) 1 else 0,
            messagesReceived = usage.messagesReceived + if (!isSent) 1 else 0,
            textCount = usage.textCount + if (!isMedia) 1 else 0,
            mediaCount = usage.mediaCount + if (isMedia) 1 else 0
        )
        dao.insertChatUsage(updated)
    }

    fun onAppForeground() {
        if (!isEnabled) return
        
        // App usage is universal (shared across all accounts)
        val universalAccount = 0
        lastUpdateTime = System.currentTimeMillis()
        scope.launch {
            val today = getTodayTimestamp()
            val usage = dao.getAppUsage(universalAccount, today) ?: AppUsageRecord(accountIndex = universalAccount, date = today)
            dao.insertAppUsage(usage.copy(sessionCount = usage.sessionCount + 1))
        }
    }

    fun onAppBackground() {
        if (!isEnabled) return
        
        // App usage is universal (shared across all accounts)
        val universalAccount = 0
        val duration = (System.currentTimeMillis() - lastUpdateTime) / 1000
        if (duration > 0) {
            scope.launch {
                val today = getTodayTimestamp()
                val usage = dao.getAppUsage(universalAccount, today) ?: AppUsageRecord(accountIndex = universalAccount, date = today)
                dao.insertAppUsage(usage.copy(totalTimeSeconds = usage.totalTimeSeconds + duration))
            }
        }
    }

    fun onChatStarted(chatId: Long) {
        if (!isEnabled) return
        
        currentAccount = UserConfig.selectedAccount
        currentChatId = chatId
        chatStartTime = System.currentTimeMillis()
    }

    fun onChatEnded(chatId: Long) {
        if (!isEnabled) return
        
        if (currentChatId == chatId) {
            val duration = (System.currentTimeMillis() - chatStartTime) / 1000
            if (duration > 0) {
                scope.launch {
                    val today = getTodayTimestamp()
                    val usage = dao.getChatUsage(currentAccount, chatId, today) 
                        ?: ChatUsageRecord(accountIndex = currentAccount, chatId = chatId, date = today)
                    dao.insertChatUsage(usage.copy(timeSpentSeconds = usage.timeSpentSeconds + duration))
                }
            }
            currentChatId = 0
            currentAccount = 0
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
