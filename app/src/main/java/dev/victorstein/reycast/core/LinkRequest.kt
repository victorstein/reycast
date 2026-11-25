package dev.victorstein.reycast.core

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.security.MessageDigest

/** One request line from the Mac: "<token> <url>". Only http(s) links with the right token are accepted. */
object LinkRequest {
    const val MAX_LINE = 8192
    const val MAX_NOTICE = 300

    fun parse(line: String, token: String): String? {
        if (token.isEmpty() || line.length > MAX_LINE) return null
        val parts = line.trim().split(' ', limit = 2)
        if (parts.size != 2) return null
        val (sentToken, url) = parts
        if (!tokenMatches(sentToken, token)) return null
        val isWebLink = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
        if (!isWebLink || url.any { it.isWhitespace() || it.isISOControl() }) return null
        return url
    }

    /** "<token> notify <text>": a message to show on screen, e.g. "Claude finished · personal". */
    fun parseNotice(line: String, token: String): String? {
        if (token.isEmpty() || line.length > MAX_LINE) return null
        val parts = line.trim().split(' ', limit = 3)
        if (parts.size != 3 || parts[1] != "notify" || !tokenMatches(parts[0], token)) return null
        return parts[2].filterNot { it.isISOControl() }.trim().take(MAX_NOTICE).ifEmpty { null }
    }

    private fun tokenMatches(sent: String, token: String) = MessageDigest.isEqual(sent.toByteArray(), token.toByteArray())

    /**
     * The port of an OAuth `redirect_uri` that points at the sender's own loopback (http://localhost:PORT/...).
     * Opened on the tablet, that callback would hit the tablet itself, so it has to be relayed to the sender.
     */
    fun loopbackCallbackPort(url: String): Int? {
        val query = url.substringAfter('?', "").substringBefore('#')
        val redirect = query.split('&')
            .firstOrNull { it.startsWith("redirect_uri=") }
            ?.let { URLDecoder.decode(it.removePrefix("redirect_uri="), Charsets.UTF_8) }
            ?: return null
        val uri = try {
            URI(redirect)
        } catch (e: URISyntaxException) {
            return null
        }
        val isLoopback = uri.host == "localhost" || uri.host == "127.0.0.1"
        return uri.port.takeIf { uri.scheme == "http" && isLoopback && it in 1..65535 }
    }

    /** Tailscale hands out addresses from the CGNAT range 100.64.0.0/10. */
    fun isTailnetPeer(address: ByteArray): Boolean =
        address.size == 4 && address[0] == 100.toByte() && (address[1].toInt() and 0xC0) == 64
}
