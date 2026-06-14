package dev.bikram.filepipe.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.data.preferences.generateTripletForSeed
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeConfirmDialog
import dev.bikram.filepipe.ui.components.FilePipeDropdownMenuItem
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalIconButton
import dev.bikram.filepipe.ui.components.FilePipeFilterChip
import dev.bikram.filepipe.ui.components.FilePipeSwitch
import dev.bikram.filepipe.ui.components.FilePipeToggleButton
import dev.bikram.filepipe.ui.components.HueColorSlider
import dev.bikram.filepipe.ui.components.colorHexFromHue
import dev.bikram.filepipe.ui.components.containers.GroupPosition
import dev.bikram.filepipe.ui.components.containers.GroupedListColumn
import dev.bikram.filepipe.ui.components.containers.GroupedListItem
import dev.bikram.filepipe.ui.components.hueFromHexColor
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.feedback.performRejectHaptic
import dev.bikram.filepipe.ui.feedback.tapSoundClickable
import dev.bikram.filepipe.ui.feedback.tapSoundCombinedClickable
import dev.bikram.filepipe.ui.theme.normalizeCustomSeedHexOrNull
import dev.bikram.filepipe.ui.theme.normalizeSeedHexOrNull
import dev.bikram.filepipe.ui.theme.parseCustomTriplet
import dev.bikram.filepipe.ui.theme.parseSeedColorHexToColorOrNull
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
fun themePaletteStyleLabel(style: ThemePaletteStyle): String =
    stringResource(
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
        },
    )

private fun customHexSwatchSelected(
    colorSource: AppColorSource,
    activeCustomSeedHex: String,
    storedHex: String,
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
    customPickerExpanded: Boolean,
    onSelectPreset: (AppColorSource) -> Unit,
    onSelectCustomHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    onAddCustomHexClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(AppColorSource.accentOptions, key = { "preset_${it.name}" }) { source ->
            val isSelected = colorSource == source
            val borderColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                }
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = borderColor,
                            shape = CircleShape,
                        ).tapSoundClickable(
                            onClick = { onSelectPreset(source) },
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                        ).semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center,
            ) {
                ThemeAccentCircleContent(source = source)
            }
        }
        items(savedCustomSeedHexes, key = { "hex_$it" }) { storedHex ->
            val isSelected = customHexSwatchSelected(colorSource, activeCustomSeedHex, storedHex)
            val borderColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                }
            val triplet = runCatching { parseCustomTriplet(storedHex) }.getOrNull()
            val fillColor =
                parseSeedColorHexToColorOrNull(storedHex.split("|").first())
                    ?: MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = borderColor,
                            shape = CircleShape,
                        ).tapSoundCombinedClickable(
                            onClick = { onSelectCustomHex(storedHex) },
                            onLongClick = { onCustomHexLongPress(storedHex) },
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                        ).semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center,
            ) {
                if (triplet != null) {
                    CuratedTripletSwatch(
                        primary = triplet.primary,
                        secondary = triplet.secondary,
                        tertiary = triplet.tertiary,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(fillColor),
                    )
                }
            }
        }
        item(key = "add_custom_seed") {
            val addBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(width = 1.dp, color = addBorder, shape = CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .tapSoundClickable(
                            onClick = onAddCustomHexClick,
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                        ).semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                FilePipeMaterialRoundedSymbol(
                    name =
                        if (customPickerExpanded) {
                            "expand_more"
                        } else {
                            "add"
                        },
                    contentDescription = stringResource(R.string.settings_custom_seed_dialog_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 26.dp,
                    filled = customPickerExpanded,
                )
            }
        }
    }
}

