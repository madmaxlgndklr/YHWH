# Canvas Tap + Tutorial Coach Marks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the CosmosCanvas tappable for Quantum Fluctuation with a particle burst, and add a 3-step first-launch coach-mark tutorial with a Settings re-enable toggle.

**Architecture:** `TutorialPrefs` wraps `SharedPreferences` for persistence. `tutorialStep: Int` lives in `GameUiState` and is driven by `GameViewModel`. `TutorialOverlay` is a self-contained composable rendered in `GameScreen` when `tutorialStep in 1..3`. `CosmosCanvas` gains an `onTap` parameter with internal burst animation — no state leaks out.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose Material3, `pointerInput` / `detectTapGestures`, `SharedPreferences`, `BlendMode.Clear` for spotlight cutout, `graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }`

---

## File Map

**New:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/TutorialPrefs.kt`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/TutorialOverlay.kt`

**Modified:**
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` — add `tutorialStep: Int`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — TutorialPrefs init + tutorial methods
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — `onTap` param + `TapBurst`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt` — wire tap + overlay
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt` — tutorial toggle
- `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt` — share ViewModel with SettingsScreen

---

## Task 1: TutorialPrefs + GameUiState

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/TutorialPrefs.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt`

- [ ] **Step 1: Add `tutorialStep` to GameUiState**

Open `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt` and add one field:

```kotlin
package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.GeneratorSnapshot
import com.madmaxlgndklr.yhwh.engine.UpgradeSnapshot

data class GameUiState(
    val epochName: String = "",
    val tickDisplay: String = "Tick 0",
    val energyDisplay: String = "0",
    val matterDisplay: String = "0",
    val epochProgress: Float = 0f,
    val generators: List<GeneratorSnapshot> = emptyList(),
    val upgrades: List<UpgradeSnapshot> = emptyList(),
    val recentEvents: List<String> = emptyList(),
    val offlineEarningsSummary: String? = null,
    val showEpochTransition: Boolean = false,
    val transitionMessage: String = "",
    /**
     * 0 = uninitialized (ViewModel overwrites before first frame),
     * 1–3 = active tutorial step, 4 = complete/dismissed.
     */
    val tutorialStep: Int = 0
)
```

- [ ] **Step 2: Create TutorialPrefs.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui

import android.content.Context

/**
 * Persists tutorial completion state and the "show on next launch" flag
 * via SharedPreferences. All writes are applied immediately (apply()).
 */
class TutorialPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("yhwh_prefs", Context.MODE_PRIVATE)

    /** True once the player has dismissed all 3 steps at least once. */
    var completed: Boolean
        get() = prefs.getBoolean("tutorial_completed", false)
        set(v) = prefs.edit().putBoolean("tutorial_completed", v).apply()

    /**
     * When true, the tutorial will re-show on the next launch even if [completed].
     * The ViewModel resets this to false immediately on launch so it only fires once.
     */
    var enabledOnNextLaunch: Boolean
        get() = prefs.getBoolean("tutorial_enabled", true)
        set(v) = prefs.edit().putBoolean("tutorial_enabled", v).apply()
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd /home/madmaxlgndklr/Git/sandbox/YHWH
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/TutorialPrefs.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/GameUiState.kt
git commit -m "feat: add TutorialPrefs and tutorialStep to GameUiState"
```

---

## Task 2: GameViewModel Tutorial Integration

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt`

- [ ] **Step 1: Replace GameViewModel.kt with the updated version**

The changes are: import `TutorialPrefs`, add a `tutorialPrefs` field, initialize `tutorialStep` in `init`, add `onTutorialNext()` and `onTutorialReset(enabled: Boolean)`. The snapshot collector must preserve `tutorialStep` when it overwrites `_uiState`.

