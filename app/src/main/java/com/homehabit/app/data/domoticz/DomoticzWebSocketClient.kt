package com.homehabit.app.data.domoticz

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

private const val TAG = "DomoticzWebSocket"

/**
 * Events emitted by the Domoticz websocket connection. Unlike
 * REST polling (getdevices), which always returns the complete list,
 * Domoticz pushes ONE device here at a time, at the moment it changes.
 */
sealed class DomoticzWsEvent {
    data object Connected : DomoticzWsEvent()
    data object Disconnected : DomoticzWsEvent()
    data class DeviceUpdate(val device: DomoticzDeviceDto) : DomoticzWsEvent()
    data class Failed(val error: Throwable) : DomoticzWsEvent()
}

/**
 * Client of the Domoticz real-time push channel (endpoint /json,
 * available since 2020.1 / build 4.11000). Complementary to
 * DomoticzClient (REST): serves only to RECEIVE state changes
 * live. Commands (switchlight, setsetpoint...) remain
 * sent via classic HTTP through DomoticzClient — the websocket channel of
 * Domoticz is not guaranteed reliable in the client -> server direction.
 *
 * Sub-protocol expected by the server: "domoticz" (hardcoded on the
 * Domoticz side, cf. `#define websocket_protocol "domoticz"` in its
 * cWebem.cpp). Basic authentication, if configured, must be
 * sent right from the initial HTTP handshake (Authorization header), not
 * after — Domoticz refuses the upgrade otherwise.
 *
 * Format of received messages: NOT officially documented. Best-effort
 * deduced from the official web client / the Dashticz project: one JSON object per
 * modified device, with substantially the same fields as
 * DomoticzDeviceDto (idx, Data, nValue, LastUpdate...). To be validated in the
 * field via logs (tag "DomoticzWebSocket", debug level) if the
 * mapping seems incomplete on a given Domoticz version: the logs
 * display the raw frame received before any parsing.
 */
class DomoticzWebSocketClient(private val config: DomoticzConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        // Keeps the connection alive despite routers/boxes that cut
        // inactive TCP connections on a home network.
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    /**
     * Event flow with automatic reconnection and exponential
     * backoff (1s -> 2s -> 5s -> 10s -> caps at 30s) in case
     * of failure or closure. Only one active socket at a time: do not
     * collect this Flow from multiple places in parallel (use
     * DomoticzRepository.observeLiveUpdates as a single entry point
     * on the caller side).
     */
    fun observeEvents(): Flow<DomoticzWsEvent> = callbackFlow {
        var manuallyClosed = false
        var retryDelayMs = 1_000L

        fun connect() {
            if (manuallyClosed) return

            fun scheduleReconnect() {
                if (manuallyClosed) return
                launch {
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
                    connect()
                }
            }

            val scheme = if (config.useHttps) "wss" else "ws"
            val url = "$scheme://${config.host}:${config.port}/json"
            val origin = "${if (config.useHttps) "https" else "http"}://${config.host}"

            Log.d(TAG, "Tentative de connexion: $url (Origin: $origin)")

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Origin", origin)
                .header("Sec-WebSocket-Protocol", "domoticz")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

            if (!config.username.isNullOrBlank()) {
                val auth = Credentials.basic(config.username, config.password.orEmpty())
                Log.d(TAG, "Ajout de l'authentification Basic")
                requestBuilder.addHeader("Authorization", auth)
            }

            webSocket = okHttpClient.newWebSocket(
                requestBuilder.build(),
                object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        Log.i(TAG, "Connecte a $url")
                        retryDelayMs = 1_000L // backoff reset as soon as a connection succeeds
                        trySend(DomoticzWsEvent.Connected)
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        //Log.d(TAG, "Message recu: $text")
                        try {
                            val root = json.parseToJsonElement(text).jsonObject
                            val event = root["event"]?.jsonPrimitive?.content
                            
                            if (event == "response") {
                                // Domoticz sometimes returns updates in a "data" field (JSON string)
                                val dataStr = root["data"]?.jsonPrimitive?.content
                                if (dataStr != null) {
                                    val dataObj = json.parseToJsonElement(dataStr).jsonObject
                                    val result = dataObj["result"]?.jsonArray
                                    result?.forEach {
                                        val device = json.decodeFromJsonElement<DomoticzDeviceDto>(it)
                                        trySend(DomoticzWsEvent.DeviceUpdate(device))
                                    }
                                }
                            } else if (root.containsKey("idx")) {
                                // Direct format (initially expected)
                                val device = json.decodeFromJsonElement<DomoticzDeviceDto>(root)
                                trySend(DomoticzWsEvent.DeviceUpdate(device))
                            } else {
                                Log.d(TAG, "Evenement ignore: $event")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Message non reconnu, ignore (${e.message})")
                        }
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "Echec websocket: ${t.message}")
                        trySend(DomoticzWsEvent.Failed(t))
                        scheduleReconnect()
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "Websocket ferme: $code $reason")
                        trySend(DomoticzWsEvent.Disconnected)
                        scheduleReconnect()
                    }
                }
            )
        }

        connect()

        awaitClose {
            manuallyClosed = true
            webSocket?.close(1000, "Client ferme")
            webSocket = null
        }
    }

    /** To be called explicitly when the client is no longer used (hot swap config, onCleared). */
    fun close() {
        webSocket?.close(1000, "Client ferme")
        webSocket = null
        okHttpClient.dispatcher.executorService.shutdown()
    }
}
