package dev.bikram.filepipe.ui.theme

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.ui.feedback.LocalHapticEnabled
import dev.bikram.filepipe.ui.feedback.LocalTapSound

private val LightColors =
    lightColorScheme(
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
        surfaceContainerHighest = Color(0xFFFFFFFF),
    )

private val DarkColors =
    darkColorScheme(
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
        surfaceContainerHighest = Color(0xFF373E47),
    )

private val BlackOledColors =
    darkColorScheme(
        primary = Blue80,
        secondary = BlueGrey80,
        tertiary = Teal80,
        background = Color.Black,
        surface = Color(0xFF050505),
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF2E2E2E),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF111111),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF303030),
    )

/** Keep BLACK mode OLED-dark while preserving enough surface separation for cards and sheets. */
private fun ColorScheme.toOled(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color(0xFF050505),
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF111111),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF303030),
    )

/** Blend every surface role toward the active accent so panels visibly pick up the theme hue. */
private fun ColorScheme.tintSurfacesTowardPrimary(
    darkTheme: Boolean,
    intensityFactor: Float,
): ColorScheme {
    if (intensityFactor <= 0.0f) return this
    val accentArgb =
        ColorUtils.blendARGB(
            primary.toArgb(),
            primaryContainer.toArgb(),
            if (darkTheme) 0.4f else 0.3f,
        )
    val blendAmount = (if (darkTheme) 0.24f else 0.15f) * intensityFactor

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
        surfaceContainerHighest = tinted(surfaceContainerHighest),
    )
}

/** Pull outline roles toward on-surface so outlined chrome stays legible. */
private fun ColorScheme.boostOutlineForVisibility(darkTheme: Boolean): ColorScheme {
    val targetArgb = onSurface.toArgb()
    val outlineBlend = if (darkTheme) 0.32f else 0.28f
    val outlineVariantBlend = if (darkTheme) 0.20f else 0.16f
    return copy(
        outline = Color(ColorUtils.blendARGB(outline.toArgb(), targetArgb, outlineBlend)),
        outlineVariant =
            Color(
                ColorUtils.blendARGB(outlineVariant.toArgb(), targetArgb, outlineVariantBlend),
            ),
    )
}

/** Pull accent containers toward their accent hues so selected pills and tonal buttons pop. */
private fun ColorScheme.boostContainersForSeedThemes(darkTheme: Boolean): ColorScheme {
    val primaryBlend = if (darkTheme) 0.30f else 0.24f
    val secondaryBlend = if (darkTheme) 0.26f else 0.20f
    val tertiaryBlend = if (darkTheme) 0.28f else 0.22f
    return copy(
        primaryContainer = Color(ColorUtils.blendARGB(primaryContainer.toArgb(), primary.toArgb(), primaryBlend)),
        secondaryContainer = Color(ColorUtils.blendARGB(secondaryContainer.toArgb(), secondary.toArgb(), secondaryBlend)),
        tertiaryContainer = Color(ColorUtils.blendARGB(tertiaryContainer.toArgb(), tertiary.toArgb(), tertiaryBlend)),
    )
}

private fun colorsTooSimilar(
    first: Color,
    second: Color,
    maxLuminanceDelta: Float = 0.07f,
): Boolean {
    val luminanceDelta =
        kotlin.math.abs(
            ColorUtils.calculateLuminance(first.toArgb()) - ColorUtils.calculateLuminance(second.toArgb()),
        )
    return luminanceDelta < maxLuminanceDelta
}

/**
 * Material You often maps [secondaryContainer] near [surfaceContainerHighest], so filled-tonal
 * controls (e.g. Run now on rule cards) blend into elevated card surfaces.
 */
private fun ColorScheme.separateMaterialYouSecondaryContainerWhenNeeded(darkTheme: Boolean): ColorScheme {
    if (!colorsTooSimilar(secondaryContainer, surfaceContainerHighest)) {
        return this
    }
    val accentTargetArgb =
        ColorUtils.blendARGB(
            primaryContainer.toArgb(),
            primary.toArgb(),
            if (darkTheme) 0.38f else 0.28f,
        )
    val blendAmount = if (darkTheme) 0.50f else 0.40f
    return copy(
        secondaryContainer =
            Color(
                ColorUtils.blendARGB(secondaryContainer.toArgb(), accentTargetArgb, blendAmount),
            ),
        onSecondaryContainer =
            Color(
                ColorUtils.blendARGB(
                    onSecondaryContainer.toArgb(),
                    onPrimaryContainer.toArgb(),
                    if (darkTheme) 0.35f else 0.30f,
                ),
            ),
    )
}

