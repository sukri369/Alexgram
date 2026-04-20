package xyz.nextalone.nagram.analytics.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.nextalone.nagram.analytics.data.AnalyticsDao
import xyz.nextalone.nagram.analytics.data.AnalyticsDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InternalAnalyticsModule {

    @Provides
    @Singleton
    fun provideAnalyticsDatabase(@ApplicationContext context: Context): AnalyticsDatabase {
        return AnalyticsDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAnalyticsDao(database: AnalyticsDatabase): AnalyticsDao {
        return database.dao()
    }
}
