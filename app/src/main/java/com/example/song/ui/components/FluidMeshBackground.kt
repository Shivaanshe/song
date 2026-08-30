package com.example.song.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.song.R

/**
 * A spec-accurate horizontal panning background.
 * Uses a single continuous asset and maps pager progress to translation.
 */
@Composable
fun FluidMeshBackground(
    modifier: Modifier = Modifier,
    pagerOffset: () -> Float = { 0f }, // Passed as a lambda to avoid full recomposition
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 🖼️ The Panning Asset
        Image(
            painter = painterResource(id = R.drawable.screen),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 🔋 Parallax logic - Executed on RenderThread/Layer level
                    scaleX = 1.2f
                    scaleY = 1.2f
                    
                    val offset = pagerOffset()
                    val maxPan = size.width * 0.1f
                    translationX = maxPan - (offset * maxPan)
                },
            contentScale = ContentScale.Crop
        )

        // 🌑 Dark Scrim for Readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
        )

        // Overlay existing UI content
        content()
    }
}
