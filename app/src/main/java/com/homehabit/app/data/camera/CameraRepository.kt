package com.homehabit.app.data.camera

import com.homehabit.app.data.WidgetLiveState
import com.homehabit.app.data.WidgetStateEntry
import com.homehabit.app.model.WidgetConfig
import com.homehabit.app.model.WidgetType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CameraRepository {

    /**
     * Pour les caméras, on ne fait pas de vrai poll réseau ici (Coil s'en
     * charge dans l'UI pour le snapshot), mais on émet un état régulier
     * pour forcer le rafraîchissement du badge "Last Update" et synchroniser
     * le cache-busting des images si nécessaire.
     */
    fun observeStates(
        widgets: List<WidgetConfig>
    ): Flow<Map<String, WidgetStateEntry>> = flow {
        val cameraWidgets = widgets.filter { it.widgetType == WidgetType.CAMERA }
        if (cameraWidgets.isEmpty()) {
            emit(emptyMap())
            return@flow
        }

        while (true) {
            val states = cameraWidgets.associate { widget ->
                widget.id to WidgetStateEntry(
                    state = WidgetLiveState.Camera(
                        isLive = true, // On suppose la caméra active par défaut
                        label = widget.label ?: "Caméra"
                    ),
                    lastUpdate = System.currentTimeMillis()
                )
            }
            emit(states)
            
            // On s'aligne sur le rafraîchissement par défaut (15s). 
            // Les réglages spécifiques par widget sont gérés côté UI.
            delay(15_000L)
        }
    }
}