/** Subtle theme hue on the flat page backdrop when the gradient is off. */
private fun pageBackgroundWithThemeHint(
    background: Color,
    primary: Color,
    primaryContainer: Color,
    darkTheme: Boolean,
): Color {
    val accentArgb =
        ColorUtils.blendARGB(
            primary.toArgb(),
            primaryContainer.toArgb(),
            if (darkTheme) 0.4f else 0.3f,
        )
    val blendAmount = if (darkTheme) 0.08f else 0.06f
    return Color(ColorUtils.blendARGB(background.toArgb(), accentArgb, blendAmount))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilePipeTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorSource: AppColorSource = AppColorSource.DEFAULT,
    savedCustomSeedHexes: List<String> = emptyList(),
    themePaletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    hapticFeedbackEnabled: Boolean = true,
    shadingIntensity: Float = 0.0f,
    activeCustomSeedHex: String = "",
    useGradientBackground: Boolean = true,
    progressiveBlurEnabled: Boolean = true,
    paintBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val reducedMotion = rememberSystemReducedMotionEnabled(context)

    val darkTheme =
        when (themeMode) {
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
            AppThemeMode.BLACK -> true
            AppThemeMode.SYSTEM -> systemDark
        }

    val useDynamic = colorSource == AppColorSource.MATERIAL_YOU
    val black = themeMode == AppThemeMode.BLACK
    val effectiveUseGradientBackground = useGradientBackground && !black

    /** Non-wallpaper themes use either curated triplets or a custom seed ramp. */
    val staticTriplet =
        if (useDynamic) {
            null
        } else if (colorSource == AppColorSource.CUSTOM) {
            parseCustomTriplet(activeCustomSeedHex)
        } else {
            colorSource.curatedTriplet()
        }
    val staticSeedColor =
        if (useDynamic || staticTriplet != null) {
            null
        } else {
            colorSource.seedPrimary() ?: Blue40
        }

    val baseColorScheme =
        when {
            useDynamic && darkTheme -> {
                dynamicDarkColorScheme(context)
            }

            useDynamic && !darkTheme -> {
                dynamicLightColorScheme(context)
            }

            staticTriplet != null -> {
                val tripletOverrides =
                    staticTriplet.takeIf { themePaletteStyle == ThemePaletteStyle.TONAL_SPOT }
                rememberDynamicColorScheme(
                    seedColor = staticTriplet.primary,
                    isDark = darkTheme,
                    primary = tripletOverrides?.primary,
                    secondary = tripletOverrides?.secondary,
                    tertiary = tripletOverrides?.tertiary,
                    style = themePaletteStyle.toLib(),
                    isAmoled = black,
                )
            }

            staticSeedColor != null -> {
                rememberDynamicColorScheme(
                    seedColor = staticSeedColor,
                    isDark = darkTheme,
                    style = themePaletteStyle.toLib(),
                    isAmoled = black,
                )
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }
    val oledAdjusted = if (black) baseColorScheme.toOled() else baseColorScheme
    val colorScheme =
        (
            if (!black) {
                oledAdjusted.tintSurfacesTowardPrimary(
                    darkTheme = darkTheme,
                    intensityFactor = shadingIntensity,
                )
            } else {
                oledAdjusted
            }
        ).let { scheme ->
            if (useDynamic) {
                scheme.separateMaterialYouSecondaryContainerWhenNeeded(darkTheme = darkTheme)
            } else {
                scheme
                    .boostOutlineForVisibility(darkTheme = darkTheme)
                    .boostContainersForSeedThemes(darkTheme = darkTheme)
            }
        }.let { scheme ->
            if (effectiveUseGradientBackground || black) {
                scheme
            } else {
                scheme.copy(
                    background =
                        pageBackgroundWithThemeHint(
                            background = scheme.background,
                            primary = scheme.primary,
                            primaryContainer = scheme.primaryContainer,
                            darkTheme = darkTheme,
                        ),
                )
            }
        }

    val view = LocalView.current
    SideEffect {
        view.isSoundEffectsEnabled = true
    }
    SideEffect {
        var context: Context? = view.context
        var hostingActivity: Activity? = null
        while (context != null) {
            if (context is Activity) {
                hostingActivity = context
                break
            }
            context = (context as? ContextWrapper)?.baseContext
        }
        val window = hostingActivity?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val noopSound = remember { {} }
    val playTapSound = noopSound
    val wallpaperTint = rememberWallpaperTintColor(context, enabled = effectiveUseGradientBackground)
    val gradientTop =
        wallpaperTint?.let { tint ->
            blendColors(oledAdjusted.primaryContainer, tint, 0.28f)
        } ?: oledAdjusted.primaryContainer
    val gradientBackgroundColors =
        GradientBackgroundColors(
            pageBackground = colorScheme.background,
            gradientBase = oledAdjusted.surface,
            gradientTop = gradientTop,
        )
    val themeState =
        FilePipeThemeState(
            themeMode = themeMode,
            colorSource = colorSource,
            savedCustomSeedHexes = savedCustomSeedHexes,
            activeCustomSeedHex = activeCustomSeedHex,
            themePaletteStyle = themePaletteStyle,
            useGradientBackground = effectiveUseGradientBackground,
            shadingIntensity = shadingIntensity,
            progressiveBlurEnabled = progressiveBlurEnabled,
        )

    CompositionLocalProvider(
        LocalIsDark provides darkTheme,
        LocalUseGradientBackground provides effectiveUseGradientBackground,
        LocalUseEnhancedShading provides (shadingIntensity > 0.0f),
        LocalShadingIntensity provides shadingIntensity,
        LocalHeroOnCards provides false,
        LocalBlurBars provides progressiveBlurEnabled,
        LocalFilePipeThemeState provides themeState,
        LocalGradientBackgroundColors provides gradientBackgroundColors,
        LocalProgressiveBlurEnabled provides progressiveBlurEnabled,
        LocalReducedMotion provides reducedMotion,
        LocalTapSound provides playTapSound,
        LocalHapticEnabled provides hapticFeedbackEnabled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = AppTypography,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (paintBackground) {
                    GradientBackground(
                        colors = gradientBackgroundColors,
                        useGradient = effectiveUseGradientBackground,
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun GradientBackground(
    colors: GradientBackgroundColors,
    useGradient: Boolean,
) {
    if (useGradient) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.gradientBase)
                .background(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0f to colors.gradientTop.copy(alpha = 0.48f),
                                0.55f to colors.gradientBase.copy(alpha = 0f),
                            ),
                    ),
                ),
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.pageBackground),
        )
    }
}

