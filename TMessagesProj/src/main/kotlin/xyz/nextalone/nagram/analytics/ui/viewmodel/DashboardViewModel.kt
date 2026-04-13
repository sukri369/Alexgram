package xyz.nextalone.nagram.analytics.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import xyz.nextalone.nagram.analytics.data.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dao: AnalyticsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val today = getTodayTimestamp()
        viewModelScope.launch {
            combine(
                dao.getAppUsageFlow(30), // Last 30 days
                dao.getTopChatsFlow(today),
                dao.getAllLimitsFlow()
            ) { appUsage, topChats, limits ->
                DashboardUiState(
                    appUsageHistory = appUsage,
                    topChats = topChats,
                    limits = limits
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

data class DashboardUiState(
    val appUsageHistory: List<AppUsageRecord> = emptyList(),
    val topChats: List<ChatUsageRecord> = emptyList(),
    val limits: List<AnalyticsLimit> = emptyList()
)
