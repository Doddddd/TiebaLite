package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.components.MediaCache
import com.huanchengfly.tieba.post.toastShort
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

data class CorePlaybackState(
    @field:Player.State
    val playbackState: Int = Player.STATE_IDLE,
    val isPlaying: Boolean = false,
    val quickSeekAction: QuickSeekDirection = QuickSeekDirection.None,
    val playbackSpeed: Float = 1f,
    val hasPlaybackError: Boolean = false,
)

@MainThread
class VideoPlayerCore(
    private val context: Context,
    initialIsPlaying: Boolean = false,
    @Player.State initialPlaybackState: Int = Player.STATE_IDLE,
    private val preloadOnInitialize: Boolean = false,
) {
    @StringRes
    private fun mapErrorCategory(errorCode: Int): Int =
        when (errorCode) {
            2001 -> R.string.playback_error_network_connection_failed
            2002 -> R.string.playback_error_network_connection_timeout
            in -999..-1 -> R.string.playback_error_policy
            in 1000..1999 -> R.string.playback_error_misc
            in 2000..2999 -> R.string.playback_error_io
            in 3000..3999 -> R.string.playback_error_parsing
            in 4000..4999 -> R.string.playback_error_decoding
            in 5000..5999 -> R.string.playback_error_audio_track
            in 6000..6999 -> R.string.playback_error_drm
            else -> R.string.playback_error_unknown
        }

    private fun assertMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "VideoPlayerCore must be accessed on main thread" }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _coreState =
        MutableStateFlow(
            CorePlaybackState(
                playbackState = initialPlaybackState,
                isPlaying = initialIsPlaying,
            ),
        )
    val coreState: StateFlow<CorePlaybackState> = _coreState.asStateFlow()

    private var _exoPlayer: ExoPlayer? = null
    private var _previewExoPlayer: ExoPlayer? = null

    @Volatile private var released: Boolean = false
    private var source: VideoPlayerSource? = null
    private var prepared: Boolean = false
    private var playWhenReady: Boolean = false
    private var previewSeekJob: Job? = null
    private var hasPendingRetry: Boolean = false

    @OptIn(UnstableApi::class)
    val exoPlayer: ExoPlayer
        get() {
            assertMainThread()
            _exoPlayer?.let { return it }
            return ExoPlayer
                .Builder(context.applicationContext)
                .build()
                .also { _exoPlayer = it }
        }

    @OptIn(UnstableApi::class)
    val previewExoPlayer: ExoPlayer
        get() {
            assertMainThread()
            _previewExoPlayer?.let { return it }
            return ExoPlayer
                .Builder(context.applicationContext)
                .build()
                .apply { playWhenReady = false }
                .also { _previewExoPlayer = it }
        }

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val effectiveState =
                    if (hasPendingRetry && playbackState == Player.STATE_IDLE) {
                        Player.STATE_ENDED
                    } else {
                        playbackState
                    }
                _coreState.update { it.copy(playbackState = effectiveState) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _coreState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPendingRetry = true
                prepared = false
                _coreState.update {
                    it.copy(
                        playbackState = Player.STATE_ENDED,
                        isPlaying = false,
                        hasPlaybackError = true,
                    )
                }
                coroutineScope.launch(Dispatchers.Main) {
                    val category = context.getString(mapErrorCategory(error.errorCode))
                    context.toastShort("[${error.errorCode}] $category")
                }
            }
        }

    @OptIn(UnstableApi::class)
    fun initialize() {
        assertMainThread()
        if (released) return
        prepared = false
        hasPendingRetry = false
        playWhenReady = _coreState.value.isPlaying
        val player = exoPlayer
        player.removeListener(playerListener)
        player.addListener(playerListener)
        player.playWhenReady = playWhenReady
        if (playWhenReady || preloadOnInitialize) source?.let { prepare(it) }
    }

    fun setPlaying(playing: Boolean) {
        assertMainThread()
        _coreState.update { it.copy(isPlaying = playing) }
    }

    fun release() {
        assertMainThread()
        if (released) return
        released = true
        previewSeekJob?.cancel()
        previewSeekJob = null
        prepared = false
        hasPendingRetry = false
        _exoPlayer?.let {
            it.stop()
            it.clearVideoSurface()
            it.removeListener(playerListener)
            it.release()
            _exoPlayer = null
        }
        _previewExoPlayer?.let {
            it.release()
            _previewExoPlayer = null
        }
        coroutineScope.cancel()
    }

    fun play() {
        assertMainThread()
        if (released) return
        if (hasPendingRetry) {
            source?.let { prepare(it) }
            hasPendingRetry = false
            _coreState.update { it.copy(hasPlaybackError = false) }
        } else {
            ensurePrepared()
        }
        _exoPlayer?.let { Util.handlePlayButtonAction(it) }
    }

    fun pause() {
        assertMainThread()
        if (released) return
        _exoPlayer?.let {
            if (it.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) it.playWhenReady = false
        }
    }

    fun togglePlaying() {
        assertMainThread()
        val player = _exoPlayer ?: return
        if (Util.shouldShowPlayButton(player)) {
            play()
        } else {
            pause()
        }
    }

    fun quickSeekForward() {
        assertMainThread()
        if (_coreState.value.quickSeekAction != QuickSeekDirection.None) return
        val player = _exoPlayer ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration))
        _coreState.update { it.copy(quickSeekAction = QuickSeekDirection.Forward) }
    }

    fun quickSeekRewind() {
        assertMainThread()
        if (_coreState.value.quickSeekAction != QuickSeekDirection.None) return
        val player = _exoPlayer ?: return
        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
        _coreState.update { it.copy(quickSeekAction = QuickSeekDirection.Rewind) }
    }

    fun clearQuickSeekAction() {
        assertMainThread()
        _coreState.update { it.copy(quickSeekAction = QuickSeekDirection.None) }
    }

    fun seekTo(position: Long) {
        assertMainThread()
        if (released) return
        _exoPlayer?.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        assertMainThread()
        if (released) return
        _exoPlayer?.playbackParameters = PlaybackParameters(speed)
        _coreState.update { it.copy(playbackSpeed = speed) }
    }

    fun getPlaybackSpeed(): Float = _exoPlayer?.playbackParameters?.speed ?: _coreState.value.playbackSpeed

    fun getCurrentPosition(): Long = _exoPlayer?.currentPosition ?: 0L

    fun setSource(source: VideoPlayerSource) {
        assertMainThread()
        if (released) return
        this.source = source
        prepared = false
        hasPendingRetry = false
        previewSeekJob?.cancel()
        _previewExoPlayer?.stop()
        _exoPlayer?.let { player ->
            if (player.playbackState != Player.STATE_IDLE) {
                player.stop()
                prepare(source)
            }
        }
    }

    fun getVideoUrl(): String? = source?.url

    fun previewSeekTo(position: Long) {
        assertMainThread()
        if (released) return
        preparePreviewPlayer()
        val seconds = position.toInt() / 1000
        val nearestEven = (seconds - seconds.rem(2)).toLong()
        val seekPos = nearestEven * 1000

        previewSeekJob?.cancel()
        previewSeekJob =
            coroutineScope.launch {
                val p = _previewExoPlayer ?: return@launch
                try {
                    withTimeout(2000) {
                        suspendCancellableCoroutine { cont ->
                            val listener =
                                object : Player.Listener {
                                    private fun completeIfActive() {
                                        if (!cont.isActive) return
                                        p.removeListener(this)
                                        cont.resumeWith(Result.success(Unit))
                                    }

                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        if (playbackState == Player.STATE_READY) completeIfActive()
                                    }

                                    override fun onRenderedFirstFrame() {
                                        completeIfActive()
                                    }
                                }
                            p.addListener(listener)
                            cont.invokeOnCancellation { p.removeListener(listener) }
                            p.seekTo(seekPos)
                        }
                    }
                } catch (_: Exception) {
                }
                delay(100)
            }
    }

    private fun ensurePrepared() {
        if (prepared) return
        source?.let { prepare(it) }
    }

    @OptIn(UnstableApi::class)
    private fun prepare(source: VideoPlayerSource) {
        val player = _exoPlayer ?: return
        if (player.playbackState != Player.STATE_IDLE) player.stop()
        player.setMediaSource(createMediaSource(source))
        player.prepare()
        prepared = true
    }

    @OptIn(UnstableApi::class)
    private fun preparePreviewPlayer() {
        val source = this.source ?: return
        val preview = previewExoPlayer
        if (preview.playbackState == Player.STATE_IDLE) {
            preview.setMediaSource(createMediaSource(source))
            preview.prepare()
        }
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSource(source: VideoPlayerSource): MediaSource {
        val networkFactory: DataSource.Factory = MediaCache.Factory(context)

        return ProgressiveMediaSource
            .Factory(networkFactory)
            .createMediaSource(MediaItem.fromUri(source.url))
    }
}
