package com.homehabit.app.camera

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

enum class RtspPlaybackState { IDLE, CONNECTING, PLAYING, ERROR }

/**
 * One instance = one RTSP playback session. To be created/released with the
 * lifecycle of the full-screen modal (no background playback
 * for now: the stream only runs when the modal is open,
 * important for CPU/network load since the screen remains on
 * permanently).
 */
class RtspPlayer(context: Context) {

    private val libVLC = LibVLC(
        context,
        arrayListOf(
            "--no-audio",
            "--network-caching=1500",
            "-vvv" // verbose logs -> filter logcat on tag "VLC" to see the real
            // RTSP server response (401 = bad credentials, timeout = network)
        )
    )

    private val mediaPlayer = MediaPlayer(libVLC)

    private val _state = MutableStateFlow(RtspPlaybackState.IDLE)
    val state: StateFlow<RtspPlaybackState> = _state

    // Some camera firmwares (observed on Tapo) reject the very first PLAY
    // right after a Digest auth challenge, then accept an immediate retry.
    // We retry a couple of times automatically before surfacing ERROR.
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var retryJob: Job? = null
    private var retryCount = 0
    private var lastSafeUrl: String? = null
    private val maxRetries = 2
    private val retryDelayMs = 800L

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> _state.value = RtspPlaybackState.CONNECTING
                MediaPlayer.Event.Playing -> {
                    retryJob?.cancel()
                    retryCount = 0
                    _state.value = RtspPlaybackState.PLAYING
                }
                MediaPlayer.Event.EncounteredError -> handlePlaybackError()
                MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached -> _state.value = RtspPlaybackState.IDLE
                else -> Unit
            }
        }
    }

    private fun handlePlaybackError() {
        val url = lastSafeUrl
        if (url != null && retryCount < maxRetries) {
            retryCount++
            _state.value = RtspPlaybackState.CONNECTING
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(retryDelayMs)
                startPlayback(url)
            }
        } else {
            _state.value = RtspPlaybackState.ERROR
        }
    }

    fun attachViews(layout: VLCVideoLayout) {
        // We use TextureView (3rd param = true) to allow smooth transitions
        // and overlays without black flashes (respects alpha and Z-order).
        mediaPlayer.attachViews(layout, null, true, false)
        mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
    }

    /**
     * Preferred entry point: pass the pieces separately so the credentials
     * are always percent-encoded correctly, even if the "Camera Account"
     * password (Tapo, etc.) contains special characters like @ # % : /.
     */
    fun play(
        host: String,
        username: String,
        password: String,
        port: Int = 554,
        path: String = "/stream1"
    ) {
        val encodedUser = Uri.encode(username)
        val encodedPass = Uri.encode(password)
        val rtspUrl = "rtsp://$encodedUser:$encodedPass@$host:$port$path"
        play(rtspUrl)
    }

    /**
     * Accepts a full RTSP URL. If it already contains a "user:pass@" section,
     * that section is re-encoded defensively: Uri.parse() does NOT encode
     * anything on its own, so a raw '@', '#' or '%' inside the password will
     * otherwise break host/port parsing and the connection will fail or send
     * wrong credentials to the camera.
     */
    fun play(rtspUrl: String) {
        retryJob?.cancel()
        retryCount = 0
        val safeUrl = encodeCredentialsIfPresent(rtspUrl)
        lastSafeUrl = safeUrl
        startPlayback(safeUrl)
    }

    /** Actual (re)connection attempt. Called on first play() and on auto-retry. */
    private fun startPlayback(safeUrl: String) {
        _state.value = RtspPlaybackState.CONNECTING

        val media = Media(libVLC, Uri.parse(safeUrl))

        // Options de stabilité pour le réseau local
        media.addOption(":network-caching=1500")
        media.addOption(":rtsp-tcp")
        media.addOption(":clock-jitter=0")
        media.addOption(":clock-synchro=0")

        // On garde l'accélération matérielle pour la vidéo plein écran (surface visible)
        media.setHWDecoderEnabled(true, false)

        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    /**
     * Finds a "user:pass@" block right after "rtsp://" and percent-encodes
     * user and pass individually, leaving the rest of the URL untouched.
     * Safe no-op if there's no "@" (no credentials in the URL) or if the
     * URL is already properly encoded.
     */
    private fun encodeCredentialsIfPresent(rtspUrl: String): String {
        val regex = Regex("^(rtsps?://)([^:/@]+):([^@]+)@(.+)$")
        val match = regex.find(rtspUrl) ?: return rtspUrl
        val (scheme, user, pass, rest) = match.destructured
        val encodedUser = Uri.encode(Uri.decode(user))
        val encodedPass = Uri.encode(Uri.decode(pass))
        return "$scheme$encodedUser:$encodedPass@$rest"
    }

    fun stopAndRelease() {
        retryJob?.cancel()
        scope.cancel()
        lastSafeUrl = null
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        runCatching { mediaPlayer.release() }
        runCatching { libVLC.release() }
    }
}