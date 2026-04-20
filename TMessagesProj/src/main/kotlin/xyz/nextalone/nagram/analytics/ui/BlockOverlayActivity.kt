package xyz.nextalone.nagram.analytics.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import xyz.nextalone.nagram.analytics.ui.screens.UnlockScreen
import xyz.nextalone.nagram.analytics.ui.theme.AnalyticsTheme
import xyz.nextalone.nagram.analytics.ui.theme.LocalAnalyticsColors

@AndroidEntryPoint
class BlockOverlayActivity : ComponentActivity(), NotificationCenter.NotificationCenterDelegate {

    private var isDarkState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val type = intent.getIntExtra("type", 0) // 0: Addiction, 1: focus, 2: Chat Lock
        val title = when (type) {
            0 -> "Limit Reached!"
            1 -> "Focus Mode"
            else -> "Chat Locked"
        }
        val subtitle = when (type) {
            0 -> "You've used the app for too long today. Take a break!"
            1 -> "This chat is hidden during focus mode."
            else -> "Protecting your privacy."
        }

        isDarkState = Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme)

        setContent {
            AnalyticsTheme(darkTheme = isDarkState) {
                val c = LocalAnalyticsColors.current
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = c.bgPrimary
                ) {
                    UnlockScreen(
                        title = title,
                        subtitle = subtitle,
                        onUnlockSuccess = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.didSetNewTheme) {
            isDarkState = Theme.isCurrentThemeDark()
        }
    }
}
