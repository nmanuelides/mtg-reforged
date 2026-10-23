package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

/** One entry of an [OverflowMenu]. */
data class MenuItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color = Palette.Ink,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A "⋮" button holding the less used actions of a screen, for phones where they don't fit as
 * buttons of their own. Opens a paper menu with an icon and a label per action.
 */
@Composable
fun OverflowMenu(items: List<MenuItem>, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OrnateButton("", { open = true }, Modifier.width(48.dp), icon = Icons.Default.MoreVert, height = height)
        DropdownMenu(open, { open = false }, containerColor = Palette.PaperLight) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = MaterialTheme.typography.bodyLarge, color = if (item.enabled) item.tint else Palette.Line) },
                    leadingIcon = { Icon(item.icon, null, tint = if (item.enabled) item.tint else Palette.Line) },
                    enabled = item.enabled,
                    onClick = { open = false; item.onClick() },
                )
            }
        }
    }
}
