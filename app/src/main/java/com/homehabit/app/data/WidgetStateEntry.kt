package com.homehabit.app.data

/**
 * Associates a widget state with its last update timestamp
 * (epoch millis). For Domoticz widgets, comes from the actual `LastUpdate`
 * field returned by the server. For demo values (weather, camera),
 * it is simply the moment the app is loaded.
 */
data class WidgetStateEntry(
    val state: WidgetLiveState,
    val lastUpdate: Long,
    val fallbackName: String? = null
)
