package com.music.bitchord.ios

import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A playable URL and the headers its fetch must repeat.
 *
 * googlevideo compares the media request against the client that minted the
 * URL (see PlayerClient on the JVM side), so the user agent travels with it.
 */
data class Stream(
    val url: String,
    val headers: Map<String, String>,
    val isHls: Boolean,
)

/** Why a `player` response gave nothing to play, in YouTube's words where it has some. */
class UnplayableException(message: String) : Exception(message)

/**
 * The subset of the phone's InnertubeParser the iPhone needs: song rows from a
 * search, and a stream from a player response. Written against
 * web/fixtures/innertube, the same captures the web app's parsers test against.
 */
object Responses {

    fun songs(search: JsonObject): List<Song> =
        collect(search, "musicResponsiveListItemRenderer").mapNotNull(::song)

    fun visitorData(response: JsonObject): String? =
        response.o("responseContext").s("visitorData")

    /**
     * Prefers itag 140 (AAC in MP4), which AVPlayer plays natively and which
     * fetches only audio; any other MP4 audio next; the HLS manifest last, since
     * the iOS client's variants all carry video.
     */
    fun stream(player: JsonObject, userAgent: String): Stream {
        val playability = player.o("playabilityStatus")
        val status = playability.s("status")
        if (status != "OK") {
            throw UnplayableException(playability.s("reason") ?: status ?: "No playability status")
        }
        val data = player.o("streamingData")
        val audio = data.a("adaptiveFormats").orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.s("url") != null && it.s("mimeType")?.startsWith("audio/mp4") == true }
        val headers = mapOf("User-Agent" to userAgent)
        val chosen = audio.firstOrNull { it.i("itag") == 140 }
            ?: audio.maxByOrNull { it.l("bitrate") ?: 0L }
        if (chosen != null) return Stream(chosen.s("url")!!, headers, isHls = false)
        data.s("hlsManifestUrl")?.let { return Stream(it, headers, isHls = true) }
        throw UnplayableException("No stream AVPlayer can open: every format is ciphered or WebM")
    }

    private fun song(renderer: JsonObject): Song? {
        val videoId = renderer.o("playlistItemData").s("videoId")
            ?: renderer.o("overlay").o("musicItemThumbnailOverlayRenderer").o("content")
                .o("musicPlayButtonRenderer").o("playNavigationEndpoint")
                .o("watchEndpoint").s("videoId")
            ?: return null
        val columns = renderer.a("flexColumns").orEmpty().map {
            (it as? JsonObject).o("musicResponsiveListItemFlexColumnRenderer").o("text")
        }
        val title = columns.getOrNull(0).runs().takeIf { it.isNotBlank() } ?: return null
        val parts = columns.getOrNull(1).runs().split(" • ").filter { it.isNotBlank() }
        val duration = parts.lastOrNull()?.takeIf { DURATION.matches(it) }
        val artist = parts.firstOrNull { !DURATION.matches(it) && it.lowercase() !in TYPE_WORDS }
        val thumbnail = renderer.o("thumbnail").o("musicThumbnailRenderer").o("thumbnail")
            .a("thumbnails")?.lastOrNull().let { (it as? JsonObject).s("url") }
        return Song(
            videoId = videoId,
            title = title,
            artist = artist ?: "Unknown artist",
            thumbnailUrl = thumbnail,
            durationText = duration,
        )
    }

    private fun collect(root: JsonElement, name: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node[name] as? JsonObject)?.let(out::add)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    private val DURATION = Regex("""\d{1,2}(:\d{2}){1,2}""")
    private val TYPE_WORDS = setOf("song", "video", "canción", "vídeo")

    private fun JsonObject?.o(key: String): JsonObject? = this?.get(key) as? JsonObject
    private fun JsonObject?.a(key: String): JsonArray? = this?.get(key) as? JsonArray
    private fun JsonObject?.s(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.i(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.intOrNull
    private fun JsonObject?.l(key: String): Long? = (this?.get(key) as? JsonPrimitive)?.let {
        it.longOrNull ?: it.contentOrNull?.toLongOrNull()
    }
    private fun JsonObject?.runs(): String =
        a("runs").orEmpty().joinToString("") { (it as? JsonObject).s("text").orEmpty() }
}
