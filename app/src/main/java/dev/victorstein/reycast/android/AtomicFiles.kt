package dev.victorstein.reycast.android

import android.util.AtomicFile
import java.io.FileOutputStream
import java.io.IOException

/** Callers treat a failed cache or history write as non-fatal. */
internal fun AtomicFile.writeQuietly(text: String) {
    var out: FileOutputStream? = null
    try {
        out = startWrite()
        out.write(text.toByteArray())
        finishWrite(out)
    } catch (e: IOException) {
        out?.let(::failWrite)
    }
}
