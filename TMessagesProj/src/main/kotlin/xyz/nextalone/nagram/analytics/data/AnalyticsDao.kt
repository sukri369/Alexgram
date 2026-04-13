package xyz.nextalone.nagram.analytics.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    // App Usage
    @Query("SELECT * FROM app_usage WHERE date = :date")
    suspend fun getAppUsage(date: Long): AppUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsage(usage: AppUsageRecord)

    @Query("SELECT * FROM app_usage ORDER BY date DESC LIMIT :days")
    fun getAppUsageFlow(days: Int): Flow<List<AppUsageRecord>>

    // Chat Usage
    @Query("SELECT * FROM chat_usage WHERE chatId = :chatId AND date = :date")
    suspend fun getChatUsage(chatId: Long, date: Long): ChatUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatUsage(usage: ChatUsageRecord)

    @Query("SELECT * FROM chat_usage WHERE date = :date ORDER BY timeSpentSeconds DESC")
    fun getTopChatsFlow(date: Long): Flow<List<ChatUsageRecord>>

    @Query("SELECT SUM(timeSpentSeconds) FROM chat_usage WHERE date BETWEEN :start AND :end")
    suspend fun getTotalTimeInRange(start: Long, end: Long): Long

    // Limits
    @Query("SELECT * FROM analytics_limits")
    fun getAllLimitsFlow(): Flow<List<AnalyticsLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLimit(limit: AnalyticsLimit)

    @Query("SELECT * FROM analytics_limits WHERE type = :type AND targetId = :targetId")
    suspend fun getLimit(type: Int, targetId: Long): AnalyticsLimit?

    // Blocked Chats
    @Query("SELECT * FROM blocked_chats")
    fun getBlockedChatsFlow(): Flow<List<BlockedChat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockChat(blockedChat: BlockedChat)

    @Query("DELETE FROM blocked_chats WHERE chatId = :chatId")
    suspend fun unblockChat(chatId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_chats WHERE chatId = :chatId)")
    suspend fun isChatBlocked(chatId: Long): Boolean
}
