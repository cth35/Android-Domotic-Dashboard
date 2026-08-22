package com.homehabit.app.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homehabit.app.model.DashboardPage
import com.homehabit.app.ui.theme.AccentBlue
import com.homehabit.app.ui.theme.SurfaceDark
import com.homehabit.app.ui.theme.TextPrimary
import com.homehabit.app.ui.theme.TextSecondary

/**
 * One tab per page. Tap = switch (animates the pager). Long press in
 * edit mode = opens PageManageDialog (rename/delete). "+"
 * tab visible only in edit mode to add a page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageTabsBar(
    pages: List<DashboardPage>,
    currentPage: Int,
    isEditMode: Boolean,
    onPageSelected: (Int) -> Unit,
    onPageLongPress: (Int) -> Unit,
    onAddPage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pages.forEachIndexed { index, page ->
            val isSelected = index == currentPage

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) AccentBlue else SurfaceDark)
                    .combinedClickable(
                        onClick = { onPageSelected(index) },
                        onLongClick = if (isEditMode) {
                            { onPageLongPress(index) }
                        } else null
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = page.name,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .clickable(onClick = onAddPage)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Ajouter une page",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
