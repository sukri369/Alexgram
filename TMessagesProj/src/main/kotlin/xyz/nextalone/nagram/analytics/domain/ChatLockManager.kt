package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.telegram.messenger.NotificationCenter
import xyz.nextalone.nagram.analytics.data.AnalyticsDao
import xyz.nextalone.nagram.analytics.data.BlockedChat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) {
    private val unlockedChats = mutableSetOf<Long>()

    suspend fun isChatLocked(chatId: Long): Boolean {
        if (unlockedChats.contains(chatId)) return false
        return dao.isChatBlocked(chatId)
    }

    fun isChatLockedBlocking(chatId: Long): Boolean {
        if (unlockedChats.contains(chatId)) return false
        return kotlinx.coroutines.runBlocking { dao.isChatBlocked(chatId) }
    }

    fun unlockChat(chatId: Long) {
        unlockedChats.add(chatId)
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces)
    }

    fun lockChat(chatId: Long) {
        unlockedChats.remove(chatId)
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces)
    }

    suspend fun setChatLock(chatId: Long, locked: Boolean) {
        if (locked) {
            dao.blockChat(BlockedChat(chatId))
        } else {
            dao.unblockChat(chatId)
            unlockedChats.add(chatId)
        }
    }
}
