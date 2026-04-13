package xyz.nextalone.nagram.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import android.graphics.Color as AndroidColor
import xyz.nextalone.nagram.analytics.data.AnalyticsLimit
import xyz.nextalone.nagram.analytics.ui.viewmodel.ChatUsageInfo
import xyz.nextalone.nagram.analytics.ui.viewmodel.DashboardViewModel

// ─── Premium Design System ────────────────────────────────────────────────────

private val BgDark      = Color(0xFF0D0F1A)
private val BgCard      = Color(0xFF161927)
private val BgCardAlt   = Color(0xFF1C2033)
private val NeonCyan    = Color(0xFF00E5FF)
private val NeonPurple  = Color(0xFFAA00FF)
private val NeonPink    = Color(0xFFE91E8C)
private val NeonGreen   = Color(0xFF00E676)
private val NeonOrange  = Color(0xFFFF6D00)
private val TextPrimary = Color(0xFFEEF0F8)
private val TextSecond  = Color(0xFF8A94B0)
private val GlassBorder = Color(0xFF2A2F4A)

private val AccentGradient = Brush.linearGradient(listOf(NeonCyan, NeonPurple))
private val HeroGradient   = Brush.verticalGradient(listOf(Color(0xFF1A1F3C), Color(0xFF0D0F1A)))
private val BgGradient     = Brush.verticalGradient(listOf(BgDark, Color(0xFF0A0C16)))

// Avatar colors for different chat types
private val avatarColors = listOf(
    0xFF1565C0.toInt(), 0xFF6A1B9A.toInt(), 0xFF00695C.toInt(),
    0xFF558B2F.toInt(), 0xFF4527A0.toInt(), 0xFF0277BD.toInt(),
    0xFFAD1457.toInt(), 0xFF4E342E.toInt(), 0xFF37474F.toInt()
)

// ─── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGradient)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 0.dp, start = 0.dp, end = 0.dp, bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Top Bar ──────────────────────────────────────────────────────
            item {
                TopHeroBar()
            }

            // ── Today + Week stats ───────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 50) {
                    UsageStatsRow(
                        todayMinutes = uiState.todayMinutes,
                        weekMinutes = uiState.weekMinutes,
                        appUsageHistory = uiState.appUsageHistory
                    )
                }
            }

            // ── Activity Chart ────────────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 150) {
                    SectionLabel(
                        title = "LIVE ACTIVITY",
                        subtitle = "Last 30 days usage",
                        icon = Icons.Default.Insights,
                        iconTint = NeonCyan
                    )
                }
            }
            item {
                AnimatedIn(isVisible, 200) {
                    ActivityBarChart(uiState.appUsageHistory)
                }
            }

            // ── Domain Dominance ─────────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 300) {
                    SectionLabel(
                        title = "DOMAIN DOMINANCE",
                        subtitle = "Top chat interactions today",
                        icon = Icons.Default.Forum,
                        iconTint = NeonPurple
                    )
                }
            }

            if (uiState.topChats.isEmpty()) {
                item {
                    AnimatedIn(isVisible, 350) {
                        EmptyState("No chat activity recorded yet")
                    }
                }
            } else {
                items(uiState.topChats.take(8), key = { it.record.chatId }) { chatInfo ->
                    AnimatedIn(isVisible, 400) {
                        ChatDominanceRow(chatInfo)
                    }
                }
            }

            // ── Control Matrix ────────────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 500) {
                    SectionLabel(
                        title = "CONTROL MATRIX",
                        subtitle = "System limits & overrides",
                        icon = Icons.Default.Tune,
                        iconTint = NeonPink
                    )
                }
            }

            items(uiState.limits, key = { "${it.type}_${it.targetId}" }) { limit ->
                AnimatedIn(isVisible, 550) {
                    ControlLimitCard(limit, onToggle = { vm.toggleLimit(it) })
                }
            }

            // If no limits configured, show add-limit card
            if (uiState.limits.isEmpty()) {
                item {
                    AnimatedIn(isVisible, 580) {
                        AddFirstLimitCard(onAdd = {
                            vm.setLimit(
                                AnalyticsLimit(
                                    type = 0,
                                    targetId = 0,
                                    dailyLimitSeconds = 3600,
                                    isEnabled = false
                                )
                            )
                        })
                    }
                }
            }

            // ── Chat Session Insights ─────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 650) {
                    SectionLabel(
                        title = "SESSION INSIGHTS",
                        subtitle = "Message breakdown",
                        icon = Icons.Default.BarChart,
                        iconTint = NeonGreen
                    )
                }
            }

            item {
                AnimatedIn(isVisible, 700) {
                    SessionInsightsCard(uiState.topChats)
                }
            }

            // ── Footer ────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(32.dp))
                Text(
                    "Neural Analytics · Alexgram",
                    color = TextSecond.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ─── Top Hero Bar ─────────────────────────────────────────────────────────────

@Composable
fun TopHeroBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeroGradient)
            .padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Animated neon dot
                val pulse = rememberInfiniteTransition(label = "pulse")
                val dotAlpha by pulse.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "dot"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NeonCyan.copy(alpha = dotAlpha), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "LIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = NeonCyan.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Neural Analytics",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Text(
                "Your digital behaviour, decoded",
                color = TextSecond,
                fontSize = 13.sp
            )
        }
    }

    // Divider glow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, NeonCyan.copy(alpha = 0.5f), Color.Transparent)
                )
            )
    )
}

