package dev.victorstein.reycast

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import android.widget.Toast
import dev.victorstein.reycast.android.AppCatalog
import dev.victorstein.reycast.android.UsageStore
import java.io.File

/**
 * The panel is an overlay window, not an activity: in DeX an activity is a freeform task that is either
 * pinned to the left edge or, when full-screen, gets a caption bar. An overlay has neither.
 */
object ReyCastOverlay {
    private var session: OverlaySession? = null
    private var catalog: AppCatalog? = null
    private var usage: UsageStore? = null

    fun toggle(context: Context, requestedAtMs: Long) {
        val open = session
        if (open != null) {
            open.close()
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, R.string.needs_overlay_permission, Toast.LENGTH_LONG).show()
            return
        }
        val appCatalog = catalog ?: AppCatalog(context).also { catalog = it }
        val usageStore = usage ?: UsageStore(AtomicFile(File(context.filesDir, "usage.tsv"))).also { usage = it }
        session = OverlaySession(context, appCatalog, usageStore, requestedAtMs) { session = null }.also { it.open() }
    }
}
