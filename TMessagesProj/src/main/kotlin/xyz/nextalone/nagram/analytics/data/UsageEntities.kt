package xyz.nextalone.nagram.analytics.data

import androidx.room.*

@Entity(
    tableName = "app_usage",
    primaryKeys = ["accountIndex", "date"]
)
data class AppUsageRecord(
    val accountIndex: Int = 0,
    val date: Long, // Midnight timestamp
    val totalTimeSeconds: Long = 0,
    val sessionCount: Int = 0
)

@Entity(tableName = "chat_usage")
data class ChatUsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountIndex: Int = 0,
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
    val accountIndex: Int,
    val chatId: Long,
    val timeSpentSeconds: Long,
    val messagesSent: Long,
    val messagesReceived: Long,
    val textCount: Long,
    val mediaCount: Long
)

@Entity(
    tableName = "analytics_limits",
    primaryKeys = ["accountIndex", "type", "targetId"]
)
data class AnalyticsLimit(
    val accountIndex: Int = 0,
    val type: Int,         // 0 = App global, 1 = specific chat
    val targetId: Long = 0, // 0 for global, chatId for per-chat
    val dailyLimitSeconds: Long = 0,
    val isEnabled: Boolean = false
)

@Entity(
    tableName = "blocked_chats",
    primaryKeys = ["accountIndex", "chatId"]
)
data class BlockedChat(
    val accountIndex: Int = 0,
    val chatId: Long,
    val lockType: Int = 0,           // 0 = Permanent, 1 = Timed
    val unlocksAtMs: Long = 0L       // Epoch ms when auto-unlock fires, 0 = never
)
