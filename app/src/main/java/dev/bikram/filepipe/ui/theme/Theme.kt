package dev.bikram.filepipe.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import android.view.SoundEffectConstants
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.feedback.LocalTapSound

private val LightColors = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Teal40,
    background = Color(0xFFE2E8F0),
    surface = Color(0xFFF8FAFC),
    surfaceDim = Color(0xFFE2E8F0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFEEF2F7),
    surfaceContainerLow = Color(0xFFF3F6FA),
    surfaceContainer = Color(0xFFF6F8FC),
    surfaceContainerHigh = Color(0xFFFCFDFF),
    surfaceContainerHighest = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Teal80,
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceDim = Color(0xFF0D1117),
    surfaceBright = Color(0xFF2D333B),
    surfaceContainerLowest = Color(0xFF0D1117),
    surfaceContainerLow = Color(0xFF1C2128),
    surfaceContainer = Color(0xFF22272E),
    surfaceContainerHigh = Color(0xFF2D333B),
    surfaceContainerHighest = Color(0xFF373E47)
)

private val OledSurfaceHighest = Color(0xFF343434)

private val BlackOledColors = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Teal80,
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2E2E2E),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF222222),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = OledSurfaceHighest
)

/** Darkens background and lightens card surfaces so lists read more clearly. */
private fun ColorScheme.increaseBackgroundCardContrast(): ColorScheme {
    val backgroundArgb = background.toArgb()
    val darkUi = ColorUtils.calculateLuminance(backgroundArgb) < 0.35
    return if (darkUi) {
        copy(
            background = Color(ColorUtils.blendARGB(backgroundArgb, AndroidColor.BLACK, 0.06f)),
            surfaceContainerLow = Color(ColorUtils.blendARGB(surfaceContainerLow.toArgb(), AndroidColor.WHITE, 0.07f)),
            surfaceContainer = Color(ColorUtils.blendARGB(surfaceContainer.toArgb(), AndroidColor.WHITE, 0.09f)),
            surfaceContainerHigh = Color(ColorUtils.blendARGB(surfaceContainerHigh.toArgb(), AndroidColor.WHITE, 0.14f)),
            surfaceBright = Color(ColorUtils.blendARGB(surfaceBright.toArgb(), AndroidColor.WHITE, 0.12f))
        )
    } else {
        copy(
            background = Color(ColorUtils.blendARGB(backgroundArgb, AndroidColor.BLACK, 0.05f)),
            surfaceContainerLow = Color(ColorUtils.blendARGB(surfaceContainerLow.toArgb(), AndroidColor.WHITE, 0.1f)),
            surfaceContainer = Color(ColorUtils.blendARGB(surfaceContainer.toArgb(), AndroidColor.WHITE, 0.12f)),
            surfaceContainerHigh = Color(ColorUtils.blendARGB(surfaceContainerHigh.toArgb(), AndroidColor.WHITE, 0.16f)),
            surfaceBright = Color(ColorUtils.blendARGB(surfaceBright.toArgb(), AndroidColor.WHITE, 0.08f))
        )
    }
}

/**
 * Subtle primary tint on container roles only (legacy). Used with gradient background so cards stay
 * neutral against a saturated scrim and remain easy to tell apart from the gradient.
 */
private fun ColorScheme.boostSurfaceContainersTowardPrimaryForGradient(darkTheme: Boolean): ColorScheme {
    val accentArgb = primary.toArgb()
    val blendAmount = if (darkTheme) 0.2f else 0.12f
    fun tinted(role: Color) = Color(ColorUtils.blendARGB(role.toArgb(), accentArgb, blendAmount))
    return copy(
        surfaceDim = tinted(surfaceDim),
        surfaceBright = tinted(surfaceBright),
        surfaceContainerLowest = tinted(surfaceContainerLowest),
        surfaceContainerLow = tinted(surfaceContainerLow),
        surfaceContainer = tinted(surfaceContainer),
        surfaceContainerHigh = tinted(surfaceContainerHigh),
        surfaceContainerHighest = tinted(surfaceContainerHighest)
    )
}

/**
 * Stronger tint for solid page backgrounds so list cards pick up visible hue (see [ElevatedCardTokens]).
 */
private fun ColorScheme.boostSurfaceContainersTowardPrimaryForSolidBackground(darkTheme: Boolean): ColorScheme {
    val accentArgb = ColorUtils.blendARGB(
        primary.toArgb(),
        primaryContainer.toArgb(),
        if (darkTheme) 0.4f else 0.3f
    )
    val blendAmount = if (darkTheme) 0.42f else 0.26f
    fun tinted(role: Color) = Color(ColorUtils.blendARGB(role.toArgb(), accentArgb, blendAmount))
    return copy(
        surface = tinted(surface),
        surfaceVariant = tinted(surfaceVariant),
        surfaceDim = tinted(surfaceDim),
        surfaceBright = tinted(surfaceBright),
        surfaceContainerLowest = tinted(surfaceContainerLowest),
        surfaceContainerLow = tinted(surfaceContainerLow),
        surfaceContainer = tinted(surfaceContainer),
        surfaceContainerHigh = tinted(surfaceContainerHigh),
        surfaceContainerHighest = tinted(surfaceContainerHighest)
    )
}

