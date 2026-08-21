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
 * Capture "best effort" d'une unique image depuis un flux RTSP, utilisée
 * comme snapshot de secours quand aucune url_snapshot n'est configurée
 * pour un widget caméra.
 *
 * Volontairement best-effort et non garanti : ouvre une vraie connexion
 * RTSP le temps de récupérer une frame (coûteux, ~quelques secondes),
 * et la méthode de capture dépend du rendu interne de libVLC :
 * - TextureView (rendu logiciel ou certains devices) : `getBitmap()`
 *   direct, fonctionne sur toutes les versions supportées (API 23+).
 * - SurfaceView (rendu matériel, le plus courant) : nécessite `PixelCopy`,
 *   disponible seulement à partir d'Android 7.0 (API 24).
 * Si la capture échoue pour n'importe quelle raison (device trop ancien,
 * flux qui ne démarre pas, timeout, vue introuvable), la fonction
 * retourne simplement `null` : l'appelant retombe alors sur un
 * placeholder générique, ce n'est pas bloquant.
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
                    // Court delai pour laisser une vraie frame se dessiner
                    // avant de tenter la capture.
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
            // IMPORTANT: Désactivation matérielle pour éviter "FrameInsert open fail"
            // sur Mediatek/Unisoc lors d'une capture hors-écran.
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
                    // PixelCopy indisponible avant API 24 : pas de capture possible
                    // depuis un SurfaceView sur ces versions.
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
