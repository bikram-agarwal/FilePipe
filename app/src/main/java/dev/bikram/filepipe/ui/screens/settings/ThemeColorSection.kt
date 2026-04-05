package dev.bikram.filepipe.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.ui.components.CustomSeedHexDialog
import dev.bikram.filepipe.ui.theme.Blue40
import dev.bikram.filepipe.ui.theme.BlueGrey40
import dev.bikram.filepipe.ui.theme.Teal40
import dev.bikram.filepipe.ui.theme.normalizeCustomSeedHexOrNull
import dev.bikram.filepipe.ui.theme.parseSeedColorHexToColorOrNull

@Composable
fun themePaletteStyleLabel(style: ThemePaletteStyle): String = stringResource(
    when (style) {
        ThemePaletteStyle.TONAL_SPOT -> R.string.theme_palette_tonal_spot
        ThemePaletteStyle.NEUTRAL -> R.string.theme_palette_neutral
        ThemePaletteStyle.VIBRANT -> R.string.theme_palette_vibrant
        ThemePaletteStyle.EXPRESSIVE -> R.string.theme_palette_expressive
        ThemePaletteStyle.RAINBOW -> R.string.theme_palette_rainbow
        ThemePaletteStyle.FRUIT_SALAD -> R.string.theme_palette_fruit_salad
        ThemePaletteStyle.MONOCHROME -> R.string.theme_palette_monochrome
        ThemePaletteStyle.FIDELITY -> R.string.theme_palette_fidelity
        ThemePaletteStyle.CONTENT -> R.string.theme_palette_content
    }
)

private fun customHexSwatchSelected(
    colorSource: AppColorSource,
    activeCustomSeedHex: String,
    storedHex: String
): Boolean {
    if (colorSource != AppColorSource.CUSTOM) return false
    val activeNorm = normalizeCustomSeedHexOrNull(activeCustomSeedHex)
    val storedNorm = normalizeCustomSeedHexOrNull(storedHex)
    return when {
        activeNorm != null && storedNorm != null -> activeNorm == storedNorm
        else -> activeCustomSeedHex.trim() == storedHex.trim()
    }
}

@Composable
fun ThemeAccentRow(
    colorSource: AppColorSource,
    activeCustomSeedHex: String,
    savedCustomSeedHexes: List<String>,
    onSelectPreset: (AppColorSource) -> Unit,
    onSelectCustomHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    onAddCustomHexClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(AppColorSource.accentOptions, key = { "preset_${it.name}" }) { source ->
            val isSelected = colorSource == source
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = borderColor,
                        shape = CircleShape
                    )
                    .clickable(
                        onClick = { onSelectPreset(source) },
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center
            ) {
                ThemeAccentCircleContent(source = source)
            }
        }
        items(savedCustomSeedHexes, key = { "hex_$it" }) { storedHex ->
            val isSelected = customHexSwatchSelected(colorSource, activeCustomSeedHex, storedHex)
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            val fillColor = parseSeedColorHexToColorOrNull(storedHex)
                ?: MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = borderColor,
                        shape = CircleShape
                    )
                    .combinedClickable(
                        onClick = { onSelectCustomHex(storedHex) },
                        onLongClick = { onCustomHexLongPress(storedHex) },
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(fillColor)
                )
            }
        }
        item(key = "add_custom_seed") {
            val addBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = addBorder, shape = CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .clickable(
                        onClick = onAddCustomHexClick,
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() }
                    )
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.settings_custom_seed_dialog_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemeAccentCircleContent(source: AppColorSource) {
    when (source) {
        AppColorSource.DEFAULT -> Row(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Blue40)
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(BlueGrey40)
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Teal40)
            )
        }
        AppColorSource.MATERIAL_YOU -> Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6750A4),
                            Color(0xFF625B71),
                            Color(0xFF7D5260)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(22.dp)
            )
        }
        else -> {
            val seed = source.seedPrimary() ?: Color.Gray
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(seed)
            )
        }
    }
}

@Composable
fun ThemePaletteStyleRow(
    selected: ThemePaletteStyle,
    enabled: Boolean,
    onSelect: (ThemePaletteStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ThemePaletteStyle.all, key = { it.name }) { style ->
            FilterChip(
                selected = selected == style,
                onClick = { if (enabled) onSelect(style) },
                enabled = enabled,
                label = {
                    Text(
                        text = themePaletteStyleLabel(style),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            )
        }
    }
}

@Composable
fun ThemeColorSection(
    colorSource: AppColorSource,
    savedCustomSeedHexes: List<String>,
    activeCustomSeedHex: String,
    themePaletteStyle: ThemePaletteStyle,
    onColorSource: (AppColorSource) -> Unit,
    onPaletteStyle: (ThemePaletteStyle) -> Unit,
    onAddCustomSeedHex: (String) -> Unit,
    onSelectCustomSeedHex: (String) -> Unit,
    onRemoveCustomSeedHex: (String) -> Unit
) {
    var showCustomHexDialog by remember { mutableStateOf(false) }
    var hexPendingRemove by remember { mutableStateOf<String?>(null) }

    if (showCustomHexDialog) {
        CustomSeedHexDialog(
            initialDraft = "",
            onDismiss = { showCustomHexDialog = false },
            onConfirm = { raw ->
                onAddCustomSeedHex(raw)
                showCustomHexDialog = false
            }
        )
    }

    val hexToConfirmRemove = hexPendingRemove
    if (hexToConfirmRemove != null) {
        AlertDialog(
            onDismissRequest = { hexPendingRemove = null },
            title = { Text(stringResource(R.string.settings_custom_seed_remove_title)) },
            text = { Text(stringResource(R.string.settings_custom_seed_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveCustomSeedHex(hexToConfirmRemove)
                    hexPendingRemove = null
                }) {
                    Text(stringResource(R.string.schedule_remove_short))
                }
            },
            dismissButton = {
                TextButton(onClick = { hexPendingRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_theme_colors_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ThemeAccentRow(
            colorSource = colorSource,
            activeCustomSeedHex = activeCustomSeedHex,
            savedCustomSeedHexes = savedCustomSeedHexes,
            onSelectPreset = onColorSource,
            onSelectCustomHex = onSelectCustomSeedHex,
            onCustomHexLongPress = { hexPendingRemove = it },
            onAddCustomHexClick = { showCustomHexDialog = true }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_palette_style),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        ThemePaletteStyleRow(
            selected = themePaletteStyle,
            enabled = colorSource == AppColorSource.DEFAULT || colorSource.isSeedBased,
            onSelect = onPaletteStyle
        )
    }
}