private fun blendColors(
    base: Color,
    tint: Color,
    tintWeight: Float,
): Color =
    Color(
        ColorUtils.blendARGB(
            base.toArgb(),
            tint.toArgb(),
            tintWeight,
        ),
    )

@Composable
private fun rememberWallpaperTintColor(
    context: Context,
    enabled: Boolean,
): Color? {
    if (!enabled) {
        return null
    }
    val appContext = context.applicationContext
    val wallpaperManager = remember(appContext) { WallpaperManager.getInstance(appContext) }

    fun extractTint(colors: WallpaperColors?): Color? = colors?.primaryColor?.toArgb()?.let(::Color)

    fun readWallpaperTint(): Color? =
        runCatching {
            extractTint(wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM))
        }.getOrNull()

    var tint by remember(appContext, enabled) { mutableStateOf(readWallpaperTint()) }
    DisposableEffect(appContext, enabled) {
        if (!enabled) {
            tint = null
            onDispose { }
        } else {
            val listener =
                WallpaperManager.OnColorsChangedListener { colors, which ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                        tint = extractTint(colors)
                    }
                }
            wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            onDispose {
                wallpaperManager.removeOnColorsChangedListener(listener)
            }
        }
    }
    return tint
}

private fun ThemePaletteStyle.toLib(): PaletteStyle =
    when (this) {
        ThemePaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
        ThemePaletteStyle.NEUTRAL -> PaletteStyle.Neutral
        ThemePaletteStyle.VIBRANT -> PaletteStyle.Vibrant
        ThemePaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
        ThemePaletteStyle.RAINBOW -> PaletteStyle.Rainbow
        ThemePaletteStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
        ThemePaletteStyle.MONOCHROME -> PaletteStyle.Monochrome
        ThemePaletteStyle.FIDELITY -> PaletteStyle.Fidelity
        ThemePaletteStyle.CONTENT -> PaletteStyle.Content
    }

@Composable
private fun rememberSystemReducedMotionEnabled(context: Context): Boolean {
    val contentResolver = context.contentResolver

    fun readReducedMotion(): Boolean =
        Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

    var reducedMotion by remember(context) { mutableStateOf(readReducedMotion()) }
    DisposableEffect(contentResolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reducedMotion = readReducedMotion()
                }
            }
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    return reducedMotion
}

/**
 * [TopAppBarDefaults.topAppBarColors] with a transparent bar body and explicit on-surface chrome colors.
 * Needed over the root gradient: some expressive theme defaults leave titles/icons dark while the scrim is dark.
 */
@Composable
fun gradientOverlayTopAppBarColors() =
    TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
    )
