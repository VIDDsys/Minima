package com.minima.chat.deeplink

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinksTest {
    @Test
    fun extraWins() {
        val i = Intent().putExtra(DeepLinks.EXTRA_SESSION_ID, "abc")
        assertEquals("abc", DeepLinks.sessionIdFrom(i))
    }

    @Test
    fun uriPath() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("minima://chat/sess-1"))
        assertEquals("sess-1", DeepLinks.sessionIdFrom(i))
    }

    @Test
    fun emptyIntent() {
        assertNull(DeepLinks.sessionIdFrom(Intent()))
    }

    @Test
    fun blankExtra() {
        val i = Intent().putExtra(DeepLinks.EXTRA_SESSION_ID, "  ")
        assertNull(DeepLinks.sessionIdFrom(i))
    }

    @Test
    fun wrongScheme() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://chat/x"))
        assertNull(DeepLinks.sessionIdFrom(i))
    }

    @Test
    fun wrongHost() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("minima://other/x"))
        assertNull(DeepLinks.sessionIdFrom(i))
    }

    @Test
    fun noPath() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("minima://chat"))
        assertNull(DeepLinks.sessionIdFrom(i))
    }

    @Test
    fun extraBeatsUri() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("minima://chat/from-uri"))
        i.putExtra(DeepLinks.EXTRA_SESSION_ID, "from-extra")
        assertEquals("from-extra", DeepLinks.sessionIdFrom(i))
    }

    @Test
    fun nullIntent() {
        assertNull(DeepLinks.sessionIdFrom(null))
    }

    @Test
    fun launcherMain_ignoresStickySessionExtra() {
        val i = Intent(Intent.ACTION_MAIN)
            .putExtra(DeepLinks.EXTRA_SESSION_ID, "stale-sess")
        assertNull(DeepLinks.sessionIdForLaunch(i))
    }

    @Test
    fun openSessionAction_honorsId() {
        val i = Intent(DeepLinks.ACTION_OPEN_SESSION)
            .putExtra(DeepLinks.EXTRA_SESSION_ID, "live")
        assertEquals("live", DeepLinks.sessionIdForLaunch(i))
    }

    @Test
    fun viewMinima_honorsId() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("minima://chat/from-view"))
        assertEquals("from-view", DeepLinks.sessionIdForLaunch(i))
    }
}