@Composable
private fun ThemeAccentCircleContent(source: AppColorSource) {
    when (source) {
        AppColorSource.MATERIAL_YOU -> {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFF6750A4),
                                        Color(0xFF625B71),
                                        Color(0xFF7D5260),
                                    ),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                FilePipeMaterialRoundedSymbol(
                    name = "palette",
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    size = 22.dp,
                )
            }
        }

        else -> {
            val triplet = source.curatedTriplet()
            if (triplet != null) {
                CuratedTripletSwatch(
                    primary = triplet.primary,
                    secondary = triplet.secondary,
                    tertiary = triplet.tertiary,
                )
            } else {
                val seed = source.seedPrimary() ?: Color.Gray
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(seed),
                )
            }
        }
    }
}

@Composable
private fun CuratedTripletSwatch(
    primary: Color,
    secondary: Color,
    tertiary: Color,
) {
    Column(
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(primary),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(secondary))
            Box(Modifier.weight(1f).fillMaxHeight().background(tertiary))
        }
    }
}

@Composable
fun ThemePaletteStyleRow(
    selected: ThemePaletteStyle,
    enabled: Boolean,
    onSelect: (ThemePaletteStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ThemePaletteStyle.all, key = { it.name }) { style ->
            FilePipeFilterChip(
                selected = selected == style,
                onClick = { if (enabled) onSelect(style) },
                enabled = enabled,
                label = {
                    Text(
                        text = themePaletteStyleLabel(style),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
            )
        }
    }
}

@Composable
fun AppearanceSection(
    themeMode: AppThemeMode,
    colorSource: AppColorSource,
    savedCustomSeedHexes: List<String>,
    activeCustomSeedHex: String,
    themePaletteStyle: ThemePaletteStyle,
    useGradientBackground: Boolean,
    shadingIntensity: Float,
    progressiveBlurEnabled: Boolean,
    onThemeMode: (AppThemeMode) -> Unit,
    onColorSource: (AppColorSource) -> Unit,
    onPaletteStyle: (ThemePaletteStyle) -> Unit,
    onAddCustomSeedHex: (String) -> Unit,
    onSelectCustomSeedHex: (String) -> Unit,
    onPreviewCustomSeedHex: (String) -> Unit,
    onRemoveCustomSeedHex: (String) -> Unit,
    onUseGradientBackground: (Boolean) -> Unit,
    onShadingIntensity: (Float) -> Unit,
    onProgressiveBlurEnabled: (Boolean) -> Unit,
    onBlackThemeEffectClick: () -> Unit,
) {
    var hexPendingRemove by remember { mutableStateOf<String?>(null) }

    val hexToConfirmRemove = hexPendingRemove
    if (hexToConfirmRemove != null) {
        FilePipeConfirmDialog(
            title = stringResource(R.string.settings_custom_seed_remove_title),
            text = stringResource(R.string.settings_custom_seed_remove_message),
            confirmLabel = stringResource(R.string.schedule_remove_short),
            onConfirm = {
                onRemoveCustomSeedHex(hexToConfirmRemove)
                hexPendingRemove = null
            },
            onDismiss = { hexPendingRemove = null },
            destructive = true,
        )
    }
    val blackThemeEffectsDisabled = themeMode == AppThemeMode.BLACK

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.FIRST) {
            AppearanceStudioControls(
                themeMode = themeMode,
                colorSource = colorSource,
                savedCustomSeedHexes = savedCustomSeedHexes,
                activeCustomSeedHex = activeCustomSeedHex,
                themePaletteStyle = themePaletteStyle,
                onThemeMode = onThemeMode,
                onColorSource = onColorSource,
                onPaletteStyle = onPaletteStyle,
                onSelectCustomSeedHex = onSelectCustomSeedHex,
                onCustomHexLongPress = { hexPendingRemove = it },
                onAddCustomSeedHex = onAddCustomSeedHex,
                onPreviewCustomSeedHex = onPreviewCustomSeedHex,
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            val enabled = !blackThemeEffectsDisabled
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.settings_surface_shading),
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                    )
                    Text(
                        text = stringResource(R.string.settings_surface_shading_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (enabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                    )
                }
                ShadingIntensitySlider(
                    intensity = shadingIntensity,
                    enabled = enabled,
                    onDisabledClick = onBlackThemeEffectClick,
                    onValueChange = onShadingIntensity,
                )
            }
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            AppearanceToggleItem(
                title = stringResource(R.string.settings_gradient_background),
                subtitle = stringResource(R.string.settings_gradient_background_desc),
                checked = useGradientBackground && !blackThemeEffectsDisabled,
                enabled = !blackThemeEffectsDisabled,
                onDisabledClick = onBlackThemeEffectClick,
                onCheckedChange = onUseGradientBackground,
            )
        }
        GroupedListItem(position = GroupPosition.LAST) {
            AppearanceToggleItem(
                title = stringResource(R.string.settings_progressive_blur),
                subtitle = stringResource(R.string.settings_progressive_blur_desc),
                checked = progressiveBlurEnabled,
                leadingIconName = "blur_on",
                onCheckedChange = onProgressiveBlurEnabled,
            )
        }
    }
}