```kotlin
package com.madmaxlgndklr.yhwh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.madmaxlgndklr.yhwh.engine.GameEngine
import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import com.madmaxlgndklr.yhwh.engine.ResourceType
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.persistence.SaveManager
import com.madmaxlgndklr.yhwh.systems.CosmologySystem
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import com.madmaxlgndklr.yhwh.ui.state.GameUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val saveFile = File(application.filesDir, "yhwh_save.json")
    private val saveManager = SaveManager(saveFile)
    private val engine = GameEngine(scope = viewModelScope)
    private val tutorialPrefs = TutorialPrefs(application)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var epochTransitionAcknowledged = false

    private val _cosmosState = MutableStateFlow(CosmosState())
    val cosmosState: StateFlow<CosmosState> = _cosmosState.asStateFlow()

    init {
        // Determine initial tutorial step before the engine starts emitting
        val initialTutorialStep = when {
            !tutorialPrefs.completed || tutorialPrefs.enabledOnNextLaunch -> {
                tutorialPrefs.enabledOnNextLaunch = false  // consume the flag
                1
            }
            else -> 4
        }
        _uiState.value = _uiState.value.copy(tutorialStep = initialTutorialStep)

        engine.registerSystem(CosmologySystem())
        engine.onSaveDue = { snapshot ->
            withContext(Dispatchers.IO) { saveManager.save(snapshot) }
        }

        viewModelScope.launch {
            engine.snapshot.filterNotNull().collect { snapshot ->
                val newUiState = snapshot.toUiState()
                val showTransition = snapshot.epochProgress >= 1f &&
                        !epochTransitionAcknowledged &&
                        !_uiState.value.showEpochTransition
                _uiState.value = newUiState.copy(
                    showEpochTransition = showTransition || _uiState.value.showEpochTransition,
                    transitionMessage = if (showTransition)
                        "A world has formed. Life stirs in the primordial ocean."
                    else _uiState.value.transitionMessage,
                    offlineEarningsSummary = _uiState.value.offlineEarningsSummary,
                    tutorialStep = _uiState.value.tutorialStep  // preserve across ticks
                )
                _cosmosState.value = snapshot.toCosmosState()
            }
        }

        val saved = saveManager.load()
        if (saved != null) {
            val missed = saveManager.computeMissedTicks(saved.lastTickTimestamp)
            if (missed > 0) {
                _uiState.value = _uiState.value.copy(
                    offlineEarningsSummary = "You were away for ~${formatOfflineTime(missed)} — your generators kept working."
                )
            }
            engine.restore(saved.snapshot, missed)
        } else {
            engine.initNewGame()
        }

        engine.start()
    }

    fun onQuantumFluctuationTap() { engine.onPlayerTap() }

    fun onUpgradePurchase(upgradeId: String) { engine.purchaseUpgrade(upgradeId) }

    fun onGeneratorPurchase(generatorId: String) { engine.purchaseGenerator(generatorId) }

    fun dismissEpochTransition() {
        epochTransitionAcknowledged = true
        _uiState.value = _uiState.value.copy(showEpochTransition = false)
    }

    fun dismissOfflineSummary() {
        _uiState.value = _uiState.value.copy(offlineEarningsSummary = null)
    }

    /** Advance to the next tutorial step. Marks tutorial complete at step 4. */
    fun onTutorialNext() {
        val next = _uiState.value.tutorialStep + 1
        if (next >= 4) tutorialPrefs.completed = true
        _uiState.value = _uiState.value.copy(tutorialStep = next.coerceAtMost(4))
    }

    /**
     * Set whether the tutorial should re-show on next launch.
     * [enabled] = true schedules a re-show; false cancels a pending re-show.
     */
    fun onTutorialReset(enabled: Boolean) {
        tutorialPrefs.enabledOnNextLaunch = enabled
    }

    /** Expose the current tutorial preference for the Settings screen switch. */
    fun isTutorialResetPending(): Boolean = tutorialPrefs.enabledOnNextLaunch

    override fun onCleared() {
        super.onCleared()
        engine.stop()
        engine.snapshot.value?.let { snapshot ->
            viewModelScope.launch(Dispatchers.IO) { saveManager.save(snapshot) }
        }
    }

    private fun GameSnapshot.toUiState(): GameUiState {
        val energy = resources[ResourceType.ENERGY.name] ?: BigDouble.ZERO
        val matter = resources[ResourceType.MATTER.name] ?: BigDouble.ZERO
        return GameUiState(
            epochName = epoch.displayName,
            tickDisplay = "Tick $tick",
            energyDisplay = energy.toDisplayString(),
            matterDisplay = matter.toDisplayString(),
            epochProgress = epochProgress,
            generators = generators,
            upgrades = upgrades,
            recentEvents = events.map { it.message }.takeLast(5)
        )
    }

    private fun GameSnapshot.toCosmosState(): CosmosState {
        val matter = resources[ResourceType.MATTER.name] ?: BigDouble.ZERO
        val stars = resources[ResourceType.STARS.name] ?: BigDouble.ZERO
        val planets = resources[ResourceType.PLANETS.name] ?: BigDouble.ZERO
        return CosmosState(
            epoch = epoch,
            matterLevel = (matter.toDouble() / CosmologySystem.MATTER_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
            starLevel = (stars.toDouble() / CosmologySystem.STAR_VISUAL_THRESHOLD).toFloat().coerceIn(0f, 1f),
            starsFormed = stars > BigDouble.ZERO,
            planetsFormed = planets >= BigDouble.ONE
        )
    }

    private fun formatOfflineTime(ticks: Long): String = when {
        ticks < 60 -> "${ticks}s"
        ticks < 3600 -> "${ticks / 60}m"
        else -> "${ticks / 3600}h ${(ticks % 3600) / 60}m"
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run unit tests (confirm no regressions)**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew testDebugUnitTest 2>&1 | tail -10
```

