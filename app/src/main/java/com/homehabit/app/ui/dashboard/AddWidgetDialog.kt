package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.homehabit.app.data.domoticz.DiscoveredDomoticzDevice
import com.homehabit.app.data.domoticz.DiscoveredDomoticzScene
import com.homehabit.app.model.WidgetType
import com.homehabit.app.ui.theme.*

@Composable
fun AddWidgetDialog(
    devices: List<DiscoveredDomoticzDevice>,
    scenes: List<DiscoveredDomoticzScene>,
    onSelect: (DiscoveredDomoticzDevice) -> Unit,
    onSelectScene: (DiscoveredDomoticzScene) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .heightIn(max = 480.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ajouter un widget",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "${devices.size} appareil(s), ${scenes.size} scene(s)/groupe(s) non encore ajoute(s)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (devices.isEmpty() && scenes.isEmpty()) {
                    Text(
                        text = "Rien de nouveau trouve sur le serveur Domoticz.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    LazyColumn {
                        if (scenes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Scenes & groupes",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            items(scenes, key = { "scene_${it.idx}" }) { scene ->
                                SceneRow(scene = scene, onClick = { onSelectScene(scene) })
                            }
                        }

                        if (devices.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Appareils",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            items(devices, key = { "device_${it.idx}" }) { device ->
                                DeviceRow(device = device, onClick = { onSelect(device) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDomoticzDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(iconFor(device.widgetType), contentDescription = null, tint = colorFor(device.widgetType), modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, color = TextPrimary, fontSize = 13.sp)
            Text(text = labelFor(device.widgetType), color = TextSecondary, fontSize = 10.sp)
        }
        Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SceneRow(scene: DiscoveredDomoticzScene, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = scene.name, color = TextPrimary, fontSize = 13.sp)
            Text(
                text = if (scene.isGroup) "Groupe (on/off)" else "Scene (declencheur)",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
        Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}

private fun iconFor(type: WidgetType): ImageVector = when (type) {
    WidgetType.LIGHT -> Icons.Filled.Lightbulb
    WidgetType.THERMOSTAT -> Icons.Filled.Thermostat
    WidgetType.SHUTTER -> Icons.Filled.Blinds
    WidgetType.LOCK -> Icons.Filled.Lock
    WidgetType.CAMERA -> Icons.Filled.Videocam
    WidgetType.WEATHER -> Icons.Filled.Cloud
    WidgetType.FORECAST -> Icons.Filled.Cloud
    WidgetType.DIMMER -> Icons.Filled.Lightbulb
    WidgetType.COLOR_LIGHT -> Icons.Filled.Palette
    WidgetType.SENSOR -> Icons.Filled.Info
    WidgetType.SCENE -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.QuestionMark
}

private fun colorFor(type: WidgetType): androidx.compose.ui.graphics.Color = when (type) {
    WidgetType.LIGHT -> AccentGreen
    WidgetType.DIMMER -> AccentGreen
    WidgetType.COLOR_LIGHT -> AccentOrange
    WidgetType.THERMOSTAT -> AccentOrange
    WidgetType.SHUTTER -> TextPrimary
    WidgetType.LOCK -> TextPrimary
    WidgetType.SENSOR -> AccentBlue
    WidgetType.SCENE -> AccentGreen
    WidgetType.FORECAST -> AccentBlue
    else -> TextMuted
}

private fun labelFor(type: WidgetType): String = when (type) {
    WidgetType.LIGHT -> "Lumiere / prise"
    WidgetType.DIMMER -> "Lumiere variable"
    WidgetType.COLOR_LIGHT -> "Lumiere couleur (Hue...)"
    WidgetType.THERMOSTAT -> "Thermostat"
    WidgetType.SHUTTER -> "Volet"
    WidgetType.LOCK -> "Serrure / contact"
    WidgetType.CAMERA -> "Camera"
    WidgetType.WEATHER -> "Meteo"
    WidgetType.FORECAST -> "Prevision 7 jours"
    WidgetType.SENSOR -> "Capteur (temp, humidite, etc.)"
    WidgetType.SCENE -> "Scene / groupe"
    else -> "Type non reconnu — a verifier"
}
