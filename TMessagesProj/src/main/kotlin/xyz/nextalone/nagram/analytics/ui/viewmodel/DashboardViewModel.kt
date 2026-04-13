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
import java.util.*
import javax.inject.Inject

// ─── Data Models ─────────────────────────────────────────────────────────────

data class ChatUsageInfo(
    val record: ChatUsageRecord,
    val displayName: String,
    val isUser: Boolean,
    val isGroup: Boolean,
    val isChannel: Boolean,
    val initial: String,
    val isLocked: Boolean = false
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
    val sessionInsights: SessionInsightData = SessionInsightData(0, 0, 0, 0, 0)
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val today = getTodayTimestamp()

        viewModelScope.launch {
            // Combine 4 flows: app history, top chats all time, limits, blocked chats
            combine(
                dao.getAppUsageFlow(30),
                dao.getTopChatsAllTimeFlow(10),
                dao.getAllLimitsFlow(),
                dao.getBlockedChatsFlow()
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                Quad(
                    arr[0] as List<AppUsageRecord>,
                    arr[1] as List<ChatUsageRecord>,
                    arr[2] as List<AnalyticsLimit>,
                    arr[3] as List<BlockedChat>
                )
            }.collect { quad ->
                val appUsage = quad.a
                val topChats = quad.b
                val limits = quad.c
                val blockedChats = quad.d

                // Today & week minutes from app_usage
                val todayRecord = appUsage.firstOrNull()
                val todayMins = (todayRecord?.totalTimeSeconds ?: 0L) / 60L
                val weekMins = appUsage.take(7).sumOf { it.totalTimeSeconds } / 60L

                // Sessions all-time
                val totalSessions = appUsage.sumOf { it.sessionCount }

                // Real session insights (all-time aggregated from chat records)
                val totalSent = topChats.sumOf { it.messagesSent.toLong() }
                val totalReceived = topChats.sumOf { it.messagesReceived.toLong() }
                val totalMedia = topChats.sumOf { it.mediaCount.toLong() }
                val uniqueChats = topChats.size
                val totalTimeSecs = topChats.sumOf { it.timeSpentSeconds }

                val blockedIds = blockedChats.map { it.chatId }.toSet()

                // Resolve real names from Telegram API
                val enrichedChats = topChats.map { record ->
                    resolveChatInfo(record, blockedIds)
                }

                _uiState.value = DashboardUiState(
                    appUsageHistory = appUsage,
                    topChats = enrichedChats,
                    limits = limits,
                    blockedChats = blockedChats,
                    todayMinutes = todayMins,
                    weekMinutes = weekMins,
                    totalSessionsAllTime = totalSessions,
                    sessionInsights = SessionInsightData(
                        totalSent = totalSent,
                        totalReceived = totalReceived,
                        totalMedia = totalMedia,
                        uniqueChats = uniqueChats,
                        totalTimeSeconds = totalTimeSecs
                    )
                )
            }
        }
    }

    /** Resolves real Telegram chat name and type for a given ChatUsageRecord */
    private fun resolveChatInfo(record: ChatUsageRecord, lockedIds: Set<Long>): ChatUsageInfo {
        return try {
            val account = UserConfig.selectedAccount
            val mc = MessagesController.getInstance(account)
            val chatId = record.chatId

            if (chatId > 0) {
                val user: TLRPC.User? = mc.getUser(chatId)
                val name = if (user != null) {
                    ContactsController.formatName(user.first_name, user.last_name)
                        .takeIf { it.isNotBlank() } ?: "User $chatId"
                } else "User $chatId"
                ChatUsageInfo(
                    record = record,
                    displayName = name,
                    isUser = true, isGroup = false, isChannel = false,
                    initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                    isLocked = lockedIds.contains(chatId)
                )
            } else {
                val chat: TLRPC.Chat? = mc.getChat(-chatId)
                val name = chat?.title?.takeIf { it.isNotBlank() } ?: "Chat ${-chatId}"
                val isChannel = chat?.broadcast == true
                val isGroup = !isChannel
                ChatUsageInfo(
                    record = record,
                    displayName = name,
                    isUser = false, isGroup = isGroup, isChannel = isChannel,
                    initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "G",
                    isLocked = lockedIds.contains(chatId)
                )
            }
        } catch (e: Exception) {
            ChatUsageInfo(
                record = record,
                displayName = "ID ${record.chatId}",
                isUser = false, isGroup = false, isChannel = false,
                initial = "?",
                isLocked = false
            )
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
            val totalSeconds = (hours * 3600L) + (minutes * 60L)
            dao.insertLimit(
                AnalyticsLimit(
                    type = 0,
                    targetId = 0,
                    dailyLimitSeconds = totalSeconds,
                    isEnabled = enabled
                )
            )
        }
    }

    fun setChatLimit(chatId: Long, hours: Int, minutes: Int, enabled: Boolean) {
        viewModelScope.launch {
            val totalSeconds = (hours * 3600L) + (minutes * 60L)
            dao.insertLimit(
                AnalyticsLimit(
                    type = 1,
                    targetId = chatId,
                    dailyLimitSeconds = totalSeconds,
                    isEnabled = enabled
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

    /** Lock a chat with optional auto-unlock duration in minutes (0 = permanent) */
    fun lockChat(chatId: Long, autoUnlockMinutes: Int = 0) {
        viewModelScope.launch {
            dao.blockChat(BlockedChat(chatId = chatId, lockType = if (autoUnlockMinutes > 0) 1 else 0))
        }
    }

    fun unlockChat(chatId: Long) {
        viewModelScope.launch {
            dao.unblockChat(chatId)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
