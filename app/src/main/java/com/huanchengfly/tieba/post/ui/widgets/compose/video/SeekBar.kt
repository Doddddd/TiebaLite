package com.huanchengfly.tieba.post.ui.widgets.compose.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderDefaults.TrackStopIndicatorSize
import androidx.compose.material3.SliderDefaults.drawStopIndicator
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.theme.FloatProducer

@Composable
fun VideoSeekBar(
    progress: FloatProducer,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackHeight: Dp = 8.dp,
    secondaryProgress: FloatProducer? = null,
    onSeek: (progress: Float) -> Unit = {},
    onSeekStopped: (stoppedProgress: Float) -> Unit = {},
    seekerPopup: @Composable () -> Unit = {},
    seekerDurationProvider: ((seekProgress: Float) -> String)? = null,
) {
    var draggingProgress by remember { mutableFloatStateOf(0f) }
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnSeekStopped by rememberUpdatedState(onSeekStopped)
    val interactionSource = remember { MutableInteractionSource() }
    val state =
        remember {
            SliderState(
                onValueChangeFinished = {
                    latestOnSeekStopped(draggingProgress)
                },
            )
        }

    state.onValueChange = {
        state.value = it
        draggingProgress = it
        if (state.isDragging) latestOnSeek(it)
    }

    LaunchedEffect(progress) {
        snapshotFlow { progress() }.collect {
            if (!state.isDragging) state.value = it
        }
    }

    Column(modifier = modifier) {
        if (state.isDragging) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.shadow(4.dp)) {
                    seekerPopup()
                }

                if (seekerDurationProvider != null) {
                    Text(
                        text = seekerDurationProvider(draggingProgress),
                        color = Color.White,
                        style =
                            TextStyle(
                                shadow =
                                    Shadow(
                                        blurRadius = 8f,
                                        offset = Offset(2f, 2f),
                                    ),
                            ),
                    )
                }
            }
        }

        val sliderColors =
            SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White,
            )
        Slider(
            modifier = Modifier.height(24.dp),
            state = state,
            enabled = enabled,
            colors = sliderColors,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = sliderColors,
                    enabled = true,
                    thumbSize = ThumbSize,
                )
            },
            track = { sliderState ->
                TrackImpl(
                    sliderState = sliderState,
                    bufferValue = secondaryProgress,
                    enabled = true,
                    colors = sliderColors,
                    trackHeight = trackHeight,
                )
            },
        )
    }
}

@Composable
private fun TrackImpl(
    sliderState: SliderState,
    bufferValue: FloatProducer?,
    enabled: Boolean,
    colors: SliderColors,
    trackHeight: Dp,
) {
    val (inactiveTrackColor, activeTrackColor) =
        if (enabled) {
            colors.inactiveTrackColor to colors.activeTrackColor
        } else {
            colors.disabledInactiveTrackColor to colors.disabledActiveTrackColor
        }
    val bufferTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val currentBuffer = bufferValue?.invoke()?.coerceIn(0f, 1f) ?: 0f
    var peakBuffer by remember { mutableFloatStateOf(0f) }
    if (currentBuffer > peakBuffer) peakBuffer = currentBuffer
    val bufferFraction = peakBuffer

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(trackHeight),
    ) {
        drawTrack(
            activeRangeEnd = sliderState.coercedValueAsFraction,
            bufferRangeEnd = bufferFraction,
            inactiveTrackColor = inactiveTrackColor,
            activeTrackColor = activeTrackColor,
            bufferTrackColor = bufferTrackColor,
        )
    }
}

private fun DrawScope.drawTrack(
    activeRangeEnd: Float,
    bufferRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    bufferTrackColor: Color,
) {
    val cornerSize = size.height / 2
    val sliderEnd = size.width
    val sliderValueEnd = sliderEnd * activeRangeEnd
    val bufferValueEnd = sliderEnd * bufferRangeEnd
    val insideCornerSize = TrackInsideCornerSize.toPx()
    val endGap = ThumbWidth.toPx() / 2 + ThumbTrackGapSize.toPx()

    // Inactive track
    val inactiveTrackThreshold = sliderEnd - endGap - cornerSize
    if (sliderValueEnd < inactiveTrackThreshold) {
        val startCornerRadius = insideCornerSize
        val endCornerRadius = cornerSize
        val start = sliderValueEnd + endGap
        val inactiveTrackWidth = sliderEnd - start
        val inactiveOffset = Offset(start, 0f)
        val inactiveSize = Size(inactiveTrackWidth, size.height)
        drawTrackPath(inactiveOffset, inactiveSize, inactiveTrackColor, startCornerRadius, endCornerRadius)

        // Buffer track
        val bufferWidth = bufferValueEnd - start
        if (bufferWidth > 0f) {
            val bufferEndCornerRadius =
                if (bufferRangeEnd >= 0.99f) endCornerRadius else insideCornerSize
            val bufferOffset = Offset(start, 0f)
            val bufferSize = Size(bufferWidth, size.height)
            drawTrackPath(
                bufferOffset,
                bufferSize,
                bufferTrackColor,
                startCornerRadius,
                bufferEndCornerRadius,
            )
        }

        // Stop indicator at the track end
        drawStopIndicator(
            offset = Offset(sliderEnd - cornerSize, center.y),
            color = activeTrackColor,
            size = TrackStopIndicatorSize,
        )
    }

    // Active track (0 to thumb)
    val activeTrackEnd = sliderValueEnd - endGap
    val activeStartCornerRadius = cornerSize
    val activeEndCornerRadius = insideCornerSize
    val activeTrackWidth = activeTrackEnd

    if (activeTrackWidth > activeStartCornerRadius) {
        val activeOffset = Offset(0f, 0f)
        val activeSize = Size(activeTrackWidth, size.height)
        drawTrackPath(
            activeOffset,
            activeSize,
            activeTrackColor,
            activeStartCornerRadius,
            activeEndCornerRadius,
        )
    }
}

private fun DrawScope.drawTrackPath(
    offset: Offset,
    size: Size,
    color: Color,
    startCornerRadius: Float,
    endCornerRadius: Float,
) {
    val startCorner = CornerRadius(startCornerRadius, startCornerRadius)
    val endCorner = CornerRadius(endCornerRadius, endCornerRadius)
    val track =
        RoundRect(
            rect = Rect(offset, size = Size(size.width, size.height)),
            topLeft = startCorner,
            topRight = endCorner,
            bottomRight = endCorner,
            bottomLeft = startCorner,
        )
    trackPath.addRoundRect(track)
    drawPath(trackPath, color)
    trackPath.rewind()
}

private val ThumbWidth = 4.dp
private val ThumbHeight = 24.dp
private val ThumbSize = DpSize(ThumbWidth, ThumbHeight)
private val TrackInsideCornerSize: Dp = 2.dp
private val ThumbTrackGapSize: Dp = 4.dp
private val trackPath = Path()
