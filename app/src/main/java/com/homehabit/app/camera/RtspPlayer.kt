package com.homehabit.app.camera

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

enum class RtspPlaybackState { IDLE, CONNECTING, PLAYING, ERROR }

/**
 * Une instance = une session de lecture RTSP. A creer/liberer avec le
 * cycle de vie de la modale plein ecran (pas de lecture en arriere-plan
 * pour l'instant : le flux ne tourne que quand la modale est ouverte,
 * important pour la charge CPU/reseau vu que l'ecran reste allume en
 * permanence).
 */
class RtspPlayer(context: Context) {

    private val libVLC = LibVLC(
        context,
        arrayListOf(
            "--no-audio",
            "--network-caching=300",
            "--rtsp-tcp"
        )
    )

    private val mediaPlayer = MediaPlayer(libVLC)

    private val _state = MutableStateFlow(RtspPlaybackState.IDLE)
    val state: StateFlow<RtspPlaybackState> = _state

    init {
        mediaPlayer.setEventListener { event ->
            _state.value = when (event.type) {
                MediaPlayer.Event.Opening -> RtspPlaybackState.CONNECTING
                MediaPlayer.Event.Playing -> RtspPlaybackState.PLAYING
                MediaPlayer.Event.EncounteredError -> RtspPlaybackState.ERROR
                MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached -> RtspPlaybackState.IDLE
                else -> _state.value
            }
        }
    }

    fun attachViews(layout: VLCVideoLayout) {
        mediaPlayer.attachViews(layout, null, false, false)
    }

    fun play(rtspUrl: String) {
        _state.value = RtspPlaybackState.CONNECTING
        val media = Media(libVLC, Uri.parse(rtspUrl))
        media.setHWDecoderEnabled(true, false)
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    fun stopAndRelease() {
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        runCatching { mediaPlayer.release() }
        runCatching { libVLC.release() }
    }
}
