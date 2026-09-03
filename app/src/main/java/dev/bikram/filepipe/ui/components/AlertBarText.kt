package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AlertBarText(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    contentScale: Float = 1f,
) {
    val titleStyle = MaterialTheme.typography.titleSmall
    val bodyStyle = MaterialTheme.typography.bodyMedium
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp * contentScale),
    ) {
        // Single line each, never wrapped: the bar spans the full chrome strip, which is sized to
        // fit these two lines, so wrapping would mean the strip geometry is wrong rather than the
        // text being too long.
        Text(
            text = title,
            style = titleStyle.copy(fontSize = titleStyle.fontSize * contentScale),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (body != null) {
            Text(
                text = body,
                style = bodyStyle.copy(fontSize = bodyStyle.fontSize * contentScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
