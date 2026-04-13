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

@Entity(tableName = "analytics_limits")
data class AnalyticsLimit(
    @PrimaryKey val type: Int, // 0: App, 1: Specific Chat (chatId)
    val targetId: Long = 0, // 0 for App, chatId for chat
    val dailyLimitSeconds: Long = 0,
    val isEnabled: Boolean = false,
    val isLocked: Boolean = false
)

@Entity(tableName = "blocked_chats")
data class BlockedChat(
    @PrimaryKey val chatId: Long,
    val lockType: Int = 0 // 0: Password, 1: Biometric
)
