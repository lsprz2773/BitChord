package com.music.bitchord.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.Foundation.NSURL

/**
 * AVPlayer in the role ExoPlayer plays on the phone, cut down to one track at
 * a time. The playback audio session keeps it going with the screen locked
 * (Info.plist declares the `audio` background mode for that).
 */
@OptIn(ExperimentalForeignApi::class)
class AudioPlayer {
    private val player = AVPlayer()

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }

    fun play(stream: Stream) {
        val url = NSURL.URLWithString(stream.url) ?: return
        // Undocumented but long-standing: the only way to put a User-Agent on
        // AVPlayer's own requests before iOS 16's AVURLAssetHTTPUserAgentKey.
        val options = mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to stream.headers)
        val asset = AVURLAsset.URLAssetWithURL(url, options)
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem.playerItemWithAsset(asset))
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.play()
}
