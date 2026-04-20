package xyz.nextalone.nagram.analytics.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppUsageRecord::class, ChatUsageRecord::class, AnalyticsLimit::class, BlockedChat::class],
    version = 3,
    exportSchema = false
)
abstract class AnalyticsDatabase : RoomDatabase() {
    abstract fun dao(): AnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AnalyticsDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS analytics_limits")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS analytics_limits (
                        type INTEGER NOT NULL,
                        targetId INTEGER NOT NULL DEFAULT 0,
                        dailyLimitSeconds INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (type, targetId)
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE blocked_chats ADD COLUMN unlocksAtMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Migration: v2 → v3
         *  - Multi-account support: Recreate all tables with accountIndex in Primary Keys
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // app_usage: Add accountIndex to PK
                db.execSQL("CREATE TABLE app_usage_new (accountIndex INTEGER NOT NULL DEFAULT 0, date INTEGER NOT NULL, totalTimeSeconds INTEGER NOT NULL DEFAULT 0, sessionCount INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(accountIndex, date))")
                db.execSQL("INSERT INTO app_usage_new (date, totalTimeSeconds, sessionCount) SELECT date, totalTimeSeconds, sessionCount FROM app_usage")
                db.execSQL("DROP TABLE app_usage")
                db.execSQL("ALTER TABLE app_usage_new RENAME TO app_usage")

                // chat_usage: Add accountIndex column
                db.execSQL("ALTER TABLE chat_usage ADD COLUMN accountIndex INTEGER NOT NULL DEFAULT 0")

                // analytics_limits: Add accountIndex to PK
                db.execSQL("CREATE TABLE analytics_limits_new (accountIndex INTEGER NOT NULL DEFAULT 0, type INTEGER NOT NULL, targetId INTEGER NOT NULL DEFAULT 0, dailyLimitSeconds INTEGER NOT NULL DEFAULT 0, isEnabled INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(accountIndex, type, targetId))")
                db.execSQL("INSERT INTO analytics_limits_new (type, targetId, dailyLimitSeconds, isEnabled) SELECT type, targetId, dailyLimitSeconds, isEnabled FROM analytics_limits")
                db.execSQL("DROP TABLE analytics_limits")
                db.execSQL("ALTER TABLE analytics_limits_new RENAME TO analytics_limits")

                // blocked_chats: Add accountIndex to PK
                db.execSQL("CREATE TABLE blocked_chats_new (accountIndex INTEGER NOT NULL DEFAULT 0, chatId INTEGER NOT NULL, lockType INTEGER NOT NULL DEFAULT 0, unlocksAtMs INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(accountIndex, chatId))")
                db.execSQL("INSERT INTO blocked_chats_new (chatId, lockType, unlocksAtMs) SELECT chatId, lockType, unlocksAtMs FROM blocked_chats")
                db.execSQL("DROP TABLE blocked_chats")
                db.execSQL("ALTER TABLE blocked_chats_new RENAME TO blocked_chats")
            }
        }

        fun getInstance(context: Context): AnalyticsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnalyticsDatabase::class.java,
                    "analytics_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
