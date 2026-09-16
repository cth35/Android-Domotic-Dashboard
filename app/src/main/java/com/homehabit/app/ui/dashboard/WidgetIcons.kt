package com.homehabit.app.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object WidgetIcons {
    private val iconMap = mapOf(
        "volume_up" to Icons.Filled.VolumeUp,
        "volume_down" to Icons.Filled.VolumeDown,
        "volume_mute" to Icons.Filled.VolumeMute,
        "volume_off" to Icons.Filled.VolumeOff,
        "play_arrow" to Icons.Filled.PlayArrow,
        "pause" to Icons.Filled.Pause,
        "stop" to Icons.Filled.Stop,
        "power_settings_new" to Icons.Filled.PowerSettingsNew,
        "lightbulb" to Icons.Filled.Lightbulb,
        "palette" to Icons.Filled.Palette,
        "thermostat" to Icons.Filled.Thermostat,
        "lock" to Icons.Filled.Lock,
        "lock_open" to Icons.Filled.LockOpen,
        "videocam" to Icons.Filled.Videocam,
        "cloud" to Icons.Filled.Cloud,
        "info" to Icons.Filled.Info,
        "auto_awesome" to Icons.Filled.AutoAwesome,
        "schedule" to Icons.Filled.Schedule,
        "add" to Icons.Filled.Add,
        "remove" to Icons.Filled.Remove,
        "close" to Icons.Filled.Close,
        "settings" to Icons.Filled.Settings,
        "delete" to Icons.Filled.Delete,
        "search" to Icons.Filled.Search,
        "air" to Icons.Filled.Air,
        "water_drop" to Icons.Filled.WaterDrop,
        "keyboard_arrow_up" to Icons.Filled.KeyboardArrowUp,
        "keyboard_arrow_down" to Icons.Filled.KeyboardArrowDown,
        "tv" to Icons.Filled.Tv,
        "speaker" to Icons.Filled.Speaker,
        "music_note" to Icons.Filled.MusicNote
    )

    /**
     * Maps a snake_case name (from JSON) to a Material Icon ImageVector.
     * Returns null if the icon is not found in the registry.
     */
    fun fromName(name: String?): ImageVector? {
        if (name == null) return null
        return iconMap[name.lowercase()]
    }
}
