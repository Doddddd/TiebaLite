package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Window
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class VideoPlayerController(
    private val context: Context,
    private val initialState: VideoPlayerModel,
    isFullScreen: Boolean = false,
) {
    companion object {
        private val nextId = AtomicLong(1)
    }

    val controllerId: Long = nextId.getAndIncrement()

    val fullScreen = FullScreenController(isFullScreen)

    private var window: Window? = null

    @Volatile private var released: Boolean = false
    private var focusRequest: AudioFocusRequest? = null

    @Volatile private var hasAudioFocus: Boolean = false
    private var wasPlayingBeforeFocusLoss: Boolean = false
    private var preDuckVolume: Float = 1f

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val audioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    wasPlayingBeforeFocusLoss = false
                    pause()
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    hasAudioFocus = false
                    val wasPlaying = state.value.isPlaying
                    core.pause()
                    cancelAutoHideControls()
                    showControls(autoHide = false)
                    wasPlayingBeforeFocusLoss = wasPlaying
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    preDuckVolume = exoPlayer.volume
                    exoPlayer.volume = preDuckVolume * 0.2f
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    if (exoPlayer.volume != preDuckVolume) exoPlayer.volume = preDuckVolume
                    if (wasPlayingBeforeFocusLoss) {
                        wasPlayingBeforeFocusLoss = false
                        play()
                    }
                }
            }
        }

    private val uiState = MutableStateFlow(initialState)

    private val core =
        VideoPlayerCore(
            context = context,
            initialIsPlaying = initialState.isPlaying,
            initialPlaybackState = initialState.playbackState,
            preloadOnInitialize = isFullScreen,
        )

    private val autoHideScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var coreStateJob: Job? = null
    private var autoHideControllerJob: Job? = null
    private var autoHideLockControlsJob: Job? = null

    private val _isPip = MutableStateFlow(false)
    val isPip: StateFlow<Boolean> = _isPip.asStateFlow()
    private val _isPipSupported = MutableStateFlow(false)
    val isPipSupported: StateFlow<Boolean> = _isPipSupported.asStateFlow()

    val state: StateFlow<VideoPlayerModel> = uiState

    @OptIn(UnstableApi::class)
    val exoPlayer: ExoPlayer
        get() = core.exoPlayer

    @OptIn(UnstableApi::class)
    val previewExoPlayer: ExoPlayer
        get() = core.previewExoPlayer

    internal inline fun <T> currentState(filter: (VideoPlayerModel) -> T): T = filter(state.value)

    fun initialize() {
        if (state.value.isPlaying && !requestAudioFocus()) core.setPlaying(false)
        coreStateJob?.cancel()
        coreStateJob =
            autoHideScope.launch {
                core.coreState.collect { coreState ->
                    uiState.update {
                        it.copy(
                            playbackState = coreState.playbackState,
                            isPlaying = coreState.isPlaying,
                            quickSeekAction = coreState.quickSeekAction,
                            playbackSpeed = coreState.playbackSpeed,
                        )
                    }
                    if ((coreState.playbackState == Player.STATE_ENDED || coreState.hasPlaybackError) && !state.value.wasEnded) {
                        uiState.update { it.copy(wasEnded = true) }
                    }
                    if (coreState.playbackState == Player.STATE_ENDED || coreState.hasPlaybackError) showControls(autoHide = false)
                }
            }
        core.initialize()
    }

    fun release() {
        if (released) return
        released = true
        cancelAutoHideControls()
        cancelAutoHideLockControls()
        coreStateJob?.cancel()
        core.release()
        autoHideScope.cancel()
        abandonAudioFocus()
        window = null
    }

    fun play() {
        if (released) return
        wasPlayingBeforeFocusLoss = false
        if (state.value.wasEnded) uiState.update { it.copy(wasEnded = false) }
        if (!requestAudioFocus()) return
        core.play()
        autoHideControls()
    }

    fun pause() {
        if (released) return
        wasPlayingBeforeFocusLoss = false
        core.pause()
        cancelAutoHideControls()
        showControls(autoHide = false)
        abandonAudioFocus()
    }

    fun togglePlaying() {
        if (state.value.isPlaying) pause() else play()
    }

    fun quickSeekForward() {
        if (released) return
        core.quickSeekForward()
    }

    fun quickSeekRewind() {
        if (released) return
        core.quickSeekRewind()
    }

    fun seekTo(position: Long) {
        if (released) return
        core.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (released) return
        core.setPlaybackSpeed(speed)
    }

    fun getPlaybackSpeed(): Float = core.getPlaybackSpeed()

    fun getCurrentPosition(): Long = core.getCurrentPosition()

    fun setSource(source: VideoPlayerSource) {
        if (released) return
        core.setSource(source)
    }

    fun getVideoUrl(): String? = core.getVideoUrl()

    fun previewSeekTo(position: Long) {
        if (released) return
        core.previewSeekTo(position)
    }

    fun showControls(autoHide: Boolean = true) {
        if (state.value.isLocked) return
        uiState.update { it.copy(controlsVisible = true) }
        if (autoHide) autoHideControls() else cancelAutoHideControls()
    }

    fun hideControls() {
        uiState.update { it.copy(controlsVisible = false) }
    }

    fun toggleLock() {
        val locked = !state.value.isLocked
        uiState.update {
            it.copy(
                isLocked = locked,
                controlsVisible = !locked,
                lockControlsVisible = locked,
            )
        }
        if (locked) {
            autoHideLockControls()
        } else {
            cancelAutoHideLockControls()
        }
    }

    fun toggleLockControls() {
        if (!state.value.isLocked) return
        val visible = !state.value.lockControlsVisible
        uiState.update { it.copy(lockControlsVisible = visible) }
        if (visible) {
            autoHideLockControls()
        } else {
            cancelAutoHideLockControls()
        }
    }

    private fun cancelAutoHideLockControls() {
        autoHideLockControlsJob?.cancel()
    }

    private fun autoHideLockControls() {
        cancelAutoHideLockControls()
        autoHideLockControlsJob =
            autoHideScope.launch {
                delay(3000)
                uiState.update { it.copy(lockControlsVisible = false) }
            }
    }

    fun setDraggingProgress(draggingProgress: DraggingProgress?) {
        uiState.update { it.copy(draggingProgress = draggingProgress) }
    }

    fun setVerticalDragAdjustment(adjustment: VerticalDragAdjustment?) {
        uiState.update { it.copy(verticalDragAdjustment = adjustment) }
    }

    fun setQuickSeekAction(quickSeekAction: QuickSeekDirection) {
        uiState.update { it.copy(quickSeekAction = quickSeekAction) }
        core.clearQuickSeekAction()
    }

    fun setWindow(window: Window?) {
        this.window = window
    }

    fun getVolumeLevel(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
    }

    fun setVolumeLevel(level: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (level * max).roundToInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    fun getBrightnessLevel(): Float {
        val w = window ?: return 0.5f
        val brightness = w.attributes.screenBrightness
        if (brightness >= 0f) return brightness
        return try {
            val systemLevel =
                Settings.System.getInt(
                    w.context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                )
            systemLevel / 255f
        } catch (e: Settings.SettingNotFoundException) {
            0.5f
        }
    }

    fun setBrightnessLevel(level: Float) {
        val w = window ?: return
        val params = w.attributes
        params.screenBrightness = level.coerceIn(0f, 1f)
        w.attributes = params
    }

    fun setPipMode(pip: Boolean) {
        _isPip.value = pip
    }

    fun setPipSupported(supported: Boolean) {
        _isPipSupported.value = supported
    }

    var onRequestPip: () -> Unit

    private fun cancelAutoHideControls() {
        autoHideControllerJob?.cancel()
    }

    private fun autoHideControls() {
        cancelAutoHideControls()
        autoHideControllerJob =
            autoHideScope.launch {
                delay(3000)
                if (state.value.isPlaying) hideControls()
            }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attr =
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                val request =
                    AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attr)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasAudioFocus = true
                true
            }

            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                true
            }

            else -> {
                false
            }
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus && focusRequest == null) return
        hasAudioFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }
}
