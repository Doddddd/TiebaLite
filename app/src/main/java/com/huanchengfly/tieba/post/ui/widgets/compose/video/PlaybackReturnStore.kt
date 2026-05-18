package com.huanchengfly.tieba.post.ui.widgets.compose.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackReturnState(
    val controllerId: Long,
    val position: Long = 0L,
    val wasPlaying: Boolean = false,
    val wasEnded: Boolean = false,
)

object PlaybackReturnStore {
    private val _lastFullscreenStateFlow = MutableStateFlow<PlaybackReturnState?>(null)
    val lastFullscreenStateFlow: StateFlow<PlaybackReturnState?>
        get() = _lastFullscreenStateFlow.asStateFlow()

    fun setPlaybackReturnState(state: PlaybackReturnState) {
        _lastFullscreenStateFlow.value = state
    }

    fun consumePlaybackReturnState(controllerId: Long): PlaybackReturnState? {
        var result: PlaybackReturnState? = null
        _lastFullscreenStateFlow.update { current ->
            if (current == null) return@update null
            if (current.controllerId != controllerId) return@update current
            if (current.position <= 0L && !current.wasPlaying && !current.wasEnded) return@update current
            result = current
            null
        }
        return result
    }
}