@Composable
private fun AppearanceStudioControls(
    themeMode: AppThemeMode,
    colorSource: AppColorSource,
    savedCustomSeedHexes: List<String>,
    activeCustomSeedHex: String,
    themePaletteStyle: ThemePaletteStyle,
    onThemeMode: (AppThemeMode) -> Unit,
    onColorSource: (AppColorSource) -> Unit,
    onPaletteStyle: (ThemePaletteStyle) -> Unit,
    onSelectCustomSeedHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    onAddCustomSeedHex: (String) -> Unit,
    onPreviewCustomSeedHex: (String) -> Unit,
) {
    var customPickerExpanded by rememberSaveable { mutableStateOf(false) }
    var editingTarget by rememberSaveable { mutableStateOf(ColorTarget.PRIMARY) }
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
    LaunchedEffect(customPickerExpanded) {
        if (!customPickerExpanded) {
            editingTarget = ColorTarget.PRIMARY
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ThemeModeSegmentedRow(
            selected = themeMode,
            onSelect = onThemeMode,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_palette),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f),
                )
            }
            ThemePaletteStyleRow(
                selected = themePaletteStyle,
                enabled = colorSource.supportsPaletteStyle,
                onSelect = onPaletteStyle,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ThemeAccentRow(
                colorSource = colorSource,
                activeCustomSeedHex = activeCustomSeedHex,
                savedCustomSeedHexes = savedCustomSeedHexes,
                customPickerExpanded = customPickerExpanded,
                onSelectPreset = { source ->
                    if (customPickerExpanded) {
                        editingTarget = ColorTarget.PRIMARY
                    }
                    onColorSource(source)
                },
                onSelectCustomHex = { hex ->
                    if (customPickerExpanded) {
                        editingTarget = ColorTarget.PRIMARY
                    }
                    onSelectCustomSeedHex(hex)
                },
                onCustomHexLongPress = onCustomHexLongPress,
                onAddCustomHexClick = {
                    customPickerExpanded = !customPickerExpanded
                },
            )
            AnimatedVisibility(
                visible = customPickerExpanded,
                enter = expandVertically(animationSpec = spatialSpec, expandFrom = Alignment.Top),
                exit = shrinkVertically(animationSpec = spatialSpec, shrinkTowards = Alignment.Top),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(0.dp))
                    CustomColorSlider(
                        initialSeedHex =
                            customSliderInitialSeedHex(
                                colorSource = colorSource,
                                activeCustomSeedHex = activeCustomSeedHex,
                                currentPrimary = MaterialTheme.colorScheme.primary,
                            ),
                        editingTarget = editingTarget,
                        onPreviewColor = onPreviewCustomSeedHex,
                        onSaveColor = onAddCustomSeedHex,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
        ThemePreviewPanel(
            colorSource = colorSource,
            isInteractive = colorSource == AppColorSource.CUSTOM,
            selectedTarget = editingTarget,
            onTargetSelect = { target ->
                editingTarget = target
                customPickerExpanded = true
            },
        )
    }
}

