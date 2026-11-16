package dev.victorstein.reycast

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
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

    fun toggle(context: Context, requestedAtMs: Long) {
        session?.let {
            it.close()
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, R.string.needs_overlay_permission, Toast.LENGTH_LONG).show()
            return
        }
        val appContext = context.applicationContext
        val appCatalog = catalog ?: AppCatalog(appContext).also { catalog = it }
        val usageStore = usage ?: UsageStore(AtomicFile(File(appContext.filesDir, "usage.tsv"))).also { usage = it }
        session = OverlaySession(context, appCatalog, usageStore, background, requestedAtMs) { session = null }
            .also { it.open() }
    }
}
