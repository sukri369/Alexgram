package xyz.nextalone.nagram.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import xyz.nextalone.nagram.analytics.data.AnalyticsLimit
import xyz.nextalone.nagram.analytics.ui.theme.LocalAnalyticsColors
import xyz.nextalone.nagram.analytics.ui.theme.NeonCyan
import xyz.nextalone.nagram.analytics.ui.theme.NeonGreen
import xyz.nextalone.nagram.analytics.ui.theme.NeonOrange
import xyz.nextalone.nagram.analytics.ui.theme.NeonPink
import xyz.nextalone.nagram.analytics.ui.theme.NeonPurple
import xyz.nextalone.nagram.analytics.ui.viewmodel.ChatUsageInfo
import xyz.nextalone.nagram.analytics.ui.viewmodel.DashboardViewModel

// ─── Avatar palette (shared) ─────────────────────────────────────────────────
private val avatarPalette = listOf(
    0xFF1565C0.toInt(), 0xFF6A1B9A.toInt(), 0xFF00695C.toInt(),
    0xFF558B2F.toInt(), 0xFF4527A0.toInt(), 0xFF0277BD.toInt(),
    0xFFAD1457.toInt(), 0xFF4E342E.toInt(), 0xFF37474F.toInt(),
    0xFFE65100.toInt(), 0xFF1B5E20.toInt(), 0xFF311B92.toInt()
)

// ─── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()
    val c = LocalAnalyticsColors.current
    val listState = rememberLazyListState()

    // Derived alpha for status bar guard
    val statusBarAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 200f).coerceIn(0f, 1f)
        }
    }

    // Dialog state
    var showLimitDialog by remember { mutableStateOf(false) }
    var editingLimit by remember { mutableStateOf<AnalyticsLimit?>(null) }
    var lockTargetChat by remember { mutableStateOf<ChatUsageInfo?>(null) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Box(modifier = Modifier.fillMaxSize().background(c.bgScreen)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item { TopHeroBar() }

            item {
                AnimatedIn(isVisible, 50) {
                    UsageStatsRow(
                        todayMinutes = uiState.todayMinutes,
                        weekMinutes = uiState.weekMinutes,
                        totalSessions = uiState.totalSessionsAllTime,
                        appUsageHistory = uiState.appUsageHistory
                    )
                }
            }

            // ── Live Activity ─────────────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 150) {
                    SectionLabel("LIVE ACTIVITY", "Last 14 days usage", Icons.Default.ShowChart, NeonCyan)
                }
            }
            item {
                AnimatedIn(isVisible, 200) {
                    ActivityBarChart(uiState.appUsageHistory)
                }
            }

            // ── Domain Dominance ──────────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 300) {
                    SectionLabel("DOMAIN DOMINANCE", "All-time chat breakdown", Icons.Default.Forum, NeonPurple)
                }
            }
            if (uiState.topChats.isEmpty()) {
                item {
                    AnimatedIn(isVisible, 340) {
                        EmptyCard("Start chatting — data will appear here automatically")
                    }
                }
            } else {
                items(uiState.topChats, key = { it.chatId }) { chatInfo ->
                    AnimatedIn(isVisible, 400) {
                        ChatDominanceRow(
                            chatInfo = chatInfo,
                            maxTime = uiState.topChats.maxOf { it.timeSpentSeconds }.coerceAtLeast(1L),
                            onLongPress = { lockTargetChat = chatInfo }
                        )
                    }
                }
            }

            // ── Control Matrix ─────────────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 500) {
                    SectionLabel("CONTROL MATRIX", "Daily limits & overrides", Icons.Default.Tune, NeonPink)
                }
            }
            items(uiState.limits, key = { "${it.type}_${it.targetId}" }) { limit ->
                AnimatedIn(isVisible, 540) {
                    ControlLimitCard(
                        limit = limit,
                        onToggle = { vm.toggleLimit(it) },
                        onEdit = {
                            editingLimit = it
                            showLimitDialog = true
                        },
                        onDelete = { vm.deleteLimit(it) }
                    )
                }
            }
            item {
                AnimatedIn(isVisible, 580) {
                    AddLimitButton(onAdd = {
                        editingLimit = null
                        showLimitDialog = true
                    })
                }
            }

            // ── Session Insights ───────────────────────────────────────────────
            item {
                AnimatedIn(isVisible, 650) {
                    SectionLabel("SESSION INSIGHTS", "All-time message analytics", Icons.Default.Analytics, NeonGreen)
                }
            }
            item {
                AnimatedIn(isVisible, 700) {
                    SessionInsightsCard(uiState)
                }
            }

            // ── Footer ─────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Neural Analytics · Alexgram",
                    color = c.textSecondary.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }

        // ── Status Bar Guard (Sticky) ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(c.bgPrimary.copy(alpha = statusBarAlpha * 0.95f))
        )
    }

    // ── Chat Lock Bottom Sheet ────────────────────────────────────────────────
    lockTargetChat?.let { target ->
        ChatLockSheet(
            chatInfo = target,
            onDismiss = { lockTargetChat = null },
            onLock = { mins ->
                if (target.isLocked) vm.unlockChat(target.chatId)
                else vm.lockChat(target.chatId, mins)
                lockTargetChat = null
            }
        )
    }

    // ── Limit Editor Dialog ───────────────────────────────────────────────────
    if (showLimitDialog) {
        LimitEditorDialog(
            existing = editingLimit,
            onDismiss = { showLimitDialog = false },
            onSave = { hours, mins, enabled ->
                vm.setAppLimit(hours, mins, enabled)
                showLimitDialog = false
            }
        )
    }
}

