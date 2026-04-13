package xyz.nextalone.nagram.analytics.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    // ── App Usage ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM app_usage WHERE accountIndex = :accountIndex AND date = :date")
    suspend fun getAppUsage(accountIndex: Int, date: Long): AppUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsage(usage: AppUsageRecord)

    @Query("SELECT * FROM app_usage WHERE accountIndex = :accountIndex ORDER BY date DESC LIMIT :days")
    fun getAppUsageFlow(accountIndex: Int, days: Int): Flow<List<AppUsageRecord>>

    @Query("DELETE FROM app_usage WHERE accountIndex = :accountIndex")
    suspend fun clearAllAppUsage(accountIndex: Int)


    // ── Chat Usage ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM chat_usage WHERE accountIndex = :accountIndex AND chatId = :chatId AND date = :date")
    suspend fun getChatUsage(accountIndex: Int, chatId: Long, date: Long): ChatUsageRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatUsage(usage: ChatUsageRecord)

    /** Today only — ordered by time spent */
    @Query("SELECT * FROM chat_usage WHERE accountIndex = :accountIndex AND date = :date ORDER BY timeSpentSeconds DESC")
    fun getTopChatsFlow(accountIndex: Int, date: Long): Flow<List<ChatUsageRecord>>

    /**
     * All-time aggregation: SUM per chatId across all dates.
     */
    @Query("""
        SELECT
            accountIndex,
            chatId,
            SUM(timeSpentSeconds)  AS timeSpentSeconds,
            SUM(messagesSent)      AS messagesSent,
            SUM(messagesReceived)  AS messagesReceived,
            SUM(textCount)         AS textCount,
            SUM(mediaCount)        AS mediaCount
        FROM chat_usage
        WHERE accountIndex = :accountIndex
        GROUP BY chatId
        ORDER BY timeSpentSeconds DESC
        LIMIT :limit
    """)
    fun getTopChatsAllTimeFlow(accountIndex: Int, limit: Int): Flow<List<ChatUsageAggregate>>

    @Query("SELECT SUM(timeSpentSeconds) FROM chat_usage WHERE accountIndex = :accountIndex AND date BETWEEN :start AND :end")
    suspend fun getTotalTimeInRange(accountIndex: Int, start: Long, end: Long): Long?

    @Query("DELETE FROM chat_usage WHERE accountIndex = :accountIndex")
    suspend fun clearAllChatUsage(accountIndex: Int)


    // ── Limits ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM analytics_limits WHERE accountIndex = :accountIndex")
    fun getAllLimitsFlow(accountIndex: Int): Flow<List<AnalyticsLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLimit(limit: AnalyticsLimit)

    @Query("DELETE FROM analytics_limits WHERE accountIndex = :accountIndex AND type = :type AND targetId = :targetId")
    suspend fun deleteLimit(accountIndex: Int, type: Int, targetId: Long)

    @Query("SELECT * FROM analytics_limits WHERE accountIndex = :accountIndex AND type = :type AND targetId = :targetId")
    suspend fun getLimit(accountIndex: Int, type: Int, targetId: Long): AnalyticsLimit?

    // ── Blocked Chats ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM blocked_chats WHERE accountIndex = :accountIndex")
    fun getBlockedChatsFlow(accountIndex: Int): Flow<List<BlockedChat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockChat(blockedChat: BlockedChat)

    @Query("DELETE FROM blocked_chats WHERE accountIndex = :accountIndex AND chatId = :chatId")
    suspend fun unblockChat(accountIndex: Int, chatId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_chats WHERE accountIndex = :accountIndex AND chatId = :chatId)")
    suspend fun isChatBlocked(accountIndex: Int, chatId: Long): Boolean
}
