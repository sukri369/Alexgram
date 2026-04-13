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
        scope.launch {
            // In a multi-account environment, we can't easily wait for a single Flow for ALL accounts
            // unless we aggregate them. For now, since user switches accounts, we refresh on init.
            // A more robust way is to observe the current account's flow.
            
            // To be safe and "God-Level", we'll just track all accounts' locks in the cache.
            // We'll iterate through all possible accounts (Telegram supports up to 4-10)
            val allAccountLocks = mutableMapOf<Int, Set<Long>>()
            for (i in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
                dao.getBlockedChatsFlow(i).collect { list ->
                    val now = System.currentTimeMillis()
                    allAccountLocks[i] = list.filter { it.unlocksAtMs == 0L || it.unlocksAtMs > now }
                        .map { it.chatId }.toSet()
                    _lockedChatIds.value = allAccountLocks.toMap()
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