Expected: `38 tests, 0 failures`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt
git commit -m "feat: add tutorial step tracking and prefs to GameViewModel"
```

---

## Task 3: TutorialOverlay Composable

**Files:**
- Create: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/TutorialOverlay.kt`

- [ ] **Step 1: Create TutorialOverlay.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity

/**
 * Defines where the spotlight is drawn for each tutorial step,
 * as fractions of screen width/height.
 */
private data class SpotlightSpec(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val radiusFraction: Float,
    val message: String,
    val cardBelowSpotlight: Boolean = true
)

private val STEPS = listOf(
    SpotlightSpec(
        centerXFraction = 0.78f,
        centerYFraction = 0.07f,
        radiusFraction = 0.15f,
        message = "⚡ Energy flows every second, automatically.\nIt powers your generators.",
        cardBelowSpotlight = true
    ),
    SpotlightSpec(
        centerXFraction = 0.50f,
        centerYFraction = 0.38f,
        radiusFraction = 0.28f,
        message = "⬡ Tap anywhere in the cosmos to trigger\na Quantum Fluctuation and generate Matter.",
        cardBelowSpotlight = true
    ),
    SpotlightSpec(
        centerXFraction = 0.50f,
        centerYFraction = 0.82f,
        radiusFraction = 0.30f,
        message = "Build generators in the Actions tab to\nautomate production and climb the resource chain.",
        cardBelowSpotlight = false
    )
)

/**
 * Full-screen tutorial coach-mark overlay.
 *
 * @param step Active step (1–3). Caller is responsible for not rendering when step < 1 or step > 3.
 * @param onNext Called when the player taps "Got it →" / "Let's go!".
 */
