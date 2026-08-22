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
        // socketTimeout not available in 5.3.0 in init()
        view.init(uri, null, null, "HomeHabit/1.0")
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
