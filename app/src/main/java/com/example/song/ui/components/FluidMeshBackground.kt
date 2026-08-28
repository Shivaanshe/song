package com.example.song.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A highly optimized, scroll-reactive gradient background.
 * Zero CPU usage when static, subtle parallax effect during scroll.
 */
@Composable
fun FluidMeshBackground(
    modifier: Modifier = Modifier,
    scrollOffset: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 🔋 Subtle parallax shift based on scroll
            // Shifting by 15% of scroll amount creates a deep layered look
            val parallaxShift = scrollOffset * 0.15f
            
            // Base Deep Gradient
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color(0xFF0F0821),
                    0.3f to Color(0xFF1A0B40),
                    0.6f to Color(0xFF0D1435),
                    1.0f to Color(0xFF1E0A2E),
                    start = Offset(0f, -parallaxShift),
                    end = Offset(width, height - parallaxShift)
                )
            )
            
            // Secondary Glow Layer for depth (Static)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE91E63).copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(width * 0.8f, height * 0.2f),
                    radius = width
                )
            )
        }
        content()
    }
}