@Composable
private fun CustomColorSlider(
    initialSeedHex: String,
    editingTarget: ColorTarget,
    onPreviewColor: (String) -> Unit,
    onSaveColor: (String) -> Unit,
) {
    var currentSeedHex by remember(initialSeedHex) {
        mutableStateOf(normalizeCustomSeedHexOrNull(initialSeedHex) ?: colorHexFromHue(DEFAULT_CUSTOM_HUE))
    }
    val targetHex =
        remember(currentSeedHex, editingTarget) {
            extractTargetHex(currentSeedHex, editingTarget)
        }
    val normalizedTargetHex = normalizeSeedHexOrNull(targetHex) ?: colorHexFromHue(DEFAULT_CUSTOM_HUE)
    var hexEditing by rememberSaveable { mutableStateOf(false) }
    var hexDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(normalizedTargetHex.toHexFieldValue())
    }
    val hexFocusRequester = remember { FocusRequester() }
    var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hexEditorBoundsInRoot by remember { mutableStateOf<Rect?>(null) }

    fun commitHexEditing(): String {
        val draftHex =
            if (hexDraft.text.length == 6) {
                normalizeSeedHexOrNull("#${hexDraft.text}")
            } else {
                null
            }
        val committedTargetHex = draftHex ?: normalizedTargetHex
        hexDraft = committedTargetHex.toHexFieldValue()
        val nextSeedHex = updateTargetHex(currentSeedHex, committedTargetHex, editingTarget)
        currentSeedHex = nextSeedHex
        if (hexEditing && draftHex != null) onPreviewColor(nextSeedHex)
        hexEditing = false
        return nextSeedHex
    }
    LaunchedEffect(normalizedTargetHex) {
        hexDraft = normalizedTargetHex.toHexFieldValue()
    }
    LaunchedEffect(hexEditing) {
        if (hexEditing) hexFocusRequester.requestFocus()
    }
    LaunchedEffect(hexDraft, hexEditing) {
        if (!hexEditing || hexDraft.text.length != 6) return@LaunchedEffect
        delay(HEX_INPUT_DEBOUNCE_MILLIS)
        val normalized = "#${hexDraft.text.uppercase(Locale.US)}"
        if (hueFromHexColor(normalized) != null) {
            val nextSeedHex = updateTargetHex(currentSeedHex, normalized, editingTarget)
            currentSeedHex = nextSeedHex
            onPreviewColor(nextSeedHex)
        }
    }
    val panelShape = MaterialTheme.shapes.extraLargeIncreased
    val sliderPanelColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val saveColorLabel = stringResource(R.string.save)

    Surface(
        color = sliderPanelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = panelShape,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { panelCoordinates = it }
                    .pointerInput(hexEditing, hexDraft, currentSeedHex, editingTarget) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val wasEditingAtDown = hexEditing
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                            if (!wasEditingAtDown || !hexEditing) return@awaitEachGesture
                            val tapInRoot = panelCoordinates?.localToRoot(up.position) ?: return@awaitEachGesture
                            val editorBounds = hexEditorBoundsInRoot
                            if (editorBounds == null || !editorBounds.contains(tapInRoot)) {
                                commitHexEditing()
                            }
                        }
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val rawString =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> stringResource(R.string.settings_custom_seed_slider_title)
                        ColorTarget.SECONDARY -> stringResource(R.string.settings_custom_seed_slider_title_secondary)
                        ColorTarget.TERTIARY -> stringResource(R.string.settings_custom_seed_slider_title_tertiary)
                    }
                val targetWord =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> stringResource(R.string.settings_theme_preview_label_primary).lowercase(Locale.US)
                        ColorTarget.SECONDARY -> stringResource(R.string.settings_theme_preview_label_secondary).lowercase(Locale.US)
                        ColorTarget.TERTIARY -> stringResource(R.string.settings_theme_preview_label_tertiary).lowercase(Locale.US)
                    }
                val parsedTriplet = remember(currentSeedHex) { parseCustomTriplet(currentSeedHex) }
                val targetColor =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> parsedTriplet?.primary ?: MaterialTheme.colorScheme.primary
                        ColorTarget.SECONDARY -> parsedTriplet?.secondary ?: MaterialTheme.colorScheme.secondary
                        ColorTarget.TERTIARY -> parsedTriplet?.tertiary ?: MaterialTheme.colorScheme.tertiary
                    }
                val annotatedTitle =
                    remember(rawString, targetWord, targetColor) {
                        val index = rawString.indexOf(targetWord, ignoreCase = true)
                        buildAnnotatedString {
                            if (index != -1) {
                                append(rawString.substring(0, index))
                                withStyle(SpanStyle(color = targetColor)) {
                                    append(rawString.substring(index, index + targetWord.length))
                                }
                                append(rawString.substring(index + targetWord.length))
                            } else {
                                append(rawString)
                            }
                        }
                    }
                Text(
                    text = annotatedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                EditableHexValue(
                    hex = normalizedTargetHex,
                    editing = hexEditing,
                    draft = hexDraft,
                    focusRequester = hexFocusRequester,
                    onStartEditing = {
                        hexDraft = normalizedTargetHex.toHexFieldValue()
                        hexEditing = true
                    },
                    onDraftChange = { hexDraft = it },
                    onStopEditing = { commitHexEditing() },
                    onBoundsChange = { hexEditorBoundsInRoot = it },
                )
                FilePipeFilledTonalIconButton(
                    onClick = { onSaveColor(commitHexEditing()) },
                    modifier = Modifier.size(40.dp),
                ) {
                    FilePipeMaterialRoundedSymbol(
                        name = "check",
                        contentDescription = saveColorLabel,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 22.dp,
                    )
                }
            }
            HueColorSlider(
                selectedHex = normalizedTargetHex,
                onSelect = { newHex ->
                    val nextSeedHex = updateTargetHex(currentSeedHex, newHex, editingTarget)
                    currentSeedHex = nextSeedHex
                    hexDraft = newHex.toHexFieldValue()
                },
                modifier = Modifier.fillMaxWidth(),
                fallbackHue = DEFAULT_CUSTOM_HUE,
                sliderPanelColor = sliderPanelColor,
                onValueChangeFinished = { newHex ->
                    val nextSeedHex = updateTargetHex(currentSeedHex, newHex, editingTarget)
                    currentSeedHex = nextSeedHex
                    onPreviewColor(nextSeedHex)
                },
            )
        }
    }
}

