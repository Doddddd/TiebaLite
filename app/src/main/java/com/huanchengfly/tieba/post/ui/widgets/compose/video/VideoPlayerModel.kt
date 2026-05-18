package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.os.Parcelable
import androidx.media3.common.Player
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.math.abs

data class VideoPlayerSource(
    val url: String,
)

enum class VerticalDragType { Brightness, Volume }

@Parcelize
data class VerticalDragAdjustment(
    val type: VerticalDragType,
    val level: Float,
) : Parcelable

@Parcelize
data class DraggingProgress(
    val finalTime: Float,
    val diffTime: Float,
) : Parcelable {
    @IgnoredOnParcel
    val progressText: String =
        "${getDurationString(finalTime.toLong())} " +
            "[${if (diffTime < 0) "-" else "+"}${
                getDurationString(abs(diffTime.toLong()))
            }]"
}

enum class QuickSeekDirection {
    None,
    Rewind,
    Forward,
}

@Parcelize
data class VideoPlayerModel(
    val thumbnailUrl: String? = null,
    val isPlaying: Boolean = false,
    val controlsVisible: Boolean = false,
    val draggingProgress: DraggingProgress? = null,
    val verticalDragAdjustment: VerticalDragAdjustment? = null,
    @field:Player.State
    val playbackState: Int = Player.STATE_IDLE,
    val wasEnded: Boolean = false,
    val quickSeekAction: QuickSeekDirection = QuickSeekDirection.None,
    val playbackSpeed: Float = 1f,
    val isLocked: Boolean = false,
    val lockControlsVisible: Boolean = false,
) : Parcelable

fun getDurationString(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"

    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(totalMinutes, seconds)
    }
}
