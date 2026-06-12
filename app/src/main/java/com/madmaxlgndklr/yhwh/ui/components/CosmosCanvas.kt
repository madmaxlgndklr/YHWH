package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.madmaxlgndklr.yhwh.engine.EpochType
import com.madmaxlgndklr.yhwh.engine.EvolutionEvent
import com.madmaxlgndklr.yhwh.ui.state.CosmosState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

private data class TapBurst(val position: Offset, val startTime: Long)

private const val BURST_DURATION_MS = 400L
private const val BURST_PARTICLE_COUNT = 7
private const val BURST_MAX_RADIUS = 80f

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
    val burstTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BURST_DURATION_MS.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "burst_tick"
    )

    val evolutionBgColor = when {
        state.mutationLevel < 0.5f -> {
            val t = state.mutationLevel * 2f
            Color(
                red = (0x00 + (0x1A * t).toInt()).coerceIn(0, 255) / 255f,
                green = (0x1A + (0x14 * t).toInt()).coerceIn(0, 255) / 255f,
                blue = (0x1A - (0x0A * t).toInt()).coerceIn(0, 255) / 255f,
                alpha = 1f
            )
        }
        else -> {
            val t = (state.mutationLevel - 0.5f) * 2f
            Color(
                red = (0x1A - (0x14 * t).toInt()).coerceIn(0, 255) / 255f,
                green = (0x2E + (0x12 * t).toInt()).coerceIn(0, 255) / 255f,
                blue = (0x10 - (0x08 * t).toInt()).coerceIn(0, 255) / 255f,
                alpha = 1f
            )
        }
    }

    val bgColor by animateColorAsState(
        targetValue = when (state.epoch) {
            EpochType.BIOLOGY -> Color(0xFF001A1A)
            EpochType.EVOLUTION -> evolutionBgColor
            EpochType.CIVILIZATION -> when (state.civEraLevel) {
                2 -> Color(0xFF1A0B00)   // Industrial
                1 -> Color(0xFF252018)   // Medieval
                else -> Color(0xFF3D2800) // Ancient
            }
            else -> if (state.planetsFormed) Color(0xFF001830) else Color(0xFF050510)
        },
        animationSpec = tween(durationMillis = 3000),
        label = "bg_color"
    )

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

            when (state.epoch) {
                EpochType.BIOLOGY -> {
                    if (state.aminoAcidLevel > 0f) drawOrganicParticles(state.aminoAcidLevel, orbitalAngle)
                    if (state.cellLevel > 0f) drawCellMembranes(state.cellLevel, glowPulse)
                }
                EpochType.EVOLUTION -> {
                    drawOrganismParticles(state.speciesLevel, orbitalAngle)
                    if (state.activeEvent != null) drawEventOverlay(state.activeEvent, glowPulse)
                }
                EpochType.CIVILIZATION -> {
                    when (state.civEraLevel) {
                        2 -> drawIndustrialParticles(state.civilizationLevel, orbitalAngle)
                        1 -> drawMedievalParticles(state.civilizationLevel, orbitalAngle)
                        else -> drawAncientParticles(state.civilizationLevel, orbitalAngle)
                    }
                    if (state.civilUnrestActive) drawCivilizationCrisisOverlay(glowPulse)
                }
                else -> {
                    if (state.matterLevel > 0f) drawMatterParticles(state.matterLevel)
                    if (state.starsFormed) drawStellarGlow(state.starLevel, glowPulse)
                    if (state.starsFormed) drawOrbitalRing(orbitalAngle, state.starLevel)
                    if (state.planetsFormed) drawPlanetRipple(glowPulse)
                }
            }

            @Suppress("UNUSED_EXPRESSION") burstTick
            val drawNow = System.currentTimeMillis()
            bursts.forEach { burst ->
                if (state.epoch == EpochType.CIVILIZATION) drawCivilizationBurst(burst, drawNow)
                else drawBurst(burst, drawNow)
            }
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

