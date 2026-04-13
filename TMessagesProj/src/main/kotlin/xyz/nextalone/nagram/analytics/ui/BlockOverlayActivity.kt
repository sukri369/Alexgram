package xyz.nextalone.nagram.analytics.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import xyz.nextalone.nagram.analytics.ui.screens.UnlockScreen

@AndroidEntryPoint
class BlockOverlayActivity : ComponentActivity() {
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

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
}