@Composable
fun TutorialOverlay(step: Int, onNext: () -> Unit) {
    val spec = STEPS.getOrNull(step - 1) ?: return

    // Pulsing ring animation for step 2 (canvas tap hint)
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val spotX = screenW * spec.centerXFraction
        val spotY = screenH * spec.centerYFraction
        val spotR = screenW * spec.radiusFraction

        // Scrim with spotlight cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            // Dark scrim
            drawRect(Color.Black.copy(alpha = 0.78f))
            // Transparent cutout
            drawCircle(
                color = Color.Transparent,
                radius = spotR,
                center = Offset(spotX, spotY),
                blendMode = BlendMode.Clear
            )
            // Highlight ring
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = spotR + 4f,
                center = Offset(spotX, spotY),
                style = Stroke(width = 2f)
            )
            // Pulsing ring for step 2
            if (step == 2) {
                drawCircle(
                    color = Color(0xFF8080FF).copy(alpha = 0.4f),
                    radius = spotR * pulseScale,
                    center = Offset(spotX, spotY),
                    style = Stroke(width = 3f)
                )
            }
        }

        // Message card — below spotlight unless step 3 (near bottom)
        val density = LocalDensity.current
        val cardTopDp = with(density) {
            if (spec.cardBelowSpotlight) {
                ((spotY + spotR + 24f) / density.density).dp
            } else {
                ((spotY - spotR - 160f) / density.density).dp
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = cardTopDp, start = 32.dp, end = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A4E))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = spec.message,
                    fontSize = 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4466AA)
                    )
                ) {
                    Text(
                        text = if (step == 3) "Let's go!" else "Got it →",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Step indicator dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..3).forEach { i ->
                Surface(
                    modifier = Modifier.size(if (i == step) 10.dp else 7.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (i == step) Color.White else Color.White.copy(alpha = 0.35f)
                ) {}
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/TutorialOverlay.kt
git commit -m "feat: add TutorialOverlay composable with 3-step coach marks and spotlight"
```

---

## Task 4: CosmosCanvas Tap + Burst Animation

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt`

- [ ] **Step 1: Replace CosmosCanvas.kt with the updated version**

Adds: `onTap: (() -> Unit)? = null` parameter, `TapBurst` data class, burst list state, `pointerInput` tap detector, `drawBursts()` draw function. All existing draw code is unchanged.

```kotlin
package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/** A single tap-burst effect at [position], animated over [BURST_DURATION_MS]. */
private data class TapBurst(val position: Offset, val startTime: Long)

private const val BURST_DURATION_MS = 400L
private const val BURST_PARTICLE_COUNT = 7
private const val BURST_MAX_RADIUS = 80f

/**
 * Animated canvas backdrop for the current epoch.
 *
 * @param onTap Optional callback triggered on each tap. When non-null, the canvas
 *              also shows a particle burst at the tap position.
 */
@Composable
fun CosmosCanvas(
    state: CosmosState,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val starField = remember { generateStarField(count = 150) }
    val bursts = remember { mutableStateListOf<TapBurst>() }

    val infiniteTransition = rememberInfiniteTransition(label = "cosmos")
    val orbitalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_angle"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )
    // Drive burst animation recompositions
    val burstTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BURST_DURATION_MS.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "burst_tick"
    )

    val bgColor by animateColorAsState(
        targetValue = if (state.planetsFormed) Color(0xFF001830) else Color(0xFF050510),
        animationSpec = tween(durationMillis = 3000),
        label = "bg_color"
    )

    // Clean up expired bursts
    val now = System.currentTimeMillis()
    bursts.removeAll { now - it.startTime > BURST_DURATION_MS }

    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                onTap()
                bursts.add(TapBurst(position = offset, startTime = System.currentTimeMillis()))
            }
        }
    } else Modifier

    Box(modifier = modifier.then(tapModifier)) {
        Canvas(modifier = Modifier.fillMaxSize().background(bgColor)) {
            drawStarField(starField)
            if (state.matterLevel > 0f) drawMatterParticles(state.matterLevel)
            if (state.starsFormed) drawStellarGlow(state.starLevel, glowPulse)
            if (state.starsFormed) drawOrbitalRing(orbitalAngle, state.starLevel)
            if (state.planetsFormed) drawPlanetRipple(glowPulse)
            // Draw all active bursts (burstTick read to trigger recompose)
            @Suppress("UNUSED_EXPRESSION") burstTick
            val drawNow = System.currentTimeMillis()
            bursts.forEach { burst -> drawBurst(burst, drawNow) }
        }
    }
}

private fun generateStarField(count: Int): List<Star> {
    val rng = Random(seed = 42L)
    return List(count) {
        Star(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            radius = rng.nextFloat() * 1.5f + 0.5f,
            alpha = rng.nextFloat() * 0.6f + 0.3f
        )
    }
}

private fun DrawScope.drawStarField(stars: List<Star>) {
    stars.forEach { star ->
        drawCircle(
            color = Color.White.copy(alpha = star.alpha),
            radius = star.radius,
            center = Offset(star.x * size.width, star.y * size.height)
        )
    }
}

private fun DrawScope.drawMatterParticles(matterLevel: Float) {
    val count = (matterLevel * 60).toInt().coerceAtLeast(1)
    val rng = Random(seed = 7L)
    repeat(count) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * size.height
        drawCircle(
            color = Color(0xFF8080FF).copy(alpha = matterLevel * 0.5f),
            radius = rng.nextFloat() * 3f + 1f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawStellarGlow(starLevel: Float, pulse: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.15f * (0.8f + starLevel * 0.4f) * pulse
    drawCircle(
        color = Color(0xFFFFDD88).copy(alpha = starLevel * pulse * 0.4f),
        radius = radius * 2.5f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFFFFEEAA).copy(alpha = starLevel * 0.8f),
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawOrbitalRing(angleDeg: Float, starLevel: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val orbitRadius = size.minDimension * 0.28f
    val dotRadius = 3f + starLevel * 3f
    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
    val dotX = cx + orbitRadius * cos(angleRad)
    val dotY = cy + orbitRadius * sin(angleRad)
    drawCircle(
        color = Color(0xFF4466AA).copy(alpha = 0.3f),
        radius = orbitRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f)
    )
    drawCircle(
        color = Color(0xFF88CCFF).copy(alpha = 0.9f),
        radius = dotRadius,
        center = Offset(dotX, dotY)
    )
}

private fun DrawScope.drawPlanetRipple(pulse: Float) {
    val cx = size.width / 2f
    val cy = size.height * 0.65f
    drawCircle(
        color = Color(0xFF2266AA).copy(alpha = (1f - pulse) * 0.4f),
        radius = size.minDimension * 0.12f * (0.7f + pulse * 0.6f),
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF44AA77).copy(alpha = 0.6f),
        radius = size.minDimension * 0.08f,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawBurst(burst: TapBurst, now: Long) {
    val elapsed = (now - burst.startTime).coerceIn(0L, BURST_DURATION_MS)
    val progress = elapsed / BURST_DURATION_MS.toFloat()  // 0→1
    val rng = Random(burst.startTime.toInt())
    repeat(BURST_PARTICLE_COUNT) { i ->
        val angle = (i.toFloat() / BURST_PARTICLE_COUNT) * 2f * Math.PI.toFloat() +
                rng.nextFloat() * 0.4f
        val distance = BURST_MAX_RADIUS * progress
        val px = burst.position.x + cos(angle) * distance
        val py = burst.position.y + sin(angle) * distance
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val radius = (4f * (1f - progress * 0.5f)).coerceAtLeast(1f)
        drawCircle(
            color = Color(0xFF8080FF).copy(alpha = alpha * 0.9f),
            radius = radius,
            center = Offset(px, py)
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt
git commit -m "feat: make CosmosCanvas tappable with particle burst animation"
```

---

## Task 5: GameScreen Wiring + AppNavigation

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt`
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt`

- [ ] **Step 1: Update GameScreen.kt**

Two changes: pass `onTap = viewModel::onQuantumFluctuationTap` to `CosmosCanvas`, and render `TutorialOverlay` when `tutorialStep in 1..3`.

```kotlin
package com.madmaxlgndklr.yhwh.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import com.madmaxlgndklr.yhwh.ui.components.ActionPanel
import com.madmaxlgndklr.yhwh.ui.components.CosmosCanvas
import com.madmaxlgndklr.yhwh.ui.components.GameTopBar
import com.madmaxlgndklr.yhwh.ui.components.TutorialOverlay

@Composable
fun GameScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cosmosState by viewModel.cosmosState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { GameTopBar(state = uiState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CosmosCanvas(
                state = cosmosState,
                onTap = viewModel::onQuantumFluctuationTap,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            ActionPanel(
                state = uiState,
                onTap = viewModel::onQuantumFluctuationTap,
                onUpgradePurchase = viewModel::onUpgradePurchase,
                onGeneratorPurchase = viewModel::onGeneratorPurchase,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Epoch transition overlay
        AnimatedVisibility(
            visible = uiState.showEpochTransition,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(paddingValues)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(text = "♁", fontSize = 64.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = uiState.transitionMessage,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = viewModel::dismissEpochTransition) {
                        Text("Continue")
                    }
                }
            }
        }

        // Tutorial coach-mark overlay — renders on top of everything
        if (uiState.tutorialStep in 1..3) {
            TutorialOverlay(
                step = uiState.tutorialStep,
                onNext = viewModel::onTutorialNext
            )
        }
    }
}
```

- [ ] **Step 2: Update AppNavigation.kt to share ViewModel with SettingsScreen**

`viewModel()` in Compose reuses the same instance within the same nav back-stack entry scope. To share between destinations, obtain the ViewModel at the NavHost level and pass it down.

```kotlin
package com.madmaxlgndklr.yhwh.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.madmaxlgndklr.yhwh.ui.GameViewModel
import com.madmaxlgndklr.yhwh.ui.screen.GameScreen
import com.madmaxlgndklr.yhwh.ui.screen.SettingsScreen

private object Routes {
    const val GAME = "game"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val gameViewModel: GameViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.GAME) {
        composable(Routes.GAME) {
            GameScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                viewModel = gameViewModel
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = gameViewModel)
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/GameScreen.kt \
        app/src/main/java/com/madmaxlgndklr/yhwh/navigation/AppNavigation.kt
git commit -m "feat: wire canvas tap and tutorial overlay into GameScreen"
```

---

## Task 6: SettingsScreen Tutorial Toggle

**Files:**
- Modify: `app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt`

- [ ] **Step 1: Replace SettingsScreen.kt**

```kotlin
package com.madmaxlgndklr.yhwh.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madmaxlgndklr.yhwh.ui.GameViewModel

@Composable
fun SettingsScreen(viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", fontSize = 22.sp, style = MaterialTheme.typography.headlineMedium)

        HorizontalDivider()

        // Tutorial toggle
        var tutorialPending by remember { mutableStateOf(viewModel.isTutorialResetPending()) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show tutorial on next launch", fontSize = 15.sp)
                Text(
                    "Replays the 3-step beginner guide",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = tutorialPending,
                onCheckedChange = { enabled ->
                    tutorialPending = enabled
                    viewModel.onTutorialReset(enabled)
                }
            )
        }
    }
}
```

- [ ] **Step 2: Verify full build**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
./gradlew assembleDebug testDebugUnitTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`, `38 tests, 0 failures`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/madmaxlgndklr/yhwh/ui/screen/SettingsScreen.kt
git commit -m "feat: add tutorial reset toggle to SettingsScreen"
```

---

## Task 7: Install on Device

- [ ] **Step 1: Connect device (if not already connected)**

```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb devices
```

If no device listed, reconnect:
```bash
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb connect 192.168.1.214:34239
```

- [ ] **Step 2: Install**

```bash
JAVA_HOME=/home/madmaxlgndklr/Android/android-studio/jbr \
ANDROID_HOME=/home/madmaxlgndklr/Android/Sdk \
/home/madmaxlgndklr/Android/Sdk/platform-tools/adb -s 192.168.1.214:34239 install -r \
  /home/madmaxlgndklr/Git/sandbox/YHWH/app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 3: Push to GitHub**

```bash
git push origin main
```

- [ ] **Step 4: Manual verification checklist**

- [ ] Tutorial appears on first launch (3 steps, correct messages per step)
- [ ] "Got it →" advances steps; step 3 shows "Let's go!"
- [ ] After step 3, tutorial is gone and does not reappear on next launch
- [ ] Settings → "Show tutorial on next launch" toggle reappears after navigating back to Settings
- [ ] Enabling the toggle then killing/relaunching the app shows the tutorial again
- [ ] Tapping the cosmos outside of tutorial mode generates Matter (check TopBar ⬡ counter)
- [ ] Particle burst appears at tap point and fades within ~400ms
- [ ] Quantum Fluctuation button in Actions tab still works as a fallback

---

## Self-Review

- [x] **Spec §3 Canvas tap** — `CosmosCanvas` gains `onTap` param, `pointerInput` tap detector, `TapBurst` internal animation (Task 4)
- [x] **Spec §3 Fallback button** — Actions tab Quantum Fluctuation button unchanged (Task 5)
- [x] **Spec §4 Step 1** — Energy chip spotlight at top-right (Task 3 `STEPS[0]`)
- [x] **Spec §4 Step 2** — Canvas center spotlight with pulsing ring (Task 3 `STEPS[1]`, step==2 branch)
- [x] **Spec §4 Step 3** — Actions tab area spotlight (Task 3 `STEPS[2]`)
- [x] **Spec §4 tutorialStep 0→4** — `GameUiState.tutorialStep` field (Task 1), ViewModel init sets to 1 or 4 (Task 2)
- [x] **Spec §4 Persistence** — `TutorialPrefs` with `completed` + `enabledOnNextLaunch` (Task 1)
- [x] **Spec §4 enabledOnNextLaunch consumed on launch** — ViewModel init sets `enabledOnNextLaunch = false` immediately (Task 2)
- [x] **Spec §5 onTutorialNext / onTutorialReset(Boolean)** — both methods in ViewModel (Task 2)
- [x] **Spec §6 Settings toggle** — `Switch` wired to `onTutorialReset(enabled)`, reads `isTutorialResetPending()` (Task 6)
- [x] **Spec §6 ViewModel shared to SettingsScreen** — obtained at `AppNavigation` level (Task 5)
- [x] **Type consistency** — `tutorialStep: Int` used consistently across `GameUiState`, `GameViewModel`, `GameScreen`, `TutorialOverlay`
