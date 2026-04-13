package xyz.nextalone.nagram.analytics.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import xyz.nextalone.nagram.analytics.ui.screens.DashboardScreen
import xyz.nextalone.nagram.analytics.ui.theme.AnalyticsTheme
import xyz.nextalone.nagram.analytics.ui.theme.DarkAnalyticsColors
import xyz.nextalone.nagram.analytics.ui.theme.LightAnalyticsColors

@AndroidEntryPoint
class AnalyticsDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnalyticsTheme {
                DashboardScreen()
            }
        }
    }
}
