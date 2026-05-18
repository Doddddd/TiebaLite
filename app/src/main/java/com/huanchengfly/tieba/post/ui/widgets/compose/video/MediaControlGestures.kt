package com.huanchengfly.tieba.post.ui.widgets.compose.video

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MediaControlGestures(
    modifier: Modifier = Modifier,
    durationProvider: () -> Long,
    positionProvider: () -> Long,
    controller: VideoPlayerController,
) {
    val state by controller.state.collectAsState(initial = controller.state.value)
    val quickSeekDirection = state.quickSeekAction
    val draggingProgress = state.draggingProgress
    val verticalDragAdjustment = state.verticalDragAdjustment

    Box(modifier = modifier) {
        QuickSeekAnimationOverlay(
            quickSeekDirection = quickSeekDirection,
            onAnimationEnd = { controller.setQuickSeekAction(QuickSeekDirection.None) },
        )
        DraggingProgressText(draggingProgress = draggingProgress)
        VerticalDragIndicator(adjustment = verticalDragAdjustment)
        GestureBox(
            durationProvider = durationProvider,
            positionProvider = positionProvider,
            controller = controller,
        )
    }
}

@Composable
private fun GestureBox(
    modifier: Modifier = Modifier,
    durationProvider: () -> Long,
    positionProvider: () -> Long,
    controller: VideoPlayerController,
) {
    val coroutineScope = rememberCoroutineScope()
    var isFastForwarding by remember { mutableStateOf(false) }
    var savedSpeed by remember { mutableStateOf(1f) }
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    var wasPlaying = true
                    var totalOffset = Offset.Zero
                    var duration: Long = 0
                    var currentPosition: Long = 0
                    var seekJob: Job? = null
                    var finalSeekTime = 0L
                    var verticalDragType = VerticalDragType.Volume
                    var initialLevel = 0f
                    var verticalTotalDrag = 0f

                    fun resetState() {
                        totalOffset = Offset.Zero
                        controller.setDraggingProgress(null)
                    }

                    detectMediaPlayerGesture(
                        onTap = {
                            if (controller.currentState { it.controlsVisible }) {
                                controller.hideControls()
                            } else {
                                controller.showControls()
                            }
                        },
                        onDoubleTap = { pos ->
                            val durationNow = controller.currentState { durationProvider() }

                            when {
                                durationNow > 20_000L && pos.x < size.width * 0.4f -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.quickSeekRewind()
                                }

                                durationNow > 20_000L && pos.x > size.width * 0.6f -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.quickSeekForward()
                                }

                                else -> {
                                    controller.togglePlaying()
                                }
                            }
                        },
                        onDragStart = { offset ->
                            wasPlaying = controller.currentState { it.isPlaying }
                            controller.pause()

                            currentPosition = controller.currentState { positionProvider() }
                            duration = controller.currentState { durationProvider() }

                            resetState()
                        },
                        onDragEnd = {
                            seekJob?.cancel()
                            seekJob = null
                            if (finalSeekTime > 0) controller.seekTo(finalSeekTime)
                            if (wasPlaying) controller.play()
                            resetState()
                        },
                        onDrag = { dragAmount: Float ->
                            seekJob?.cancel()

                            totalOffset += Offset(x = dragAmount, y = 0f)

                            val diff = totalOffset.x

                            var diffTime =
                                if (duration <= 60_000) {
                                    duration.toFloat() * diff / size.width.toFloat()
                                } else {
                                    60_000.toFloat() * diff / size.width.toFloat()
                                }

                            var finalTime = currentPosition + diffTime
                            if (finalTime < 0) {
                                finalTime = 0f
                            } else if (finalTime > duration) {
                                finalTime = duration.toFloat()
                            }
                            diffTime = finalTime - currentPosition
                            finalSeekTime = finalTime.toLong()

                            controller.setDraggingProgress(
                                DraggingProgress(
                                    finalTime = finalTime,
                                    diffTime = diffTime,
                                ),
                            )

                            seekJob =
                                coroutineScope.launch {
                                    delay(200)
                                    controller.seekTo(finalTime.toLong())
                                }
                        },
                        onVerticalDragStart = { startOffset ->
                            verticalTotalDrag = 0f
                            verticalDragType =
                                if (startOffset.x < size.width * 0.5f) {
                                    VerticalDragType.Brightness
                                } else {
                                    VerticalDragType.Volume
                                }
                            initialLevel =
                                when (verticalDragType) {
                                    VerticalDragType.Volume -> controller.getVolumeLevel()
                                    VerticalDragType.Brightness -> controller.getBrightnessLevel()
                                }
                            controller.setVerticalDragAdjustment(
                                VerticalDragAdjustment(type = verticalDragType, level = initialLevel),
                            )
                        },
                        onVerticalDragEnd = {
                            controller.setVerticalDragAdjustment(null)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            verticalTotalDrag += dragAmount
                            val delta = -verticalTotalDrag / size.height
                            val newLevel = (initialLevel + delta).coerceIn(0f, 1f)

                            when (verticalDragType) {
                                VerticalDragType.Volume -> controller.setVolumeLevel(newLevel)
                                VerticalDragType.Brightness -> controller.setBrightnessLevel(newLevel)
                            }

                            controller.setVerticalDragAdjustment(
                                VerticalDragAdjustment(
                                    type = verticalDragType,
                                    level = newLevel,
                                ),
                            )
                            if (change.positionChange() != Offset.Zero) change.consume()
                        },
                        onLongPressStart = {
                            savedSpeed = controller.getPlaybackSpeed()
                            controller.setPlaybackSpeed(2f)
                            isFastForwarding = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onLongPressEnd = {
                            controller.setPlaybackSpeed(savedSpeed)
                            isFastForwarding = false
                        },
                    )
                }.then(modifier),
    ) {
        if (isFastForwarding) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.fillMaxHeight(0.20f))
                Box(
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.speed_2x),
                        color = Color.White,
                        style = TextStyle(shadow = VideoControlShadow),
                    )
                }
            }
        }
    }
}

