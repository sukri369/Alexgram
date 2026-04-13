package xyz.nextalone.nagram.analytics.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import xyz.nextalone.nagram.analytics.domain.ChatLockManager
import xyz.nextalone.nagram.analytics.domain.AnalyticsManager

class ChatLockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val chatId = intent.getLongExtra("chat_id", 0L)

        // Resolve chat name
        val chatName = resolveChatName(chatId)

        setContent {
            ChatLockedScreen(
                chatName = chatName,
                onGoToAnalytics = {
                    startActivity(Intent(this, AnalyticsDashboardActivity::class.java))
                    finish()
                },
                onDismiss = { finish() }
            )
        }
    }

    private fun resolveChatName(chatId: Long): String {
        return try {
            val mc = MessagesController.getInstance(UserConfig.selectedAccount)
            if (chatId > 0) {
                val user = mc.getUser(chatId)
                if (user != null) "${user.first_name ?: ""} ${user.last_name ?: ""}".trim()
                    .ifBlank { "Contact" }
                else "Contact"
            } else {
                mc.getChat(-chatId)?.title?.ifBlank { "Chat" } ?: "Chat"
            }
        } catch (e: Exception) {
            "Chat"
        }
    }
}

@Composable
private fun ChatLockedScreen(
    chatName: String,
    onGoToAnalytics: () -> Unit,
    onDismiss: () -> Unit
) {
    val BgDark    = Color(0xFF0A0C18)
    val NeonPink  = Color(0xFFE91E8C)
    val NeonCyan  = Color(0xFF00E5FF)
    val TextPrimary = Color(0xFFEEF0F8)
    val TextSecond  = Color(0xFF8A94B0)
    val CardBg      = Color(0xFF12152A)
    val GlassBorder = Color(0xFF2A2F4A)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Pulsing lock animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1A0D2E), Color(0xFF0A0C18)),
                    radius = 1200f
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = OvershootInterpolatorEasing))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                // Animated lock icon
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow ring
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(NeonPink.copy(alpha = glowAlpha * 0.15f))
                    )
                    // Inner icon container
                    Box(
                        Modifier
                            .size(88.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(NeonPink.copy(0.3f), Color(0xFF1A0D2E)))
                            )
                            .border(2.dp, NeonPink.copy(0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = NeonPink,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Title
                Text(
                    "Chat Locked",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(8.dp))

                // Subtitle with chat name
                Text(
                    "\"$chatName\"",
                    color = NeonPink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(16.dp))

                // Info card
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        "This chat has been locked by you.\nTo unlock it, visit the Analytics page\nand remove the lock from your settings.",
                        color = TextSecond,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(28.dp))

                // Go to Analytics button
                Button(
                    onClick = onGoToAnalytics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(NeonPink, Color(0xFFAA00FF))),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Open Analytics",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Dismiss / go back
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Go Back",
                        color = TextSecond,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private val OvershootInterpolatorEasing = Easing { t ->
    val tension = 2.5f
    val scaledT = t - 1f
    scaledT * scaledT * ((tension + 1) * scaledT + tension) + 1f
}
