package xyz.nextalone.nagram.analytics.data

import androidx.room.*

@Entity(tableName = "app_usage")
data class AppUsageRecord(
    @PrimaryKey val date: Long, // Midnight timestamp
    val totalTimeSeconds: Long = 0,
    val sessionCount: Int = 0
)

@Entity(tableName = "chat_usage")
data class ChatUsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val date: Long, // Midnight timestamp
    val timeSpentSeconds: Long = 0,
    val messagesSent: Int = 0,
    val messagesReceived: Int = 0,
    val textCount: Int = 0,
    val mediaCount: Int = 0
)

/** Aggregated result from an all-time GROUP BY chatId query */
data class ChatUsageAggregate(
    val chatId: Long,
    val timeSpentSeconds: Long,
    val messagesSent: Long,
    val messagesReceived: Long,
    val textCount: Long,
    val mediaCount: Long
)

@Entity(
    tableName = "analytics_limits",
    primaryKeys = ["type", "targetId"]
)
data class AnalyticsLimit(
    val type: Int,         // 0 = App global, 1 = specific chat
    val targetId: Long = 0, // 0 for global, chatId for per-chat
    val dailyLimitSeconds: Long = 0,
    val isEnabled: Boolean = false
)

@Entity(tableName = "blocked_chats")
data class BlockedChat(
    @PrimaryKey val chatId: Long,
    val lockType: Int = 0,           // 0 = Permanent, 1 = Timed
    val unlocksAtMs: Long = 0L       // Epoch ms when auto-unlock fires, 0 = never
)
