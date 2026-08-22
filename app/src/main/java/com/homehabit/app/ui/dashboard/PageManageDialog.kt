package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.homehabit.app.model.GridConfig
import com.homehabit.app.ui.theme.AccentBlue
import com.homehabit.app.ui.theme.AccentRed
import com.homehabit.app.ui.theme.SurfaceDark
import com.homehabit.app.ui.theme.SurfaceVariantDark
import com.homehabit.app.ui.theme.TextPrimary
import com.homehabit.app.ui.theme.TextSecondary

/**
 * Allows to rename a page or change the number of columns of its
 * grid (to adapt to the screen size).
 */
@Composable
fun PageManageDialog(
    currentName: String,
    currentGrid: GridConfig,
    canDelete: Boolean,
    onSave: (String, GridConfig) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var columns by remember { mutableIntStateOf(currentGrid.columns) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Parametres de la page", color = TextPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))

                Text(text = "Nom", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .padding(12.dp)
                )

                Spacer(Modifier.height(20.dp))

                Text(text = "Colonnes (densite horizontale)", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { columns = (columns - 1).coerceAtLeast(2) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Moins", tint = TextPrimary)
                    }
                    Text(text = "$columns colonnes", color = TextPrimary, fontSize = 14.sp)
                    IconButton(onClick = { columns = (columns + 1).coerceAtMost(12) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Plus", tint = TextPrimary)
                    }
                }

                Spacer(Modifier.height(20.dp))

                var rows by remember { mutableIntStateOf(currentGrid.rows) }
                Text(text = "Lignes (hauteur de l'ecran)", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVariantDark)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { rows = (rows - 1).coerceAtLeast(0) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Moins", tint = TextPrimary)
                    }
                    Text(
                        text = if (rows == 0) "Auto (defilement)" else "$rows lignes (fit)",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = { rows = (rows + 1).coerceAtMost(12) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Plus", tint = TextPrimary)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue)
                            .clickable { onSave(name, GridConfig(columns, rows)) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Enregistrer", color = TextPrimary, fontSize = 13.sp)
                    }

                    if (canDelete) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariantDark)
                                .clickable(onClick = onDelete)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = AccentRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Annuler", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
