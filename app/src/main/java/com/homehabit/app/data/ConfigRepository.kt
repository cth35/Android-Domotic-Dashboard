package com.homehabit.app.data

import android.content.Context
import com.homehabit.app.model.DashboardConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single source of truth for the dashboard config. Shared between
 * DashboardViewModel (read + write via drag/resize, adding
 * widgets) and the embedded HTTP server (read + write via
 * browser). Any modification, whatever its origin, passes
 * through updateConfig(): it is immediately persisted to disk and
 * republished in configFlow, so both "clients" remain synchronized
 * without additional mechanisms.
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

    /** Replaces the current config, persists it, and notifies all observers. */
    fun updateConfig(newConfig: DashboardConfig) {
        configFile.writeText(serialize(newConfig))
        _configFlow.value = newConfig
    }

    /** Used by the HTTP server: parses and then applies a raw JSON received from the browser. */
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
     * On first launch (no file in internal storage yet),
     * we start from the asset embedded in the APK and copy it to
     * internal storage so it becomes modifiable. In all cases,
     * we ensure an auth token exists (generated once,
     * never regenerated thereafter as long as it is not empty).
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
     * Generates a token only once if absent (default asset does not
     * carry a hardcoded token, intentionally — each installation
     * must have its own). Alphabet without ambiguous characters (no 0/O,
     * 1/I/L) because the user must re-type it in a browser.
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
