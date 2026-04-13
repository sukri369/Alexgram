package xyz.nextalone.nagram.analytics.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppUsageRecord::class, ChatUsageRecord::class, AnalyticsLimit::class, BlockedChat::class],
    version = 2,
    exportSchema = false
)
abstract class AnalyticsDatabase : RoomDatabase() {
    abstract fun dao(): AnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AnalyticsDatabase? = null

        /** Migration: v1 → v2
         *  - AnalyticsLimit: drop old table (single PK on 'type'), recreate with composite PK (type, targetId)
         *  - BlockedChat: add unlocksAtMs column
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate analytics_limits with composite primary key
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

                // Add unlocksAtMs column to blocked_chats
                db.execSQL("ALTER TABLE blocked_chats ADD COLUMN unlocksAtMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AnalyticsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnalyticsDatabase::class.java,
                    "analytics_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
