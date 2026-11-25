package dev.victorstein.reycast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRequestTest {
    private val token = "s3cret"

    @Test fun acceptsHttpsWithToken() =
        assertEquals("https://github.com/a/b?x=1&y=2", LinkRequest.parse("s3cret https://github.com/a/b?x=1&y=2\n", token))

    @Test fun acceptsHttp() = assertEquals("http://localhost:3000", LinkRequest.parse("s3cret http://localhost:3000", token))

    @Test fun rejectsWrongToken() = assertNull(LinkRequest.parse("nope https://example.com", token))

    @Test fun rejectsEmptyConfiguredToken() = assertNull(LinkRequest.parse(" https://example.com", ""))

    @Test fun rejectsOtherSchemes() {
        assertNull(LinkRequest.parse("s3cret intent://scan/#Intent;end", token))
        assertNull(LinkRequest.parse("s3cret file:///sdcard/x", token))
        assertNull(LinkRequest.parse("s3cret javascript:alert(1)", token))
    }

    @Test fun rejectsMissingUrl() = assertNull(LinkRequest.parse("s3cret", token))

    @Test fun rejectsEmbeddedWhitespace() = assertNull(LinkRequest.parse("s3cret https://a.com/x y", token))

    @Test fun rejectsOversizedLine() =
        assertNull(LinkRequest.parse("s3cret https://a.com/" + "a".repeat(LinkRequest.MAX_LINE), token))

    @Test fun findsEncodedLoopbackCallback() = assertEquals(
        54545,
        LinkRequest.loopbackCallbackPort(
            "https://claude.com/oauth/authorize?code=true&client_id=x&redirect_uri=http%3A%2F%2Flocalhost%3A54545%2Fcallback&state=s",
        ),
    )

    @Test fun findsIpv4LoopbackCallback() =
        assertEquals(8085, LinkRequest.loopbackCallbackPort("https://a.com/auth?redirect_uri=http://127.0.0.1:8085/cb"))

    @Test fun ignoresRemoteOrMissingCallbacks() {
        assertNull(LinkRequest.loopbackCallbackPort("https://a.com/auth?redirect_uri=https%3A%2F%2Fapp.example.com%2Fcb"))
        assertNull(LinkRequest.loopbackCallbackPort("https://a.com/auth?redirect_uri=http%3A%2F%2Flocalhost%2Fcb"))
        assertNull(LinkRequest.loopbackCallbackPort("https://a.com/auth?client_id=x"))
        assertNull(LinkRequest.loopbackCallbackPort("https://github.com/a/b"))
    }

    @Test fun parsesNotice() =
        assertEquals("Claude finished · personal", LinkRequest.parseNotice("s3cret notify Claude finished · personal\n", token))

    @Test fun noticeNeedsTokenAndText() {
        assertNull(LinkRequest.parseNotice("nope notify hi", token))
        assertNull(LinkRequest.parseNotice(" notify hi", ""))
        assertNull(LinkRequest.parseNotice("s3cret notify   ", token))
        assertNull(LinkRequest.parseNotice("s3cret https://a.com", token))
    }

    @Test fun noticeDropsControlCharsAndIsCapped() {
        assertEquals("ab", LinkRequest.parseNotice("s3cret notify a\u001b\u0007b", token))
        assertEquals(LinkRequest.MAX_NOTICE, LinkRequest.parseNotice("s3cret notify " + "x".repeat(1000), token)?.length)
    }

    @Test fun noticeIsNotALink() = assertNull(LinkRequest.parse("s3cret notify https://a.com", token))

    @Test fun tailnetRange() {
        assertTrue(LinkRequest.isTailnetPeer(byteArrayOf(100, 70, 158.toByte(), 120)))
        assertTrue(LinkRequest.isTailnetPeer(byteArrayOf(100, 127, 0, 1)))
        assertFalse(LinkRequest.isTailnetPeer(byteArrayOf(100, 128.toByte(), 0, 1)))
        assertFalse(LinkRequest.isTailnetPeer(byteArrayOf(192.toByte(), 168.toByte(), 68, 52)))
        assertFalse(LinkRequest.isTailnetPeer(ByteArray(16)))
    }
}