// ─── Top Hero Bar ─────────────────────────────────────────────────────────────

@Composable
fun TopHeroBar() {
    val c = LocalAnalyticsColors.current
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(c.bgHero)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 16.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val dotAlpha by pulse.animateFloat(
                    0.4f, 1f,
                    infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "dot"
                )
                Box(Modifier.size(7.dp).background(NeonCyan.copy(alpha = dotAlpha), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Analytics, null, tint = NeonCyan.copy(0.5f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Neural Analytics", color = c.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Text("Your digital behaviour, decoded", color = c.textSecondary, fontSize = 13.sp)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(
        Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan.copy(0.5f), Color.Transparent))
    ))
}

// ─── Usage Stats Row ──────────────────────────────────────────────────────────

@Composable
fun UsageStatsRow(
    todayMinutes: Long,
    weekMinutes: Long,
    totalSessions: Int,
    appUsageHistory: List<xyz.nextalone.nagram.analytics.data.AppUsageRecord>
) {
    val c = LocalAnalyticsColors.current
    val todaySecs = appUsageHistory.firstOrNull()?.totalTimeSeconds ?: 0L
    val progress = (todaySecs / (24f * 3600f)).coerceIn(0f, 1f)
    val todayH = todayMinutes / 60; val todayM = todayMinutes % 60
    val weekH = weekMinutes / 60;   val weekM = weekMinutes % 60
    val activeDays = appUsageHistory.count { it.totalTimeSeconds > 0 }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        GlassCard {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                // Circular ring
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(108.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(c.bgCardAlt, style = Stroke(9.dp.toPx()))
                        drawArc(c.accentGradient, -90f, 360f * progress, false,
                            style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (todayH > 0) "${todayH}h\n${todayM}m" else "${todayM}m",
                            color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            lineHeight = 20.sp
                        )
                        Text("TODAY", color = c.textSecondary, fontSize = 8.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    StatRow("Today", if (todayH > 0) "${todayH}h ${todayM}m" else "${todayM}m", NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    Spacer(Modifier.height(8.dp))
                    StatRow("This Week", if (weekH > 0) "${weekH}h ${weekM}m" else "${weekM}m", NeonPurple)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip(Modifier.weight(1f), "Sessions", totalSessions.toString(), NeonGreen)
            StatChip(Modifier.weight(1f), "Active Days", activeDays.toString(), NeonOrange)
            val avgMins = if (activeDays > 0) weekMinutes / activeDays else 0L
            StatChip(Modifier.weight(1f), "Avg/Day", "${avgMins}m", NeonPink)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    val c = LocalAnalyticsColors.current
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = c.textSecondary, fontSize = 12.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatChip(modifier: Modifier, label: String, value: String, color: Color) {
    val c = LocalAnalyticsColors.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(c.bgCard)
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = c.textSecondary, fontSize = 10.sp)
        }
    }
}

// ─── Activity Chart ───────────────────────────────────────────────────────────

@Composable
fun ActivityBarChart(history: List<xyz.nextalone.nagram.analytics.data.AppUsageRecord>) {
    val c = LocalAnalyticsColors.current
    GlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(155.dp).padding(10.dp)) {
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BarChart, null, tint = c.textSecondary.copy(0.4f), modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Activity tracked after first session", color = c.textSecondary, fontSize = 11.sp)
                    }
                }
            } else {
                val isDark = c.isDark
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        BarChart(ctx).apply {
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
                            val maxVal = entries.maxOf { it.y }
                            val barColors = entries.map { e ->
                                val ratio = if (maxVal > 0) e.y / maxVal else 0f
                                if (isDark) {
                                    when {
                                        ratio > 0.7f -> AndroidColor.parseColor("#00E5FF")
                                        ratio > 0.4f -> AndroidColor.parseColor("#7B2FFF")
                                        else         -> AndroidColor.parseColor("#3A1A6A")
                                    }
                                } else {
                                    when {
                                        ratio > 0.7f -> AndroidColor.parseColor("#0077AA")
                                        ratio > 0.4f -> AndroidColor.parseColor("#5500AA")
                                        else         -> AndroidColor.parseColor("#BBCAF0")
                                    }
                                }
                            }
                            val dataSet = BarDataSet(entries, "").apply {
                                colors = barColors
                                setDrawValues(false)
                                barBorderWidth = 0f
                            }
                            chart.data = BarData(dataSet).also { it.barWidth = 0.65f }
                            chart.animateY(700, com.github.mikephil.charting.animation.Easing.EaseInOutCubic)
                            chart.invalidate()
                        }
                    }
                )
                Text(
                    "Last 14 days",
                    color = c.textSecondary.copy(0.6f), fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                )
            }
        }
    }
}

