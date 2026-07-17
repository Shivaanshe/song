package com.example.song.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A highly robust multi-selection drag handler that works with high-refresh rate screens.
 * It uses the Initial pass to intercept gestures before child components (like clickables)
 * can consume them, ensuring "sticky" selection during rapid movement.
 */
fun Modifier.multiSelectDragHandler(
    listState: LazyListState,
    isSelectionMode: Boolean,
    onSelect: (Any) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {}
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    
    // Track current finger position for the auto-scroll loop
    val dragYState = remember { mutableFloatStateOf(-1f) }

    // Auto-scroll loop: Drives the list scroll when the finger is near the edges
    LaunchedEffect(Unit) {
        while (isActive) {
            val y = dragYState.floatValue
            if (y != -1f) {
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                
                if (viewportHeight > 0) {
                    val scrollThresholdUp = viewportHeight * 0.15f
                    val scrollThresholdDown = viewportHeight * 0.85f
                    var scrollAmount = 0f

                    if (y < scrollThresholdUp && y >= 0) {
                        val intensity = ((scrollThresholdUp - y) / scrollThresholdUp).coerceIn(0f, 1f)
                        scrollAmount = -(intensity * 40f) // Max 40px per 10ms
                    } else if (y > scrollThresholdDown && y <= viewportHeight) {
                        val intensity = ((y - scrollThresholdDown) / (viewportHeight - scrollThresholdDown)).coerceIn(0f, 1f)
                        scrollAmount = intensity * 40f
                    }
                    
                    if (scrollAmount != 0f) {
                        listState.scrollBy(scrollAmount)
                        
                        // After scrolling, check if a new item has moved under the finger
                        val info = listState.layoutInfo
                        info.visibleItemsInfo.find { 
                            y.toInt() in it.offset..(it.offset + it.size)
                        }?.let { currentOnSelect(it.key) }
                    }
                }
            }
            delay(10) 
        }
    }

    pointerInput(Unit) {
        awaitEachGesture {
            // 1. Wait for the initial touch. Use Initial pass to see it before children.
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var dragInProgress = false
            
            // 2. Detection loop: Wait for long press duration without moving too much
            val longPressTriggered = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var pointer = down
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.find { it.id == pointer.id } ?: break
                    
                    if (change.changedToUp()) break
                    
                    // If the user moves beyond touch slop, it's a regular scroll/swipe
                    if (change.positionChange().getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull "moved"
                    }
                    pointer = change
                }
                "up"
            }

            // 3. If the timer expired (longPressTriggered is null), start selection mode
            if (longPressTriggered == null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnDragStart()
                dragInProgress = true
                
                // Initial selection of the item under the finger
                listState.layoutInfo.visibleItemsInfo.find { 
                    down.position.y.toInt() in it.offset..(it.offset + it.size)
                }?.let { currentOnSelect(it.key) }

                // 4. Drag Selection Loop: Intercept and consume all subsequent pointer moves
                val pointerId = down.id
                while (dragInProgress) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.find { it.id == pointerId } ?: break
                    
                    // IMPORTANT: Consume the event so the LazyColumn and children don't respond to it
                    change.consume()
                    
                    if (change.changedToUp()) {
                        dragInProgress = false
                        break
                    }

                    val currentY = change.position.y
                    dragYState.floatValue = currentY
                    
                    // Hit detection: Find the item under the current finger position
                    // We use a small 10px buffer to make selection feel more responsive
                    val info = listState.layoutInfo
                    val itemUnderFinger = info.visibleItemsInfo.find { 
                        currentY.toInt() in (it.offset - 10)..(it.offset + it.size + 10)
                    }
                    
                    if (itemUnderFinger != null) {
                        currentOnSelect(itemUnderFinger.key)
                    }
                }
                
                // Reset drag state
                dragYState.floatValue = -1f
                currentOnDragEnd()
            }
        }
    }
}
