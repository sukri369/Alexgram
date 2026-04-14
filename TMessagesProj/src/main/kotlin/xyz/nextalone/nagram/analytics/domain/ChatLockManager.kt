package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.telegram.messenger.UserConfig
import xyz.nextalone.nagram.analytics.data.AnalyticsDao
import xyz.nextalone.nagram.analytics.data.BlockedChat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) {
    companion object {
        fun get(context: Context): ChatLockManager {
            return AnalyticsManager.getEntryPoint(context).chatLockManager()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Per-account lock cache: Map<AccountIndex, Set<ChatId>>
    private val _lockedChatIds = MutableStateFlow<Map<Int, Set<Long>>>(emptyMap())
    
    @Volatile
    private var isCacheWarm = false

    init {
        refreshCache()
    }

    private fun refreshCache() {
        for (i in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            scope.launch {
                dao.getBlockedChatsFlow(i).collect { list ->
                    val now = System.currentTimeMillis()
                    val lockedSet = list.filter { it.unlocksAtMs == 0L || it.unlocksAtMs > now }
                        .map { it.chatId }.toSet()
                    
                    // Atomically update the per-account cache
                    val current = _lockedChatIds.value.toMutableMap()
                    current[i] = lockedSet
                    _lockedChatIds.value = current.toMap()
                    
                    isCacheWarm = true
                }
            }
        }
    }

    /** 
     * Fail-safe synchronous check for the UI thread.
     * Uses currently active account.
     */
    fun isLockedSync(chatId: Long): Boolean {
        val account = UserConfig.selectedAccount
        if (!isCacheWarm) {
            // COLD CACHE FALLBACK: Blocking DB read (only happens once on cold start)
            return runBlocking(Dispatchers.IO) {
                dao.isChatBlocked(account, chatId)
            }
        }
        return _lockedChatIds.value[account]?.contains(chatId) ?: false
    }

    fun unlockSession(chatId: Long) {
        val account = UserConfig.selectedAccount
        val current = _lockedChatIds.value.toMutableMap()
        val accountLocks = current[account]?.toMutableSet() ?: mutableSetOf()
        accountLocks.remove(chatId)
        current[account] = accountLocks
        _lockedChatIds.value = current
    }
}
