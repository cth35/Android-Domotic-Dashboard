package com.homehabit.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.suspendCancellableCoroutine
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.coroutines.resume

/**
 * "Best effort" capture of a single image from an RTSP stream, used
 * as a backup snapshot when no url_snapshot is configured
 * for a camera widget.
 *
 * Voluntarily best-effort and not guaranteed: opens a real RTSP
 * connection long enough to retrieve a frame (costly, ~a few seconds),
 * and the capture method depends on the internal rendering of libVLC:
 * - TextureView (software rendering or some devices): direct `getBitmap()`,
 *   works on all supported versions (API 23+).
 * - SurfaceView (hardware rendering, the most common): requires `PixelCopy`,
 *   available only from Android 7.0 (API 24).
 * If the capture fails for any reason (device too old,
 * stream not starting, timeout, view not found), the function
 * returns simply `null`: the caller then falls back to a
 * generic placeholder, this is not blocking.
 */
class RtspThumbnailGrabber(private val context: Context) {

    suspend fun capture(rtspUrl: String, timeoutMs: Long = 8_000L): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val libVLC = LibVLC(context, arrayListOf("--no-audio"))
            val mediaPlayer = MediaPlayer(libVLC)
            val layout = VLCVideoLayout(context)
            val handler = Handler(Looper.getMainLooper())
            var resolved = false

            fun cleanup() {
                runCatching {
                    mediaPlayer.stop()
                    mediaPlayer.detachViews()
                    mediaPlayer.release()
                    libVLC.release()
                }
            }

            fun finish(bitmap: Bitmap?) {
                if (resolved) return
                resolved = true
                cleanup()
                if (continuation.isActive) continuation.resume(bitmap)
            }

            val timeoutRunnable = Runnable { finish(null) }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            mediaPlayer.setEventListener { event ->
                if (event.type == MediaPlayer.Event.Playing && !resolved) {
                    // Short delay to let a real frame be drawn
                    // before attempting capture.
                    handler.postDelayed({
                        if (resolved) return@postDelayed
                        handler.removeCallbacks(timeoutRunnable)
                        captureFromRenderView(layout, handler, ::finish)
                    }, 300)
                } else if (event.type == MediaPlayer.Event.EncounteredError) {
                    handler.removeCallbacks(timeoutRunnable)
                    finish(null)
                }
            }

            continuation.invokeOnCancellation { cleanup() }

            mediaPlayer.attachViews(layout, null, false, false)
            val media = Media(libVLC, Uri.parse(rtspUrl))
            // IMPORTANT: Hardware deactivation to avoid "FrameInsert open fail"
            // on Mediatek/Unisoc during off-screen capture.
            media.setHWDecoderEnabled(false, false)
            media.addOption(":network-caching=1500")
            media.addOption(":rtsp-tcp")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")

            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        }

    private fun captureFromRenderView(
        root: ViewGroup,
        handler: Handler,
        onResult: (Bitmap?) -> Unit
    ) {
        when (val renderView = findRenderView(root)) {
            is TextureView -> onResult(renderView.bitmap)

            is SurfaceView -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val width = renderView.width.coerceAtLeast(1)
                    val height = renderView.height.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(renderView, bitmap, { result ->
                        onResult(if (result == PixelCopy.SUCCESS) bitmap else null)
                    }, handler)
                } else {
                    // PixelCopy unavailable before API 24: no capture possible
                    // from a SurfaceView on these versions.
                    onResult(null)
                }
            }

            else -> onResult(null)
        }
    }

    private fun findRenderView(viewGroup: ViewGroup): View? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is SurfaceView || child is TextureView) return child
            if (child is ViewGroup) {
                findRenderView(child)?.let { return it }
            }
        }
        return null
    }
}
