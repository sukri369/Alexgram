package xyz.nextalone.nagram.analytics.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.ContactsController
import org.telegram.tgnet.TLRPC
import xyz.nextalone.nagram.analytics.data.*
import java.util.*
import javax.inject.Inject

/**
 * Enriched chat info with resolved name.
 */
data class ChatUsageInfo(
    val record: ChatUsageRecord,
    val displayName: String,
    val isUser: Boolean,      // true = private chat, false = group/channel
    val isGroup: Boolean,     // true = group/supergroup
    val isChannel: Boolean,   // true = channel
    val initial: String       // First letter for avatar placeholder
)

data class DashboardUiState(
    val appUsageHistory: List<AppUsageRecord> = emptyList(),
    val topChats: List<ChatUsageInfo> = emptyList(),
    val limits: List<AnalyticsLimit> = emptyList(),
    val todayMinutes: Long = 0,
    val weekMinutes: Long = 0
)

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
        val weekAgo = today - 7L * 24 * 60 * 60 * 1000

        viewModelScope.launch {
            combine(
                dao.getAppUsageFlow(30),
                dao.getTopChatsFlow(today),
                dao.getAllLimitsFlow()
            ) { appUsage, topChats, limits ->

                // Today's total minutes
                val todayRecord = appUsage.firstOrNull()
                val todayMins = (todayRecord?.totalTimeSeconds ?: 0L) / 60L

                // Week total minutes
                val weekMins = appUsage.take(7).sumOf { it.totalTimeSeconds } / 60L

                // Resolve real chat names from Telegram
                val enrichedChats = topChats.map { record ->
                    resolveChatInfo(record)
                }

                DashboardUiState(
                    appUsageHistory = appUsage,
                    topChats = enrichedChats,
                    limits = limits,
                    todayMinutes = todayMins,
                    weekMinutes = weekMins
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Resolves the real display name for a chat using Telegram's MessagesController.
     * chatId > 0  → private user chat
     * chatId < 0  → group / channel (use -chatId to look up Chat)
     */
    private fun resolveChatInfo(record: ChatUsageRecord): ChatUsageInfo {
        val chatId = record.chatId
        return try {
            val account = UserConfig.selectedAccount
            val mc = MessagesController.getInstance(account)

            if (chatId > 0) {
                // Private user chat
                val user: TLRPC.User? = mc.getUser(chatId)
                val name = if (user != null) {
                    ContactsController.formatName(user.first_name, user.last_name)
                        .takeIf { it.isNotBlank() } ?: "User $chatId"
                } else {
                    "User $chatId"
                }
                ChatUsageInfo(
                    record = record,
                    displayName = name,
                    isUser = true,
                    isGroup = false,
                    isChannel = false,
                    initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                )
            } else {
                // Group / Channel (chatId is negative for chats)
                val chat: TLRPC.Chat? = mc.getChat(-chatId)
                val name = chat?.title?.takeIf { it.isNotBlank() } ?: "Chat ${-chatId}"
                // broadcast=true means broadcast channel; megagroup=true means supergroup (group)
                val isChannel = chat?.broadcast == true
                val isSuperGroup = chat?.megagroup == true
                val isGroup = isSuperGroup || (chat != null && !isChannel)
                ChatUsageInfo(
                    record = record,
                    displayName = name,
                    isUser = false,
                    isGroup = isGroup,
                    isChannel = isChannel,
                    initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
                )
            }
        } catch (e: Exception) {
            val fallback = "Chat ${record.chatId}"
            ChatUsageInfo(
                record = record,
                displayName = fallback,
                isUser = false,
                isGroup = false,
                isChannel = false,
                initial = "?"
            )
        }
    }

    fun toggleLimit(limit: AnalyticsLimit) {
        viewModelScope.launch {
            dao.insertLimit(limit.copy(isEnabled = !limit.isEnabled))
        }
    }

    fun setLimit(limit: AnalyticsLimit) {
        viewModelScope.launch {
            dao.insertLimit(limit)
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
