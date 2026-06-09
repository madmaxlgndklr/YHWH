package com.madmaxlgndklr.yhwh.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            drawRect(Color.Black.copy(alpha = 0.78f))
            drawCircle(
                color = Color.Transparent,
                radius = spotR,
                center = Offset(spotX, spotY),
                blendMode = BlendMode.Clear
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = spotR + 4f,
                center = Offset(spotX, spotY),
                style = Stroke(width = 2f)
            )
            if (step == 2) {
                drawCircle(
                    color = Color(0xFF8080FF).copy(alpha = 0.4f),
                    radius = spotR * pulseScale,
                    center = Offset(spotX, spotY),
                    style = Stroke(width = 3f)
                )
            }
        }

        // Message card
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4466AA))
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
                    shape = CircleShape,
                    color = if (i == step) Color.White else Color.White.copy(alpha = 0.35f)
                ) {}
            }
        }
    }
}
