package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import com.madmaxlgndklr.yhwh.ui.state.ResourceDisplay

@Composable
fun ActionPanel(
    state: GameUiState,
    onTap: () -> Unit,
    onUpgradePurchase: (String) -> Unit,
    onGeneratorPurchase: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Actions", "Upgrades", "Stats")

    Column(modifier = modifier) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1A1A4E),
            contentColor = Color.White
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, color = Color.White) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp)) {
            when (selectedTab) {
                0 -> ActionsTab(state.generators, onTap, onGeneratorPurchase)
                1 -> UpgradesTab(state.upgrades, onUpgradePurchase)
                2 -> StatsTab(state, onNavigateToSettings)
            }
        }
    }
}

@Composable
private fun ActionsTab(
    generators: List<GeneratorSnapshot>,
    onTap: () -> Unit,
    onGeneratorPurchase: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Button(onClick = onTap, modifier = Modifier.fillMaxWidth()) {
                Text("✨ Quantum Fluctuation", fontSize = 16.sp)
            }
        }
        items(generators.filter { it.unlocked }) { gen ->
            GeneratorCard(gen, onGeneratorPurchase)
        }
    }
}

@Composable
private fun GeneratorCard(gen: GeneratorSnapshot, onPurchase: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(gen.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "+${gen.productionRate.toDisplayString()} ${gen.productionType.symbol}/tick",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Lv.${gen.level}  Next: ${gen.nextLevelCost.toDisplayString()} ${gen.costType.symbol}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onPurchase(gen.id) },
                enabled = gen.canAfford,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (gen.level == 0) "Buy" else "▲", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun UpgradesTab(upgrades: List<UpgradeSnapshot>, onPurchase: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(upgrades.filter { !it.purchased || it.repeatable }) { upg ->
            UpgradeCard(upg, onPurchase)
        }
    }
}

@Composable
private fun UpgradeCard(upg: UpgradeSnapshot, onPurchase: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (upg.available)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(upg.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(upg.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Cost: ${upg.costAmount.toDisplayString()} ${upg.costType.symbol}", fontSize = 11.sp)
            }
            Button(
                onClick = { onPurchase(upg.id) },
                enabled = upg.available,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (upg.repeatable) "Use" else "Buy")
            }
        }
    }
}

@Composable
private fun StatsTab(state: GameUiState, onNavigateToSettings: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Settings link
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSettings)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", fontSize = 15.sp)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
        }

        // Epoch progress
        item {
            Text("Epoch Progress", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { state.epochProgress }, modifier = Modifier.fillMaxWidth())
            Text("${(state.epochProgress * 100).toInt()}% to ${state.nextEpochName}", fontSize = 12.sp)
        }

        // All resources across all epochs
        if (state.allResources.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("All Resources", fontWeight = FontWeight.Bold)
            }
            items(state.allResources) { res ->
                ResourceRow(res)
            }
        }

        // Restart counter
        if (state.restartCount > 0) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Restarts: ${state.restartCount}  " +
                    "(×${"%.2f".format(state.activeSeedMultiplier)} production seed)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Recent events
        if (state.recentEvents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Recent Events", fontWeight = FontWeight.Bold)
            }
            items(state.recentEvents) { event ->
                Text("• $event", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResourceRow(res: ResourceDisplay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${res.symbol} ${res.displayName}", fontSize = 13.sp)
        Text(res.value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
