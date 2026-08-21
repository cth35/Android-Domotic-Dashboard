package com.homehabit.app.server

import com.homehabit.app.data.ConfigRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/**
 * Serveur HTTP local permettant d'éditer la config du dashboard depuis
 * n'importe quel navigateur du réseau local (ex: http://<ip-tablette>:8090).
 *
 * Protege par un token simple (voir ConfigRepository.ensureHttpAuthToken)
 * plutot qu'un vrai systeme de comptes — suffisant pour un reseau local,
 * evite qu'un appareil quelconque sur le wifi puisse lire/modifier la
 * config (qui contient desormais le mot de passe Domoticz) ou piloter
 * les widgets sans rien demander. Toujours pas de HTTPS : le token
 * circule en clair sur le reseau local, cohérent avec le niveau de
 * confiance suppose (LAN domestique), mais a revisiter serieusement si
 * l'app doit un jour etre exposee au-dela du LAN.
 */
class ConfigHttpServer(
    private val repository: ConfigRepository,
    private val port: Int = 8090
) {
    private var engine: ApplicationEngine? = null

    fun start() {
        if (engine != null) return

        engine = embeddedServer(CIO, port = port) {
            install(CORS) {
                anyHost()
                allowHeader("Content-Type")
                allowHeader("Authorization")
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
            }

            routing {
                get("/") {
                    // Le premier chargement de page ne peut pas porter
                    // d'entete Authorization (navigation navigateur
                    // classique) : le token doit passer en query param
                    // ici. Le HTML servi l'embarque ensuite dans son JS
                    // pour les appels fetch() suivants (voir
                    // configEditorHtml).
                    if (!isAuthorized(call)) {
                        call.respondText(
                            "Acces refuse : ajoutez ?token=VOTRE_TOKEN a l'URL (visible dans l'app, mode edition).",
                            ContentType.Text.Plain,
                            HttpStatusCode.Unauthorized
                        )
                        return@get
                    }
                    call.respondText(
                        configEditorHtml(repository.current().settings.httpAuthToken),
                        ContentType.Text.Html
                    )
                }

                get("/config") {
                    if (!isAuthorized(call)) {
                        call.respondText("Non autorise", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                        return@get
                    }
                    // Force la desactivation du cache pour eviter de voir une ancienne config
                    call.response.headers.append("Cache-Control", "no-cache, no-store, must-revalidate")
                    call.respondText(
                        repository.serialize(repository.current()),
                        ContentType.Application.Json
                    )
                }

                post("/config") {
                    if (!isAuthorized(call)) {
                        call.respondText("Non autorise", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val body = call.receiveText()
                    repository.updateFromJson(body)
                        .onSuccess {
                            call.respondText(
                                """{"status":"ok"}""",
                                ContentType.Application.Json
                            )
                        }
                        .onFailure { error ->
                            call.respondText(
                                """{"status":"error","message":"${error.message.orEmpty().replace("\"", "'")}"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.BadRequest
                            )
                        }
                }
            }
        }.start(wait = false)
    }

    /**
     * Accepte le token soit en query param (?token=...), soit en entete
     * Authorization: Bearer .... Si aucun token n'est configure (ne
     * devrait plus arriver depuis ConfigRepository.ensureHttpAuthToken,
     * mais filet de securite au cas ou), refuse tout par defaut plutot
     * que d'ouvrir l'acces en grand.
     */
    private fun isAuthorized(call: ApplicationCall): Boolean {
        val expected = repository.current().settings.httpAuthToken
        if (expected.isBlank()) return false

        val fromQuery = call.request.queryParameters["token"]
        val fromHeader = call.request.headers["Authorization"]
            ?.removePrefix("Bearer ")
            ?.trim()

        return expected == fromQuery || expected == fromHeader
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        engine = null
    }
}
