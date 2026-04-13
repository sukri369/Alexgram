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
                dao.getDailyMessageVolumeFlow(activeAccount, 14) // ISOLATED graph data
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                Penta(
                    arr[0] as List<AppUsageRecord>,
                    arr[1] as List<ChatUsageAggregate>,
                    arr[2] as List<AnalyticsLimit>,
                    arr[3] as List<BlockedChat>,
                    arr[4] as List<ChatUsageRecord>
                )
            }.collect { penta ->
                val appUsage     = penta.a
                val topChats     = penta.b
                val limits       = penta.c
                val blockedChats = penta.d
                val messagePulse = penta.e

                // ── UNIVERSAL STATS ──
                val todayMins = (appUsage.firstOrNull()?.totalTimeSeconds ?: 0L) / 60L
                val weekMins  = appUsage.take(7).sumOf { it.totalTimeSeconds } / 60L
                val totalSessions = appUsage.sumOf { it.sessionCount }

                // ── ISOLATED STATS ──
                val totalSent     = topChats.sumOf { it.messagesSent }
                val totalReceived = topChats.sumOf { it.messagesReceived }
                val totalMedia    = topChats.sumOf { it.mediaCount }
                val totalTimeSecs = topChats.sumOf { it.timeSpentSeconds }
                val lockedIds     = blockedChats.map { it.chatId }.toSet()
                val enriched      = topChats.map { agg -> resolveChatInfo(agg, lockedIds) }

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
                        uniqueChats      = topChats.size,
                        totalTimeSeconds = totalTimeSecs
                    ),
                    isTrackingEnabled = analyticsManager.isEnabled
                )
            }
        }
    }

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
            dao.deleteLimit(universalAccount, limit.type, limit.targetId)
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
