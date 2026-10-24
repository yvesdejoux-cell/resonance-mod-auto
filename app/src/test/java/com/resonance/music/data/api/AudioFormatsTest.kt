package com.resonance.music.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatsTest {

    @Test
    fun `formats Android decodes are streamed untouched`() {
        listOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "FLAC", "Mp3").forEach {
            assertFalse("$it should be played as-is", AudioFormats.needsTranscoding(it))
        }
    }

    @Test
    fun `formats Android cannot decode are transcoded`() {
        listOf("wma", "ape", "wv", "mpc", "tta", "dsf", "dff", "aiff", "aif", "WMA").forEach {
            assertTrue("$it should be transcoded", AudioFormats.needsTranscoding(it))
        }
    }

    @Test
    fun `unknown suffix is left to the player`() {
        assertFalse(AudioFormats.needsTranscoding(null))
        assertFalse(AudioFormats.needsTranscoding(""))
    }

    @Test
    fun `transcoded stream url asks for mp3 with a length estimate`() {
        val helper = SubsonicApiHelper { ServerCredentials("https://music.example", "user", "pass") }

        val plain = helper.getStreamUrl("abc")!!
        val converted = helper.getStreamUrl("abc", transcode = true)!!

        assertFalse(AudioFormats.isTranscodedUrl(plain))
        assertTrue(AudioFormats.isTranscodedUrl(converted))
        assertTrue(converted.contains("estimateContentLength=true"))
        assertTrue(converted.contains("maxBitRate=320"))
        assertEquals(plain.substringBefore("&id=abc"), converted.substringBefore("&id=abc"))
    }
}
