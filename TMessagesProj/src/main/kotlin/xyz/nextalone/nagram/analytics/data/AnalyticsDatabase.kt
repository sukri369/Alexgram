package xyz.nextalone.nagram.analytics.data

import android.content.Context
import androidx.room.*

@Database(
    entities = [AppUsageRecord::class, ChatUsageRecord::class, AnalyticsLimit::class, BlockedChat::class],
    version = 1,
    exportSchema = false
)
abstract class AnalyticsDatabase : RoomDatabase() {
    abstract fun dao(): AnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AnalyticsDatabase? = null

        fun getInstance(context: Context): AnalyticsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnalyticsDatabase::class.java,
                    "analytics_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
