package com.huanchengfly.tieba.post.ui.widgets.compose.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.GlideImage
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.clickableNoIndication

@Composable
fun VideoThumbnail(
    modifier: Modifier = Modifier,
    thumbnailUrl: String?,
    contentScale: ContentScale = ContentScale.FillWidth,
    showReplay: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (!thumbnailUrl.isNullOrEmpty()) {
            GlideImage(
                model = thumbnailUrl,
                contentDescription = stringResource(R.string.desc_video),
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        }

        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clickableNoIndication(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            ShadowedSymbol(
                painter = painterResource(if (showReplay) R.drawable.ic_sym_replay else R.drawable.ic_sym_play_arrow),
                modifier = Modifier.size(48.dp),
                iconSize = 48.dp,
            )
        }
    }
}