// ─── Usage Stats Row ──────────────────────────────────────────────────────────

@Composable
fun UsageStatsRow(
    todayMinutes: Long,
    weekMinutes: Long,
    appUsageHistory: List<xyz.nextalone.nagram.analytics.data.AppUsageRecord>
) {
    val todaySeconds = appUsageHistory.firstOrNull()?.totalTimeSeconds ?: 0L
    val progress = (todaySeconds / (24f * 3600f)).coerceIn(0f, 1f)
    val hours = todayMinutes / 60
    val mins = todayMinutes % 60

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        // Hero ring card
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Arc ring
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = android.graphics.Color.parseColor("#1A2040").toComposeColor(),
                            style = Stroke(width = 10.dp.toPx())
                        )
                        drawArc(
                            brush = AccentGradient,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("TODAY", color = TextSecond, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Screen Time", color = TextSecond, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Today",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (hours > 0) "${hours}h ${mins}m" else "${mins} minutes",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorder, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("This Week", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (weekMinutes >= 60) "${weekMinutes / 60}h ${weekMinutes % 60}m"
                        else "${weekMinutes}m",
                        color = NeonPurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Quick stat chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val sessions = appUsageHistory.firstOrNull()?.sessionCount ?: 0
            val totalDays = appUsageHistory.size
            StatChip(
                modifier = Modifier.weight(1f),
                label = "Sessions",
                value = sessions.toString(),
                color = NeonGreen
            )
            StatChip(
                modifier = Modifier.weight(1f),
                label = "Active Days",
                value = totalDays.toString(),
                color = NeonOrange
            )
            StatChip(
                modifier = Modifier.weight(1f),
                label = "Avg/Day",
                value = if (totalDays > 0) "${weekMinutes / maxOf(totalDays.toLong(), 1)}m" else "0m",
                color = NeonPink
            )
        }
    }
}

@Composable
fun StatChip(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = TextSecond, fontSize = 10.sp)
        }
    }
}

// ─── Activity Chart ───────────────────────────────────────────────────────────

@Composable
fun ActivityBarChart(history: List<xyz.nextalone.nagram.analytics.data.AppUsageRecord>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        GlassCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        BarChart(context).apply {
                            description.isEnabled = false
                            legend.isEnabled = false
                            setTouchEnabled(false)
                            xAxis.isEnabled = false
                            axisLeft.isEnabled = false
                            axisRight.isEnabled = false
                            setDrawGridBackground(false)
                            setDrawBorders(false)
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            setNoDataText("")
                        }
                    },
                    update = { chart ->
                        val data = history.reversed().takeLast(14)
                        val entries = data.mapIndexed { i, r ->
                            BarEntry(i.toFloat(), r.totalTimeSeconds / 60f)
                        }
                        if (entries.isNotEmpty()) {
                            val dataSet = BarDataSet(entries, "").apply {
                                val gradientColors = entries.map { _ ->
                                    // Alternate cyan/purple for visual depth
                                    AndroidColor.parseColor("#00E5FF")
                                }
                                colors = gradientColors
                                setDrawValues(false)
                                barBorderWidth = 0f
                            }
                            val barData = BarData(dataSet).apply {
                                barWidth = 0.6f
                            }
                            chart.data = barData
                            chart.animateY(800, com.github.mikephil.charting.animation.Easing.EaseInOutCubic)
                            chart.invalidate()
                        }
                    }
                )
                // "Last 14 days" label
                Text(
                    "Last 14 days",
                    color = TextSecond,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }
    }
}

// ─── Section Label ─────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(title: String, subtitle: String, icon: ImageVector, iconTint: Color) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                title,
                color = iconTint,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Text(subtitle, color = TextSecond, fontSize = 11.sp)
        }
    }
}

// ─── Chat Dominance Row ────────────────────────────────────────────────────────

