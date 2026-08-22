package com.example.song.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A battery-optimized, cinematic fluid mesh background for the Pulse app.
 * Automatically freezes in Power Saving Mode and suspends when off-screen.
 */
@Composable
fun FluidMeshBackground(
    modifier: Modifier = Modifier,
    showOrbs: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    
    // 🔋 Power Saver Detection
    val isPowerSaveMode by produceState(initialValue = false) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        while (true) {
            value = powerManager.isPowerSaveMode
            delay(5000) // Polling every 5s is efficient enough for UI state
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "FluidMeshTransition")

    // Master animation progress (0f to 1f over 8 seconds)
    // ⚡ Automatic Frame Clock Pausing: infiniteTransition natively suspends when composable is not visible.
    val progressState = if (isPowerSaveMode || !showOrbs) {
        remember { mutableStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "MasterProgress"
        )
    }
    val progress by progressState

    Box(modifier = modifier.fillMaxSize()) {
        // Background Drawing Layer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 🔋 Optimization: Remove expensive blur filter in Power Save Mode or when orbs are hidden
                .then(if (isPowerSaveMode || !showOrbs) Modifier else Modifier.blur(80.dp))
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val width = size.width
            val height = size.height

            // 1. Base Canvas: Near-black radial background gradient
            drawRect(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFF110726),
                    0.5f to Color(0xFF070D1F),
                    1.0f to Color(0xFF0C0618),
                    center = center,
                    radius = size.minDimension
                )
            )

            if (showOrbs && !isPowerSaveMode) {
                // 2. Animated Orbs with Asynchronous Phase Delays
                // Phase Offsets: Violet: 0s, Cobalt: -2.4s, Magenta: -5.1s, Coral: -3.7s
                // (Relative to 8s loop: 0f, -0.3f, -0.6375f, -0.4625f)
                
                // Orb 1 (Violet): Top-left anchor, clockwise drift
                drawOrb(
                    color = Color(0xFF8A2BE2),
                    progress = (progress + 0.000f) % 1f,
                    center = center,
                    radius = width * 0.6f,
                    movement = { p ->
                        val angle = p * 2 * PI
                        Offset(
                            x = cos(angle).toFloat() * width * 0.15f - width * 0.2f,
                            y = sin(angle).toFloat() * height * 0.15f - height * 0.2f
                        )
                    }
                )

                // Orb 2 (Cobalt): Top-right, counter-clockwise sweep
                drawOrb(
                    color = Color(0xFF1E40AF),
                    progress = (progress + 0.700f) % 1f, // -2.4s phase
                    center = center,
                    radius = width * 0.7f,
                    movement = { p ->
                        val angle = -p * 2 * PI
                        Offset(
                            x = cos(angle).toFloat() * width * 0.2f + width * 0.25f,
                            y = sin(angle).toFloat() * height * 0.1f - height * 0.25f
                        )
                    }
                )

                // Orb 3 (Magenta): Center-bottom, figure-8 pulse
                drawOrb(
                    color = Color(0xFFC026D3),
                    progress = (progress + 0.3625f) % 1f, // -5.1s phase
                    center = center,
                    radius = width * 0.55f,
                    movement = { p ->
                        val angle = p * 2 * PI
                        Offset(
                            x = sin(angle).toFloat() * width * 0.25f,
                            y = sin(angle * 2).toFloat() * height * 0.15f + height * 0.25f
                        )
                    }
                )

                // Orb 4 (Coral): Bottom-right, inward arc
                drawOrb(
                    color = Color(0xFFF43F5E),
                    progress = (progress + 0.5375f) % 1f, // -3.7s phase
                    center = center,
                    radius = width * 0.5f,
                    movement = { p ->
                        val angle = p * 2 * PI
                        Offset(
                            x = cos(angle).toFloat() * width * 0.15f + width * 0.3f,
                            y = cos(angle).toFloat() * height * 0.15f + height * 0.3f
                        )
                    }
                )
            }
        }

        // Overlay existing UI content
        content()
    }
}

/**
 * Draws a single fluid orb with radial falloff and additive blending.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrb(
    color: Color,
    progress: Float,
    center: Offset,
    radius: Float,
    movement: (Float) -> Offset
) {
    val offset = movement(progress)
    
    // 0% === 100% Rule: Ensure scale and position loop perfectly
    // Movement logic already uses 2*PI to ensure 0% == 100%
    
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to color.copy(alpha = 0.8f),
            0.7f to Color.Transparent, // Double-soft falloff at 70%
            center = center + offset,
            radius = radius
        ),
        radius = radius,
        center = center + offset,
        blendMode = BlendMode.Screen // Additive blending
    )
}
