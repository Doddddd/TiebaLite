package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.content.ClipData
import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.toastShort
import com.huanchengfly.tieba.post.ui.widgets.compose.preference.SegmentedListPreference
import kotlinx.coroutines.launch

data class VideoInfoData(
    val resolution: String,
    val duration: String,
    val fileSize: String,
    val videoUrl: String,
)

private data class PlayerInfoSnapshot(
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int? = null,
    val durationMs: Long = 0L,
)

@OptIn(UnstableApi::class)
@Composable
internal fun deriveVideoInfo(controller: VideoPlayerController): VideoInfoData {
    val exoPlayer = controller.exoPlayer
    val infoSnapshot by
        produceState(initialValue = PlayerInfoSnapshot(), exoPlayer) {
            fun captureSnapshot(): PlayerInfoSnapshot {
                val format = exoPlayer.videoFormat
                return PlayerInfoSnapshot(
                    width = format?.width ?: 0,
                    height = format?.height ?: 0,
                    bitrate = format?.bitrate,
                    durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
                )
            }

            val listener =
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            value = captureSnapshot()
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        value =
                            value.copy(
                                width = videoSize.width,
                                height = videoSize.height,
                            )
                    }
                }

            exoPlayer.addListener(listener)
            value = captureSnapshot()
            awaitDispose { exoPlayer.removeListener(listener) }
        }
    val resolution =
        if (infoSnapshot.width > 0 && infoSnapshot.height > 0) {
            "${infoSnapshot.width} × ${infoSnapshot.height}"
        } else {
            stringResource(id = R.string.unknown)
        }
    val durationMs = infoSnapshot.durationMs
    val duration =
        if (durationMs > 0) {
            getDurationString(durationMs)
        } else {
            stringResource(id = R.string.unknown)
        }
    val context = LocalContext.current
    val bitrate = infoSnapshot.bitrate
    val fileSize =
        if (bitrate != null && bitrate > 0 && durationMs > 0) {
            val estimatedBytes = bitrate.toLong() * durationMs / 8000
            Formatter.formatFileSize(context, estimatedBytes)
        } else {
            stringResource(R.string.unknown)
        }
    val videoUrl = controller.getVideoUrl() ?: stringResource(id = R.string.unknown)
    return VideoInfoData(resolution, duration, fileSize, videoUrl)
}

@Composable
internal fun VideoInfoDialog(
    resolution: String,
    duration: String,
    fileSize: String,
    videoUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val copyColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.title_video_info)) },
        text = {
            Column {
                InfoRow(label = stringResource(id = R.string.label_resolution), value = resolution)
                InfoRow(label = stringResource(id = R.string.label_duration), value = duration)
                InfoRow(label = stringResource(id = R.string.label_file_size), value = fileSize)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.label_video_url),
                        color = labelColor,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(id = R.string.label_click_to_copy),
                        color = copyColor,
                        textDecoration = TextDecoration.Underline,
                        modifier =
                            Modifier.clickable {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(videoUrl, videoUrl),
                                        ),
                                    )
                                }
                                context.toastShort(R.string.toast_copy_success)
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.btn_close))
            }
        },
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = labelColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun formatSpeed(speed: Float): String {
    val text =
        if (speed == speed.toInt().toFloat()) {
            speed.toInt().toString()
        } else {
            speed.toString()
        }
    return "${text}X"
}

@Composable
fun SpeedControlButton(
    iconSize: Dp,
    buttonSize: Dp,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val speeds = listOf(0.25f, 0.5f, 1f, 1.25f, 1.5f, 2f, 4f)
    val speedLabel = formatSpeed(currentSpeed)
    val contentDesc = stringResource(R.string.btn_playback_speed) + " " + speedLabel
    val options =
        remember {
            speeds.associateWith { formatSpeed(it) }
        }

    Box(
        modifier = Modifier.size(buttonSize),
        contentAlignment = Alignment.Center,
    ) {
        SegmentedListPreference(
            modifier = Modifier.size(0.dp),
            value = currentSpeed,
            onValueChange = onSpeedChange,
            options = options,
            title = "",
            expanded = showMenu,
            onExpandedChange = { showMenu = it },
        )

        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable { showMenu = true }
                    .semantics { contentDescription = contentDesc },
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ShadowedSymbol(
                    painter = painterResource(R.drawable.ic_sym_speed),
                    iconSize = iconSize,
                )
            }
        }

        if (currentSpeed != 1f) {
            Text(
                text = speedLabel,
                color = Color.White,
                fontSize = 11.sp,
                style = TextStyle(shadow = VideoControlShadow),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 2.dp),
            )
        }
    }
}
