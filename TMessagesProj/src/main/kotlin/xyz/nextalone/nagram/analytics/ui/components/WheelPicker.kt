package xyz.nextalone.nagram.analytics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.nextalone.nagram.analytics.ui.theme.LocalAnalyticsColors

@Composable
fun WheelPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    val c = LocalAnalyticsColors.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val snappingLayout = rememberSnapFlingBehavior(lazyListState = listState)
    
    // Internal synchronization: When scrolling stops, notify the parent
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            if (centerIndex in range) {
                onValueChange(centerIndex)
            }
        }
    }
    
    // External synchronization: If value changes externally (buttons), scroll to it
    LaunchedEffect(value) {
        if (listState.firstVisibleItemIndex != value) {
            listState.animateScrollToItem(value)
        }
    }

    Column(
        modifier = modifier.width(70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label.isNotEmpty()) {
            Text(
                label,
                color = c.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Box(
            modifier = Modifier
                .height(130.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Highlight Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(c.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            )
            
            LazyColumn(
                state = listState,
                flingBehavior = snappingLayout,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 45.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(range.last - range.first + 1) { index ->
                    val itemValue = range.first + index
                    val isSelected = listState.firstVisibleItemIndex == index
                    
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = itemValue.toString().padStart(2, '0'),
                            color = if (isSelected) c.textPrimary else c.textSecondary,
                            fontSize = if (isSelected) 22.sp else 18.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                            modifier = Modifier.alpha(if (isSelected) 1f else 0.4f)
                        )
                    }
                }
            }
        }
    }
}