// ─── Section Label ─────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(title: String, subtitle: String, icon: ImageVector, color: Color) {
    val c = LocalAnalyticsColors.current
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).background(color.copy(0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            Text(subtitle, color = c.textSecondary, fontSize = 11.sp)
        }
    }
}

// ─── Chat Dominance Row ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDominanceRow(
    chatInfo: ChatUsageInfo,
    maxTime: Long,
    onLongPress: () -> Unit
) {
    val c = LocalAnalyticsColors.current
    val progress = (chatInfo.timeSpentSeconds / maxTime.toFloat()).coerceIn(0f, 1f)
    val mins = chatInfo.timeSpentSeconds / 60
    val secs = chatInfo.timeSpentSeconds % 60
    val timeLabel = when {
        mins >= 60 -> "${mins / 60}h ${mins % 60}m"
        mins > 0   -> "${mins}m"
        else       -> "${secs}s"
    }
    val msgTotal = chatInfo.messagesSent + chatInfo.messagesReceived

    // Avatar color from palette
    val avatarColor = remember(chatInfo.chatId) {
        val idx = ((chatInfo.chatId % avatarPalette.size).toInt().let {
            if (it < 0) it + avatarPalette.size else it
        })
        Color(avatarPalette[idx])
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgCard)
            .border(
                1.dp,
                if (chatInfo.isLocked) NeonPink.copy(0.5f) else c.border,
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Real Avatar via Telegram's BackupImageView ────────────────────────
        TelegramAvatar(
            chatId = chatInfo.chatId,
            initial = chatInfo.initial,
            fallbackColor = avatarColor,
            size = 44
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chatInfo.displayName,
                    color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chatInfo.isLocked) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Lock, null, tint = NeonPink, modifier = Modifier.size(13.dp))
                }
            }
            val typeLabel = when {
                chatInfo.isChannel -> "Channel"
                chatInfo.isGroup   -> "Group"
                chatInfo.isUser    -> "Contact"
                else               -> "Chat"
            }
            Text(typeLabel, color = c.textSecondary, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = NeonCyan, trackColor = c.border
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(timeLabel, color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("${msgTotal} MSG", color = c.textSecondary, fontSize = 10.sp)
            if (chatInfo.mediaCount > 0) {
                Text("${chatInfo.mediaCount} media", color = NeonPurple.copy(0.7f), fontSize = 9.sp)
            }
        }
    }
}

