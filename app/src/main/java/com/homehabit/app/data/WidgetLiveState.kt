package com.homehabit.app.data

/**
 * Représente l'état "live" d'un widget. Pour le moment ces valeurs sont
 * générées en dur ; elles seront remplacées par les données réelles issues
 * du client Domoticz (json.htm) et du client météo (Open-Meteo).
 */
sealed class WidgetLiveState {
    data class Weather(
        val temperature: Int,
        val condition: String,
        val min: Int,
        val max: Int
    ) : WidgetLiveState()

    data class Light(
        val isOn: Boolean,
        val brightness: Int? = null,   // 0-100, null si le device n'est pas dimmable
        val colorHex: String? = null   // "#RRGGBB", null si pas de couleur ou non determinable
    ) : WidgetLiveState()

    data class Thermostat(val temperature: Float) : WidgetLiveState()

    data class Shutter(val percentOpen: Int) : WidgetLiveState()

    data class Lock(val isLocked: Boolean) : WidgetLiveState()

    data class Camera(val isLive: Boolean, val label: String) : WidgetLiveState()

    /**
     * Capteur générique (temp, humidité, pluie, vent, UV, baromètre,
     * énergie, etc.). displayValue reprend directement le champ "Data"
     * de Domoticz (déjà formaté avec unité, ex "21.5 C"). gaugePercent
     * n'est renseigné que pour les grandeurs naturellement bornées 0-100
     * (humidité, pourcentage) — pour tout le reste, une jauge inventerait
     * une échelle arbitraire, donc null plutôt qu'une fausse précision.
     */
    data class Sensor(
        val displayValue: String,
        val kind: SensorKind = SensorKind.GENERIC,
        val gaugePercent: Float? = null
    ) : WidgetLiveState()

    data class BinarySensor(
        val isOn: Boolean,
        val isContact: Boolean = false
    ) : WidgetLiveState()

    /**
     * Scene ou groupe Domoticz declenche en un tap. isGroup distingue les
     * deux : un Group a un vrai etat on/off togglable, une Scene est un
     * declencheur sans etat persistant (toujours "Off" au repos meme
     * juste apres declenchement) — isOn n'a donc de sens durable que pour
     * un Group.
     */
    data class Scene(val isGroup: Boolean, val isOn: Boolean) : WidgetLiveState()

    /** Prevision 7 jours (widget FORECAST), Open-Meteo. */
    data class Forecast(val days: List<ForecastDay>) : WidgetLiveState()

    object Empty : WidgetLiveState()
}

/**
 * Un jour de la prevision. dayLabel est deja forme ("Lun", "Mar"...),
 * pas de logique de formatage a refaire cote UI.
 */
data class ForecastDay(
    val dateIso: String,
    val dayLabel: String,
    val condition: String,
    val weatherCode: Int?,
    val tempMin: Int,
    val tempMax: Int
)

object FakeStateProvider {

    /**
     * Seule la camera reste en demo : la meteo a maintenant son vrai
     * client (Open-Meteo), Domoticz gere light/thermostat/shutter/lock.
     */
    fun defaultStates(now: Long = System.currentTimeMillis()): Map<String, WidgetStateEntry> = mapOf(
        "cam_jardin" to WidgetStateEntry(
            WidgetLiveState.Camera(isLive = true, label = "Jardin"),
            now
        )
    )
}
