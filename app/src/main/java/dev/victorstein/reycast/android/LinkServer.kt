package dev.victorstein.reycast.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.victorstein.reycast.core.LinkRequest
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * Opens links (and shows notices) the Mac sends over Tailscale, so `open <url>` run over ssh lands on the tablet.
 * Started by the accessibility service because that keeps the process alive; the background activity start
 * itself is allowed by the overlay permission install.sh grants (logged as BAL_ALLOW_SAW_PERMISSION).
 */
class LinkServer(private val context: Context, private val onNotice: (String) -> Unit) {
    @Volatile private var server: ServerSocket? = null

    fun start() {
        val token = File(context.getExternalFilesDir(null), TOKEN_FILE).takeIf { it.canRead() }?.readText()?.trim()
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "no $TOKEN_FILE pushed by scripts/install.sh; link server off")
            return
        }
        val socket = try {
            ServerSocket(PORT)
        } catch (e: IOException) {
            Log.w(TAG, "cannot listen on $PORT", e)
            return
        }
        server = socket
        Thread({ acceptLoop(socket, token) }, "reycast-links").apply { isDaemon = true }.start()
    }

    fun stop() {
        server?.close()
        server = null
    }

    private fun acceptLoop(socket: ServerSocket, token: String) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                return
            }
            client.use { handle(it, token) }
        }
    }

    private fun handle(client: Socket, token: String) {
        try {
            if (!LinkRequest.isTailnetPeer(client.inetAddress.address)) return
            client.soTimeout = 2000
            val line = client.getInputStream().bufferedReader().readLine() ?: return
            LinkRequest.parseNotice(line, token)?.let {
                onNotice(it)
                client.getOutputStream().write("ok\n".toByteArray())
                return
            }
            val url = LinkRequest.parse(line, token)
            url?.let(LinkRequest::loopbackCallbackPort)?.let { CallbackRelay.start(it, client.inetAddress) }
            val opened = url != null && open(url)
            client.getOutputStream().write(if (opened) "ok\n".toByteArray() else "no\n".toByteArray())
        } catch (e: IOException) {
            Log.w(TAG, "link request failed", e)
        }
    }

    private fun open(url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "cannot open link", e)
        false
    }

    companion object {
        const val PORT = 47011
        const val TOKEN_FILE = "link-token"
        private const val TAG = "ReyCastLinks"
    }
}
