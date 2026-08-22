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
 * Local HTTP server allowing to edit the dashboard config from
 * any browser on the local network (e.g., http://<tablet-ip>:8090).
 *
 * Protected by a simple token (see ConfigRepository.ensureHttpAuthToken)
 * rather than a real account system — sufficient for a local network,
 * prevents any device on the wifi from being able to read/modify the
 * config (which now contains the Domoticz password) or control
 * widgets without asking anything. Still no HTTPS: the token
 * circulates in clear on the local network, consistent with the level of
 * assumed trust (domestic LAN), but to be seriously revisited if
 * the app must one day be exposed beyond the LAN.
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
                    // The first page load cannot carry
                    // an Authorization header (classic browser
                    // navigation): the token must pass in a query param
                    // here. The served HTML then embeds it in its JS
                    // for subsequent fetch() calls (see
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
                    // Force cache deactivation to avoid seeing an old config
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
     * Accepts the token either in a query param (?token=...), or in an
     * Authorization: Bearer ... header. If no token is configured (should
     * no longer happen since ConfigRepository.ensureHttpAuthToken,
     * but safeguard just in case), refuses everything by default rather
     * than opening access wide.
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