// ─── Telegram Avatar (BackupImageView) ──────────────────────────────────────

@Composable
fun TelegramAvatar(chatId: Long, initial: String, fallbackColor: Color, size: Int) {
    val sizeDp = size.dp
    AndroidView(
        modifier = Modifier.size(sizeDp).clip(CircleShape),
        factory = { ctx ->
            BackupImageView(ctx).apply {
                val avatarPx = (size * ctx.resources.displayMetrics.density).toInt()
                layoutParams = android.view.ViewGroup.LayoutParams(avatarPx, avatarPx)
                setRoundRadius(avatarPx / 2)

                try {
                    val account = UserConfig.selectedAccount
                    val mc = MessagesController.getInstance(account)
                    val avatarDrawable = AvatarDrawable()

                    if (chatId > 0) {
                        val user: TLRPC.User? = mc.getUser(chatId)
                        if (user != null) {
                            avatarDrawable.setInfo(account, user)
                            setForUserOrChat(user, avatarDrawable)
                        } else {
                            // Fallback: draw a colored avatar with the initial letter
                            avatarDrawable.setColor(
                                fallbackColor.red.times(255).toInt() shl 16 or
                                (fallbackColor.green.times(255).toInt() shl 8) or
                                fallbackColor.blue.times(255).toInt() or 0xFF000000.toInt()
                            )
                            setImageDrawable(avatarDrawable)
                        }
                    } else {
                        val chat: TLRPC.Chat? = mc.getChat(-chatId)
                        if (chat != null) {
                            avatarDrawable.setInfo(account, chat)
                            setForUserOrChat(chat, avatarDrawable)
                        } else {
                            avatarDrawable.setColor(
                                fallbackColor.red.times(255).toInt() shl 16 or
                                (fallbackColor.green.times(255).toInt() shl 8) or
                                fallbackColor.blue.times(255).toInt() or 0xFF000000.toInt()
                            )
                            setImageDrawable(avatarDrawable)
                        }
                    }
                } catch (e: Exception) {
                    val avatarDrawable = AvatarDrawable()
                    avatarDrawable.setColor(0xFF455A64.toInt())
                    setImageDrawable(avatarDrawable)
                }
            }
        }
    )
}

// ─── Control Limit Card ───────────────────────────────────────────────────────

