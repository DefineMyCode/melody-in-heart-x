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
 * 从封面提取「上色 + 下色」双色（方案D 氛围背景，网易云式）。
 *
 * 网易云的背景与封面融洽的关键：不是全图一个主色，而是**上段取色用于封面以上/封面区，
 * 下段取色用于封面以下的渐变终点**——封面图自身的上下色调差异被保留。
 *
 * ponytail: 解码采样放 Default 线程，结果 remember 按 uri 缓存；
 * 上段=位图上部 1/3 区域平均色，下段=下部 1/3 区域平均色（比 Palette swatch 更贴合"融洽"）。
 * 无封面/解码失败 → 返回 null，调用方退化为主题色渐变。
 */
data class CoverColors(
    val top: Color,
    val bottom: Color,
)

@Composable
fun rememberCoverColors(albumArtUri: android.net.Uri?): CoverColors? {
    val context = LocalContext.current
    var colors by remember(albumArtUri) { mutableStateOf<CoverColors?>(null) }

    LaunchedEffect(albumArtUri) {
        val uri = albumArtUri ?: return@LaunchedEffect
        val extracted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context).data(uri).allowHardware(false).build()
                val drawable = context.imageLoader.execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: drawable?.toBitmap() ?: return@runCatching null
                // 采样降尺寸：取色不需要原图分辨率
                val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                val upper = avgColor(small, 0, 0, 64, 21)
                val lower = avgColor(small, 0, 43, 64, 64)
                CoverColors(top = upper, bottom = lower)
            }.getOrNull()
        }
        colors = extracted
    }
    return colors
}

/** 区域平均色（全像素参与，不过滤极值）。 */
private fun avgColor(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Color {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val c = bitmap.getPixel(x, y)
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            n++
        }
    }
    if (n == 0L) return Color.White
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
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
    return if (bmp.width > max || bmp.height > max) {
        val scale = max.toFloat() / maxOf(bmp.width, bmp.height)
        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    } else bmp
}
