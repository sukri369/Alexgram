package xyz.nextalone.nagram.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.nextalone.nagram.analytics.ui.viewmodel.DashboardViewModel
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable

// Premium Design System
object PremiumTheme {
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
    )
    val NeonCyan = Color(0xFF00FFF0)
    val NeonPurple = Color(0xFFBC13FE)
    val GlassWhite = Color(0x1FFFFFFF)
    val GlassBorder = Color(0x3FFFFFFF)
    
    val AccentsGradient = Brush.linearGradient(
        colors = listOf(NeonCyan, NeonPurple)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    // Animation state
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumTheme.BackgroundGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            "NEURAL ANALYTICS", 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                color = Color.White
                            )
                        ) 
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp, start = 20.dp, end = 20.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    AnimatedEntrance(visible, 0) {
                        UsageHeroSection(uiState.appUsageHistory.firstOrNull()?.totalTimeSeconds ?: 0)
                    }
                }

                item {
                    AnimatedEntrance(visible, 200) {
                        SectionHeader("Live Activity", "Last 30 days insights")
                    }
                }

                item {
                    AnimatedEntrance(visible, 300) {
                        UsageActivityChart(uiState.appUsageHistory)
                    }
                }

                item {
                    AnimatedEntrance(visible, 400) {
                        SectionHeader("Domain Dominance", "Top chat interactions")
                    }
                }

                items(uiState.topChats.take(5)) { chat ->
                    AnimatedEntrance(visible, 500) {
                        ModernChatStats(chat)
                    }
                }

                item {
                    AnimatedEntrance(visible, 600) {
                        SectionHeader("Control Matrix", "System limits & overrides")
                    }
                }

                items(uiState.limits) { limit ->
                    AnimatedEntrance(visible, 700) {
                        PremiumLimitCard(limit)
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedEntrance(visible: Boolean, delay: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = delay)) +
                slideInVertically(animationSpec = tween(durationMillis = 600, delayMillis = delay)) { it / 2 } +
                scaleIn(initialScale = 0.9f, animationSpec = tween(durationMillis = 600, delayMillis = delay)),
    ) {
        content()
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = PremiumTheme.NeonCyan,
                letterSpacing = 1.sp
            )
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
        )
    }
}

@Composable
fun UsageHeroSection(totalTimeSeconds: Long) {
    val minutes = totalTimeSeconds / 60
    val progress = (minutes / 1440f).coerceIn(0f, 1f) // Relative to 24 hours
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(PremiumTheme.GlassWhite)
            .border(1.dp, PremiumTheme.GlassBorder, RoundedCornerShape(32.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        style = Stroke(width = 12.dp.toPx())
                    )
                    drawArc(
                        brush = PremiumTheme.AccentsGradient,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = minutes.toString(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "MINUTES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 2.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Overall Screen Interaction",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun UsageActivityChart(history: List<xyz.nextalone.nagram.analytics.data.AppUsageRecord>) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = PremiumTheme.GlassWhite)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    setTouchEnabled(false)
                    xAxis.isEnabled = false
                    axisLeft.isEnabled = false
                    axisRight.isEnabled = false
                    setDrawGridBackground(false)
                    setDrawBorders(false)
                }
            },
            update = { chart ->
                val entries = history.reversed().take(30).mapIndexed { index, record ->
                    Entry(index.toFloat(), record.totalTimeSeconds / 60f)
                }
                if (entries.isNotEmpty()) {
                    val dataSet = LineDataSet(entries, "Usage").apply {
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        setCircleColor(PremiumTheme.NeonCyan.hashCode())
                        color = PremiumTheme.NeonCyan.hashCode()
                        setDrawFilled(true)
                        fillDrawable = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(0x4400FFF0, 0x00000000)
                        )
                        setDrawCircles(false)
                        lineWidth = 3f
                        valueTextSize = 0f
                    }
                    chart.data = LineData(dataSet)
                    chart.invalidate()
                }
            }
        )
    }
}

@Composable
fun ModernChatStats(record: xyz.nextalone.nagram.analytics.data.ChatUsageRecord) {
    val progress = (record.timeSpentSeconds / 3600f).coerceIn(0f, 1f) // Relative to 1 hour for viz
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PremiumTheme.GlassWhite)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PremiumTheme.AccentsGradient)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        record.chatId.toString().take(1), 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Domain ${record.chatId}", 
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = PremiumTheme.NeonCyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${record.timeSpentSeconds / 60}m", 
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = PremiumTheme.NeonCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    "${record.messagesSent + record.messagesReceived} MSG", 
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun PremiumLimitCard(limit: xyz.nextalone.nagram.analytics.data.AnalyticsLimit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PremiumTheme.GlassWhite)
            .border(1.dp, if(limit.isEnabled) PremiumTheme.NeonCyan.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    if (limit.type == 0) "GLOBAL PROTOCOL" else "CHAT NODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PremiumTheme.NeonCyan,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    if (limit.type == 0) "Daily Consumption Limit" else "Targeted Interaction Limit",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
            }
            Switch(
                checked = limit.isEnabled, 
                onCheckedChange = {},
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PremiumTheme.NeonCyan,
                    checkedTrackColor = PremiumTheme.NeonCyan.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}
