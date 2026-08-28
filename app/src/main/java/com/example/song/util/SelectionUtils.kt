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
 * A highly robust drag handler that supports both Multi-Selection and Reordering modes.
 */
fun Modifier.dragGestureHandler(
    listState: LazyListState,
    isReorderMode: Boolean = false,
    onSelectStart: (Any) -> Unit = {},
    onSelectUpdate: (Any) -> Unit = {},
    onSelectEnd: () -> Unit = {},
    onReorderStart: (Any, Float, Float) -> Unit = { _, _, _ -> },
    onReorderUpdate: (Float) -> Unit = {},
    onReorderEnd: () -> Unit = {}
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    
    val currentOnSelectStart by rememberUpdatedState(onSelectStart)
    val currentOnSelectUpdate by rememberUpdatedState(onSelectUpdate)
    val currentOnSelectEnd by rememberUpdatedState(onSelectEnd)
    val currentOnReorderStart by rememberUpdatedState(onReorderStart)
    val currentOnReorderUpdate by rememberUpdatedState(onReorderUpdate)
    val currentOnReorderEnd by rememberUpdatedState(onReorderEnd)
    
    val dragYState = remember { mutableFloatStateOf(-1f) }

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

                    if (y in 0f..scrollThresholdUp) {
                        val intensity = ((scrollThresholdUp - y) / scrollThresholdUp).coerceIn(0f, 1f)
                        scrollAmount = -(intensity * 40f)
                    } else if (y > scrollThresholdDown && y <= viewportHeight) {
                        val intensity = ((y - scrollThresholdDown) / (viewportHeight - scrollThresholdDown)).coerceIn(0f, 1f)
                        scrollAmount = intensity * 40f
                    }
                    
                    if (scrollAmount != 0f) {
                        listState.scrollBy(scrollAmount)
                        
                        if (!isReorderMode) {
                            val info = listState.layoutInfo
                            info.visibleItemsInfo.find { 
                                y.toInt() in it.offset..(it.offset + it.size)
                            }?.let { currentOnSelectUpdate(it.key) }
                        } else {
                            currentOnReorderUpdate(y)
                        }
                    }
                }
            }
            delay(10) 
        }
    }

    pointerInput(isReorderMode) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var active = false
            
            val timeout = if (isReorderMode) 200L else viewConfiguration.longPressTimeoutMillis
            val trigger = withTimeoutOrNull(timeout) {
                var pointer = down
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.find { it.id == pointer.id } ?: break
                    if (change.changedToUp()) break
                    if (change.positionChange().getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull "moved"
                    pointer = change
                }
                "up"
            }

            if (trigger == null) {
                val initialItem = listState.layoutInfo.visibleItemsInfo.find { 
                    val itemTop = it.offset
                    val itemBottom = it.offset + it.size
                    down.position.y.toInt() in itemTop..itemBottom
                }

                if (initialItem != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val key = initialItem.key
                    if (isReorderMode) {
                        currentOnReorderStart(key, down.position.y, initialItem.offset.toFloat())
                    } else {
                        currentOnSelectStart(key)
                    }
                    active = true

                    try {
                        while (active) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.find { it.id == down.id } ?: break
                            
                            change.consume()

                            if (change.changedToUp()) {
                                active = false
                                break
                            }

                            val currentY = change.position.y
                            dragYState.floatValue = currentY
                            
                            if (isReorderMode) {
                                currentOnReorderUpdate(currentY)
                            } else {
                                val info = listState.layoutInfo
                                val itemUnderFinger = info.visibleItemsInfo.find { 
                                    val itemTop = it.offset
                                    val itemBottom = it.offset + it.size
                                    currentY.toInt() in itemTop..itemBottom
                                }
                                itemUnderFinger?.let { currentOnSelectUpdate(it.key) }
                            }
                        }
                    } finally {
                        active = false
                        dragYState.floatValue = -1f
                        if (isReorderMode) currentOnReorderEnd() else currentOnSelectEnd()
                    }
                }
            }
        }
    }
}
