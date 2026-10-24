package com.resonance.music.data.api

/**
 * Navidrome serves a wider range of audio formats than Android can decode. Files in a
 * format the device plays natively are streamed untouched; the others are requested from
 * Navidrome already converted to MP3 (its ffmpeg transcoding), so every format it supports
 * is playable.
 */
object AudioFormats {
    // Containers/codecs Android and Media3 decode without extra libraries. Anything else
    // (WMA, APE, WavPack, Musepack, TTA, DSD, AIFF...) is transcoded by the server.
    private val PLAYABLE = setOf(
        "mp3", "flac", "ogg", "oga", "opus", "aac", "m4a", "m4b", "mp4", "wav", "mka", "webm", "amr"
    )

    const val TRANSCODE_FORMAT = "mp3"
    const val TRANSCODE_MAX_BITRATE = 320

    fun needsTranscoding(suffix: String?): Boolean =
        !suffix.isNullOrBlank() && suffix.lowercase() !in PLAYABLE

    fun isTranscodedUrl(url: String): Boolean = "format=$TRANSCODE_FORMAT" in url
}
