package com.homehabit.app.camera

import android.net.Uri
import com.alexvas.rtsp.widget.RtspStatusListener
import com.alexvas.rtsp.widget.RtspSurfaceView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Alternative to RtspPlayer (libVLC) using rtsp-client-android.
 * Lighter and aiming for minimal latency (zero-buffering).
 */
class RtspPlayerNative {

    private val _state = MutableStateFlow(RtspPlaybackState.IDLE)
    val state: StateFlow<RtspPlaybackState> = _state

    private var view: RtspSurfaceView? = null
    private var currentUrl: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    fun attachView(rtspView: RtspSurfaceView) {
        this.view = rtspView
        rtspView.setStatusListener(object : RtspStatusListener {
            override fun onRtspStatusConnecting() {
                _state.value = RtspPlaybackState.CONNECTING
            }

            override fun onRtspStatusConnected() {
            }

            override fun onRtspStatusDisconnecting() {
            }

            override fun onRtspStatusDisconnected() {
                _state.value = RtspPlaybackState.IDLE
            }

            override fun onRtspStatusFailed(message: String?) {
                _state.value = RtspPlaybackState.ERROR
                // Automatic reconnection attempt in case of error (timeout, network cut)
                if (retryCount < maxRetries) {
                    retryCount++
                    view?.postDelayed({
                        currentUrl?.let { play(it) }
                    }, 3000)
                }
            }

            override fun onRtspStatusFailedUnauthorized() {
                _state.value = RtspPlaybackState.ERROR
                android.util.Log.e("RtspPlayerNative", "Echec d'authentification RTSP")
            }

            override fun onRtspFirstFrameRendered() {
                _state.value = RtspPlaybackState.PLAYING
                retryCount = 0 // Reset on first success
            }

            override fun onRtspFrameSizeChanged(width: Int, height: Int) {
            }
        })
    }

    fun play(rtspUrl: String) {
        val view = view ?: return
        this.currentUrl = rtspUrl
        _state.value = RtspPlaybackState.CONNECTING
        
        val uri = Uri.parse(rtspUrl)

        // Extract credentials if present in the URL (user:pwd@host)
        var username: String? = null
        var password: String? = null
        uri.userInfo?.let { userInfo ->
            val parts = userInfo.split(":", limit = 2)
            username = parts.getOrNull(0)
            password = parts.getOrNull(1)
        }

        // Clean the URI by removing userInfo to avoid "Invalid status code -1"
        // caused by malformed RTSP requests on some servers.
        val cleanUri = if (uri.userInfo != null) {
            uri.buildUpon()
                .encodedAuthority(
                    (if (uri.host != null) uri.host else "") + 
                    (if (uri.port != -1) ":${uri.port}" else "")
                )
                .build()
        } else {
            uri
        }

        // socketTimeout not available in 5.3.0 in init()
        // Use a more standard User-Agent to avoid rejection by some cameras
        view.init(cleanUri, username, password, "vlc/3.0.16")
        view.debug = false
        view.start(true, false)
    }

    fun stopAndRelease() {
        currentUrl = null
        retryCount = 0
        view?.stop()
        view = null
        _state.value = RtspPlaybackState.IDLE
    }
}
