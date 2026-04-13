package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.telegram.messenger.NotificationCenter
import xyz.nextalone.nagram.analytics.data.AnalyticsDao
import xyz.nextalone.nagram.analytics.data.BlockedChat
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** In-memory set of currently locked chat IDs — synced from DB flow */
    private val lockedChatIds = mutableSetOf<Long>()
    
    /** Flag to indicate if the cache has been populated at least once */
    @Volatile
    private var isCacheWarm = false

    /** Chats the user has unlocked this session (temporary unlock) */
    private val sessionUnlocked = Collections.synchronizedSet(mutableSetOf<Long>())

    companion object {
        fun get(context: Context): ChatLockManager {
            return AnalyticsManager.getEntryPoint(context).chatLockManager()
        }
    }

    init {
        // Keep in-memory set in sync with DB
        scope.launch {
            dao.getBlockedChatsFlow().collect { list ->
                synchronized(lockedChatIds) {
                    lockedChatIds.clear()
                    lockedChatIds.addAll(list.map { it.chatId })
                }
                isCacheWarm = true
            }
        }
    }

    // ── Sync access (callable from Java) ──────────────────────────────────────

    /** Returns true if this chat should be blocked right now */
    fun isLockedSync(chatId: Long): Boolean {
        if (sessionUnlocked.contains(chatId)) return false
        
        // Fast path: cache is pre-populated
        if (isCacheWarm) {
            return synchronized(lockedChatIds) { lockedChatIds.contains(chatId) }
        }
        
        // Safe path: cache is cold, perform legacy blocking lookup to avoid race
        return isChatLockedBlocking(chatId)
    }

    /** Temporarily unlock for this session (until app restart) */
    fun sessionUnlock(chatId: Long) {
        sessionUnlocked.add(chatId)
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces)
    }

    // ── Suspend / Blocking ───────────────────────────────────────────────────

    suspend fun isChatLocked(chatId: Long): Boolean {
        if (sessionUnlocked.contains(chatId)) return false
        return dao.isChatBlocked(chatId)
    }

    fun isChatLockedBlocking(chatId: Long): Boolean {
        if (sessionUnlocked.contains(chatId)) return false
        return try {
            // Using runBlocking is generally stable for rare sync events in TMessagesProj
            kotlinx.coroutines.runBlocking { dao.isChatBlocked(chatId) }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setChatLock(chatId: Long, locked: Boolean) {
        if (locked) {
            dao.blockChat(BlockedChat(chatId))
        } else {
            dao.unblockChat(chatId)
            sessionUnlocked.add(chatId)
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces)
    }
}
