package dev.victorstein.reycast.android

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * For a few minutes, forwards the tablet's 127.0.0.1:[port] to [target]:[port], so an OAuth callback to
 * http://localhost:PORT opened in the tablet's browser reaches the Mac (whose open-link exposes that port on
 * the tailnet for the same window).
 */
object CallbackRelay {
    private const val TAG = "ReyCastLinks"
    private const val LIFETIME_MS = 5 * 60 * 1000L
    private val active = HashSet<Int>()

    fun start(port: Int, target: InetAddress) {
        synchronized(active) { if (!active.add(port)) return }
        val server = try {
            ServerSocket(port, 50, InetAddress.getLoopbackAddress())
        } catch (e: IOException) {
            Log.w(TAG, "cannot relay callback port $port", e)
            synchronized(active) { active.remove(port) }
            return
        }
        Thread({ acceptUntilExpired(server, port, target) }, "reycast-callback-$port").apply { isDaemon = true }.start()
    }

    private fun acceptUntilExpired(server: ServerSocket, port: Int, target: InetAddress) {
        val deadline = System.currentTimeMillis() + LIFETIME_MS
        server.use {
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                server.soTimeout = remaining.toInt()
                val browser = try {
                    server.accept()
                } catch (e: SocketTimeoutException) {
                    break
                } catch (e: IOException) {
                    break
                }
                Thread({ bridge(browser, target, port) }, "reycast-callback-conn").apply { isDaemon = true }.start()
            }
        }
        synchronized(active) { active.remove(port) }
    }

    private fun bridge(browser: Socket, target: InetAddress, port: Int) {
        val mac = try {
            Socket(target, port)
        } catch (e: IOException) {
            Log.w(TAG, "callback relay cannot reach $target:$port", e)
            browser.close()
            return
        }
        val upstream = Thread { pipe(browser.getInputStream(), mac.getOutputStream(), mac) }.apply { isDaemon = true }
        upstream.start()
        pipe(mac.getInputStream(), browser.getOutputStream(), browser)
        upstream.join()
        browser.close()
        mac.close()
    }

    private fun pipe(from: InputStream, to: OutputStream, toSocket: Socket) {
        try {
            from.copyTo(to)
            toSocket.shutdownOutput()
        } catch (e: IOException) {
            toSocket.close()
        }
    }
}
