package com.huanchengfly.tieba.post.ui.widgets.compose.video

import androidx.media3.common.Player

class FullScreenLauncher(
    private val controller: VideoPlayerController,
    private val onFullScreen: (
        startPosition: Long,
        playWhenReady: Boolean,
        playbackState: Int,
        wasEnded: Boolean,
        controllerId: Long,
    ) -> Boolean,
) : FullScreenModeListener {
    override fun onFullScreenModeChanged(
        action: FullScreenChangeType,
        orientation: Int,
    ) {
        when (action) {
            FullScreenChangeType.ROTATE -> {
                return
            }

            FullScreenChangeType.TOGGLE -> {
                val currentPosition = controller.getCurrentPosition()
                val position = currentPosition.coerceAtLeast(0L)
                val currentState = controller.state.value
                val wasPlaying = currentState.isPlaying
                val playbackState = currentState.playbackState
                val wasEnded = currentState.wasEnded || playbackState == Player.STATE_ENDED
                controller.pause()
                val launched = onFullScreen(position, wasPlaying, playbackState, wasEnded, controller.controllerId)
                if (!launched && wasPlaying) controller.play()
            }
        }
    }
}
