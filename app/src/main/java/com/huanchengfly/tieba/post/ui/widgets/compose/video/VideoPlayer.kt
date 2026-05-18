package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.view.SurfaceView
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.RetainedEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.PresentationState
import androidx.media3.ui.compose.state.rememberPresentationState
import com.huanchengfly.tieba.post.ui.common.theme.compose.clickableNoIndication

internal val LocalVideoPlayerController =
    compositionLocalOf<VideoPlayerController> { error("VideoPlayerController is not initialized") }

@Composable
fun retainVideoPlayerController(
    source: VideoPlayerSource? = null,
    thumbnailUrl: String? = null,
    fullScreenModeChangedListener: FullScreenModeListener? = null,
    playWhenReady: Boolean = false,
    @Player.State initialPlaybackState: Int = Player.STATE_IDLE,
    initialWasEnded: Boolean = false,
    isFullScreen: Boolean = false,
): VideoPlayerController {
    val context = LocalContext.current
    val controller =
        retain {
            VideoPlayerController(
                context = context.applicationContext,
                initialState =
                    VideoPlayerModel(
                        thumbnailUrl = thumbnailUrl,
                        isPlaying = playWhenReady,
                        playbackState = initialPlaybackState,
                        wasEnded = initialWasEnded || initialPlaybackState == Player.STATE_ENDED,
                    ),
                isFullScreen = isFullScreen,
            ).apply {
                source?.let { setSource(it) }
            }
        }

    DisposableEffect(fullScreenModeChangedListener) {
        controller.fullScreen.setListener(fullScreenModeChangedListener)
        onDispose { controller.fullScreen.setListener(null) }
    }

    LaunchedEffect(source) {
        source?.let { controller.setSource(it) }
    }

    return controller
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    videoPlayerController: VideoPlayerController,
    contentScale: ContentScale = ContentScale.Fit,
    backgroundColor: Color = Color.Black,
) {
    val context = LocalContext.current
    DisposableEffect(videoPlayerController) {
        (context as? ComponentActivity)?.window?.let { videoPlayerController.setWindow(it) }
        onDispose { videoPlayerController.setWindow(null) }
    }

    RetainedEffect(Unit) {
        videoPlayerController.initialize()
        onRetire { videoPlayerController.release() }
    }

    val presentationState = rememberPresentationState(videoPlayerController.exoPlayer)
    CompositionLocalProvider(
        LocalVideoPlayerController provides videoPlayerController,
    ) {
        val isExplicitFullScreen = videoPlayerController.fullScreen.isExplicitFullScreen()
        val window = (context as? ComponentActivity)?.window
        val insetsController =
            remember(window) {
                window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            }

        LaunchedEffect(isExplicitFullScreen) {
            if (isExplicitFullScreen) {
                insetsController?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        DisposableEffect(insetsController) {
            onDispose {
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        val state by videoPlayerController.state.collectAsState(initial = videoPlayerController.state.value)

        BackHandler(enabled = isExplicitFullScreen) {
            if (state.isLocked) {
                videoPlayerController.toggleLock()
            } else {
                videoPlayerController.fullScreen.toggleFullScreen()
            }
        }

        VideoLayout(
            modifier = modifier,
            controller = videoPlayerController,
            presentationState = presentationState,
            backgroundColor = backgroundColor,
            thumbnailUrl = state.thumbnailUrl,
            contentScale = contentScale,
            isFullScreen = isExplicitFullScreen,
        )
    }
}

@UnstableApi
@Composable
private fun VideoLayout(
    modifier: Modifier,
    controller: VideoPlayerController,
    presentationState: PresentationState,
    backgroundColor: Color,
    thumbnailUrl: String?,
    contentScale: ContentScale,
    isFullScreen: Boolean,
) {
    val isPip by controller.isPip.collectAsState()
    var showVideoInfo by remember { mutableStateOf(false) }
    var wasPlayingBeforeDialog by remember { mutableStateOf(false) }

    if (showVideoInfo && !isPip) {
        val videoInfo = deriveVideoInfo(controller)
        VideoInfoDialog(
            resolution = videoInfo.resolution,
            duration = videoInfo.duration,
            fileSize = videoInfo.fileSize,
            videoUrl = videoInfo.videoUrl,
            onDismiss = {
                showVideoInfo = false
                if (wasPlayingBeforeDialog) controller.play()
            },
        )
    }

    if (!isFullScreen) {
        LaunchedEffect(Unit) {
            controller.showControls(autoHide = true)
        }

        val coordinatorState by PlaybackReturnStore.lastFullscreenStateFlow.collectAsState()
        LaunchedEffect(coordinatorState) {
            val consumed = PlaybackReturnStore.consumePlaybackReturnState(controller.controllerId)
            if (consumed != null) {
                if (consumed.wasEnded) return@LaunchedEffect
                if (consumed.position > 0) controller.seekTo(consumed.position)
                if (consumed.wasPlaying) controller.play()
            }
        }
    } else {
        LaunchedEffect(Unit) {
            if (!isPip && !controller.currentState { it.isPlaying }) controller.showControls(autoHide = false)
        }
    }

    Box(
        modifier =
            if (isFullScreen) {
                Modifier
                    .background(color = backgroundColor)
                    .fillMaxSize()
                    .then(modifier)
            } else {
                modifier.background(color = backgroundColor)
            },
        contentAlignment = if (isFullScreen) Alignment.Center else Alignment.TopStart,
    ) {
        PlayerFrame(
            controller = controller,
            presentationState = presentationState,
            thumbnailUrl = thumbnailUrl,
            contentScale = contentScale,
            coverScale = if (isFullScreen) ContentScale.FillHeight else ContentScale.FillWidth,
            useTextureView = !isFullScreen,
            showCover = !isFullScreen && !isPip,
            tapToToggleControls = !isFullScreen,
        )

        if (!isPip) {
            MediaControls(
                controller = controller,
                isFullscreen = isFullScreen,
                presentationState = presentationState,
                onVideoInfoClick = {
                    wasPlayingBeforeDialog = controller.currentState { it.isPlaying }
                    showVideoInfo = true
                    controller.pause()
                },
                onExitFullScreen = { controller.fullScreen.toggleFullScreen() },
            )
        }
    }
}

@UnstableApi
@Composable
private fun BoxScope.PlayerFrame(
    controller: VideoPlayerController,
    presentationState: PresentationState,
    thumbnailUrl: String?,
    contentScale: ContentScale,
    coverScale: ContentScale,
    useTextureView: Boolean = false,
    showCover: Boolean = true,
    tapToToggleControls: Boolean = false,
) {
    val context = LocalContext.current
    val playerState by controller.state.collectAsState(initial = controller.state.value)
    val videoView =
        remember(useTextureView, context) {
            if (useTextureView) {
                TextureView(context)
            } else {
                SurfaceView(context)
            }
        }

    DisposableEffect(controller.exoPlayer, videoView) {
        val player = controller.exoPlayer
        when (videoView) {
            is TextureView -> player.setVideoTextureView(videoView)
            is SurfaceView -> player.setVideoSurfaceView(videoView)
        }
        onDispose {
            when (videoView) {
                is TextureView -> player.clearVideoTextureView(videoView)
                is SurfaceView -> player.clearVideoSurfaceView(videoView)
            }
        }
    }

    AndroidView(
        factory = { videoView },
        modifier =
            Modifier
                .matchParentSize()
                .resizeWithContentScale(contentScale, presentationState.videoSizeDp),
    )

    if (showCover && presentationState.coverSurface) {
        CoverSurface(
            thumbnailUrl = thumbnailUrl,
            contentScale = coverScale,
            showReplay = playerState.wasEnded,
            onClick = { controller.play() },
        )
        val isBuffering by remember { derivedStateOf { playerState.playbackState == Player.STATE_BUFFERING } }
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 3.dp,
            )
        }
    } else {
        Box(
            modifier =
                if (tapToToggleControls) {
                    Modifier
                        .matchParentSize()
                        .clickableNoIndication {
                            if (controller.currentState { it.controlsVisible }) {
                                controller.hideControls()
                            } else {
                                controller.showControls()
                            }
                        }
                } else {
                    Modifier.matchParentSize()
                },
        )
    }
}

@Composable
private fun BoxScope.CoverSurface(
    thumbnailUrl: String?,
    contentScale: ContentScale,
    showReplay: Boolean = false,
    onClick: () -> Unit,
) {
    if (!thumbnailUrl.isNullOrEmpty()) {
        VideoThumbnail(
            modifier = Modifier.matchParentSize(),
            thumbnailUrl = thumbnailUrl,
            contentScale = contentScale,
            showReplay = showReplay,
            onClick = onClick,
        )
    } else {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Color.Black)
                    .clickableNoIndication(onClick = onClick),
        )
    }
}
