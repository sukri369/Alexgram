package xyz.nextalone.nagram.analytics.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.telegram.messenger.NotificationCenter
import org.telegram.ui.ActionBar.Theme
import xyz.nextalone.nagram.analytics.domain.AddictionController
import xyz.nextalone.nagram.analytics.domain.AnalyticsManager
import xyz.nextalone.nagram.analytics.ui.theme.AnalyticsTheme
import xyz.nextalone.nagram.analytics.ui.theme.LocalAnalyticsColors
import xyz.nextalone.nagram.analytics.ui.theme.NeonOrange
import xyz.nextalone.nagram.analytics.ui.theme.NeonRed
import xyz.nextalone.nagram.analytics.ui.theme.NeonCyan

class AppLimitReachedActivity : ComponentActivity(), NotificationCenter.NotificationCenterDelegate {

    private var isDarkState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Block back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        isDarkState = Theme.isCurrentThemeDark()
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme)

        val controller = AddictionController.get(applicationContext)
        val usedSeconds = controller.getTodaySeconds()
        val limitSeconds = controller.getLimitSeconds()

        setContent {
            AnalyticsTheme(darkTheme = isDarkState) {
                AppLimitReachedScreen(
                    usedSeconds = usedSeconds,
                    limitSeconds = limitSeconds,
                    onGoToAnalytics = {
                        startActivity(Intent(this, AnalyticsDashboardActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsManager.get(this).isLockScreenActive = true
    }

    override fun onPause() {
        super.onPause()
        AnalyticsManager.get(this).isLockScreenActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        AnalyticsManager.get(this).isLockScreenActive = false
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.didSetNewTheme) {
            isDarkState = Theme.isCurrentThemeDark()
        }
    }
}

@Composable
private fun AppLimitReachedScreen(
    usedSeconds: Long,
    limitSeconds: Long,
    onGoToAnalytics: () -> Unit
) {
    val c = LocalAnalyticsColors.current

    val usedH = usedSeconds / 3600
    val usedM = (usedSeconds % 3600) / 60
    val limitH = limitSeconds / 3600
    val limitM = (limitSeconds % 3600) / 60

    val usedLabel = when {
        usedH > 0 -> "${usedH}h ${usedM}m"
        else -> "${usedM}m"
    }
    val limitLabel = when {
        limitH > 0 -> "${limitH}h ${limitM}m"
        else -> "${limitM}m"
    }

    val progress = if (limitSeconds > 0) (usedSeconds / limitSeconds.toFloat()).coerceIn(0f, 1f) else 1f

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgScreen)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 4 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = c.bgCardAlt,
                            style = Stroke(12.dp.toPx())
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(NeonOrange, NeonRed, NeonOrange)),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                        )
                        val angle = Math.toRadians((-90f + 360f * progress).toDouble())
                        val radius = size.minDimension / 2f - 6.dp.toPx()
                        drawCircle(
                            color = NeonRed.copy(alpha = glowAlpha),
                            radius = 8.dp.toPx(),
                            center = Offset(
                                x = center.x + radius * Math.cos(angle).toFloat(),
                                y = center.y + radius * Math.sin(angle).toFloat()
                            )
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = NeonOrange,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        val percentage = (progress * 100).toInt()
                        Text(
                            "$percentage%",
                            color = NeonRed,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "Daily Limit Reached",
                    color = c.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "You've been on Alexgram for too long today.",
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.bgCard)
                        .border(1.dp, NeonOrange.copy(0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        UsageStat("Used", usedLabel, NeonOrange)
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(c.border)
                        )
                        UsageStat("Limit", limitLabel, NeonRed)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.bgCard)
                        .border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonCyan.copy(0.7f),
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "To extend your limit or remove it, go to\nAnalytics → Control Matrix → Daily App Limit.",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onGoToAnalytics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(NeonOrange, NeonRed)),
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
                                modifier = Modifier.size(20.dp)
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

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(NeonOrange.copy(glowAlpha * 0.8f), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Limit resets at midnight",
                        color = c.textSecondary.copy(0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageStat(label: String, value: String, color: Color) {
    val c = LocalAnalyticsColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = c.textSecondary, fontSize = 11.sp)
    }
}
