package dev.victorstein.reycast.android

import android.util.AtomicFile
import dev.victorstein.reycast.core.UsageHistory
import java.io.IOException
import java.util.concurrent.Executors

class UsageStore(
    private val file: AtomicFile,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val writer = Executors.newSingleThreadExecutor()
    private val history: UsageHistory = try {
        UsageHistory.parse(String(file.readFully()))
    } catch (e: IOException) {
        UsageHistory()
    }

    fun scores(): Map<String, Int> = synchronized(history) { history.scores(clock()) }

    fun record(component: String) {
        val now = clock()
        writer.execute {
            val text = synchronized(history) {
                history.record(component, now)
                history.serialize()
            }
            file.writeQuietly(text)
        }
    }
}
