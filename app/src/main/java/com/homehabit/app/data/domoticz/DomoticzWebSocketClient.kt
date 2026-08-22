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
 * Evenements emis par la connexion websocket Domoticz. Contrairement au
 * polling REST (getdevices), qui renvoie toujours la liste complete,
 * Domoticz pousse ici UN device a la fois, au moment ou il change.
 */
sealed class DomoticzWsEvent {
    data object Connected : DomoticzWsEvent()
    data object Disconnected : DomoticzWsEvent()
    data class DeviceUpdate(val device: DomoticzDeviceDto) : DomoticzWsEvent()
    data class Failed(val error: Throwable) : DomoticzWsEvent()
}

/**
 * Client du canal de push temps reel de Domoticz (endpoint /json,
 * disponible depuis la 2020.1 / build 4.11000). Complementaire de
 * DomoticzClient (REST) : sert uniquement a RECEVOIR les changements
 * d'etat en direct. Les commandes (switchlight, setsetpoint...) restent
 * envoyees en HTTP classique via DomoticzClient — le canal websocket de
 * Domoticz n'est pas garanti fiable dans le sens client -> serveur.
 *
 * Sous-protocole attendu par le serveur : "domoticz" (code en dur cote
 * Domoticz, cf. `#define websocket_protocol "domoticz"` dans son
 * cWebem.cpp). L'authentification Basic, si configuree, doit etre
 * envoyee des le handshake HTTP initial (header Authorization), pas
 * apres — Domoticz refuse sinon l'upgrade.
 *
 * Format des messages recus : NON documente officiellement. Best-effort
 * deduit du client web officiel / du projet Dashticz : un objet JSON par
 * device modifie, avec sensiblement les memes champs que
 * DomoticzDeviceDto (idx, Data, nValue, LastUpdate...). A valider sur le
 * terrain via les logs (tag "DomoticzWebSocket", niveau debug) si le
 * mapping semble incomplet sur une version Domoticz donnee : les logs
 * affichent la trame brute recue avant tout parsing.
 */
class DomoticzWebSocketClient(private val config: DomoticzConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        // Garde la connexion vivante malgre les routeurs/box qui coupent
        // les connexions TCP inactives sur un reseau domestique.
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    /**
     * Flow d'evenements avec reconnexion automatique et backoff
     * exponentiel (1s -> 2s -> 5s -> 10s -> plafonne a 30s) en cas
     * d'echec ou de fermeture. Un seul socket actif a la fois : ne pas
     * collecter ce Flow depuis plusieurs endroits en parallele (utiliser
     * DomoticzRepository.observeLiveUpdates comme point d'entree unique
     * cote appelant).
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
                        retryDelayMs = 1_000L // reset du backoff des qu'une connexion reussit
                        trySend(DomoticzWsEvent.Connected)
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        Log.d(TAG, "Message recu: $text")
                        try {
                            val root = json.parseToJsonElement(text).jsonObject
                            val event = root["event"]?.jsonPrimitive?.content
                            
                            if (event == "response") {
                                // Domoticz renvoie parfois les updates dans un champ "data" (string JSON)
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
                                // Format direct (attendu initialement)
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

    /** A appeler explicitement quand le client n'est plus utilise (hot swap de config, onCleared). */
    fun close() {
        webSocket?.close(1000, "Client ferme")
        webSocket = null
        okHttpClient.dispatcher.executorService.shutdown()
    }
}
