package xyz.nextalone.nagram.analytics.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import dagger.hilt.android.AndroidEntryPoint
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import xyz.nextalone.nagram.analytics.domain.AnalyticsManager
import xyz.nextalone.nagram.analytics.ui.screens.DashboardScreen
import xyz.nextalone.nagram.analytics.ui.theme.AnalyticsTheme

@AndroidEntryPoint
class AnalyticsDashboardActivity : ComponentActivity(), NotificationCenter.NotificationCenterDelegate {

    private var isDarkState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        isDarkState = Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme)

        setContent {
            AnalyticsTheme(darkTheme = isDarkState) {
                DashboardScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsManager.get(this).isDashboardActive = true
    }

    override fun onPause() {
        super.onPause()
        AnalyticsManager.get(this).isDashboardActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        AnalyticsManager.get(this).isDashboardActive = false
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.didSetNewTheme) {
            isDarkState = Theme.isCurrentThemeDark()
        }
    }
}
