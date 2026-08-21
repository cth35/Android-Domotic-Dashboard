package com.homehabit.app.data

import android.content.Context
import com.homehabit.app.model.DashboardConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Source de vérité unique pour la config du dashboard. Partagée entre
 * DashboardViewModel (lecture + écriture via drag/resize, ajout de
 * widget) et le serveur HTTP embarqué (lecture + écriture via
 * navigateur). Toute modification, quelle que soit son origine, passe
 * par updateConfig() : elle est aussitôt persistée sur disque et
 * republiée dans configFlow, donc les deux "clients" restent synchronisés
 * sans mécanisme supplémentaire.
 */
class ConfigRepository(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val configFile: File
        get() = File(appContext.filesDir, "dashboard_config.json")

    private val _configFlow = MutableStateFlow(loadInitialConfig())
    val configFlow: StateFlow<DashboardConfig> = _configFlow.asStateFlow()

    fun current(): DashboardConfig = _configFlow.value

    /** Remplace la config courante, la persiste et notifie tous les observateurs. */
    fun updateConfig(newConfig: DashboardConfig) {
        configFile.writeText(serialize(newConfig))
        _configFlow.value = newConfig
    }

    /** Utilisé par le serveur HTTP : parse puis applique un JSON brut reçu du navigateur. */
    fun updateFromJson(rawJson: String): Result<DashboardConfig> = runCatching {
        val parsed = parse(rawJson)
        updateConfig(parsed)
        parsed
    }

    fun serialize(config: DashboardConfig): String =
        json.encodeToString(DashboardConfig.serializer(), config)

    fun parse(rawJson: String): DashboardConfig =
        json.decodeFromString(DashboardConfig.serializer(), rawJson)

    /**
     * Au premier lancement (pas encore de fichier en stockage interne),
     * on repart de l'asset embarqué dans l'APK et on le copie en
     * stockage interne pour qu'il devienne modifiable. Dans tous les
     * cas, on s'assure qu'un token d'auth existe (genere une seule fois,
     * jamais regenere ensuite tant qu'il n'est pas vide).
     */
    private fun loadInitialConfig(): DashboardConfig {
        val file = configFile
        val loaded = if (file.exists()) {
            runCatching { parse(file.readText()) }.getOrElse { loadFromAssets() }
        } else {
            val default = loadFromAssets()
            file.writeText(serialize(default))
            default
        }
        return ensureHttpAuthToken(loaded)
    }

    /**
     * Genere un token une seule fois si absent (asset par defaut ne
     * porte pas de token en dur, volontairement — chaque installation
     * doit avoir le sien). Alphabet sans caracteres ambigus (pas de 0/O,
     * 1/I/L) car l'utilisateur doit le retaper dans un navigateur.
     */
    private fun ensureHttpAuthToken(config: DashboardConfig): DashboardConfig {
        if (config.settings.httpAuthToken.isNotBlank()) return config

        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val token = (1..8).map { alphabet.random() }.joinToString("")
        val updated = config.copy(settings = config.settings.copy(httpAuthToken = token))
        configFile.writeText(serialize(updated))
        return updated
    }

    private fun loadFromAssets(fileName: String = "dashboard_config.json"): DashboardConfig {
        val text = appContext.assets.open(fileName).bufferedReader().use { it.readText() }
        return parse(text)
    }
}
