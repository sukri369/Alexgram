package xyz.nextalone.nagram.analytics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.nextalone.nagram.analytics.ui.theme.LocalAnalyticsColors
import xyz.nextalone.nagram.analytics.ui.theme.NeonPink
import xyz.nextalone.nagram.analytics.ui.theme.NeonCyan
import xyz.nextalone.nagram.analytics.ui.theme.NeonPurple

@Composable
fun UnlockScreen(
    title: String = "Chat Locked",
    subtitle: String = "Enter PIN to access this chat",
    onUnlockSuccess: () -> Unit
) {
    val c = LocalAnalyticsColors.current
    var pin by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgScreen),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = NeonPink,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = subtitle,
                color = c.textSecondary,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Simple PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (filled) NeonCyan else c.textSecondary.copy(alpha = 0.2f),
                                CircleShape
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Number Pad Placeholder
            Button(
                onClick = { onUnlockSuccess() },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(0.8f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text("Unlock (Demo)", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
