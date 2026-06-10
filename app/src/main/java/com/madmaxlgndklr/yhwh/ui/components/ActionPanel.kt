package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot
import com.madmaxlgndklr.yhwh.ui.state.GameUiState

@Composable
fun ActionPanel(
    state: GameUiState,
    onTap: () -> Unit,
    onUpgradePurchase: (String) -> Unit,
    onGeneratorPurchase: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Actions", "Upgrades", "Stats")

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp)) {
            when (selectedTab) {
                0 -> ActionsTab(state.generators, onTap, onGeneratorPurchase)
                1 -> UpgradesTab(state.upgrades, onUpgradePurchase)
                2 -> StatsTab(state)
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
                Text("⚡ Quantum Fluctuation", fontSize = 16.sp)
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
                    "Lv.${gen.level}  Cost: ${gen.costAmount.toDisplayString()} ${gen.costType.symbol}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onPurchase(gen.id) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("▲", fontSize = 14.sp)
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
private fun StatsTab(state: GameUiState) {
    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.offlineEarningsSummary?.let { summary ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(text = summary, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
        Text("Epoch Progress", fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { state.epochProgress },
            modifier = Modifier.fillMaxWidth()
        )
        Text("${(state.epochProgress * 100).toInt()}% to ${state.nextEpochName}", fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (state.recentEvents.isNotEmpty()) {
            Text("Recent Events", fontWeight = FontWeight.Bold)
            state.recentEvents.forEach { event ->
                Text("• $event", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