@Composable
private fun EditableHexValue(
    hex: String,
    editing: Boolean,
    draft: TextFieldValue,
    focusRequester: FocusRequester,
    onStartEditing: () -> Unit,
    onDraftChange: (TextFieldValue) -> Unit,
    onStopEditing: () -> Unit,
    onBoundsChange: (Rect?) -> Unit,
) {
    val view = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    val shape = CircleShape
    val textStyle =
        MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    var hadFocus by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (!editing) onBoundsChange(null)
    }
    if (!editing) {
        Box(
            modifier =
                Modifier
                    .width(HexValueWidth)
                    .height(HexValueHeight)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    ).tapSoundCombinedClickable(onClick = onStartEditing)
                    .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = hex,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        return
    }

    Box(
        modifier =
            Modifier
                .width(HexValueWidth)
                .height(HexValueHeight)
                .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = draft.toPrefixedHexFieldValue(),
            onValueChange = { value ->
                val acceptedValue = value.acceptPrefixedHexInput()
                if (acceptedValue != null) {
                    onDraftChange(acceptedValue)
                } else if (hapticEnabled) {
                    view.performRejectHaptic()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            onStopEditing()
                        }
                    },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onStopEditing() }),
        )
    }
}

private enum class ColorTarget { PRIMARY, SECONDARY, TERTIARY }

