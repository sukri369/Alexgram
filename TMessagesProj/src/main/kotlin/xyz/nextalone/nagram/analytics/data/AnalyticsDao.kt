package xyz.nextalone.nagram.analytics.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    // ── App Usage ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM app_usage WHERE date = :date")
    suspend fun getAppUsage(date: Long): AppUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsage(usage: AppUsageRecord)

    @Query("SELECT * FROM app_usage ORDER BY date DESC LIMIT :days")
    fun getAppUsageFlow(days: Int): Flow<List<AppUsageRecord>>

    @Query("SELECT SUM(totalTimeSeconds) FROM app_usage")
    suspend fun getTotalAppTimeAllTime(): Long?

    @Query("SELECT SUM(sessionCount) FROM app_usage")
    suspend fun getTotalSessionsAllTime(): Int?

    // ── Chat Usage ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM chat_usage WHERE chatId = :chatId AND date = :date")
    suspend fun getChatUsage(chatId: Long, date: Long): ChatUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatUsage(usage: ChatUsageRecord)

    /** Today only — for top chats section */
    @Query("SELECT * FROM chat_usage WHERE date = :date ORDER BY timeSpentSeconds DESC")
    fun getTopChatsFlow(date: Long): Flow<List<ChatUsageRecord>>

    /** All-time aggregated per chat — groups by chatId summing everything */
    @Query("""
        SELECT chatId, 0 AS id, 0 AS date,
               SUM(timeSpentSeconds) AS timeSpentSeconds,
               SUM(messagesSent) AS messagesSent,
               SUM(messagesReceived) AS messagesReceived,
               SUM(textCount) AS textCount,
               SUM(mediaCount) AS mediaCount
        FROM chat_usage
        GROUP BY chatId
        ORDER BY timeSpentSeconds DESC
        LIMIT :limit
    """)
    fun getTopChatsAllTimeFlow(limit: Int): Flow<List<ChatUsageRecord>>

    /** All-time aggregated totals for session insights */
    @Query("SELECT SUM(messagesSent) FROM chat_usage")
    suspend fun getTotalMessagesSent(): Long?

    @Query("SELECT SUM(messagesReceived) FROM chat_usage")
    suspend fun getTotalMessagesReceived(): Long?

    @Query("SELECT SUM(mediaCount) FROM chat_usage")
    suspend fun getTotalMedia(): Long?

    @Query("SELECT COUNT(DISTINCT chatId) FROM chat_usage")
    suspend fun getTotalUniqueChats(): Int?

    @Query("SELECT SUM(timeSpentSeconds) FROM chat_usage WHERE date BETWEEN :start AND :end")
    suspend fun getTotalTimeInRange(start: Long, end: Long): Long?

    // ── Limits ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM analytics_limits")
    fun getAllLimitsFlow(): Flow<List<AnalyticsLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLimit(limit: AnalyticsLimit)

    @Query("DELETE FROM analytics_limits WHERE type = :type AND targetId = :targetId")
    suspend fun deleteLimit(type: Int, targetId: Long)

    @Query("SELECT * FROM analytics_limits WHERE type = :type AND targetId = :targetId")
    suspend fun getLimit(type: Int, targetId: Long): AnalyticsLimit?

    // ── Blocked Chats ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM blocked_chats")
    fun getBlockedChatsFlow(): Flow<List<BlockedChat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockChat(blockedChat: BlockedChat)

    @Query("DELETE FROM blocked_chats WHERE chatId = :chatId")
    suspend fun unblockChat(chatId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_chats WHERE chatId = :chatId)")
    suspend fun isChatBlocked(chatId: Long): Boolean

    @Query("SELECT * FROM blocked_chats")
    suspend fun getAllBlockedChats(): List<BlockedChat>
}
