package dev.victorstein.reycast

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.victorstein.reycast.android.AppCatalog
import dev.victorstein.reycast.android.UsageStore
import dev.victorstein.reycast.core.CatalogEntry
import dev.victorstein.reycast.core.KeyAction
import dev.victorstein.reycast.core.KeyRouter
import dev.victorstein.reycast.core.Ranker
import java.util.concurrent.Executors

class OverlaySession(
    private val context: Context,
    private val catalog: AppCatalog,
    private val usage: UsageStore,
    private val requestedAtMs: Long,
    private val onClosed: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadExecutor()
    private val root = LayoutInflater.from(ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault))
        .inflate(R.layout.overlay_panel, null) as OverlayRoot
    private val query: EditText = root.findViewById(R.id.query)
    private val rows: List<View>
    private val icons = HashMap<String, Drawable?>()
    private val iconsLoading = HashSet<String>()
    private var entries: List<CatalogEntry> = emptyList()
    private var frecency: Map<String, Int> = emptyMap()
    private var results: List<CatalogEntry> = emptyList()
    private var selected = 0
    private var closed = false

    init {
        val container = root.findViewById<LinearLayout>(R.id.results)
        val inflater = LayoutInflater.from(root.context)
        rows = List(Ranker.MAX_RESULTS) {
            inflater.inflate(R.layout.row_result, container, false).also(container::addView)
        }
    }

    fun open() {
        placePanel()
        root.setOnClickListener { close() }
        root.onFocusLost = ::close
        root.onKey = ::handleKey
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                selected = 0
                render()
            }
        })

        frecency = usage.scores()
        entries = catalog.cached()
        render()
        logFirstFrame()
        windowManager.addView(root, overlayParams())
        query.requestFocus()

        val cachedEntries = entries
        background.execute {
            val fresh = catalog.refresh(cachedEntries)
            main.post {
                if (!closed && fresh != entries) {
                    entries = fresh
                    render()
                }
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        windowManager.removeView(root)
        background.shutdown()
        onClosed()
    }

    private fun placePanel() {
        val screen = windowManager.maximumWindowMetrics.bounds
        val panel = root.findViewById<View>(R.id.panel)
        panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
            width = (screen.width() * 0.40).toInt()
            topMargin = (screen.height() * 0.15).toInt()
        }
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Cover the status bar and DeX taskbar too, so clicking them counts as "outside" and closes the panel.
        fitInsetsTypes = 0
        title = "ReyCast"
    }

    private fun logFirstFrame() {
        root.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var logged = false
            override fun onDraw() {
                if (logged) return
                logged = true
                Log.i(TAG, "first frame +${SystemClock.uptimeMillis() - requestedAtMs} ms")
                main.post { root.viewTreeObserver.removeOnDrawListener(this) }
            }
        })
    }

    private fun render() {
        results = Ranker.rank(entries, query.text.toString(), frecency)
        selected = selected.coerceIn(0, maxOf(0, results.lastIndex))
        rows.forEachIndexed { index, row ->
            val entry = results.getOrNull(index)
            row.visibility = if (entry == null) View.GONE else View.VISIBLE
            if (entry != null) {
                row.findViewById<TextView>(R.id.label).text = entry.label
                row.setBackgroundResource(if (index == selected) R.drawable.row_selected_bg else 0)
                bindIcon(row.findViewById(R.id.icon), entry.component)
            }
        }
    }

    private fun bindIcon(view: ImageView, component: String) {
        if (icons.containsKey(component)) {
            view.setImageDrawable(icons[component] ?: context.getDrawable(android.R.drawable.sym_def_app_icon))
            return
        }
        view.setImageDrawable(null)
        if (!iconsLoading.add(component)) return
        background.execute {
            val icon = catalog.loadIcon(component)
            main.post {
                icons[component] = icon
                if (!closed) render()
            }
        }
    }

    // Handled before the text field: it would consume ↑, move focus out on ↓ and turn Enter into an
    // editor action.
    private fun handleKey(event: KeyEvent): Boolean {
        val unicodeWithoutAlt = event.getUnicodeChar(event.metaState and KeyEvent.META_ALT_MASK.inv())
        // Alt that produces no character is the Cmd key (system Meta→Alt remap); AltGr symbols like @ must type.
        val cmdHeld = event.isAltPressed && event.getUnicodeChar(event.metaState) <= 0
        val action = KeyRouter.route(event.keyCode, event.isCtrlPressed, cmdHeld, unicodeWithoutAlt)
        if (action == KeyAction.PassThrough) return false
        if (event.action == KeyEvent.ACTION_DOWN) perform(action)
        return true
    }

    private fun perform(action: KeyAction) {
        when (action) {
            KeyAction.MoveUp -> moveSelection(-1)
            KeyAction.MoveDown -> moveSelection(1)
            KeyAction.Launch -> results.getOrNull(selected)?.let(::launch)
            KeyAction.Close -> close()
            is KeyAction.Type -> query.text.insert(query.selectionStart.coerceAtLeast(0), action.char.toString())
            KeyAction.PassThrough -> Unit
        }
    }

    private fun moveSelection(delta: Int) {
        selected = (selected + delta).coerceIn(0, maxOf(0, results.lastIndex))
        render()
    }

    // Started while the overlay is still visible: a visible overlay window is what exempts ReyCast from
    // Android's background-activity-start block.
    private fun launch(entry: CatalogEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName.unflattenFromString(entry.component))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            return cannotOpen(entry)
        } catch (e: SecurityException) {
            return cannotOpen(entry)
        }
        usage.record(entry.component)
        close()
    }

    private fun cannotOpen(entry: CatalogEntry) {
        Toast.makeText(context, context.getString(R.string.cannot_open, entry.label), Toast.LENGTH_SHORT).show()
        entries = entries - entry
        render()
    }

    private companion object {
        const val TAG = "ReyCast"
    }
}
