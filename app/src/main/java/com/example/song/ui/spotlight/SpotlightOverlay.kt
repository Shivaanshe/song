package com.example.song.ui.spotlight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpotlightOverlay(
    controller: SpotlightController,
    modifier: Modifier = Modifier
) {
    val activeStep by controller.activeStep.collectAsState()
    val targetBounds by controller.targetBounds.collectAsState()
    val isTransitioning by controller.isTransitioning.collectAsState()

    val step = activeStep ?: return

    BackHandler(enabled = true) {
        if (step.stepIndex > 1) {
            controller.previousStep()
        } else {
            controller.skipTour()
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val activeTargetRect = targetBounds[step]
    val isTargetValid = activeTargetRect != null && activeTargetRect.width > 0f && activeTargetRect.height > 0f

    val defaultLeft = screenWidthPx * 0.05f
    val defaultTop = screenHeightPx * 0.75f
    val defaultRight = screenWidthPx * 0.95f
    val defaultBottom = screenHeightPx * 0.85f

    val animatedLeft by animateFloatAsState(
        targetValue = activeTargetRect?.left ?: defaultLeft,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "spotlight_left"
    )
    val animatedTop by animateFloatAsState(
        targetValue = activeTargetRect?.top ?: defaultTop,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "spotlight_top"
    )
    val animatedRight by animateFloatAsState(
        targetValue = activeTargetRect?.right ?: defaultRight,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "spotlight_right"
    )
    val animatedBottom by animateFloatAsState(
        targetValue = activeTargetRect?.bottom ?: defaultBottom,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "spotlight_bottom"
    )

    val targetRect = Rect(animatedLeft, animatedTop, animatedRight, animatedBottom)

    val paddingPx = with(density) { 4.dp.toPx() }
    val cornerRadiusPx = with(density) { 12.dp.toPx() }

    // Pulsating aura animation around cutout
    val infiniteTransition = rememberInfiniteTransition(label = "spotlight_aura")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    AnimatedVisibility(
        visible = !isTransitioning,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { /* Intercept all taps outside controls */ }
                }
        ) {
            // 1. Dimmed Scrim with Clear Cutout
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                // Draw 80% dark background scrim
                drawRect(color = Color.Black.copy(alpha = 0.80f))

                // Cut out spotlight hole only when measurement is valid
                if (isTargetValid) {
                    val cutoutLeft = targetRect.left - paddingPx
                    val cutoutTop = targetRect.top - paddingPx
                    val cutoutWidth = targetRect.width + paddingPx * 2
                    val cutoutHeight = targetRect.height + paddingPx * 2

                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(cutoutLeft, cutoutTop),
                        size = Size(cutoutWidth, cutoutHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        blendMode = BlendMode.Clear
                    )
                }
            }

            // 2. Glowing Target Border
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (isTargetValid) {
                    val cutoutLeft = targetRect.left - paddingPx
                    val cutoutTop = targetRect.top - paddingPx
                    val cutoutWidth = targetRect.width + paddingPx * 2
                    val cutoutHeight = targetRect.height + paddingPx * 2

                    drawRoundRect(
                        color = Color(0xFFFF3B30).copy(alpha = auraAlpha),
                        topLeft = Offset(cutoutLeft, cutoutTop),
                        size = Size(cutoutWidth, cutoutHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // 3. Position Floating Tooltip Card
            val isBottomSpaceSufficient = (screenHeightPx - targetRect.bottom) > with(density) { 220.dp.toPx() }
            val cardTopDp = if (isBottomSpaceSufficient) {
                with(density) { (targetRect.bottom + paddingPx + 16.dp.toPx()).toDp() }
            } else {
                val cardEstimatedHeightPx = with(density) { 200.dp.toPx() }
                val offset16Px = with(density) { 16.dp.toPx() }
                val calculatedTop = targetRect.top - paddingPx - cardEstimatedHeightPx - offset16Px
                with(density) { maxOf(offset16Px, calculatedTop).toDp() }
            }

            val animatedCardTopDp by animateDpAsState(
                targetValue = cardTopDp,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
                label = "card_top_dp"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = animatedCardTopDp)
            ) {
                SpotlightTooltipCard(
                    step = step,
                    onSkip = { controller.skipTour() },
                    onPrevious = { controller.previousStep() },
                    onNext = { controller.nextStep() }
                )
            }
        }
    }
}

@Composable
private fun SpotlightTooltipCard(
    step: TourStep,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    // Typewriter text animation logic
    val animatedCharCount = remember { Animatable(0f) }

    LaunchedEffect(step) {
        animatedCharCount.snapTo(0f)
        animatedCharCount.animateTo(
            targetValue = step.description.length.toFloat(),
            animationSpec = tween(
                durationMillis = (step.description.length * 25).coerceAtLeast(250),
                easing = LinearEasing
            )
        )
    }

    val visibleCharCount = animatedCharCount.value.toInt().coerceIn(0, step.description.length)
    val displayedDescription = step.description.substring(0, visibleCharCount)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF1E1E24).copy(alpha = 0.95f),
            contentColor = Color.White
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Step Counter Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF3B30).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Step ${step.stepIndex} of ${step.totalSteps}",
                        color = Color(0xFFFF3B30),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                TextButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Skip Tour",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }

            // Body: Title & Typewriter Description
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = displayedDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )

            // Bottom Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step.stepIndex > 1) {
                    OutlinedButton(
                        onClick = onPrevious,
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Previous")
                    }
                }

                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF3B30),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (step.stepIndex == step.totalSteps) "Finish" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
