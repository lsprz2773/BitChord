package com.music.bitchord.ios

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.UIKit.UIViewController

private val http = HttpClient(Darwin)
private val yt = YtMusic(http)
private val player by lazy { AudioPlayer() }

/** What iosApp's SwiftUI shell hosts; Swift sees it as `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { http })) }
            .build()
    }
    App(yt, player)
}
