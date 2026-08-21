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
            "--network-caching=1500"
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
        // On utilise TextureView (3eme param = true) pour permettre des transitions
        // fluides et des overlays sans flash noir (respecte l'alpha et le Z-order).
        mediaPlayer.attachViews(layout, null, true, false)
        mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
    }

    fun play(rtspUrl: String) {
        _state.value = RtspPlaybackState.CONNECTING
        val media = Media(libVLC, Uri.parse(rtspUrl))
        // Options de stabilit� pour le r�seau local
        media.addOption(":network-caching=1500")
        media.addOption(":rtsp-tcp")
        media.addOption(":clock-jitter=0")
        media.addOption(":clock-synchro=0")
        
        // On garde l'acc�l�ration mat�rielle pour la vid�o plein �cran (surface visible)
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
