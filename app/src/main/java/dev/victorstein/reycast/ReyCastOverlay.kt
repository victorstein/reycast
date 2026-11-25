package dev.victorstein.reycast

import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import dev.victorstein.reycast.android.AppCatalog
import dev.victorstein.reycast.android.HintTargets
import dev.victorstein.reycast.android.ScrollAreas
import dev.victorstein.reycast.android.UsageStore
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface Session {
    fun close()
}

/**
 * The panel is an overlay window, not an activity: in DeX an activity is a freeform task that is either
 * pinned to the left edge or, when full-screen, gets a caption bar. An overlay has neither.
 */
object ReyCastOverlay {
    private const val TAG = "ReyCast"
    private var session: Session? = null
    private var catalog: AppCatalog? = null
    private var usage: UsageStore? = null
    private val background: ExecutorService = Executors.newSingleThreadExecutor()
    // Separate from [background] so a catalog refresh or icon load never delays hints, and a slow app never delays the launcher.
    private val hintCollector: ExecutorService = Executors.newSingleThreadExecutor()
    // Bumped by every summon; a collection that finishes after a newer summon is dropped.
    private var summons = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val icons = HashMap<String, Drawable>()

    var accessibilityService: ReyCastAccessibilityService? = null

    fun closeSession() {
        session?.close()
    }

    private val overlayContexts = HashMap<Int, Context>()

    // The overlay lands on the given display (DeX external monitor) rather than always on the tablet screen.
    fun toggle(appContext: Context, requestedAtMs: Long, display: Display? = null) {
        summons++
        session?.let {
            it.close()
            if (it is OverlaySession) return
        }
        val service = accessibilityService
        val sessionContext: Context
        val windowType: Int
        if (service != null) {
            sessionContext = service
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            if (!Settings.canDrawOverlays(appContext)) {
                Toast.makeText(appContext, R.string.needs_overlay_permission, Toast.LENGTH_LONG).show()
                return
            }
            val targetDisplay = display
                ?: appContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            sessionContext = overlayContexts.getOrPut(targetDisplay.displayId) {
                appContext.createWindowContext(targetDisplay, windowType, null)
            }
        }
        val appCatalog = catalog ?: AppCatalog(appContext).also { catalog = it }
        val usageStore = usage ?: UsageStore(AtomicFile(File(appContext.filesDir, "usage.tsv")), background).also { usage = it }
        session = OverlaySession(sessionContext, appCatalog, usageStore, icons, background, requestedAtMs, windowType) { session = null }
            .also { it.open() }
    }

    fun toggleHints(appContext: Context, requestedAtMs: Long) {
        val summon = ++summons
        session?.let {
            it.close()
            if (it is HintSession) return
        }
        val service = accessibilityService ?: run {
            Toast.makeText(appContext, R.string.needs_accessibility_service, Toast.LENGTH_LONG).show()
            return
        }
        hintCollector.execute {
            val targets = HintTargets.collect(service)
            Log.i(TAG, "hints collected: ${targets.size} +${SystemClock.uptimeMillis() - requestedAtMs} ms")
            mainHandler.post {
                if (summon != summons || session != null || accessibilityService !== service) return@post
                if (targets.isEmpty()) {
                    Toast.makeText(service, R.string.nothing_clickable, Toast.LENGTH_SHORT).show()
                    return@post
                }
                session = HintSession(service, targets, requestedAtMs) { session = null }.also { it.open() }
            }
        }
    }

    fun toggleScroll(appContext: Context, requestedAtMs: Long) {
        val summon = ++summons
        session?.let {
            it.close()
            if (it is ScrollSession) return
        }
        val service = accessibilityService ?: run {
            Toast.makeText(appContext, R.string.needs_accessibility_service, Toast.LENGTH_LONG).show()
            return
        }
        hintCollector.execute {
            val areas = ScrollAreas.collect(service)
            Log.i(TAG, "scroll areas: ${areas.joinToString()} +${SystemClock.uptimeMillis() - requestedAtMs} ms")
            mainHandler.post {
                if (summon != summons || session != null || accessibilityService !== service) return@post
                if (areas.isEmpty()) {
                    Toast.makeText(service, R.string.nothing_scrollable, Toast.LENGTH_SHORT).show()
                    return@post
                }
                session = ScrollSession(service, areas) { session = null }.also { it.open() }
            }
        }
    }
}
