package cn.com.dcsgo.mihx.feature.home

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

/**
 * 从封面 URI 提取主色（方案D 氛围背景）。
 *
 * ponytail: Palette 同步解码放 Default 线程，结果 remember 按 uri 缓存；
 * 无封面/解码失败 → 返回 null，调用方退化为主题色渐变。不做 LRU——
 * 主屏一次只显示一首歌，同一首歌反复切页时 coil 自己有内存缓存。
 */
/** 主色 + 次色(vibrant)成对提取。次色与主色过近时给 null（调用方自行退化）。 */
@Composable
fun rememberCoverColors(albumArtUri: android.net.Uri?): Pair<Color, Color?>? {
    val context = LocalContext.current
    var colors by remember(albumArtUri) { mutableStateOf<Pair<Color, Color?>?>(null) }

    LaunchedEffect(albumArtUri) {
        val uri = albumArtUri ?: return@LaunchedEffect
        val extracted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context).data(uri).allowHardware(false).build()
                val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching null
                val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
                    ?: return@runCatching null
                val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                val dominant = (palette.dominantSwatch ?: palette.vibrantSwatch)?.let { Color(it.rgb) }
                    ?: return@runCatching null
                val vibrant = palette.vibrantSwatch?.let { Color(it.rgb) }
                dominant to vibrant
            }.getOrNull()
        }
        colors = extracted
    }
    return colors
}

@Composable
fun rememberDominantColor(albumArtUri: android.net.Uri?): Color? {
    val context = LocalContext.current
    var color by remember(albumArtUri) { mutableStateOf<Color?>(null) }

    LaunchedEffect(albumArtUri) {
        val uri = albumArtUri ?: return@LaunchedEffect
        val extracted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context).data(uri).allowHardware(false).build()
                val drawable = context.imageLoader.execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: drawable?.toBitmap() ?: return@runCatching null
                Palette.from(bitmap).maximumColorCount(16).generate()
                    .getDominantColorOrDefault()
            }.getOrNull()
        }
        color = extracted
    }
    return color
}

private fun Palette.getDominantColorOrDefault(): Color? {
    val swatch = dominantSwatch ?: vibrantSwatch ?: return null
    return Color(swatch.rgb)
}

private fun Drawable.toBitmap(): Bitmap? {
    val bmp = if (this is BitmapDrawable) this.bitmap
    else Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
        val canvas = android.graphics.Canvas(it)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
    }
    // 采样降尺寸：取色不需要原图分辨率，压到 128px 足够（ponytail: 省内存省时间）
    val max = 128
    if (bmp.width <= max && bmp.height <= max) return bmp
    val scale = max.toFloat() / maxOf(bmp.width, bmp.height)
    return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
}
