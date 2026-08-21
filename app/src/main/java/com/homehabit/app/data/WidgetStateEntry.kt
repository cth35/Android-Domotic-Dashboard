package com.homehabit.app.data

/**
 * Associe un état de widget à son timestamp de dernière mise à jour
 * (epoch millis). Pour les widgets Domoticz, vient du champ `LastUpdate`
 * réel renvoyé par le serveur. Pour les valeurs de démo (météo, caméra),
 * c'est simplement le moment du chargement de l'app.
 */
data class WidgetStateEntry(
    val state: WidgetLiveState,
    val lastUpdate: Long
)