@Composable
fun ControlLimitCard(
    limit: AnalyticsLimit,
    onToggle: (AnalyticsLimit) -> Unit,
    onEdit: (AnalyticsLimit) -> Unit,
    onDelete: (AnalyticsLimit) -> Unit
) {
    val c = LocalAnalyticsColors.current
    val enabled = limit.isEnabled
    val h = limit.dailyLimitSeconds / 3600
    val m = (limit.dailyLimitSeconds % 3600) / 60
    val limitLabel = when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0          -> "${h}h"
        m > 0          -> "${m}m"
        else           -> "Not set"
    }

    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        borderColor = if (enabled) NeonPink.copy(0.4f) else c.border
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(
                        if (enabled) NeonPink.copy(0.15f) else c.bgCardAlt,
                        RoundedCornerShape(10.dp)
                    ), contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (limit.type == 0) Icons.Default.Timer else Icons.Default.LockClock,
                        null, tint = if (enabled) NeonPink else c.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (limit.type == 0) "DAILY APP LIMIT" else "CHAT LIMIT",
                        color = if (enabled) NeonPink else c.textSecondary,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                    Text(
                        if (limit.type == 0) "Daily App Time Limit" else "Per-Chat Limit",
                        color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle(limit) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeonPink.copy(0.8f),
                        uncheckedThumbColor = c.textSecondary,
                        uncheckedTrackColor = c.bgCardAlt
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Spacer(Modifier.height(10.dp))

            // Time display + Edit/Delete
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, null, tint = NeonCyan, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Limit: $limitLabel", color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onEdit(limit) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Edit, null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", color = NeonCyan, fontSize = 12.sp)
                }
                TextButton(onClick = { onDelete(limit) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Delete, null, tint = NeonPink.copy(0.7f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ─── Add Limit Button ─────────────────────────────────────────────────────────

@Composable
fun AddLimitButton(onAdd: () -> Unit) {
    val c = LocalAnalyticsColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgCard)
            .border(1.dp, NeonPink.copy(0.3f), RoundedCornerShape(16.dp))
            .clickable { onAdd() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AddCircle, null, tint = NeonPink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Set Daily Limit", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap to configure a custom time limit", color = c.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ─── Session Insights ─────────────────────────────────────────────────────────

@Composable
fun SessionInsightsCard(state: xyz.nextalone.nagram.analytics.ui.viewmodel.DashboardUiState) {
    val si = state.sessionInsights
    val c = LocalAnalyticsColors.current

    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("All-Time Breakdown", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InsightTile(Modifier.weight(1f), "Sent", si.totalSent.toString(), NeonCyan, Icons.Default.Send)
                InsightTile(Modifier.weight(1f), "Received", si.totalReceived.toString(), NeonPurple, Icons.Default.Inbox)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InsightTile(Modifier.weight(1f), "Media", si.totalMedia.toString(), NeonOrange, Icons.Default.Image)
                InsightTile(Modifier.weight(1f), "Chats", si.uniqueChats.toString(), NeonGreen, Icons.Default.Chat)
            }
            if (si.totalTimeSeconds > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                Spacer(Modifier.height(10.dp))
                val totalH = si.totalTimeSeconds / 3600
                val totalM = (si.totalTimeSeconds % 3600) / 60
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Total chat time: ", color = c.textSecondary, fontSize = 12.sp)
                    Text(
                        if (totalH > 0) "${totalH}h ${totalM}m" else "${totalM}m",
                        color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InsightTile(modifier: Modifier, label: String, value: String, color: Color, icon: ImageVector) {
    val c = LocalAnalyticsColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.07f))
            .border(1.dp, color.copy(0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(value, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = c.textSecondary, fontSize = 10.sp)
        }
    }
}

// ─── Chat Lock Bottom Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatLockSheet(
    chatInfo: ChatUsageInfo,
    onDismiss: () -> Unit,
    onLock: (autoUnlockMins: Int) -> Unit
) {
    val c = LocalAnalyticsColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMins by remember { mutableIntStateOf(0) } // 0 = permanent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.bgCard,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {

            // Handle bar
            Box(Modifier.width(40.dp).height(4.dp).background(c.border, CircleShape).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(
                        if (chatInfo.isLocked) NeonGreen.copy(0.15f) else NeonPink.copy(0.15f),
                        CircleShape
                    ), contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (chatInfo.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        null,
                        tint = if (chatInfo.isLocked) NeonGreen else NeonPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (chatInfo.isLocked) "Unlock Chat?" else "Lock Chat",
                        color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text(chatInfo.displayName, color = c.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!chatInfo.isLocked) {
                Text("Auto-unlock timer", color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))

                // Timer options
                val options = listOf(
                    0   to "Permanent",
                    30  to "30 minutes",
                    60  to "1 hour",
                    120 to "2 hours",
                    480 to "8 hours",
                    1440 to "24 hours"
                )
                options.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (mins, label) ->
                            val sel = selectedMins == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) NeonPink.copy(0.2f) else c.bgCardAlt)
                                    .border(1.dp, if (sel) NeonPink else c.border, RoundedCornerShape(12.dp))
                                    .clickable { selectedMins = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (mins == 0) Icons.Default.Lock else Icons.Default.Timer,
                                        null, tint = if (sel) NeonPink else c.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(label, color = if (sel) NeonPink else c.textSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                        // Fill remaining slots if row < 3 items
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onLock(selectedMins) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (chatInfo.isLocked) NeonGreen.copy(0.3f) else NeonPink.copy(0.9f)
                )
            ) {
                Icon(
                    if (chatInfo.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    null, tint = Color.White, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (chatInfo.isLocked) "Unlock This Chat" else "Lock This Chat",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ─── Limit Editor Dialog ──────────────────────────────────────────────────────

@Composable
fun LimitEditorDialog(
    existing: AnalyticsLimit?,
    onDismiss: () -> Unit,
    onSave: (hours: Int, minutes: Int, enabled: Boolean) -> Unit
) {
    val c = LocalAnalyticsColors.current
    val initH = existing?.let { (it.dailyLimitSeconds / 3600).toInt() } ?: 1
    val initM = existing?.let { ((it.dailyLimitSeconds % 3600) / 60).toInt() } ?: 0

    var hours by remember { mutableIntStateOf(initH) }
    var minutes by remember { mutableIntStateOf(initM) }
    var enabled by remember { mutableStateOf(existing?.isEnabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        title = {
            Text(
                if (existing == null) "Set Daily App Limit" else "Edit Limit",
                color = c.textPrimary, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Choose your daily usage limit", color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))

                // Hours picker
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hours", color = c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (hours > 0) hours-- }) {
                            Icon(Icons.Default.Remove, null, tint = NeonCyan)
                        }
                        Text(
                            "$hours", color = NeonCyan, fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.widthIn(min = 36.dp).wrapContentWidth()
                        )
                        IconButton(onClick = { if (hours < 23) hours++ }) {
                            Icon(Icons.Default.Add, null, tint = NeonCyan)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Minutes picker (increments of 5)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Minutes", color = c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { minutes = ((minutes - 5 + 60) % 60) }) {
                            Icon(Icons.Default.Remove, null, tint = NeonPurple)
                        }
                        Text(
                            "${minutes.toString().padStart(2, '0')}",
                            color = NeonPurple, fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.widthIn(min = 36.dp).wrapContentWidth()
                        )
                        IconButton(onClick = { minutes = (minutes + 5) % 60 }) {
                            Icon(Icons.Default.Add, null, tint = NeonPurple)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Preview
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(NeonCyan.copy(0.07f))
                        .border(1.dp, NeonCyan.copy(0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Limit: ${if (hours > 0) "${hours}h " else ""}${if (minutes > 0) "${minutes.toString().padStart(2, '0')}m" else if (hours == 0) "0m" else ""}",
                        color = NeonCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable immediately", color = c.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled, onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeonPink.copy(0.8f),
                            uncheckedThumbColor = c.textSecondary,
                            uncheckedTrackColor = c.bgCardAlt
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(hours, minutes, enabled) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPink.copy(0.8f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Limit", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        }
    )
}

// ─── Empty Card ───────────────────────────────────────────────────────────────

@Composable
fun EmptyCard(message: String) {
    val c = LocalAnalyticsColors.current
    Box(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgCard)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.HourglassEmpty, null, tint = c.textSecondary.copy(0.5f), modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(message, color = c.textSecondary, fontSize = 12.sp)
        }
    }
}

// ─── Reusable Components ──────────────────────────────────────────────────────

@Composable
fun GlassCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable () -> Unit) {
    val c = LocalAnalyticsColors.current
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.bgCard).border(1.dp, borderColor ?: c.border, RoundedCornerShape(20.dp))) {
        content()
    }
}

@Composable
fun AnimatedIn(visible: Boolean, delay: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(500, delay)) + slideInVertically(tween(500, delay)) { it / 3 }) {
        content()
    }
}
