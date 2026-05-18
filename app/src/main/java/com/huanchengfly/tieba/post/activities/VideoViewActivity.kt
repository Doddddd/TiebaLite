package com.huanchengfly.tieba.post.activities

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.VideoInfo
import com.huanchengfly.tieba.post.arch.collectIn
import com.huanchengfly.tieba.post.components.BD_VIDEO_HOST
import com.huanchengfly.tieba.post.goToActivity
import com.huanchengfly.tieba.post.theme.TiebaLiteTheme
import com.huanchengfly.tieba.post.toastShort
import com.huanchengfly.tieba.post.ui.widgets.compose.video.FullScreenChangeType
import com.huanchengfly.tieba.post.ui.widgets.compose.video.FullScreenModeListener
import com.huanchengfly.tieba.post.ui.widgets.compose.video.PlaybackReturnState
import com.huanchengfly.tieba.post.ui.widgets.compose.video.PlaybackReturnStore
import com.huanchengfly.tieba.post.ui.widgets.compose.video.VideoPlayer
import com.huanchengfly.tieba.post.ui.widgets.compose.video.VideoPlayerController
import com.huanchengfly.tieba.post.ui.widgets.compose.video.VideoPlayerSource
import com.huanchengfly.tieba.post.ui.widgets.compose.video.retainVideoPlayerController
import com.huanchengfly.tieba.post.utils.ThemeUtil
import kotlinx.coroutines.flow.distinctUntilChangedBy

