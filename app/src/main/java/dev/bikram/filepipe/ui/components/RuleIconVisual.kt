package dev.bikram.filepipe.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.bikram.filepipe.domain.model.RuleIcon

/** Curated emoji shortcuts for rule icons (Unicode only, no assets). Twelve presets; custom slot + apply in the rule sheet. */
val RuleIconEmojiPresets: List<String> = listOf(
    "\uD83D\uDCC1", "\uD83D\uDDBC\uFE0F", "\uD83D\uDCF8", "\uD83C\uDFAC", "\uD83C\uDFB5", "\uD83D\uDCBF", "\uD83C\uDFA7",
    "\uD83D\uDCE5", "\uD83D\uDCC4", "\uD83D\uDCDA", "\uD83C\uDFAE", "\u2B50"
)

@Composable
fun RuleIconOrEmoji(
    iconEmoji: String?,
    icon: RuleIcon,
    modifier: Modifier = Modifier,
    vectorSize: Dp,
    emojiFontSize: TextUnit,
    tint: Color,
    contentDescription: String? = null
) {
    val emoji = iconEmoji?.trim()?.takeIf { it.isNotEmpty() }
    if (emoji != null) {
        Text(
            text = emoji,
            fontSize = emojiFontSize,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = icon.toImageVector(),
            contentDescription = contentDescription,
            modifier = modifier.size(vectorSize),
            tint = tint
        )
    }
}

fun RuleIcon.toImageVector(): ImageVector = when (this) {
    RuleIcon.DEFAULT -> Icons.Filled.FolderSpecial
    RuleIcon.IMAGE -> Icons.Filled.Image
    RuleIcon.SCREENSHOT -> Icons.Filled.Screenshot
    RuleIcon.VIDEO -> Icons.Filled.Movie
    RuleIcon.MUSIC -> Icons.Filled.MusicNote
    RuleIcon.DOWNLOAD -> Icons.Filled.Download
    RuleIcon.DOCUMENT -> Icons.AutoMirrored.Filled.TextSnippet
    RuleIcon.INSTALLABLE -> Icons.Filled.Android
}
