package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.PresentationState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.clickableNoIndication
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.isWindowWidthCompact
import kotlin.math.roundToLong

internal val VideoControlShadow =
    Shadow(
        color = Color.Black.copy(alpha = 0.6f),
        offset = Offset(2f, 2f),
        blurRadius = 8f,
    )

@Immutable
private class VideoControlLayoutSpec(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val textSize: TextUnit,
    val iconSize: Dp,
    val buttonSize: Dp,
    val seekbarHeight: Dp,
    val centerFabSize: Dp,
    val centerIconSize: Dp,
    val centerIndicatorStroke: Dp,
)

private fun buildVideoControlLayoutSpec(
    isFullscreen: Boolean,
    isLandscape: Boolean,
    isCompactWidth: Boolean,
): VideoControlLayoutSpec =
    when {
        !isFullscreen -> {
            VideoControlLayoutSpec(
                horizontalPadding = 12.dp,
                verticalPadding = 12.dp,
                textSize = 12.sp,
                iconSize = 20.dp,
                buttonSize = 40.dp,
                seekbarHeight = 8.dp,
                centerFabSize = 72.dp,
                centerIconSize = 40.dp,
                centerIndicatorStroke = 2.5.dp,
            )
        }

        isLandscape || isCompactWidth -> {
            VideoControlLayoutSpec(
                horizontalPadding = 16.dp,
                verticalPadding = 16.dp,
                textSize = 14.sp,
                iconSize = 24.dp,
                buttonSize = 44.dp,
                seekbarHeight = 10.dp,
                centerFabSize = 96.dp,
                centerIconSize = 50.dp,
                centerIndicatorStroke = 3.dp,
            )
        }

        else -> {
            VideoControlLayoutSpec(
                horizontalPadding = 24.dp,
                verticalPadding = 36.dp,
                textSize = 16.sp,
                iconSize = 28.dp,
                buttonSize = 48.dp,
                seekbarHeight = 12.dp,
                centerFabSize = 120.dp,
                centerIconSize = 60.dp,
                centerIndicatorStroke = 3.dp,
            )
        }
    }

