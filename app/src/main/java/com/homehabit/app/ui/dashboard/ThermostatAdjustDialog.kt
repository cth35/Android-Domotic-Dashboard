package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.homehabit.app.ui.theme.AccentBlue
import com.homehabit.app.ui.theme.SurfaceDark
import com.homehabit.app.ui.theme.SurfaceVariantDark
import com.homehabit.app.ui.theme.TextPrimary

@Composable
fun ThermostatAdjustDialog(
    label: String,
    currentSetpoint: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentSetpoint) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = label, color = TextPrimary, fontSize = 14.sp)

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    IconButton(onClick = { value = (value - 0.5f).coerceAtLeast(5f) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Diminuer", tint = TextPrimary)
                    }
                    Text(
                        text = "%.1f°C".format(value),
                        color = TextPrimary,
                        fontSize = 28.sp
                    )
                    IconButton(onClick = { value = (value + 0.5f).coerceAtMost(30f) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Augmenter", tint = TextPrimary)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceVariantDark)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Annuler", color = TextPrimary, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue)
                            .clickable(onClick = { onConfirm(value) })
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Valider", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
