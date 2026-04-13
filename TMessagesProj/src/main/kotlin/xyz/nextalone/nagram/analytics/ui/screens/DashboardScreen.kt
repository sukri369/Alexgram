package xyz.nextalone.nagram.analytics.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.nextalone.nagram.analytics.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics Dashboard", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                UsageSummaryCard(uiState.appUsageHistory.firstOrNull()?.totalTimeSeconds ?: 0)
            }

            item {
                Text("Top Chats", style = MaterialTheme.typography.titleLarge)
            }

            items(uiState.topChats) { chat ->
                ChatUsageItem(chat)
            }

            item {
                Text("Limits", style = MaterialTheme.typography.titleLarge)
            }

            items(uiState.limits) { limit ->
                LimitItem(limit)
            }
        }
    }
}

@Composable
fun UsageSummaryCard(totalTime: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Total Usage Today", style = MaterialTheme.typography.labelLarge)
            Text("${totalTime / 60} minutes", style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
fun ChatUsageItem(record: xyz.nextalone.nagram.analytics.data.ChatUsageRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Chat ID: ${record.chatId}")
                Text("${record.timeSpentSeconds / 60}m spent", style = MaterialTheme.typography.bodySmall)
            }
            Text("${record.messagesSent + record.messagesReceived} msgs")
        }
    }
}

@Composable
fun LimitItem(limit: xyz.nextalone.nagram.analytics.data.AnalyticsLimit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (limit.type == 0) "Daily App Limit" else "Chat Limit")
            Switch(checked = limit.isEnabled, onCheckedChange = {})
        }
    }
}