suspend fun PointerInputScope.detectMediaPlayerGesture(
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float) -> Unit,
    onVerticalDragStart: (Offset) -> Unit,
    onVerticalDragEnd: () -> Unit,
    onVerticalDrag: (PointerInputChange, Float) -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
) {
    coroutineScope {
        launch {
            val touchSlop = viewConfiguration.touchSlop

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPos = down.position

                var gestureType: Int = TYPE_UNDECIDED
                var longPressTriggered = false

                val longPressJob =
                    launch {
                        delay(500L)
                        longPressTriggered = true
                        gestureType = TYPE_LONG_PRESS
                        onLongPressStart()
                    }

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        longPressJob.cancel()
                        when (gestureType) {
                            TYPE_LONG_PRESS -> {
                                if (longPressTriggered) {
                                    change.consume()
                                    onLongPressEnd()
                                }
                            }

                            TYPE_HORIZONTAL_DRAG -> {
                                onDragEnd()
                            }

                            TYPE_VERTICAL_DRAG -> {
                                onVerticalDragEnd()
                            }
                        }
                        break
                    }

                    val dx = change.position.x - downPos.x
                    val dy = change.position.y - downPos.y

                    if (gestureType == TYPE_UNDECIDED && !longPressTriggered) {
                        val distanceSq = dx * dx + dy * dy
                        if (distanceSq > touchSlop * touchSlop) {
                            longPressJob.cancel()
                            gestureType =
                                if (abs(dx) > abs(dy)) {
                                    onDragStart(downPos)
                                    TYPE_HORIZONTAL_DRAG
                                } else {
                                    onVerticalDragStart(downPos)
                                    TYPE_VERTICAL_DRAG
                                }
                        }
                    }

                    when (gestureType) {
                        TYPE_HORIZONTAL_DRAG -> {
                            onDrag(change.positionChange().x)
                            change.consume()
                        }

                        TYPE_VERTICAL_DRAG -> {
                            val dragAmount = change.positionChange().y
                            onVerticalDrag(change, dragAmount)
                            if (change.positionChange() != Offset.Zero) change.consume()
                        }

                        TYPE_LONG_PRESS -> {
                            change.consume()
                        }
                    }
                }
            }
        }

        detectTapGestures(
            onTap = onTap,
            onDoubleTap = onDoubleTap,
        )
    }
}

private const val TYPE_UNDECIDED = 0
private const val TYPE_HORIZONTAL_DRAG = 1
private const val TYPE_VERTICAL_DRAG = 2
private const val TYPE_LONG_PRESS = 3

@Composable
private fun QuickSeekAnimationOverlay(
    quickSeekDirection: QuickSeekDirection,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alphaRewind = remember { Animatable(0f) }
    val alphaForward = remember { Animatable(0f) }

    LaunchedEffect(quickSeekDirection) {
        when (quickSeekDirection) {
            QuickSeekDirection.Rewind -> alphaRewind
            QuickSeekDirection.Forward -> alphaForward
            else -> null
        }?.let { animatable ->
            animatable.animateTo(1f, animationSpec = tween(200))
            animatable.animateTo(0f, animationSpec = tween(200))
            onAnimationEnd()
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            ShadowedSymbol(
                painterResource(R.drawable.ic_sym_fast_rewind),
                modifier =
                    Modifier
                        .alpha(alphaRewind.value)
                        .align(Alignment.Center),
            )
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            ShadowedSymbol(
                painterResource(R.drawable.ic_sym_fast_forward),
                modifier =
                    Modifier
                        .alpha(alphaForward.value)
                        .align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun BoxScope.DraggingProgressText(
    modifier: Modifier = Modifier,
    draggingProgress: DraggingProgress?,
) {
    if (draggingProgress != null) {
        val textStyle =
            remember {
                TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(blurRadius = 8f, offset = Offset(2f, 2f)),
                )
            }

        val text =
            remember(draggingProgress.finalTime.toLong(), draggingProgress.diffTime.toLong()) {
                draggingProgress.progressText
            }

        Text(
            text = text,
            style = textStyle,
            color = Color.White,
            modifier = modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun BoxScope.VerticalDragIndicator(
    modifier: Modifier = Modifier,
    adjustment: VerticalDragAdjustment?,
) {
    if (adjustment != null) {
        val painter =
            when (adjustment.type) {
                VerticalDragType.Volume -> painterResource(R.drawable.ic_sym_volume_up)
                VerticalDragType.Brightness -> painterResource(R.drawable.ic_sym_brightness_high)
            }

        val textStyle =
            remember {
                TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    shadow = Shadow(blurRadius = 8f, offset = Offset(2f, 2f)),
                )
            }

        Row(
            modifier = modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            ShadowedSymbol(
                painter = painter,
                iconSize = 32.dp,
            )
            Text(
                text = "${(adjustment.level * 100).toInt()}%",
                style = textStyle,
                color = Color.White,
            )
        }
    }
}
