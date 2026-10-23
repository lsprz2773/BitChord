package com.music.bitchord.ios

import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Anonymous YouTube Music: search as WEB_REMIX, as the phone's Innertube does,
 * and resolve streams as the IOS client, whose URLs come unciphered — there is
 * no NewPipe or JavaScript engine on this side to undo a signature.
 *
 * The client identities mirror PlayerClient in shared/jvmSharedMain; keep the
 * versions in step with it when YouTube retires one.
 */
class YtMusic(private val http: HttpClient) {

    private var visitorData: String? = null

    suspend fun searchSongs(query: String): List<Song> {
        val response = post("$MUSIC_BASE/search", WEB_REMIX_HEADERS) {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", "es")
                    visitorData?.let { put("visitorData", it) }
                }
            }
            put("query", query)
            put("params", SearchFilter.SONGS.params)
        }
        Responses.visitorData(response)?.let { visitorData = it }
        return Responses.songs(response)
    }

    suspend fun stream(videoId: String): Stream {
        val response = post(
            "$PLAYER_BASE/player",
            mapOf(
                "User-Agent" to IOS_USER_AGENT,
                "X-YouTube-Client-Name" to "5",
                "X-YouTube-Client-Version" to IOS_VERSION,
            ) + (visitorData?.let { mapOf("X-Goog-Visitor-Id" to it) } ?: emptyMap()),
        ) {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "IOS")
                    put("clientVersion", IOS_VERSION)
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.3.2.22D82")
                    put("hl", "es")
                    visitorData?.let { put("visitorData", it) }
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        return Responses.stream(response, IOS_USER_AGENT)
    }

    private suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val text = http.post(url) {
            parameter("prettyPrint", "false")
            contentType(ContentType.Application.Json)
            headers.forEach { (name, value) -> header(name, value) }
            setBody(buildJsonObject(body).toString())
        }.bodyAsText()
        return json.parseToJsonElement(text).jsonObject
    }

    private companion object {
        const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
        const val PLAYER_BASE = "https://youtubei.googleapis.com/youtubei/v1"
        const val MUSIC_ORIGIN = "https://music.youtube.com"
        const val WEB_REMIX_VERSION = "1.20260707.12.00"
        const val IOS_VERSION = "21.26.4"
        const val IOS_USER_AGENT =
            "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

        val WEB_REMIX_HEADERS = mapOf(
            "Origin" to MUSIC_ORIGIN,
            "Referer" to "$MUSIC_ORIGIN/",
            "X-Origin" to MUSIC_ORIGIN,
            "X-YouTube-Client-Name" to "67",
            "X-YouTube-Client-Version" to WEB_REMIX_VERSION,
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/18.3 Safari/605.1.15",
        )

        val json = Json { ignoreUnknownKeys = true }
    }
}
