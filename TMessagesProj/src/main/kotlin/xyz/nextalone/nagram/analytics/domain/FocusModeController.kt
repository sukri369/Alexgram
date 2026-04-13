package xyz.nextalone.nagram.analytics.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import xyz.nextalone.nagram.analytics.data.AnalyticsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusModeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AnalyticsDao
) {
    private var isFocusModeEnabled = false
    private val whitelistedChats = mutableSetOf<Long>()

    fun setFocusMode(enabled: Boolean) {
        isFocusModeEnabled = enabled
    }

    fun isFocusModeEnabled() = isFocusModeEnabled

    fun setWhitelistedChats(chatIds: List<Long>) {
        whitelistedChats.clear()
        whitelistedChats.addAll(chatIds)
    }

    fun isChatAllowed(chatId: Long): Boolean {
        if (!isFocusModeEnabled) return true
        return whitelistedChats.contains(chatId)
    }
}
