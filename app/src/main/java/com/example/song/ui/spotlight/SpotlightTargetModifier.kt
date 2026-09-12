package com.example.song.ui.spotlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

fun Modifier.spotlightTarget(
    step: TourStep,
    controller: SpotlightController?
): Modifier = if (controller != null) {
    this.onGloballyPositioned { coordinates ->
        controller.registerBounds(step, coordinates.boundsInRoot())
    }
} else {
    this
}
