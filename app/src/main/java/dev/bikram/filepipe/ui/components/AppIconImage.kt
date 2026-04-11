package dev.bikram.filepipe.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Renders the app icon the same way the system launcher preview does, including adaptive icons
 * (API 26+). [androidx.compose.ui.res.painterResource] for [R.mipmap.ic_launcher] is unsafe on
 * adaptive resources, so we rasterize [android.content.pm.PackageManager.getApplicationIcon].
 *
 * The [ImageBitmap] is cached in [remember] keyed by package name so [Bitmap.asImageBitmap] is
 * not recreated every recomposition.
 */
@Composable
fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageBitmap: ImageBitmap = remember(context.applicationContext.packageName) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        bitmap.asImageBitmap()
    }
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = modifier
    )
}
