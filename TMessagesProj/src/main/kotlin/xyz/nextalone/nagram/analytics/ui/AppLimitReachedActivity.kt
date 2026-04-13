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
import androidx.compose.ui.draw.scale
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
import xyz.nextalone.nagram.analytics.domain.AddictionController

class AppLimitReachedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Block back press — user must go to Analytics or wait
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Move to background instead of showing previous screen
                moveTaskToBack(true)
            }
        })

        val controller = AddictionController.get(applicationContext)
        val usedSeconds = controller.getTodaySeconds()
        val limitSeconds = controller.getLimitSeconds()

        setContent {
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

@Composable
private fun AppLimitReachedScreen(
    usedSeconds: Long,
    limitSeconds: Long,
    onGoToAnalytics: () -> Unit
) {
    val BgDark      = Color(0xFF090B15)
    val NeonOrange  = Color(0xFFFF6D00)
    val NeonRed     = Color(0xFFFF1744)
    val NeonCyan    = Color(0xFF00E5FF)
    val TextPrimary = Color(0xFFEEF0F8)
    val TextSecond  = Color(0xFF8A94B0)
    val CardBg      = Color(0xFF12152A)
    val GlassBorder = Color(0xFF2A2F4A)

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
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1F0A00), Color(0xFF090B15)),
                    radius = 1400f
                )
            )
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
                // Animated circular progress indicator
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        // Background ring
                        drawCircle(
                            color = Color(0xFF1E2040),
                            style = Stroke(12.dp.toPx())
                        )
                        // Progress arc
                        drawArc(
                            brush = Brush.sweepGradient(listOf(NeonOrange, NeonRed, NeonOrange)),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Glow dot at end of arc
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
                    // Center icon
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = NeonOrange,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "100%",
                            color = NeonRed,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "Daily Limit Reached",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "You've been on Alexgram for too long today.",
                    color = TextSecond,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // Usage stats card
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBg)
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
                                .background(GlassBorder)
                        )
                        UsageStat("Limit", limitLabel, NeonRed)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Info card
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
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
                            color = TextSecond,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Go to Analytics button (primary CTA)
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

                // Bottom warning
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
                        color = TextSecond.copy(0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color(0xFF8A94B0), fontSize = 11.sp)
    }
}
