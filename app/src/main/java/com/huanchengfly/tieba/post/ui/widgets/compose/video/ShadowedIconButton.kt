package com.huanchengfly.tieba.post.ui.widgets.compose.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShadowedSymbol(
    painter: Painter,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
) {
    Box(modifier = modifier) {
        Icon(
            painter = painter,
            tint = Color.Black.copy(alpha = 0.3f),
            modifier =
                Modifier
                    .size(iconSize)
                    .offset(2.dp, 2.dp),
            contentDescription = null,
        )
        Icon(
            painter = painter,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
            contentDescription = null,
        )
    }
}
