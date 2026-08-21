package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.homehabit.app.data.domoticz.DiscoveredDomoticzDevice
import com.homehabit.app.data.domoticz.DiscoveredDomoticzScene
import com.homehabit.app.model.WidgetType
import com.homehabit.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWidgetDialog(
    devices: List<DiscoveredDomoticzDevice>,
    scenes: List<DiscoveredDomoticzScene>,
    onSelect: (DiscoveredDomoticzDevice) -> Unit,
    onSelectScene: (DiscoveredDomoticzScene) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredDevices = remember(devices, searchQuery) {
        if (searchQuery.isBlank()) devices
        else devices.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredScenes = remember(scenes, searchQuery) {
        if (searchQuery.isBlank()) scenes
        else scenes.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .heightIn(max = 540.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ajouter un widget",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un appareil...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = AccentBlue,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                if (filteredDevices.isEmpty() && filteredScenes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isBlank()) "Rien de nouveau trouve." else "Aucun resultat pour \"$searchQuery\"",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(
                                text = "Systeme",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { 
                                        onSelect(DiscoveredDomoticzDevice(
                                            idx = "system_clock",
                                            name = "Horloge",
                                            widgetType = WidgetType.CLOCK,
                                            raw = com.homehabit.app.data.domoticz.DomoticzDeviceDto(idx = "0")
                                        ))
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Horloge & Date", color = TextPrimary, fontSize = 13.sp)
                                    Text(text = "Affiche l'heure et la date actuelle", color = TextSecondary, fontSize = 10.sp)
                                }
                                Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        if (filteredScenes.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Scenes & groupes (${filteredScenes.size})",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            items(filteredScenes, key = { "scene_${it.idx}" }) { scene ->
                                SceneRow(scene = scene, onClick = { onSelectScene(scene) })
                            }
                        }

                        if (filteredDevices.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Appareils (${filteredDevices.size})",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            items(filteredDevices, key = { "device_${it.idx}" }) { device ->
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
    WidgetType.CLOCK -> Icons.Default.Schedule
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
    WidgetType.CLOCK -> AccentBlue
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
    WidgetType.CLOCK -> "Horloge"
    else -> "Type non reconnu — a verifier"
}