/**
 * Seed-generated schemes can leave outline roles too neutral once surfaces are tinted toward primary.
 * Nudge borders slightly toward the active accent while also pulling them closer to on-surface contrast.
 */
private fun ColorScheme.boostOutlineRolesForSeedThemes(darkTheme: Boolean): ColorScheme {
    val targetOutline = Color(
        ColorUtils.blendARGB(
            onSurface.toArgb(),
            primary.toArgb(),
            if (darkTheme) 0.18f else 0.12f
        )
    )
    val targetOutlineVariant = Color(
        ColorUtils.blendARGB(
            onSurfaceVariant.toArgb(),
            primary.toArgb(),
            if (darkTheme) 0.12f else 0.08f
        )
    )
    return copy(
        outline = Color(
            ColorUtils.blendARGB(
                outline.toArgb(),
                targetOutline.toArgb(),
                if (darkTheme) 0.4f else 0.3f
            )
        ),
        outlineVariant = Color(
            ColorUtils.blendARGB(
                outlineVariant.toArgb(),
                targetOutlineVariant.toArgb(),
                if (darkTheme) 0.32f else 0.24f
            )
        )
    )
}

/**
 * Seed-generated [primaryContainer] can read hotter than the equivalent Material You badge tone.
 * Nudge it toward elevated surfaces so compact rule icons and expanded extension chips keep the
 * softer badge look without switching to a different semantic role.
 */
private fun ColorScheme.softenPrimaryContainerForSeedThemes(darkTheme: Boolean): ColorScheme = copy(
    primaryContainer = Color(
        ColorUtils.blendARGB(
            primaryContainer.toArgb(),
            surfaceContainerHighest.toArgb(),
            if (darkTheme) 0.34f else 0.42f
        )
    )
)

/** Keeps true-black OLED surfaces while preserving dynamic (Material You) accent colors. */
private fun oledSurfacesFrom(dynamicScheme: ColorScheme): ColorScheme = dynamicScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = BlackSurfaceHigh,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF222222),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = OledSurfaceHighest
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilePipeTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorSource: AppColorSource = AppColorSource.DEFAULT,
    themePaletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    hapticFeedbackEnabled: Boolean = true,
    useGradientBackground: Boolean = true,
    activeCustomSeedHex: String = "",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.BLACK -> true
        AppThemeMode.SYSTEM -> systemDark
    }

    val useDynamic = colorSource == AppColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** When not using wallpaper colors, all accents (including DEFAULT and CUSTOM) share the seed-based ramp. */
    val staticSeedColor = if (useDynamic) {
        null
    } else {
        when (colorSource) {
            AppColorSource.CUSTOM -> parseSeedColorHexToColorOrNull(activeCustomSeedHex) ?: Blue40
            else -> colorSource.seedPrimary() ?: Blue40
        }
    }
    val seedLightScheme = remember(staticSeedColor, themePaletteStyle) {
        staticSeedColor?.let { colorSchemeFromSeed(it, themePaletteStyle, darkTheme = false) }
    }
    val seedDarkScheme = remember(staticSeedColor, themePaletteStyle) {
        staticSeedColor?.let { colorSchemeFromSeed(it, themePaletteStyle, darkTheme = true) }
    }

    val baseColorScheme = when {
        themeMode == AppThemeMode.BLACK && useDynamic ->
            oledSurfacesFrom(dynamicDarkColorScheme(context))
        themeMode == AppThemeMode.BLACK && seedDarkScheme != null ->
            oledSurfacesFrom(seedDarkScheme)
        themeMode == AppThemeMode.BLACK -> BlackOledColors
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme && seedDarkScheme != null -> seedDarkScheme
        !darkTheme && seedLightScheme != null -> seedLightScheme
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = baseColorScheme
        .let { base ->
            if (useDynamic || staticSeedColor != null) base
            else base.increaseBackgroundCardContrast()
        }
        .let { scheme ->
            if (useGradientBackground) {
                scheme.boostSurfaceContainersTowardPrimaryForGradient(darkTheme = darkTheme)
            } else {
                scheme.boostSurfaceContainersTowardPrimaryForSolidBackground(darkTheme = darkTheme)
            }
        }
        .let { scheme ->
            if (staticSeedColor != null) {
                scheme
                    .softenPrimaryContainerForSeedThemes(darkTheme = darkTheme)
                    .boostOutlineRolesForSeedThemes(darkTheme = darkTheme)
            } else {
                scheme
            }
        }

    val view = LocalView.current
    SideEffect {
        view.isSoundEffectsEnabled = true
    }
    val realTapSound = remember(view) {
        val lastTapTimeMs = longArrayOf(0L)
        val minTapSoundSpacingMs = 85L
        {
            val now = SystemClock.uptimeMillis()
            if (now - lastTapTimeMs[0] >= minTapSoundSpacingMs) {
                lastTapTimeMs[0] = now
                if (view.isShown) {
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                }
            }
        }
    }
    val noopSound = remember { {} }
    val playTapSound = if (hapticFeedbackEnabled) realTapSound else noopSound

    CompositionLocalProvider(
        LocalTapSound provides playTapSound,
        LocalHapticEnabled provides hapticFeedbackEnabled
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
