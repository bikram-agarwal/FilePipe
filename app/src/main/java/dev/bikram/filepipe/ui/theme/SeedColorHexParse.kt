package dev.bikram.filepipe.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/**
 * Parses user-entered hex into [Color]. Optional `#`; supports 3-digit RGB, 6-digit RRGGBB, 8-digit AARRGGBB.
 * Returns null if empty or invalid.
 */
fun parseSeedColorHexToColorOrNull(raw: String): Color? {
    val compact = raw.trim().removePrefix("#").uppercase()
    if (compact.isEmpty() || !compact.all { it in '0'..'9' || it in 'A'..'F' }) {
        return null
    }
    val expanded = when (compact.length) {
        3 -> compact.map { char -> "$char$char" }.joinToString("")
        6, 8 -> compact
        else -> return null
    }
    val parsedLong = expanded.toLongOrNull(16) ?: return null
    val argb = if (expanded.length == 6) {
        (0xFF000000L or parsedLong).toInt()
    } else {
        parsedLong.toInt()
    }
    return Color(argb)
}

/** Canonical `#RRGGBB` for storage and deduplication; null if [raw] does not parse. */
fun normalizeCustomSeedHexOrNull(raw: String): String? {
    val color = parseSeedColorHexToColorOrNull(raw) ?: return null
    val rgb = color.toArgb() and 0xFFFFFF
    return String.format(Locale.US, "#%06X", rgb)
}
