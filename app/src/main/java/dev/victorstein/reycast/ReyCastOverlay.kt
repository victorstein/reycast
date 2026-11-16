package dev.victorstein.reycast

import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.AtomicFile
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import dev.victorstein.reycast.android.AppCatalog
import dev.victorstein.reycast.android.UsageStore
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The panel is an overlay window, not an activity: in DeX an activity is a freeform task that is either
 * pinned to the left edge or, when full-screen, gets a caption bar. An overlay has neither.
 */
object ReyCastOverlay {
    private var session: OverlaySession? = null
    private var catalog: AppCatalog? = null
    private var usage: UsageStore? = null
    private val background: ExecutorService = Executors.newSingleThreadExecutor()
    private val icons = HashMap<String, Drawable>()

    private val overlayContexts = HashMap<Int, Context>()

    // The overlay lands on the given display (DeX external monitor) rather than always on the tablet screen.
    fun toggle(appContext: Context, requestedAtMs: Long, display: Display? = null) {
        session?.let {
            it.close()
            return
        }
        if (!Settings.canDrawOverlays(appContext)) {
            Toast.makeText(appContext, R.string.needs_overlay_permission, Toast.LENGTH_LONG).show()
            return
        }
        val targetDisplay = display
            ?: appContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val overlayContext = overlayContexts.getOrPut(targetDisplay.displayId) {
            appContext.createWindowContext(targetDisplay, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }
        val appCatalog = catalog ?: AppCatalog(appContext).also { catalog = it }
        val usageStore = usage ?: UsageStore(AtomicFile(File(appContext.filesDir, "usage.tsv")), background).also { usage = it }
        session = OverlaySession(overlayContext, appCatalog, usageStore, icons, background, requestedAtMs) { session = null }
            .also { it.open() }
    }
}
