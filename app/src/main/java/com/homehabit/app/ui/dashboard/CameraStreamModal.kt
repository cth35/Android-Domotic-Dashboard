package com.homehabit.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.homehabit.app.camera.RtspPlaybackState
import com.homehabit.app.camera.RtspPlayer
import com.homehabit.app.camera.RtspPlayerNative
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import com.alexvas.rtsp.widget.RtspSurfaceView

@Composable
fun CameraStreamModal(
    label: String,
    rtspUrl: String,
    posterUrl: String?,
    useRtspClientNative: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playerVlc = remember { if (!useRtspClientNative) RtspPlayer(context) else null }
    val playerNative = remember { if (useRtspClientNative) RtspPlayerNative() else null }
    
    val playbackState by (playerNative?.state ?: playerVlc?.state!!).collectAsState()

    // MediaPlayer.Event.Playing signale un changement d'etat interne chez
    // libVLC, mais ne garantit pas qu'une frame ait deja ete rendue a
    // l'ecran (negociation RTSP, attente de keyframe, demarrage du
    // decodage materiel). Sans ce delai, le fondu peut demarrer avant
    // qu'il y ait vraiment une image, provoquant un flash noir entre le
    // poster et le flux. Reinitialise immediatement si l'etat quitte
    // PLAYING (ex: coupure puis reconnexion).
    var visuallyReady by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState) {
        if (playbackState == RtspPlaybackState.PLAYING) {
            // On augmente le d�lai pour �tre certain que la premi�re frame
            // vid�o est bien affich�e en dessous avant de masquer le poster.
            delay(if (useRtspClientNative) 500 else 1500)
            visuallyReady = true
        } else {
            visuallyReady = false
        }
    }

    // Liberation systematique du player a la fermeture de la modale
    // (pas de lecture RTSP en arriere-plan).
    DisposableEffect(Unit) {
        onDispose { 
            playerVlc?.stopAndRelease()
            playerNative?.stopAndRelease()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. Fond statique en COULEUR (�vite l'�cran noir si la vid�o tarde)
            if (posterUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(posterUrl)
                        .memoryCacheKey(posterUrl)
                        .diskCacheKey(posterUrl)
                        .placeholderMemoryCacheKey(posterUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Flux Vid�o VLC (en TextureView pour supporter l'alpha)
            val videoAlpha by animateFloatAsState(
                targetValue = if (visuallyReady) 1f else 0f,
                animationSpec = tween(durationMillis = 1000),
                label = "videoFadeIn"
            )

            if (useRtspClientNative) {
                AndroidView(
                    factory = { ctx ->
                        RtspSurfaceView(ctx).also { view ->
                            playerNative?.attachView(view)
                            playerNative?.play(rtspUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(videoAlpha)
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        VLCVideoLayout(ctx).also { layout ->
                            playerVlc?.attachViews(layout)
                            playerVlc?.play(rtspUrl)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(videoAlpha)
                )
            }

            // 3. Masque d'attente GRIS� (s'efface quand pr�t)
            AnimatedVisibility(
                visible = !visuallyReady,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(durationMillis = 1000))
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    if (posterUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(posterUrl)
                                .memoryCacheKey(posterUrl)
                                .diskCacheKey(posterUrl)
                                .placeholderMemoryCacheKey(posterUrl)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Color(0xFFC8C9CC),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when (playbackState) {
                                    RtspPlaybackState.ERROR -> "Flux indisponible"
                                    else -> "Connexion au flux..."
                                },
                                color = Color(0xFFC8C9CC),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
            }
        }
    }
}
