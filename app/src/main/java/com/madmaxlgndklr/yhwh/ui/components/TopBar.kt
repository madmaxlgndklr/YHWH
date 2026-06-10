package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import com.madmaxlgndklr.yhwh.ui.state.ResourceDisplay
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTopBar(state: GameUiState) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = state.epochName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = state.tickDisplay,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 8.dp)
                ) {
                    items(state.resources) { resource ->
                        ResourceChip(resource)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ResourceChip(resource: ResourceDisplay) {
    var showName by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier = Modifier.clickable { showName = true },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = resource.symbol, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary)
            Text(
                text = resource.value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        if (showName) {
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { showName = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = resource.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
            LaunchedEffect(Unit) {
                delay(1500)
                showName = false
            }
        }
    }
}