private fun extractTargetHex(
    seedHex: String,
    target: ColorTarget,
): String {
    val parts = seedHex.split("|")
    return when (target) {
        ColorTarget.PRIMARY -> parts.getOrNull(0) ?: seedHex
        ColorTarget.SECONDARY -> parts.getOrNull(1) ?: seedHex
        ColorTarget.TERTIARY -> parts.getOrNull(2) ?: seedHex
    }
}

private fun updateTargetHex(
    seedHex: String,
    newHex: String,
    target: ColorTarget,
): String {
    val parts = seedHex.split("|").toMutableList()
    while (parts.size < 3) {
        val primaryHex = parts.getOrNull(0) ?: "#16A34A"
        val primaryColor = parseSeedColorHexToColorOrNull(primaryHex) ?: Color(0xFF16A34A)
        val generatedColors = generateTripletForSeed(primaryColor)
        val defaultColors =
            listOf(
                primaryHex,
                hexFromColor(generatedColors.secondary),
                hexFromColor(generatedColors.tertiary),
            )
        parts.add(defaultColors[parts.size])
    }
    parts[target.ordinal] = newHex

    if (target == ColorTarget.PRIMARY) {
        val newPrimaryColor = parseSeedColorHexToColorOrNull(newHex) ?: Color(0xFF16A34A)
        val generated = generateTripletForSeed(newPrimaryColor)
        parts[1] = hexFromColor(generated.secondary)
        parts[2] = hexFromColor(generated.tertiary)
    }

    return parts.joinToString("|")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShadingIntensitySlider(
    intensity: Float,
    enabled: Boolean,
    onDisabledClick: () -> Unit,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember { mutableFloatStateOf(intensity.roundToShadingStep()) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val interacting = isDragged || isPressed

    LaunchedEffect(intensity, interacting) {
        if (!interacting) {
            sliderValue = intensity.roundToShadingStep()
        }
    }

    Slider(
        value = sliderValue,
        onValueChange = { rawValue ->
            if (enabled) {
                val steppedValue = rawValue.roundToShadingStep()
                if (steppedValue != sliderValue) {
                    sliderValue = steppedValue
                    onValueChange(steppedValue)
                }
            } else {
                onDisabledClick()
            }
        },
        valueRange = 0f..2f,
        steps = 19,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            Label(
                label = {
                    PlainTooltip(
                        modifier =
                            Modifier
                                .sizeIn(
                                    minWidth = ShadingSliderLabelMinWidth,
                                    minHeight = ShadingSliderLabelMinHeight,
                                ).wrapContentWidth(),
                    ) {
                        Text(getShadingLabel(sliderValue))
                    }
                },
                interactionSource = interactionSource,
                isPersistent = interacting,
            ) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    thumbSize = ShadingSliderThumbSize,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private fun getShadingLabel(value: Float): String {
    val percentage = (value * 100f).roundToInt()
    return "$percentage%"
}

private fun Float.roundToShadingStep(): Float =
    (this * 10f)
        .roundToInt()
        .coerceIn(0, 20) / 10f

private const val DEFAULT_CUSTOM_HUE = 270f
private const val HEX_INPUT_DEBOUNCE_MILLIS = 450L
private val HexValueWidth = 84.dp
private val HexValueHeight = 40.dp
private val ShadingSliderLabelMinWidth = 45.dp
private val ShadingSliderLabelMinHeight = 25.dp
private val ShadingSliderThumbSize = DpSize(width = 4.dp, height = 32.dp)

private fun String.dropHexPrefix(): String = removePrefix("#").take(6).uppercase(Locale.US)

private fun String.toHexFieldValue(): TextFieldValue {
    val text = dropHexPrefix()
    return TextFieldValue(text = text, selection = TextRange(text.length))
}

private fun TextFieldValue.toPrefixedHexFieldValue(): TextFieldValue {
    val prefixedSelection =
        TextRange(
            start = (selection.start + 1).coerceIn(1, text.length + 1),
            end = (selection.end + 1).coerceIn(1, text.length + 1),
        )
    return copy(text = "#$text", selection = prefixedSelection)
}

private fun TextFieldValue.acceptPrefixedHexInput(): TextFieldValue? {
    val hasPrefix = text.startsWith("#")
    val hexText = text.removePrefix("#").take(6).uppercase(Locale.US)
    if (text.removePrefix("#").length > 6) return null
    if (hexText.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val prefixOffset = if (hasPrefix) 1 else 0
    return TextFieldValue(
        text = hexText,
        selection =
            TextRange(
                start = (selection.start - prefixOffset).coerceIn(0, hexText.length),
                end = (selection.end - prefixOffset).coerceIn(0, hexText.length),
            ),
    )
}

private fun TextFieldValue.acceptHexInput(): TextFieldValue? {
    if (text.length > 6) return null
    if (text.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val uppercaseText = text.uppercase(Locale.US)
    return copy(
        text = uppercaseText,
        selection =
            TextRange(
                start = selection.start.coerceIn(0, uppercaseText.length),
                end = selection.end.coerceIn(0, uppercaseText.length),
            ),
    )
}

private fun customSliderInitialSeedHex(
    colorSource: AppColorSource,
    activeCustomSeedHex: String,
    currentPrimary: Color,
): String {
    val activeCustomSeed = normalizeCustomSeedHexOrNull(activeCustomSeedHex)
    if (colorSource == AppColorSource.CUSTOM && activeCustomSeed != null) {
        return activeCustomSeed
    }
    if (colorSource == AppColorSource.MATERIAL_YOU) {
        return hexFromColor(currentPrimary)
    }
    return hexFromColor(colorSource.migrated().seedPrimary() ?: Color(0xFF16A34A))
}

private fun hexFromColor(color: Color): String {
    val colorInt =
        AndroidColor.argb(
            255,
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
    return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
}

@Composable
private fun ThemeModeSegmentedRow(
    selected: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
) {
    val colors =
        ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    val labels = themePickerOrder.map { mode -> themeModeLabel(mode) }
    val shapes =
        themePickerOrder.mapIndexed { index, _ ->
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                themePickerOrder.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        }
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        themePickerOrder.forEachIndexed { index, mode ->
            val label = labels[index]
            customItem(
                buttonGroupContent = {
                    FilePipeToggleButton(
                        checked = selected == mode,
                        onCheckedChange = { checked -> if (checked) onSelect(mode) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                        shapes = shapes[index],
                        colors = colors,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                menuContent = { menuState ->
                    FilePipeDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelect(mode)
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun AppearanceToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    leadingIconName: String? = null,
    onDisabledClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundCombinedClickable(onClick = {
                    if (enabled) {
                        onCheckedChange(!checked)
                    } else {
                        onDisabledClick?.invoke()
                    }
                })
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIconName != null) {
            FilePipeMaterialRoundedSymbol(
                name = leadingIconName,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        Box {
            FilePipeSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = if (enabled) onCheckedChange else null,
            )
            if (!enabled && onDisabledClick != null) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clip(MaterialTheme.shapes.extraExtraLarge)
                            .tapSoundCombinedClickable(onClick = { onDisabledClick() }),
                )
            }
        }
    }
}

private val themePickerOrder =
    listOf(
        AppThemeMode.SYSTEM,
        AppThemeMode.LIGHT,
        AppThemeMode.DARK,
        AppThemeMode.BLACK,
    )

@Composable
private fun themeModeLabel(mode: AppThemeMode): String =
    stringResource(
        when (mode) {
            AppThemeMode.SYSTEM -> R.string.theme_system
            AppThemeMode.LIGHT -> R.string.theme_light
            AppThemeMode.DARK -> R.string.theme_dark
            AppThemeMode.BLACK -> R.string.theme_black
        },
    )

@Composable
private fun ThemePreviewPanel(
    colorSource: AppColorSource,
    isInteractive: Boolean,
    selectedTarget: ColorTarget,
    onTargetSelect: (ColorTarget) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val title =
        if (colorSource == AppColorSource.CUSTOM) {
            stringResource(R.string.settings_theme_preview_customize)
        } else {
            stringResource(
                R.string.settings_theme_preview_named,
                colorSourceDisplayName(colorSource),
            )
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AccentContainersStrip(
            scheme = scheme,
            isInteractive = isInteractive,
            selectedTarget = selectedTarget,
            onTargetSelect = onTargetSelect,
        )
        SurfaceLadderStrip(scheme = scheme)
    }
}

@Composable
private fun SurfaceLadderStrip(scheme: ColorScheme) {
    val swatches =
        listOf(
            scheme.surfaceContainerLowest to stringResource(R.string.settings_theme_preview_label_lowest),
            scheme.surface to stringResource(R.string.settings_theme_preview_label_surface),
            scheme.surfaceContainerLow to stringResource(R.string.settings_theme_preview_label_low),
            scheme.surfaceContainer to stringResource(R.string.settings_theme_preview_label_base),
            scheme.surfaceContainerHigh to stringResource(R.string.settings_theme_preview_label_high),
            scheme.surfaceContainerHighest to stringResource(R.string.settings_theme_preview_label_highest),
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(MaterialTheme.shapes.small)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        swatches.forEach { (color, label) ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contrastingTextColor(color),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AccentContainersStrip(
    scheme: ColorScheme,
    isInteractive: Boolean,
    selectedTarget: ColorTarget,
    onTargetSelect: (ColorTarget) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
            label = stringResource(R.string.settings_theme_preview_label_primary),
            isSelected = isInteractive && selectedTarget == ColorTarget.PRIMARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.PRIMARY) },
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
            label = stringResource(R.string.settings_theme_preview_label_secondary),
            isSelected = isInteractive && selectedTarget == ColorTarget.SECONDARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.SECONDARY) },
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.tertiaryContainer,
            onContainer = scheme.onTertiaryContainer,
            label = stringResource(R.string.settings_theme_preview_label_tertiary),
            isSelected = isInteractive && selectedTarget == ColorTarget.TERTIARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.TERTIARY) },
        )
    }
}

@Composable
private fun AccentChip(
    modifier: Modifier,
    container: Color,
    onContainer: Color,
    label: String,
    isSelected: Boolean = false,
    isInteractive: Boolean = false,
    onClick: () -> Unit = {},
) {
    val chipModifier =
        if (isInteractive) {
            modifier.tapSoundClickable(onClick = onClick)
        } else {
            modifier
        }
    Surface(
        modifier =
            chipModifier.then(
                if (isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            ),
        shape = MaterialTheme.shapes.medium,
        color = container,
        tonalElevation = if (isSelected) 4.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.settings_theme_preview_sample),
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun contrastingTextColor(background: Color): Color =
    if (ColorUtils.calculateLuminance(background.toArgb()) > 0.5) {
        Color.Black.copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.9f)
    }

@Composable
private fun colorSourceDisplayName(source: AppColorSource): String =
    stringResource(
        when (source.migrated()) {
            AppColorSource.MATERIAL_YOU -> R.string.theme_material_you
            AppColorSource.DEFAULT -> R.string.theme_color_forest
            AppColorSource.CURATED_EMBER -> R.string.theme_color_ember
            AppColorSource.CURATED_GROVE -> R.string.theme_color_grove
            AppColorSource.CURATED_HONEY -> R.string.theme_color_honey
            AppColorSource.CURATED_OCEAN -> R.string.theme_color_ocean
            AppColorSource.CURATED_IRIS -> R.string.theme_color_iris
            AppColorSource.CURATED_DUSK -> R.string.theme_color_dusk
            AppColorSource.CURATED_BERRY -> R.string.theme_color_berry
            AppColorSource.CUSTOM -> R.string.theme_color_custom
            else -> R.string.theme_color_custom
        },
    )
