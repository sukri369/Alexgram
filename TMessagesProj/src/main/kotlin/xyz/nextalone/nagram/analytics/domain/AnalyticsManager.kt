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
    private var monitorJob: Job? = null
    private var lastUpdateTime: Long = 0
    private var currentChatId: Long = 0
    private var currentAccount: Int = 0
    private var chatStartTime: Long = 0
    private var lastTodayTimestamp: Long = 0

    // Context Awareness to prevent navigation loops and "stuck" UI
    @Volatile var isDashboardActive: Boolean = false
    @Volatile var isLockScreenActive: Boolean = false

    // ── Global Enable/Disable Logic ───────────────────────────────────────────
    
    private val prefs = MessagesController.getGlobalMainSettings()
    
    var isEnabled: Boolean
        get() = prefs.getBoolean("nagram_analytics_enabled", true)
        set(value) {
            prefs.edit().putBoolean("nagram_analytics_enabled", value).apply()
        }

    // ── Live Session Exposure (God-Level Precision) ──────────────────────────

    fun getLiveAppSeconds(): Long {
        if (!isEnabled || lastUpdateTime == 0L) return 0L
        return (System.currentTimeMillis() - lastUpdateTime) / 1000
    }

    fun getLiveChatSeconds(chatId: Long): Long {
        if (!isEnabled || currentChatId != chatId || chatStartTime == 0L) return 0L
        return (System.currentTimeMillis() - chatStartTime) / 1000
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
                    // Exclude Service Messages (User joined, etc.)
                    if (msg.messageOwner !is org.telegram.tgnet.TLRPC.TL_messageService) {
                        trackMessage(account, msg)
                    }
                }
            }
        }
    }

    private suspend fun trackMessage(accountIndex: Int, msg: MessageObject) {
        val today = getTodayTimestamp()
        val usage = dao.getChatUsage(accountIndex, msg.getDialogId(), today) 
            ?: ChatUsageRecord(accountIndex = accountIndex, chatId = msg.getDialogId(), date = today)
        
        val isSent = msg.isOut()
        // Broad Media Detection (Everything that isn't raw text or service)
        val isMedia = msg.isPhoto() || msg.isVideo() || msg.isDocument() || 
                      msg.isMusic() || msg.isVoice() || msg.isRoundVideo() ||
                      msg.isSticker() || msg.isAnimatedSticker() || msg.isGif() ||
                      msg.messageOwner.media != null && msg.messageOwner.media !is org.telegram.tgnet.TLRPC.TL_messageMediaEmpty
        
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
        lastTodayTimestamp = getTodayTimestamp()
        
        startMonitoring()
        
        scope.launch {
            val today = getTodayTimestamp()
            val usage = dao.getAppUsage(universalAccount, today) ?: AppUsageRecord(accountIndex = universalAccount, date = today)
            dao.insertAppUsage(usage.copy(sessionCount = usage.sessionCount + 1))
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                if (isEnabled) {
                    val now = System.currentTimeMillis()
                    val today = getTodayTimestamp()

                    // MIDNIGHT RESET LOGIC (GOD-LEVEL)
                    if (lastTodayTimestamp != 0L && today != lastTodayTimestamp) {
                        // Midnight passed! Save current progress and rotate session
                        onAppBackground() // Flush app usage
                        if (currentChatId != 0L) {
                            val activeChat = currentChatId // Copy to avoid race
                            onChatEnded(currentAccount, activeChat) // Flush chat usage
                            onChatStarted(currentAccount, activeChat) // Restart for new day
                        }
                        onAppForeground() // Restart app usage for new day
                        continue // Re-evaluate in next loop with new timestamps
                    }
                    
                    // 1. Check Global App Limit
                    // CRITICAL FIX: Skip enforcement if dashboard is active to allow management
                    // and Skip if already on a lock screen to avoid navigation loops (stuck UI)
                    if (!isDashboardActive && !isLockScreenActive) {
                        val addictionController = addictionController()
                        val appSessionSecs = (now - lastUpdateTime) / 1000
                        
                        // DEBUG: Log enforcement pulse every minute or on hit
                        if (appSessionSecs % 60 == 0L || addictionController.isLimitExceeded(appSessionSecs)) {
                            FileLog.d("NagramAnalytics: Pulse check - session_secs=$appSessionSecs exceeded=${addictionController.isLimitExceeded(appSessionSecs)}")
                        }

                        if (addictionController.isLimitExceeded(appSessionSecs)) {
                            withContext(Dispatchers.Main) {
                                val intent = android.content.Intent(context, xyz.nextalone.nagram.analytics.ui.AppLimitReachedActivity::class.java).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                context.startActivity(intent)
                            }
                        }

                        // 2. Check Active Chat Quota
                        val activeChat = currentChatId
                        val activeAccount = currentAccount
                        if (activeChat != 0L) {
                            val chatSessionSecs = (now - chatStartTime) / 1000
                            enforceChatLimit(activeAccount, activeChat, chatSessionSecs)
                        }
                    }
                }
                delay(5000) // 5s heartbeat
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun addictionController() = AddictionController.get(context)

    fun onAppBackground() {
        if (!isEnabled) return
        
        stopMonitoring()
        
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

    fun onChatStarted(accountIndex: Int, chatId: Long) {
        if (!isEnabled) return
        
        currentAccount = accountIndex
        currentChatId = chatId
        chatStartTime = System.currentTimeMillis()

        // Real-time enforcement: Check quota on entry
        scope.launch {
            enforceChatLimit(accountIndex, chatId)
        }
    }

    private suspend fun enforceChatLimit(accountIndex: Int, chatId: Long, sessionDurationSeconds: Long = 0) {
        val limit = dao.getLimit(accountIndex, 1, chatId)
        if (limit != null && limit.isEnabled && limit.dailyLimitSeconds > 0) {
            val usage = dao.getChatUsage(accountIndex, chatId, getTodayTimestamp())
            val timeSpent = (usage?.timeSpentSeconds ?: 0L) + sessionDurationSeconds
            
            if (timeSpent >= limit.dailyLimitSeconds) {
                withContext(Dispatchers.Main) {
                    val intent = android.content.Intent(context, xyz.nextalone.nagram.analytics.ui.ChatLockedActivity::class.java).apply {
                        putExtra("chat_id", chatId)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    fun onChatEnded(accountIndex: Int, chatId: Long) {
        if (!isEnabled) return
        
        if (currentChatId == chatId && currentAccount == accountIndex) {
            val duration = (System.currentTimeMillis() - chatStartTime) / 1000
            if (duration > 0) {
                scope.launch {
                    val today = getTodayTimestamp()
                    val usage = dao.getChatUsage(accountIndex, chatId, today) 
                        ?: ChatUsageRecord(accountIndex = accountIndex, chatId = chatId, date = today)
                    dao.insertChatUsage(usage.copy(timeSpentSeconds = usage.timeSpentSeconds + duration))
                }
            }
            currentChatId = 0
            currentAccount = -1 // Reset to invalid -1 instead of 0 (which is an actual account index)
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