class VideoViewActivity :
    ComponentActivity(),
    FullScreenModeListener {
    private lateinit var mInsetsController: WindowInsetsControllerCompat
    private var videoPlayerController: VideoPlayerController? = null
    private var playOnRecreate = false
    private var playWhenReadyOnLaunch = true
    private var startPosition: Long = 0L

    @Player.State private var playbackStateOnLaunch: Int = Player.STATE_IDLE
    private var wasEndedOnLaunch = false
    private var returnStateSaved = false
    private var videoWidth = 0
    private var videoHeight = 0
    private lateinit var pipAspectRatio: Rational

    private val pipActionReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PIP_PLAY_PAUSE -> {
                        videoPlayerController?.togglePlaying()
                    }

                    ACTION_PIP_REPLAY -> {
                        videoPlayerController?.seekTo(0)
                        videoPlayerController?.play()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
        )

        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter().apply {
                addAction(ACTION_PIP_PLAY_PAUSE)
                addAction(ACTION_PIP_REPLAY)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        playOnRecreate = savedInstanceState?.getBoolean("PLAY_ON_RECREATE", false) == true
        playWhenReadyOnLaunch = savedInstanceState?.getBoolean("PLAY_WHEN_READY_ON_LAUNCH")
            ?: intent.getBooleanExtra("play_when_ready", true)
        startPosition = savedInstanceState?.getLong("START_POSITION", 0L)
            ?: intent.getLongExtra("start_position", 0L)
        playbackStateOnLaunch = savedInstanceState?.getInt("PLAYBACK_STATE_ON_LAUNCH")
            ?: intent.getIntExtra("playback_state", Player.STATE_IDLE)
        wasEndedOnLaunch = savedInstanceState?.getBoolean("WAS_ENDED_ON_LAUNCH")
            ?: intent.getBooleanExtra("was_ended", false)
        mInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        mInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val data = intent.data ?: throw NullPointerException("No video provided!")
        val thumbnailUrl = intent.getStringExtra("video_thumbnail")
        videoWidth = intent.getIntExtra("video_width", 0)
        videoHeight = intent.getIntExtra("video_height", 0)
        pipAspectRatio =
            if (videoWidth > 0 && videoHeight > 0) {
                Rational(videoWidth, videoHeight)
            } else {
                Rational(16, 9)
            }

        val fullScreenOrientation =
            if (videoWidth > 0 && videoHeight > 0 && videoHeight > videoWidth) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        mInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        this.requestedOrientation = fullScreenOrientation

        setContent {
            videoPlayerController =
                retainVideoPlayerController(
                    source = VideoPlayerSource(data.toString()),
                    thumbnailUrl = thumbnailUrl,
                    fullScreenModeChangedListener = this,
                    playWhenReady = playWhenReadyOnLaunch,
                    initialPlaybackState = playbackStateOnLaunch,
                    initialWasEnded = wasEndedOnLaunch,
                    isFullScreen = true,
                )
            val ctrl = videoPlayerController
            LaunchedEffect(Unit) {
                ctrl?.fullScreen?.setExplicitFullScreen(true)
                val isPipSupported =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                ctrl?.setPipSupported(isPipSupported)
                if (isPipSupported) ctrl?.onRequestPip = { enterPipMode() }
                if (startPosition > 0) ctrl?.seekTo(startPosition)
            }

            TiebaLiteTheme(colorSchemeExt = ThemeUtil.colorState.value) {
                VideoPlayer(videoPlayerController = videoPlayerController!!)
            }

            LaunchedEffect(videoPlayerController) {
                val controller = videoPlayerController ?: return@LaunchedEffect

                if (playOnRecreate) controller.play()

                controller.state
                    .distinctUntilChangedBy { it.isPlaying to it.wasEnded }
                    .collectIn(this@VideoViewActivity) {
                        if (it.isPlaying) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        updatePipActions(it.isPlaying, it.wasEnded)
                    }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isInPipMode) return
        playOnRecreate = videoPlayerController?.state?.value?.isPlaying == true
        videoPlayerController?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipActionReceiver)
        val controller = videoPlayerController ?: return
        val inlineControllerId = intent.getLongExtra("controller_id", 0L)
        if (inlineControllerId != 0L && !returnStateSaved) savePlaybackReturnState(controller)
    }

    private fun savePlaybackReturnState(controller: VideoPlayerController) {
        val inlineControllerId = intent.getLongExtra("controller_id", 0L)
        if (inlineControllerId == 0L) return
        val currentState = controller.state.value
        val returnState =
            PlaybackReturnState(
                controllerId = inlineControllerId,
                position = controller.getCurrentPosition(),
                wasPlaying = currentState.isPlaying,
                wasEnded = currentState.wasEnded || currentState.playbackState == Player.STATE_ENDED,
            )
        PlaybackReturnStore.setPlaybackReturnState(returnState)
        returnStateSaved = true
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPipMode) return
        val controller = videoPlayerController ?: return
        val state = controller.state.value
        val params = buildPipParams(state.isPlaying, state.wasEnded, pipAspectRatio)
        enterPictureInPictureMode(params)
    }

    private fun updatePipActions(
        isPlaying: Boolean,
        wasEnded: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPipMode) return
        setPictureInPictureParams(buildPipParams(isPlaying, wasEnded, pipAspectRatio))
    }

    private fun buildPipParams(
        isPlaying: Boolean,
        wasEnded: Boolean,
        aspectRatio: Rational,
    ): PictureInPictureParams {
        val (iconRes, titleRes, actionIntent) =
            when {
                wasEnded -> {
                    Triple(
                        R.drawable.ic_sym_replay,
                        R.string.btn_replay,
                        Intent(ACTION_PIP_REPLAY),
                    )
                }

                isPlaying -> {
                    Triple(
                        R.drawable.ic_sym_pause,
                        R.string.btn_pause,
                        Intent(ACTION_PIP_PLAY_PAUSE),
                    )
                }

                else -> {
                    Triple(
                        R.drawable.ic_sym_play_arrow,
                        R.string.btn_play,
                        Intent(ACTION_PIP_PLAY_PAUSE),
                    )
                }
            }
        val title = getString(titleRes)
        val action =
            RemoteAction(
                Icon.createWithResource(this, iconRes),
                title,
                title,
                PendingIntent.getBroadcast(
                    this,
                    0,
                    actionIntent.setPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        return PictureInPictureParams
            .Builder()
            .setAspectRatio(aspectRatio)
            .setActions(listOf(action))
            .build()
    }

    override fun onPictureInPictureModeChanged(
        isInPipMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        videoPlayerController?.setPipMode(isInPipMode)
        if (!isInPipMode) videoPlayerController?.showControls()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("PLAY_ON_RECREATE", playOnRecreate)
        outState.putBoolean("PLAY_WHEN_READY_ON_LAUNCH", playWhenReadyOnLaunch)
        outState.putLong("START_POSITION", videoPlayerController?.getCurrentPosition() ?: 0L)
        outState.putInt("PLAYBACK_STATE_ON_LAUNCH", videoPlayerController?.state?.value?.playbackState ?: playbackStateOnLaunch)
        outState.putBoolean("WAS_ENDED_ON_LAUNCH", videoPlayerController?.state?.value?.wasEnded ?: wasEndedOnLaunch)
        super.onSaveInstanceState(outState)
    }

    override fun onFullScreenModeChanged(
        action: FullScreenChangeType,
        orientation: Int,
    ) {
        val controller = videoPlayerController ?: return

        when (action) {
            FullScreenChangeType.ROTATE -> {
                this.requestedOrientation = orientation
            }

            FullScreenChangeType.TOGGLE -> {
                savePlaybackReturnState(controller)
                finish()
            }
        }
    }

    companion object {
        private const val ACTION_PIP_PLAY_PAUSE = "com.huanchengfly.tieba.post.PIP_PLAY_PAUSE"
        private const val ACTION_PIP_REPLAY = "com.huanchengfly.tieba.post.PIP_REPLAY"

        fun launch(
            context: Context,
            videoUrl: String,
            thumbnailUrl: String?,
            videoWidth: Int = 0,
            videoHeight: Int = 0,
            startPosition: Long = 0L,
            playWhenReady: Boolean = true,
            @Player.State playbackState: Int = Player.STATE_IDLE,
            wasEnded: Boolean = false,
            controllerId: Long = 0L,
        ): Boolean {
            val data = Uri.parse(videoUrl)

            // Check tb-video is unauthorized
            if (data.host == BD_VIDEO_HOST && videoUrl.endsWith(".mp4")) {
                context.toastShort(R.string.title_not_logged_in)
                return false
            }

            // Free more memory now
            Glide.get(context).clearMemory()

            context.goToActivity<VideoViewActivity> {
                this.data = data
                thumbnailUrl?.let { putExtra("video_thumbnail", it) }
                if (videoWidth > 0) putExtra("video_width", videoWidth)
                if (videoHeight > 0) putExtra("video_height", videoHeight)
                if (startPosition > 0) putExtra("start_position", startPosition)
                putExtra("play_when_ready", playWhenReady)
                putExtra("playback_state", playbackState)
                putExtra("was_ended", wasEnded)
                if (controllerId != 0L) putExtra("controller_id", controllerId)
            }
            return true
        }

        fun launch(
            context: Context,
            videoInfo: VideoInfo,
        ): Boolean =
            launch(
                context,
                videoInfo.videoUrl,
                videoInfo.thumbnailUrl,
                videoInfo.videoWidth,
                videoInfo.videoHeight,
            )
    }
}
