package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madmaxlgndklr.yhwh.data.remote.SupabaseModule
import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot
import com.madmaxlgndklr.yhwh.ui.AuthState
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import com.madmaxlgndklr.yhwh.ui.state.ResourceDisplay
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle

@Composable
fun ActionPanel(
    state: GameUiState,
    viewModel: GameViewModel,
    onTap: () -> Unit,
    onUpgradePurchase: (String) -> Unit,
    onGeneratorPurchase: (String) -> Unit,
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
                2 -> StatsTab(state, viewModel)
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
private fun StatsTab(state: GameUiState, viewModel: GameViewModel) {
    // Must be outside LazyColumn — activity result launchers can't register inside lazy items
    val googleSignIn = SupabaseModule.client.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            if (result is NativeSignInResult.Success) viewModel.onGoogleSignInSuccess()
        }
    )

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

        // Account section
        item {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            AccountSection(viewModel, onGoogleSignIn = { googleSignIn.startFlow() })
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

@Composable
private fun AccountSection(viewModel: GameViewModel, onGoogleSignIn: () -> Unit) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val loading by viewModel.authLoading.collectAsStateWithLifecycle()
    val error by viewModel.authError.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Account", fontWeight = FontWeight.Bold)

        when (val s = authState) {
            is AuthState.SignedIn -> {
                Text(
                    s.email ?: "Google Account",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign Out", fontSize = 13.sp)
                }
            }
            is AuthState.Anonymous -> {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to "Sign In", true to "Create Account").forEach { (signup, label) ->
                        TextButton(
                            onClick = { isSignUp = signup; viewModel.clearAuthError() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                color = if (isSignUp == signup) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSignUp == signup) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        if (isSignUp) viewModel.signUpWithEmail(email, password)
                        else viewModel.signInWithEmail(email, password)
                    })
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            keyboard?.hide()
                            if (isSignUp) viewModel.signUpWithEmail(email, password)
                            else viewModel.signInWithEmail(email, password)
                        },
                        enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(if (isSignUp) "Create" else "Sign In", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = onGoogleSignIn,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("Google", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
