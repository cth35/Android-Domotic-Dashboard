package com.homehabit.app.model

import kotlinx.serialization.Serializable

/**
 * Racine de la config. BREAKING CHANGE : avant multi-dashboard, la racine
 * avait directement `grid` + `widgets`. Desormais c'est une liste de
 * pages, chacune avec sa propre grille et ses propres widgets. Un
 * dashboard.json ecrit avec l'ancien format ne sera pas migre
 * automatiquement (les champs `grid`/`widgets` a plat seront ignores,
 * `pages` repartira sur son defaut) : a reinitialiser si necessaire.
 *
 * `settings` est additif (nouveau champ, valeur par defaut) : pas de
 * breaking change pour un fichier deja au format multi-pages.
 */
@Serializable
data class DashboardConfig(
    val settings: AppSettings = AppSettings(),
    val pages: List<DashboardPage> = listOf(DashboardPage())
)

/**
 * Reglages globaux, persistes et editables depuis l'app (ecran de
 * reglages) ou depuis le navigateur (meme JSON que le reste de la
 * config). Volontairement plat pour l'instant : un seul serveur
 * Domoticz. Pas de reglage global pour la meteo, chaque widget porte
 * deja ses propres lat/lon (WidgetSource).
 */
@Serializable
data class AppSettings(
    val domoticzHost: String = "192.168.1.10",
    val domoticzPort: Int = 8080,
    val domoticzUseHttps: Boolean = false,
    val domoticzUsername: String? = null,
    val domoticzPassword: String? = null,
    // Genere automatiquement au premier lancement (ConfigRepository),
    // jamais laisse vide en pratique. Protege le serveur HTTP embarque.
    val httpAuthToken: String = "",

    // --- Mode nuit (assombrissement + extinction planifiee) ---
    val nightModeEnabled: Boolean = false,
    val nightStartHour: Int = 22,   // 0-23, heure de debut (peut traverser minuit si > nightEndHour)
    val nightEndHour: Int = 7,      // 0-23, heure de fin
    val nightBrightness: Float = 0.03f,   // 0.01-1.0, luminosite pendant la nuit
    // Extinction reelle (pas juste assombrissement) via droits
    // administrateur — voir NightModeSchedule et DeviceAdmin. Desactive
    // par defaut : necessite une action explicite de l'utilisateur pour
    // accorder les droits, ne peut pas s'activer tout seul.
    val nightScreenOffEnabled: Boolean = false
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
    val rows: Int = 0 // 0 = mode scroll (hauteur libre), > 0 = mode fit (nombre fixe de lignes)
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
    val deviceId: String? = null,       // ex "idx:12" pour Domoticz
    val url: String? = null,            // uri du snapshot JPEG (widget camera au repos)
    val rtspUrl: String? = null,        // uri RTSP (flux video plein ecran)
    val refreshSeconds: Int? = null,    // frequence de rafraichissement du snapshot
    val latitude: Double? = null,       // widget meteo (Open-Meteo travaille en lat/lon)
    val longitude: Double? = null,
    val shutterStyle: String? = null,   // "buttons" (defaut) ou "toggle", widget SHUTTER uniquement
    val imageScale: String? = null      // "crop" (par defaut) ou "fit", widget CAMERA uniquement
)
