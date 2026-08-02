@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Square album-art thumbnail for song rows (UI 设计定稿统一版). Renders the embedded art when
 * [uri] is present; otherwise a monochrome music-note placeholder on
 * [MaterialTheme.colorScheme.surfaceVariant] so every song entry keeps its visual slot.
 */
@Composable
fun AlbumArtThumb(
    uri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 6.dp,
) {
    if (uri != null) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Material "music note" glyph (24dp viewport), drawn locally so :core:ui stays free of the heavy
 * material-icons-extended dependency (MusicNote lives there, not in the icons-core default set).
 * The Icon tint above recolors the solid fill.
 */
private val MusicNote: ImageVector = ImageVector.Builder(
    name = "MusicNote",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 3f)
        verticalLineToRelative(10.55f)
        curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2f, -0.55f)
        curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
        reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
        reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
        verticalLineTo(7f)
        horizontalLineToRelative(4f)
        verticalLineTo(3f)
        close()
    }
}.build()
