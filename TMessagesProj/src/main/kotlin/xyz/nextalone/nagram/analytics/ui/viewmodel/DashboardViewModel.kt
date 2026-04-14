package xyz.nextalone.nagram.analytics.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.telegram.messenger.ContactsController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import xyz.nextalone.nagram.analytics.data.*
import xyz.nextalone.nagram.analytics.domain.AnalyticsManager
import java.util.*
import javax.inject.Inject

// ─── UI Data Models ───────────────────────────────────────────────────────────

data class ChatUsageInfo(
    val chatId: Long,
    val displayName: String,
    val isUser: Boolean,
    val isGroup: Boolean,
    val isChannel: Boolean,
    val initial: String,
    val isLocked: Boolean = false,
    val timeSpentSeconds: Long = 0,
    val messagesSent: Long = 0,
    val messagesReceived: Long = 0,
    val mediaCount: Long = 0
)

data class SessionInsightData(
    val totalSent: Long,
    val totalReceived: Long,
    val totalMedia: Long,
    val uniqueChats: Int,
    val totalTimeSeconds: Long
)

data class DashboardUiState(
    /** The "Live Activity" graph — now showing message pulse per account (Isolated) */
    val messageHistoryPulse: List<ChatUsageRecord> = emptyList(),
    /** Universal summary of app usage (Shared) */
    val globalAppHistory: List<AppUsageRecord> = emptyList(),
    val topChats: List<ChatUsageInfo> = emptyList(),
    val limits: List<AnalyticsLimit> = emptyList(),
    val blockedChats: List<BlockedChat> = emptyList(),
    val todayMinutes: Long = 0,
    val weekMinutes: Long = 0,
    val totalSessionsAllTime: Int = 0,
    val sessionInsights: SessionInsightData = SessionInsightData(0, 0, 0, 0, 0),
    val isTrackingEnabled: Boolean = true
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val analyticsManager = AnalyticsManager.get(context)
    private val activeAccount = UserConfig.selectedAccount
    private val universalAccount = 0

    private val tickerFlow = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(1000)
        }
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                dao.getAppUsageFlow(universalAccount, 30),      // UNIVERSAL
                dao.getTopChatsAllTimeFlow(activeAccount, 20),  // ISOLATED
                dao.getAllLimitsFlow(universalAccount),        // UNIVERSAL
                dao.getBlockedChatsFlow(activeAccount),          // ISOLATED
                dao.getDailyMessageVolumeFlow(activeAccount, 14), // ISOLATED graph data
                tickerFlow // Tick every second
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                Hexa(
                    arr[0] as List<AppUsageRecord>,
                    arr[1] as List<ChatUsageAggregate>,
                    arr[2] as List<AnalyticsLimit>,
                    arr[3] as List<BlockedChat>,
                    arr[4] as List<ChatUsageRecord>,
                    arr[5] as Unit
                )
            }.collect { hexa ->
                val appUsage     = hexa.a
                val dbTopChats   = hexa.b
                val limits       = hexa.c
                val blockedChats = hexa.d
                val messagePulse = hexa.e

                // ── LIVE INJECTION ──
                val liveAppSecs = analyticsManager.getLiveAppSeconds()
                
                // ── UNIVERSAL STATS ──
                val dbTodaySecs = appUsage.firstOrNull()?.totalTimeSeconds ?: 0L
                val todayMins   = (dbTodaySecs + liveAppSecs) / 60L
                val weekMins    = (appUsage.take(7).sumOf { it.totalTimeSeconds } + liveAppSecs) / 60L
                val totalSessions = appUsage.sumOf { it.sessionCount }

                // ── ISOLATED STATS ──
                val totalSent     = dbTopChats.sumOf { it.messagesSent }
                val totalReceived = dbTopChats.sumOf { it.messagesReceived }
                val totalMedia    = dbTopChats.sumOf { it.mediaCount }
                val dbTotalTime   = dbTopChats.sumOf { it.timeSpentSeconds }
                
                val lockedIds     = blockedChats.map { it.chatId }.toSet()
                
                // Enrich chats and inject live chat duration
                val enriched = dbTopChats.map { agg -> 
                    val chatInfo = resolveChatInfo(agg, lockedIds)
                    val liveChatSecs = analyticsManager.getLiveChatSeconds(agg.chatId)
                    chatInfo.copy(timeSpentSeconds = chatInfo.timeSpentSeconds + liveChatSecs)
                }

                _uiState.value = DashboardUiState(
                    messageHistoryPulse  = messagePulse, 
                    globalAppHistory     = appUsage,
                    topChats             = enriched,
                    limits               = limits,
                    blockedChats         = blockedChats,
                    todayMinutes         = todayMins,
                    weekMinutes          = weekMins,
                    totalSessionsAllTime = totalSessions,
                    sessionInsights      = SessionInsightData(
                        totalSent        = totalSent,
                        totalReceived    = totalReceived,
                        totalMedia       = totalMedia,
                        uniqueChats      = dbTopChats.size,
                        totalTimeSeconds = dbTotalTime + liveAppSecs // Best effort total
                    ),
                    isTrackingEnabled = analyticsManager.isEnabled
                )
            }
        }
    }

    private data class Hexa<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)

    private fun resolveChatInfo(agg: ChatUsageAggregate, lockedIds: Set<Long>): ChatUsageInfo {
        return try {
            val mc      = MessagesController.getInstance(activeAccount)
            val chatId  = agg.chatId

            if (chatId > 0) {
                val user: TLRPC.User? = mc.getUser(chatId)
                val name = if (user != null) {
                    ContactsController.formatName(user.first_name, user.last_name)
                        .takeIf { it.isNotBlank() } ?: "User $chatId"
                } else "User $chatId"

                ChatUsageInfo(
                    chatId        = chatId,
                    displayName   = name,
                    isUser        = true,
                    isGroup       = false,
                    isChannel     = false,
                    initial       = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                    isLocked      = lockedIds.contains(chatId),
                    timeSpentSeconds = agg.timeSpentSeconds,
                    messagesSent  = agg.messagesSent,
                    messagesReceived = agg.messagesReceived,
                    mediaCount    = agg.mediaCount
                )
            } else {
                val chat: TLRPC.Chat? = mc.getChat(-chatId)
                val name      = chat?.title?.takeIf { it.isNotBlank() } ?: "Chat ${-chatId}"
                val isChannel = chat?.broadcast == true

                ChatUsageInfo(
                    chatId        = chatId,
                    displayName   = name,
                    isUser        = false,
                    isGroup       = !isChannel,
                    isChannel     = isChannel,
                    initial       = name.firstOrNull()?.uppercaseChar()?.toString() ?: "G",
                    isLocked      = lockedIds.contains(chatId),
                    timeSpentSeconds = agg.timeSpentSeconds,
                    messagesSent  = agg.messagesSent,
                    messagesReceived = agg.messagesReceived,
                    mediaCount    = agg.mediaCount
                )
            }
        } catch (e: Exception) {
            ChatUsageInfo(
                chatId = agg.chatId, displayName = "ID ${agg.chatId}", isUser = false, isGroup = false, isChannel = false, initial = "?",
                timeSpentSeconds = agg.timeSpentSeconds, messagesSent = agg.messagesSent, messagesReceived = agg.messagesReceived, mediaCount = agg.mediaCount
            )
        }
    }

    // ─── Global Controls (Universal) ──────────────────────────────────────────

    fun toggleTracking() {
        analyticsManager.isEnabled = !analyticsManager.isEnabled
        _uiState.update { it.copy(isTrackingEnabled = analyticsManager.isEnabled) }
    }

    fun resetAnalyticsData() {
        viewModelScope.launch {
            dao.clearAllAppUsage(universalAccount)
            dao.clearAllChatUsage(activeAccount)
        }
    }

    // ─── Limit Actions (Universal) ───────────────────────────────────────────

    fun toggleLimit(limit: AnalyticsLimit) {
        viewModelScope.launch {
            dao.insertLimit(limit.copy(isEnabled = !limit.isEnabled))
        }
    }

    fun setAppLimit(hours: Int, minutes: Int, enabled: Boolean) {
        viewModelScope.launch {
            // First clear any existing app limits on any account to ensure total sync
            dao.clearAllAppLimits()
            dao.insertLimit(
                AnalyticsLimit(
                    accountIndex      = universalAccount,
                    type              = 0,
                    targetId          = 0L,
                    dailyLimitSeconds = (hours * 3600L) + (minutes * 60L),
                    isEnabled         = enabled
                )
            )
        }
    }

    fun deleteLimit(limit: AnalyticsLimit) {
        viewModelScope.launch {
            if (limit.type == 0) {
                // Universal sync: Delete all app limits across all accounts
                dao.clearAllAppLimits()
            } else {
                dao.deleteLimit(activeAccount, limit.type, limit.targetId)
            }
        }
    }

    // ─── Chat Lock Actions (Isolated) ─────────────────────────────────────────

    fun lockChat(chatId: Long, autoUnlockMinutes: Int = 0) {
        viewModelScope.launch {
            val unlockAt = if (autoUnlockMinutes > 0)
                System.currentTimeMillis() + autoUnlockMinutes * 60_000L else 0L
            dao.blockChat(
                BlockedChat(
                    accountIndex = activeAccount,
                    chatId      = chatId,
                    lockType    = if (autoUnlockMinutes > 0) 1 else 0,
                    unlocksAtMs = unlockAt
                )
            )
        }
    }

    fun unlockChat(chatId: Long) {
        viewModelScope.launch {
            dao.unblockChat(activeAccount, chatId)
        }
    }

    private data class Penta<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
