package com.homehabit.app.model

import kotlinx.serialization.Serializable

/**
 * Root of the config. BREAKING CHANGE: before multi-dashboard, the root
 * had `grid` + `widgets` directly. Now it is a list of
 * pages, each with its own grid and widgets. A
 * dashboard.json written in the old format will not be migrated
 * automatically (flat `grid`/`widgets` fields will be ignored,
 * `pages` will start from its default): to be reset if necessary.
 *
 * `settings` is additive (new field, default value): no
 * breaking change for a file already in multi-page format.
 */
@Serializable
data class DashboardConfig(
    val settings: AppSettings = AppSettings(),
    val pages: List<DashboardPage> = listOf(DashboardPage())
)

/**
 * Global settings, persisted and editable from the app (settings
 * screen) or from the browser (same JSON as the rest of the
 * config). Intentionally flat for now: a single Domoticz
 * server. No global setting for weather, each widget already carries
 * its own lat/lon (WidgetSource).
 */
@Serializable
data class AppSettings(
    val domoticzHost: String = "192.168.1.10",
    val domoticzPort: Int = 8080,
    val domoticzUseHttps: Boolean = false,
    val domoticzUsername: String? = null,
    val domoticzPassword: String? = null,
    // Automatically generated at first launch (ConfigRepository),
    // never left empty in practice. Protects the embedded HTTP server.
    val httpAuthToken: String = "",

    // --- Night mode (dimming + planned shutdown) ---
    val nightModeEnabled: Boolean = false,
    val nightStartHour: Int = 22,   // 0-23, start hour (can cross midnight if > nightStartHour)
    val nightEndHour: Int = 7,      // 0-23, end hour
    val nightBrightness: Float = 0.03f,   // 0.01-1.0, brightness during the night
    // Actual shutdown (not just dimming) via administrator
    // rights — see NightModeSchedule and DeviceAdmin. Disabled
    // by default: requires an explicit action from the user to
    // grant rights, cannot activate itself.
    val nightScreenOffEnabled: Boolean = false,

    // --- Video Player ---
    // If true, uses rtsp-client-android (more reactive, low latency)
    // instead of libVLC (more stable on difficult streams).
    val useRtspClientNative: Boolean = false
)

@Serializable
data class DashboardPage(
    val id: String = "page_1",
    val name: String = "Accueil",
    val grid: GridConfig = GridConfig(),
    val widgets: List<WidgetConfig> = emptyList()
)

@Serializable
data class GridConfig(
    val columns: Int = 6,
    val rows: Int = 0 // 0 = scroll mode (free height), > 0 = fit mode (fixed number of rows)
)

@Serializable
data class WidgetConfig(
    val id: String,
    val type: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val label: String? = null,
    val source: WidgetSource? = null
) {
    val widgetType: WidgetType get() = WidgetType.fromRaw(type)
}

@Serializable
data class WidgetSource(
    val provider: String? = null,       // "domoticz", "open-meteo", "camera", ...
    val deviceId: String? = null,       // e.g. "idx:12" for Domoticz
    val url: String? = null,            // JPEG snapshot uri (camera widget at rest)
    val rtspUrl: String? = null,        // RTSP uri (full-screen video stream)
    val refreshSeconds: Int? = null,    // snapshot refresh frequency
    val latitude: Double? = null,       // weather widget (Open-Meteo works in lat/lon)
    val longitude: Double? = null,
    val shutterStyle: String? = null,   // "buttons" (default) or "toggle", SHUTTER widget only
    val imageScale: String? = null,     // "crop" (default) or "fit", CAMERA widget only
    val sensorMode: String? = null,     // "temp" (default), "humidity" or "both"
    val useRtspClientNative: Boolean? = null, // CAMERA widget only, overrides global setting
    val triggerId: String? = null,      // e.g. "idx:24", CAMERA widget only
    val autoCloseSeconds: Int? = null   // default 60s, CAMERA widget only
)
