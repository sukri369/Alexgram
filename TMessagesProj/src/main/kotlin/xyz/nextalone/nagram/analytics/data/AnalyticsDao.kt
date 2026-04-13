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

    @Query("DELETE FROM app_usage")
    suspend fun clearAllAppUsage()


    // ── Chat Usage ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM chat_usage WHERE chatId = :chatId AND date = :date")
    suspend fun getChatUsage(chatId: Long, date: Long): ChatUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatUsage(usage: ChatUsageRecord)

    /** Today only — ordered by time spent */
    @Query("SELECT * FROM chat_usage WHERE date = :date ORDER BY timeSpentSeconds DESC")
    fun getTopChatsFlow(date: Long): Flow<List<ChatUsageRecord>>

    /**
     * All-time aggregation: SUM per chatId across all dates.
     * Returns [ChatUsageAggregate] — a dedicated POJO, not the entity,
     * to avoid Room PK conflicts.
     */
    @Query("""
        SELECT
            chatId,
            SUM(timeSpentSeconds)  AS timeSpentSeconds,
            SUM(messagesSent)      AS messagesSent,
            SUM(messagesReceived)  AS messagesReceived,
            SUM(textCount)         AS textCount,
            SUM(mediaCount)        AS mediaCount
        FROM chat_usage
        GROUP BY chatId
        ORDER BY timeSpentSeconds DESC
        LIMIT :limit
    """)
    fun getTopChatsAllTimeFlow(limit: Int): Flow<List<ChatUsageAggregate>>

    @Query("SELECT SUM(timeSpentSeconds) FROM chat_usage WHERE date BETWEEN :start AND :end")
    suspend fun getTotalTimeInRange(start: Long, end: Long): Long?

    @Query("DELETE FROM chat_usage")
    suspend fun clearAllChatUsage()


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
}