@UnstableApi
@Composable
internal fun BoxScope.MediaControls(
    controller: VideoPlayerController,
    isFullscreen: Boolean,
    presentationState: PresentationState,
    onVideoInfoClick: () -> Unit,
    onExitFullScreen: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactWidth = isWindowWidthCompact()
    val state by controller.state.collectAsState(initial = controller.state.value)
    val isPipSupported by controller.isPipSupported.collectAsState()
    val controlsVisible = state.controlsVisible
    val isLocked = state.isLocked
    val lockControlsVisible = state.lockControlsVisible
    val layoutSpec =
        remember(isFullscreen, isLandscape, isCompactWidth) {
            buildVideoControlLayoutSpec(
                isFullscreen = isFullscreen,
                isLandscape = isLandscape,
                isCompactWidth = isCompactWidth,
            )
        }
    val progressStateWithTick =
        rememberProgressStateWithTickInterval(
            player = controller.exoPlayer,
            tickIntervalMs = 200,
            scope = coroutineScope,
        )
    val seekbarState =
        rememberProgressStateWithTickCount(
            player = controller.exoPlayer,
            totalTickCount = (progressStateWithTick.durationMs / 300).toInt().coerceAtLeast(1),
            scope = coroutineScope,
        )

    val horizontalPadding = layoutSpec.horizontalPadding
    val verticalPadding = layoutSpec.verticalPadding

    if (isLocked) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickableNoIndication { controller.toggleLockControls() },
        )

        if (lockControlsVisible) {
            PlayerIconButton(
                onClick = controller::toggleLock,
                painter = painterResource(R.drawable.ic_sym_lock),
                contentDescription = stringResource(id = R.string.btn_unlock),
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                iconSize = layoutSpec.iconSize,
                buttonSize = layoutSpec.buttonSize,
            )
        }
    } else {
        if (isFullscreen && !isLocked) {
            val topPadding = verticalPadding + layoutSpec.iconSize
            val bottomPadding = verticalPadding + 24.dp + layoutSpec.buttonSize
            MediaControlGestures(
                modifier =
                    Modifier
                        .matchParentSize()
                        .padding(top = topPadding, bottom = bottomPadding),
                durationProvider = { progressStateWithTick.durationMs },
                positionProvider = { progressStateWithTick.currentPositionMs },
                controller = controller,
            )
        }

        MediaControlButtons(
            modifier = Modifier.align(Alignment.Center),
            enabled = isFullscreen || !presentationState.coverSurface,
            layoutSpec = layoutSpec,
        )
    }

    if (controlsVisible && !isLocked && isFullscreen) {
        PlayerIconButton(
            onClick = controller::toggleLock,
            painter = painterResource(R.drawable.ic_sym_lock_open_right),
            contentDescription = stringResource(id = R.string.btn_lock),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            iconSize = layoutSpec.iconSize,
            buttonSize = layoutSpec.buttonSize,
        )
    }

    if (controlsVisible && !isLocked) {
        if (isFullscreen) {
            PlayerIconButton(
                onClick = onExitFullScreen,
                painter = painterResource(R.drawable.ic_sym_arrow_back),
                contentDescription = stringResource(id = R.string.btn_full_screen_exit),
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                iconSize = layoutSpec.iconSize,
                buttonSize = layoutSpec.buttonSize,
            )
        }

        if (isFullscreen) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPipSupported) {
                    PlayerIconButton(
                        onClick = controller.onRequestPip,
                        painter = painterResource(R.drawable.ic_sym_picture_in_picture),
                        contentDescription = stringResource(id = R.string.btn_pip),
                        iconSize = layoutSpec.iconSize,
                        buttonSize = layoutSpec.buttonSize,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                PlayerIconButton(
                    onClick = onVideoInfoClick,
                    painter = painterResource(R.drawable.ic_sym_info),
                    contentDescription = stringResource(id = R.string.btn_video_info),
                    iconSize = layoutSpec.iconSize,
                    buttonSize = layoutSpec.buttonSize,
                )
            }
        }

        val buttonInternalPadding = (layoutSpec.buttonSize - layoutSpec.iconSize) / 2
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = verticalPadding / 2,
                        bottom = verticalPadding / 2,
                    ),
        ) {
            val durationMs = progressStateWithTick.durationMs

            VideoSeekBar(
                progress = { seekbarState.currentPositionProgress },
                enabled = controlsVisible && durationMs > 0,
                trackHeight = layoutSpec.seekbarHeight,
                modifier = Modifier.fillMaxWidth().padding(horizontal = buttonInternalPadding),
                onSeek = { seekProgress ->
                    if (durationMs > 0) {
                        controller.showControls(autoHide = false)
                        controller.previewSeekTo(position = (seekProgress * durationMs).roundToLong())
                    }
                },
                onSeekStopped = { stoppedProgress ->
                    if (durationMs > 0) {
                        controller.showControls(autoHide = true)
                        controller.seekTo(position = (stoppedProgress * durationMs).roundToLong())
                    }
                },
                secondaryProgress = { seekbarState.bufferedPositionProgress },
                seekerPopup = {
                    if (!isFullscreen) return@VideoSeekBar
                    val videoSize = presentationState.videoSizeDp ?: return@VideoSeekBar
                    val aspectRatio = videoSize.run { width / height }
                    val previewHeight =
                        (configuration.screenHeightDp * 0.20f)
                            .dp
                            .coerceIn(80.dp, 160.dp)
                    AutoClearPlayerSurface(
                        player = controller.previewExoPlayer,
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        modifier =
                            Modifier
                                .height(previewHeight)
                                .width(previewHeight * aspectRatio)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray),
                    )
                },
                seekerDurationProvider = { seekProgress ->
                    getDurationString((seekProgress * durationMs).roundToLong())
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeDurationText(
                    currentMs = progressStateWithTick.currentPositionMs,
                    totalMs = progressStateWithTick.durationMs,
                    textSize = layoutSpec.textSize,
                    modifier = Modifier.padding(start = buttonInternalPadding),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isFullscreen) {
                    SpeedControlButton(
                        iconSize = layoutSpec.iconSize,
                        buttonSize = layoutSpec.buttonSize,
                        currentSpeed = state.playbackSpeed,
                        onSpeedChange = { controller.setPlaybackSpeed(it) },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    PlayerIconButton(
                        onClick = { controller.fullScreen.rotateFullScreen(isLandscape) },
                        painter = painterResource(R.drawable.ic_sym_screen_rotation),
                        contentDescription = stringResource(id = R.string.btn_rotate),
                        iconSize = layoutSpec.iconSize,
                        buttonSize = layoutSpec.buttonSize,
                        scaleX = if (isLandscape) 1f else -1f,
                        rotation = if (isLandscape) 90f else 0f,
                    )
                } else {
                    PlayerIconButton(
                        onClick = controller.fullScreen::toggleFullScreen,
                        painter = painterResource(R.drawable.ic_sym_fullscreen),
                        contentDescription = stringResource(id = R.string.btn_full_screen),
                        iconSize = layoutSpec.iconSize,
                        buttonSize = layoutSpec.buttonSize,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TimeDurationText(
    currentMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier,
    textSize: TextUnit = TextUnit.Unspecified,
) {
    val positionText = getDurationString(currentMs.coerceAtLeast(0))
    val durationText = getDurationString(totalMs.coerceAtLeast(0))
    val textStyle =
        MaterialTheme.typography.labelMedium
            .copy(fontFamily = FontFamily.Monospace)
            .merge(
                TextStyle(
                    shadow = VideoControlShadow,
                    fontSize = textSize,
                ),
            )
    Box(
        modifier = modifier.widthIn(min = 88.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "$positionText / $durationText",
            color = Color.White,
            style = textStyle,
            maxLines = 1,
        )
    }
}

@Immutable
sealed interface PlayPauseButtonState {
    data object Play : PlayPauseButtonState

    data object Pause : PlayPauseButtonState

    data object Replay : PlayPauseButtonState

    data object Loading : PlayPauseButtonState
}

@Composable
fun MediaControlButtons(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    layoutSpec: VideoControlLayoutSpec,
) {
    val controller = LocalVideoPlayerController.current
    val state by controller.state.collectAsState(initial = controller.state.value)
    val isAdjusting = state.verticalDragAdjustment != null
    val controlsVisible = state.controlsVisible

    if (enabled && !isAdjusting && controlsVisible) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            PlayerPlayPauseButton(layoutSpec = layoutSpec)
        }
    }
}

@Composable
fun PlayPauseButton(
    state: PlayPauseButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layoutSpec: VideoControlLayoutSpec,
) {
    val fabSize = layoutSpec.centerFabSize
    val iconSize = layoutSpec.centerIconSize
    val indicatorStroke = layoutSpec.centerIndicatorStroke

    val (painter, contentDescription) =
        when (state) {
            PlayPauseButtonState.Play -> painterResource(R.drawable.ic_sym_play_arrow) to stringResource(R.string.btn_play)
            PlayPauseButtonState.Pause -> painterResource(R.drawable.ic_sym_pause) to stringResource(R.string.btn_pause)
            PlayPauseButtonState.Replay -> painterResource(R.drawable.ic_sym_replay) to stringResource(R.string.btn_replay)
            PlayPauseButtonState.Loading -> null to stringResource(R.string.btn_buffering)
        }

    CompositionLocalProvider(
        LocalIndication provides
            ripple(
                bounded = true,
                radius = 0.dp,
                color = Color.Unspecified,
            ),
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier =
                modifier
                    .size(fabSize)
                    .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            shape = FloatingActionButtonDefaults.shape,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            when (state) {
                PlayPauseButtonState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize),
                        color = LocalContentColor.current,
                        strokeWidth = indicatorStroke,
                    )
                }

                else -> {
                    ShadowedSymbol(
                        painter = requireNotNull(painter),
                        iconSize = iconSize,
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerPlayPauseButton(
    modifier: Modifier = Modifier,
    layoutSpec: VideoControlLayoutSpec,
) {
    val controller = LocalVideoPlayerController.current
    val playerState by controller.state.collectAsState(initial = controller.state.value)

    val buttonState =
        when {
            playerState.isPlaying -> PlayPauseButtonState.Pause
            playerState.wasEnded || playerState.playbackState == Player.STATE_ENDED -> PlayPauseButtonState.Replay
            playerState.playbackState == Player.STATE_BUFFERING -> PlayPauseButtonState.Loading
            else -> PlayPauseButtonState.Play
        }

    PlayPauseButton(
        state = buttonState,
        onClick = { if (buttonState != PlayPauseButtonState.Loading) controller.togglePlaying() },
        modifier = modifier,
        layoutSpec = layoutSpec,
    )
}

@Composable
fun PlayerIconButton(
    onClick: () -> Unit,
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    buttonSize: Dp = 48.dp,
    scaleX: Float = 1f,
    rotation: Float = 0f,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(buttonSize)
                .semantics { contentDescription?.let { this.contentDescription = it } },
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.3f),
            ),
    ) {
        ShadowedSymbol(
            painter = painter,
            iconSize = iconSize,
            modifier =
                Modifier.graphicsLayer {
                    this.scaleX = scaleX
                    rotationZ = rotation
                },
        )
    }
}

@Composable
private fun AutoClearPlayerSurface(
    player: Player,
    surfaceType: Int,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(player) { onDispose { player.clearVideoSurface() } }

    PlayerSurface(
        player = player,
        surfaceType = surfaceType,
        modifier = modifier,
    )
}
