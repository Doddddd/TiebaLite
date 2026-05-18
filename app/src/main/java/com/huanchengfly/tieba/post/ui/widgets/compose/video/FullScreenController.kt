package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.content.pm.ActivityInfo
import androidx.compose.runtime.mutableStateOf

enum class FullScreenChangeType { TOGGLE, ROTATE }

interface FullScreenModeListener {
    fun onFullScreenModeChanged(
        action: FullScreenChangeType,
        orientation: Int,
    )
}

class FullScreenController(
    isFullScreen: Boolean = false,
) {
    private var listener: FullScreenModeListener? = null
    private val explicitFullScreen = mutableStateOf(isFullScreen)

    fun setListener(listener: FullScreenModeListener?) {
        this.listener = listener
    }

    fun hasListener(): Boolean = listener != null

    fun setExplicitFullScreen(explicit: Boolean) {
        explicitFullScreen.value = explicit
    }

    fun isExplicitFullScreen(): Boolean = explicitFullScreen.value

    fun toggleFullScreen() {
        listener?.onFullScreenModeChanged(FullScreenChangeType.TOGGLE, -1)
    }

    fun rotateFullScreen(isCurrentlyLandscape: Boolean) {
        val targetOrientation =
            if (isCurrentlyLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        listener?.onFullScreenModeChanged(FullScreenChangeType.ROTATE, targetOrientation)
    }
}
