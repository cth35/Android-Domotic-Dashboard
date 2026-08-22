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
     * For cameras, we don't do a real network poll here (Coil handles it
     * in the UI for the snapshot), but we emit a regular state
     * to force the refresh of the "Last Update" badge and synchronize
     * image cache-busting if necessary.
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
            
            // Global refresh of the "Last Update" badge (default 30s).
            // Specific settings per widget for the image itself are
            // managed directly in the CameraContent component.
            delay(30_000L)
        }
    }
}
