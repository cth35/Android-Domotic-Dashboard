package com.homehabit.app.ui.dashboard

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.homehabit.app.model.AppSettings
import com.homehabit.app.model.DashboardPage
import com.homehabit.app.power.ScreenPowerController
import com.homehabit.app.server.NetworkUtils
import com.homehabit.app.ui.theme.AccentBlue
import com.homehabit.app.ui.theme.AccentRed
import com.homehabit.app.ui.theme.SurfaceDark
import com.homehabit.app.ui.theme.SurfaceVariantDark
import com.homehabit.app.ui.theme.TextPrimary
import com.homehabit.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    initial: AppSettings,
    pages: List<DashboardPage>,
    onManagePage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var host by remember { mutableStateOf(initial.domoticzHost) }
    var port by remember { mutableStateOf(initial.domoticzPort.toString()) }
    var username by remember { mutableStateOf(initial.domoticzUsername.orEmpty()) }
    var password by remember { mutableStateOf(initial.domoticzPassword.orEmpty()) }
    var useHttps by remember { mutableStateOf(initial.domoticzUseHttps) }
    var error by remember { mutableStateOf<String?>(null) }

    var nightModeEnabled by remember { mutableStateOf(initial.nightModeEnabled) }
    var nightStartHour by remember { mutableStateOf(initial.nightStartHour) }
    var nightEndHour by remember { mutableStateOf(initial.nightEndHour) }
    var nightBrightnessPercent by remember { mutableStateOf((initial.nightBrightness * 100).roundToInt().coerceIn(1, 100)) }
    var nightScreenOffEnabled by remember { mutableStateOf(initial.nightScreenOffEnabled) }
    var useRtspClientNative by remember { mutableStateOf(initial.useRtspClientNative) }
    var isAdminActive by remember { mutableStateOf(ScreenPowerController.isDeviceAdminActive(context)) }

    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isAdminActive = ScreenPowerController.isDeviceAdminActive(context)
        nightScreenOffEnabled = isAdminActive
    }

    fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ScreenPowerController.deviceAdminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Necessaire pour eteindre reellement l'ecran la nuit (pas juste l'assombrir)."
            )
        }
        runCatching { adminLauncher.launch(intent) }
    }

    Dialog(onDismissRequest = onDismiss) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Reglages Domoticz", color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Necessaire pour les widgets lumiere, volet, thermostat, serrure et capteur.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(16.dp))
                    SettingsField(
                        label = "Adresse IP ou nom d'hote",
                        value = host,
                        onValueChange = { host = it },
                        placeholder = "192.168.1.10"
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsField(
                        label = "Port",
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit) },
                        placeholder = "8080",
                        keyboardType = KeyboardType.Number
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsField(
                        label = "Utilisateur (optionnel)",
                        value = username,
                        onValueChange = { username = it }
                    )

                    Spacer(Modifier.height(10.dp))
                    SettingsField(
                        label = "Mot de passe (optionnel)",
                        value = password,
                        onValueChange = { password = it },
                        isPassword = true
                    )

                    Spacer(Modifier.height(14.dp))
                    ToggleRow(label = "HTTPS", checked = useHttps, onCheckedChange = { useHttps = it })

                    Spacer(Modifier.height(24.dp))
                    Text(text = "Editeur de configuration", color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Utilisez cette URL sur votre ordinateur pour modifier le JSON en temps reel.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    val clipboardManager = LocalClipboardManager.current
                    val fullUrl = remember(initial.httpAuthToken) {
                        "http://${NetworkUtils.getLocalIpAddress() ?: "?"}:8090/?token=${initial.httpAuthToken}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(fullUrl))
                                Toast.makeText(context, "URL copiee", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = fullUrl,
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(text = "Mode nuit", color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Assombrit l'ecran automatiquement selon l'horaire. Fonctionne toujours, aucun droit particulier requis.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(12.dp))
                    ToggleRow(label = "Activer le mode nuit", checked = nightModeEnabled, onCheckedChange = { nightModeEnabled = it })

                    if (nightModeEnabled) {
                        Spacer(Modifier.height(10.dp))
                        HourStepper(label = "Debut", hour = nightStartHour, onChange = { nightStartHour = it })
                        Spacer(Modifier.height(8.dp))
                        HourStepper(label = "Fin", hour = nightEndHour, onChange = { nightEndHour = it })
                        Spacer(Modifier.height(8.dp))
                        PercentStepper(
                            label = "Luminosite nocturne",
                            percent = nightBrightnessPercent,
                            onChange = { nightBrightnessPercent = it }
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Extinction reelle (avance)",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Eteint vraiment l'ecran plutot que de l'assombrir. Necessite les droits " +
                                "administrateur de l'appareil (popup systeme, revocable a tout moment). " +
                                "Best-effort : peut ne pas fonctionner selon le fabricant, et le rallumage " +
                                "peut retomber sur l'ecran de verrouillage si un code est configure sur " +
                                "l'appareil.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        ToggleRow(
                            label = if (isAdminActive) "Extinction reelle activee" else "Activer (demande les droits)",
                            checked = nightScreenOffEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !isAdminActive) {
                                    requestDeviceAdmin()
                                } else {
                                    nightScreenOffEnabled = checked
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(text = "Camera", color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Options pour le flux RTSP plein ecran.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        label = "Lecteur basse latence (Experimental)",
                        checked = useRtspClientNative,
                        onCheckedChange = { useRtspClientNative = it }
                    )
                    Text(
                        text = "Utilise un client RTSP natif plutot que libVLC. Plus reactif, mais peut etre moins stable sur certains flux.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(Modifier.height(24.dp))
                    Text(text = "Gestion des pages", color = TextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Appuyez sur une page pour modifier son nom ou sa grille.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    pages.forEachIndexed { index, page ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .clickable { 
                                    onDismiss()
                                    onManagePage(index) 
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = page.name, color = TextPrimary, fontSize = 13.sp)
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .clickable { onAddPage() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ajouter une page", color = AccentBlue, fontSize = 13.sp)
                        }
                    }

                    error?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(text = message, color = AccentRed, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Annuler", color = TextSecondary, fontSize = 13.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue)
                            .clickable {
                                val portInt = port.toIntOrNull()
                                when {
                                    host.isBlank() -> error = "Adresse requise"
                                    portInt == null -> error = "Port invalide"
                                    else -> {
                                        val newSettings = AppSettings(
                                            domoticzHost = host.trim(),
                                            domoticzPort = portInt,
                                            domoticzUseHttps = useHttps,
                                            domoticzUsername = username.trim().ifBlank { null },
                                            domoticzPassword = password.ifBlank { null },
                                            httpAuthToken = initial.httpAuthToken,
                                            nightModeEnabled = nightModeEnabled,
                                            nightStartHour = nightStartHour,
                                            nightEndHour = nightEndHour,
                                            nightBrightness = (nightBrightnessPercent / 100f).coerceIn(0.01f, 1f),
                                            nightScreenOffEnabled = nightScreenOffEnabled,
                                            useRtspClientNative = useRtspClientNative
                                        )
                                        ScreenPowerController.scheduleAlarms(context, newSettings)
                                        onSave(newSettings)
                                    }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Enregistrer", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
        )
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(((hour - 1) + 24) % 24) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Diminuer", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            Text(text = "%02dh".format(hour), color = TextPrimary, fontSize = 14.sp)
            IconButton(onClick = { onChange((hour + 1) % 24) }) {
                Icon(Icons.Filled.Add, contentDescription = "Augmenter", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PercentStepper(label: String, percent: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((percent - 5).coerceAtLeast(1)) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Diminuer", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
            Text(text = "$percent%", color = TextPrimary, fontSize = 14.sp)
            IconButton(onClick = { onChange((percent + 5).coerceAtMost(100)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Augmenter", tint = TextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(AccentBlue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark)
                .padding(10.dp),
            decorationBox = { innerTextField ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(text = placeholder, color = TextSecondary, fontSize = 14.sp)
                }
                innerTextField()
            }
        )
    }
}
