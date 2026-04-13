package xyz.nextalone.nagram.analytics.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** Enriched chat info with resolved Telegram name, type, and lock state */
data class ChatUsageInfo(
    val chatId: Long,
    val displayName: String,
    val isUser: Boolean,
    val isGroup: Boolean,
    val isChannel: Boolean,
    val initial: String,
    val isLocked: Boolean = false,
    // Aggregated metrics from all-time data
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
    val appUsageHistory: List<AppUsageRecord> = emptyList(),
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

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                dao.getAppUsageFlow(30),
                dao.getTopChatsAllTimeFlow(20),
                dao.getAllLimitsFlow(),
                dao.getBlockedChatsFlow()
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                Quad(
                    arr[0] as List<AppUsageRecord>,
                    arr[1] as List<ChatUsageAggregate>,
                    arr[2] as List<AnalyticsLimit>,
                    arr[3] as List<BlockedChat>
                )
            }.collect { quad ->
                val appUsage    = quad.a
                val topChats    = quad.b
                val limits      = quad.c
                val blockedChats = quad.d

                // Today & this week in minutes
                val todayMins = (appUsage.firstOrNull()?.totalTimeSeconds ?: 0L) / 60L
                val weekMins  = appUsage.take(7).sumOf { it.totalTimeSeconds } / 60L
                val totalSessions = appUsage.sumOf { it.sessionCount }

                // Session insights from all-time aggregated data
                val totalSent     = topChats.sumOf { it.messagesSent }
                val totalReceived = topChats.sumOf { it.messagesReceived }
                val totalMedia    = topChats.sumOf { it.mediaCount }
                val totalTimeSecs = topChats.sumOf { it.timeSpentSeconds }

                val lockedIds = blockedChats.map { it.chatId }.toSet()

                // Resolve real Telegram names / types
                val enriched = topChats.map { agg -> resolveChatInfo(agg, lockedIds) }

                _uiState.value = DashboardUiState(
                    appUsageHistory      = appUsage,
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

    /** Resolves the real Telegram display name and type for an aggregated chat record */
    private fun resolveChatInfo(agg: ChatUsageAggregate, lockedIds: Set<Long>): ChatUsageInfo {
        return try {
            val account = UserConfig.selectedAccount
            val mc      = MessagesController.getInstance(account)
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
                chatId      = agg.chatId,
                displayName = "ID ${agg.chatId}",
                isUser      = false,
                isGroup     = false,
                isChannel   = false,
                initial     = "?",
                isLocked    = false,
                timeSpentSeconds = agg.timeSpentSeconds,
                messagesSent     = agg.messagesSent,
                messagesReceived = agg.messagesReceived,
                mediaCount       = agg.mediaCount
            )
        }
    }

    // ─── Global Controls ──────────────────────────────────────────────────────

    fun toggleTracking() {
        analyticsManager.isEnabled = !analyticsManager.isEnabled
        // Force refresh state
        _uiState.update { it.copy(isTrackingEnabled = analyticsManager.isEnabled) }
    }

    fun resetAnalyticsData() {
        viewModelScope.launch {
            dao.clearAllAppUsage()
            dao.clearAllChatUsage()
            // Data will clear automatically via Flow collection
        }
    }

    // ─── Limit Actions ────────────────────────────────────────────────────────

    fun toggleLimit(limit: AnalyticsLimit) {
        viewModelScope.launch {
            dao.insertLimit(limit.copy(isEnabled = !limit.isEnabled))
        }
    }

    fun setAppLimit(hours: Int, minutes: Int, enabled: Boolean) {
        viewModelScope.launch {
            dao.insertLimit(
                AnalyticsLimit(
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
            dao.deleteLimit(limit.type, limit.targetId)
        }
    }

    // ─── Chat Lock Actions ────────────────────────────────────────────────────

    /**
     * Lock a chat.
     * @param autoUnlockMinutes 0 = permanent, >0 = auto-unlock after that many minutes
     */
    fun lockChat(chatId: Long, autoUnlockMinutes: Int = 0) {
        viewModelScope.launch {
            val unlockAt = if (autoUnlockMinutes > 0)
                System.currentTimeMillis() + autoUnlockMinutes * 60_000L else 0L
            dao.blockChat(
                BlockedChat(
                    chatId      = chatId,
                    lockType    = if (autoUnlockMinutes > 0) 1 else 0,
                    unlocksAtMs = unlockAt
                )
            )
        }
    }

    fun unlockChat(chatId: Long) {
        viewModelScope.launch {
            dao.unblockChat(chatId)
        }
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
