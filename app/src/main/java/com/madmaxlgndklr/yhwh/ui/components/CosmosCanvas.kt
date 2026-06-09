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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
            // Read burstTick to force recompose each animation frame
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
