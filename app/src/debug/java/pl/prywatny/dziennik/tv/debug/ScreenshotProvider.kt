package pl.prywatny.dziennik.tv.debug

import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.inspector.WindowInspector
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Debug builds only. Android TV caps the UI at 1080p even on 4K screens, so screencap gives
 * 1920×1080. This renders every window of the app (incl. dialogs) into a bitmap at [scale]×,
 * which keeps text and shapes sharp: `adb shell am broadcast -a pl.prywatny.dziennik.tv.SCREENSHOT
 * --es name start --ef scale 2` → files/screenshots/start.png. Started via this provider, so the
 * app code has no trace of it.
 */
class ScreenshotProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return false
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val name = intent.getStringExtra("name") ?: "screenshot"
                    val scale = intent.getFloatExtra("scale", 2f)
                    resultData = runCatching { capture(c, name, scale).path }.getOrElse { "error: ${it.message}" }
                }
            },
            IntentFilter(ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        return true
    }

    private fun capture(context: Context, name: String, scale: Float): File {
        val roots = WindowInspector.getGlobalWindowViews().filter { it.isShown && it.width > 0 }
        require(roots.isNotEmpty()) { "brak okien" }
        val metrics = context.resources.displayMetrics
        val bitmap = Bitmap.createBitmap((metrics.widthPixels * scale).toInt(), (metrics.heightPixels * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val location = IntArray(2)
        // Windows are returned bottom to top, so dialogs land on top of the activity.
        for (root in roots) {
            root.getLocationOnScreen(location)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.translate(location[0].toFloat(), location[1].toFloat())
            root.draw(canvas)
            canvas.restore()
        }
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        return File(dir, "$name.png").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        const val ACTION = "pl.prywatny.dziennik.tv.SCREENSHOT"
    }
}
