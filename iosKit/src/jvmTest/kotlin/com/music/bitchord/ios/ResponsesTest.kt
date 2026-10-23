package com.music.bitchord.ios

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResponsesTest {

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(File("../web/fixtures/innertube/$name").readText()).jsonObject

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `song search rows become songs`() {
        val songs = Responses.songs(fixture("search-songs.json"))
        assertTrue(songs.size >= 5, "expected a page of songs, got ${songs.size}")
        val first = songs.first()
        assertEquals(11, first.videoId.length)
        assertTrue(first.title.isNotBlank())
        assertTrue(first.artist != "Unknown artist", "artist missing for ${first.title}")
        assertTrue(first.thumbnailUrl!!.startsWith("https://"))
        assertTrue(songs.any { it.title.contains("Let Down", ignoreCase = true) })
        assertTrue(songs.all { it.durationText?.contains(':') == true })
    }

    @Test
    fun `an unplayable answer says why`() {
        val error = assertFailsWith<UnplayableException> {
            Responses.stream(fixture("player-web-remix-no-potoken.json"), "ua")
        }
        assertTrue(error.message!!.isNotBlank())
    }

    @Test
    fun `AAC itag 140 wins over other audio and HLS`() {
        val stream = Responses.stream(
            json(
                """
                {"playabilityStatus":{"status":"OK"},
                 "streamingData":{"hlsManifestUrl":"https://hls",
                  "adaptiveFormats":[
                   {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":160000,"url":"https://opus"},
                   {"itag":139,"mimeType":"audio/mp4; codecs=\"mp4a.40.5\"","bitrate":48000,"url":"https://low"},
                   {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":130000,"url":"https://aac"}]}}
                """,
            ),
            "ua",
        )
        assertEquals("https://aac", stream.url)
        assertEquals(mapOf("User-Agent" to "ua"), stream.headers)
    }

    @Test
    fun `HLS is the fallback when no MP4 audio has a URL`() {
        val stream = Responses.stream(
            json(
                """
                {"playabilityStatus":{"status":"OK"},
                 "streamingData":{"hlsManifestUrl":"https://hls",
                  "adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","signatureCipher":"s=x"}]}}
                """,
            ),
            "ua",
        )
        assertEquals("https://hls", stream.url)
        assertTrue(stream.isHls)
    }
}