private fun DrawScope.drawOrganicParticles(aminoAcidLevel: Float, orbitalAngle: Float) {
    val count = (aminoAcidLevel * 40).toInt().coerceAtLeast(1)
    val rng = Random(seed = 13L)
    repeat(count) { i ->
        val baseX = rng.nextFloat()
        val baseY = rng.nextFloat()
        val driftX = sin((orbitalAngle + i * 37f) * Math.PI.toFloat() / 180f) * 0.02f
        val driftY = cos((orbitalAngle + i * 23f) * Math.PI.toFloat() / 180f) * 0.02f
        val x = (baseX + driftX).coerceIn(0f, 1f) * size.width
        val y = (baseY + driftY).coerceIn(0f, 1f) * size.height
        drawCircle(
            color = Color(0xFF44BB66).copy(alpha = aminoAcidLevel * 0.5f),
            radius = rng.nextFloat() * 3f + 1.5f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawCellMembranes(cellLevel: Float, pulse: Float) {
    val rng = Random(seed = 99L)
    repeat(4) { i ->
        val cx = (rng.nextFloat() * 0.6f + 0.2f) * size.width
        val cy = (rng.nextFloat() * 0.6f + 0.2f) * size.height
        val radius = size.minDimension * (0.06f + i * 0.03f) * (0.8f + pulse * 0.4f)
        drawCircle(
            color = Color(0xFF004040).copy(alpha = cellLevel * pulse * 0.6f),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
    }
}

private fun DrawScope.drawBurst(burst: TapBurst, now: Long) {
    val elapsed = (now - burst.startTime).coerceIn(0L, BURST_DURATION_MS)
    val progress = elapsed / BURST_DURATION_MS.toFloat()
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

private fun DrawScope.drawOrganismParticles(speciesLevel: Float, orbitalAngle: Float) {
    val baseCount = (speciesLevel * 30).toInt().coerceAtLeast(3)
    val rng = Random(seed = 77L)
    repeat(baseCount) { i ->
        val baseX = rng.nextFloat()
        val baseY = rng.nextFloat()
        val driftX = sin((orbitalAngle + i * 41f) * Math.PI.toFloat() / 180f) * 0.015f
        val driftY = cos((orbitalAngle + i * 29f) * Math.PI.toFloat() / 180f) * 0.015f
        val x = (baseX + driftX).coerceIn(0f, 1f) * size.width
        val y = (baseY + driftY).coerceIn(0f, 1f) * size.height

        val sizeVariance = if (speciesLevel > 0.3f) rng.nextFloat() * 5f + 2f else 2.5f
        val alpha = (0.4f + speciesLevel * 0.4f).coerceIn(0f, 1f)

        drawOval(
            color = Color(0xFF6DBF67).copy(alpha = alpha),
            topLeft = Offset(x - sizeVariance, y - sizeVariance * 0.6f),
            size = Size(sizeVariance * 2f, sizeVariance * 1.2f)
        )
    }
}

private fun DrawScope.drawEventOverlay(event: EvolutionEvent, pulse: Float) {
    when (event) {
        EvolutionEvent.ICE_AGE ->
            drawRect(
                color = Color(0xFF88CCFF).copy(alpha = 0.12f + pulse * 0.06f),
                size = size
            )
        EvolutionEvent.ASTEROID_IMPACT ->
            drawRect(
                color = Color(0xFFFF4444).copy(alpha = 0.08f + pulse * 0.04f),
                size = size
            )
        EvolutionEvent.VOLCANIC_WINTER ->
            drawRect(
                color = Color(0xFF886644).copy(alpha = 0.15f + pulse * 0.05f),
                size = size
            )
    }
}

private fun DrawScope.drawAncientParticles(civLevel: Float, orbitalAngle: Float) {
    val count = (civLevel * 30).toInt().coerceAtLeast(4)
    val rng = Random(seed = 31L)
    repeat(count) { i ->
        val baseX = rng.nextFloat()
        val baseY = rng.nextFloat()
        val driftX = sin((orbitalAngle + i * 27f) * Math.PI.toFloat() / 180f) * 0.01f
        val driftY = cos((orbitalAngle + i * 19f) * Math.PI.toFloat() / 180f) * 0.01f
        val x = (baseX + driftX).coerceIn(0f, 1f) * size.width
        val y = (baseY + driftY).coerceIn(0f, 1f) * size.height
        drawCircle(
            color = Color(0xFFFFAA44).copy(alpha = 0.3f + civLevel * 0.4f),
            radius = rng.nextFloat() * 2.5f + 1f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawMedievalParticles(civLevel: Float, orbitalAngle: Float) {
    val count = (civLevel * 20).toInt().coerceAtLeast(3)
    val rng = Random(seed = 53L)
    repeat(count) { i ->
        val baseX = rng.nextFloat()
        val baseY = rng.nextFloat()
        val driftX = sin((orbitalAngle + i * 33f) * Math.PI.toFloat() / 180f) * 0.008f
        val driftY = cos((orbitalAngle + i * 21f) * Math.PI.toFloat() / 180f) * 0.008f
        val x = (baseX + driftX).coerceIn(0f, 1f) * size.width
        val y = (baseY + driftY).coerceIn(0f, 1f) * size.height
        val w = rng.nextFloat() * 6f + 3f
        val h = w * (1.5f + rng.nextFloat())
        drawRect(
            color = Color(0xFF998877).copy(alpha = 0.35f + civLevel * 0.35f),
            topLeft = Offset(x - w / 2f, y - h / 2f),
            size = Size(w, h)
        )
    }
}

private fun DrawScope.drawIndustrialParticles(civLevel: Float, orbitalAngle: Float) {
    val cols = (civLevel * 8).toInt().coerceAtLeast(2)
    val rows = (civLevel * 6).toInt().coerceAtLeast(2)
    val spacingX = size.width / (cols + 1f)
    val spacingY = size.height / (rows + 1f)
    val rng = Random(seed = 71L)
    repeat(cols * rows) { i ->
        val col = i % cols
        val row = i / cols
        val jitterX = (rng.nextFloat() - 0.5f) * spacingX * 0.3f
        val jitterY = (rng.nextFloat() - 0.5f) * spacingY * 0.3f
        val x = spacingX * (col + 1f) + jitterX
        val y = spacingY * (row + 1f) + jitterY
        drawRect(
            color = Color(0xFFCC6600).copy(alpha = 0.25f + civLevel * 0.3f),
            topLeft = Offset(x - 3f, y - 5f),
            size = Size(6f, 10f)
        )
    }
}

private fun DrawScope.drawCivilizationCrisisOverlay(pulse: Float) {
    drawRect(
        color = Color(0xFFCC2200).copy(alpha = 0.10f + pulse * 0.08f),
        size = size
    )
}

private fun DrawScope.drawCivilizationBurst(burst: TapBurst, now: Long) {
    val elapsed = (now - burst.startTime).coerceIn(0L, BURST_DURATION_MS)
    val progress = elapsed / BURST_DURATION_MS.toFloat()
    val rng = Random(burst.startTime.toInt())
    repeat(BURST_PARTICLE_COUNT) { i ->
        val angle = (i.toFloat() / BURST_PARTICLE_COUNT) * 2f * Math.PI.toFloat() +
                rng.nextFloat() * 0.4f
        val distance = BURST_MAX_RADIUS * progress
        val px = burst.position.x + cos(angle) * distance
        val py = burst.position.y + sin(angle) * distance
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val headR = 2.5f * (1f - progress * 0.5f)
        drawCircle(
            color = Color(0xFFFFCC66).copy(alpha = alpha),
            radius = headR,
            center = Offset(px, py - headR * 1.5f)
        )
        drawRect(
            color = Color(0xFFFFAA44).copy(alpha = alpha * 0.8f),
            topLeft = Offset(px - headR * 0.6f, py - headR * 0.5f),
            size = Size(headR * 1.2f, headR * 2f)
        )
    }
}