@Composable
fun ChatDominanceRow(chatInfo: ChatUsageInfo) {
    val record = chatInfo.record
    val totalSecs = record.timeSpentSeconds
    val maxSecs = 3600L // 1h as 100% for progress bar
    val progress = (totalSecs / maxSecs.toFloat()).coerceIn(0f, 1f)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val timeLabel = if (mins > 0) "${mins}m" else "${secs}s"
    val msgCount = record.messagesSent + record.messagesReceived

    // Pick avatar color based on chatId hash
    val avatarBg = remember(chatInfo.record.chatId) {
        val idx = (chatInfo.record.chatId % avatarColors.size).toInt().let {
            if (it < 0) it + avatarColors.size else it
        }
        val argb = avatarColors[idx]
        Color(argb)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with initial
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(
                        listOf(avatarBg, avatarBg.copy(alpha = 0.6f))
                    ),
                    CircleShape
                )
                .border(1.5.dp, NeonCyan.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chatInfo.initial,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Real chat name
            Text(
                chatInfo.displayName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Type badge
            val typeLabel = when {
                chatInfo.isChannel -> "Channel"
                chatInfo.isGroup   -> "Group"
                chatInfo.isUser    -> "Contact"
                else               -> "Chat"
            }
            Text(typeLabel, color = TextSecond, fontSize = 10.sp)

            Spacer(Modifier.height(5.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = NeonCyan,
                trackColor = GlassBorder
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                timeLabel,
                color = NeonCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "${msgCount} MSG",
                color = TextSecond,
                fontSize = 10.sp
            )
            if (record.mediaCount > 0) {
                Text(
                    "${record.mediaCount} 🖼",
                    color = NeonPurple.copy(alpha = 0.8f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ─── Control Limit Card ────────────────────────────────────────────────────────

@Composable
fun ControlLimitCard(limit: AnalyticsLimit, onToggle: (AnalyticsLimit) -> Unit) {
    val isEnabled = limit.isEnabled
    val borderColor = if (isEnabled) NeonPink.copy(alpha = 0.4f) else GlassBorder

    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        borderColor = borderColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isEnabled) NeonPink.copy(alpha = 0.15f) else BgCardAlt,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (limit.type == 0) Icons.Default.Timer else Icons.Default.LockClock,
                    contentDescription = null,
                    tint = if (isEnabled) NeonPink else TextSecond,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (limit.type == 0) "GLOBAL PROTOCOL" else "CHAT NODE",
                    color = if (isEnabled) NeonPink else TextSecond,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    if (limit.type == 0) "Daily App Time Limit" else "Per-Chat Time Limit",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (limit.dailyLimitSeconds > 0) {
                    val h = limit.dailyLimitSeconds / 3600
                    val m = (limit.dailyLimitSeconds % 3600) / 60
                    Text(
                        "Limit: ${if (h > 0) "${h}h " else ""}${m}m",
                        color = TextSecond,
                        fontSize = 11.sp
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle(limit) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeonPink.copy(alpha = 0.8f),
                    uncheckedThumbColor = TextSecond,
                    uncheckedTrackColor = BgCardAlt
                )
            )
        }
    }
}

// ─── Empty / Add cards ────────────────────────────────────────────────────────

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = TextSecond,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextSecond, fontSize = 13.sp)
        }
    }
}

@Composable
fun AddFirstLimitCard(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, NeonPink.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable { onAdd() }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = NeonPink,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Set Daily Limit", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap to configure app time limit", color = TextSecond, fontSize = 11.sp)
            }
        }
    }
}

// ─── Session Insights Card ────────────────────────────────────────────────────

@Composable
fun SessionInsightsCard(chats: List<ChatUsageInfo>) {
    val totalSent = chats.sumOf { it.record.messagesSent }
    val totalReceived = chats.sumOf { it.record.messagesReceived }
    val totalMedia = chats.sumOf { it.record.mediaCount }
    val totalText = chats.sumOf { it.record.textCount }
    val uniqueChats = chats.size

    GlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Today's Breakdown", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InsightMetric(Modifier.weight(1f), "Sent", totalSent.toString(), NeonCyan, Icons.Default.Send)
                InsightMetric(Modifier.weight(1f), "Received", totalReceived.toString(), NeonPurple, Icons.Default.Inbox)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InsightMetric(Modifier.weight(1f), "Media", totalMedia.toString(), NeonOrange, Icons.Default.Image)
                InsightMetric(Modifier.weight(1f), "Active Chats", uniqueChats.toString(), NeonGreen, Icons.Default.Chat)
            }
        }
    }
}

@Composable
fun InsightMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color,
    icon: ImageVector
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.07f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = TextSecond, fontSize = 10.sp)
        }
    }
}

// ─── Glass Card wrapper ───────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        content()
    }
}

// ─── Animated Entrance ────────────────────────────────────────────────────────

@Composable
fun AnimatedIn(visible: Boolean, delay: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delay)) + slideInVertically(tween(500, delay)) { it / 3 }
    ) {
        content()
    }
}

// ─── Helper extension ─────────────────────────────────────────────────────────

private fun Int.toComposeColor(): Color = Color(this)
