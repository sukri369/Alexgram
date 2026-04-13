package xyz.nextalone.nagram.analytics.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import androidx.compose.material3.MaterialTheme

@Composable
fun UsageChart(usageData: List<Long>) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        factory = { context ->
            BarChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                legend.isEnabled = false
                axisLeft.textColor = textColor
                axisRight.isEnabled = false
                xAxis.textColor = textColor
                setDrawGridBackground(false)
                setTouchEnabled(false)
            }
        },
        update = { chart ->
            val entries = usageData.mapIndexed { index, value ->
                BarEntry(index.toFloat(), value.toFloat() / 60) // in minutes
            }
            val dataSet = BarDataSet(entries, "Usage").apply {
                color = primaryColor
                setDrawValues(false)
            }
            chart.data = BarData(dataSet)
            chart.invalidate()
        }
    )
}
