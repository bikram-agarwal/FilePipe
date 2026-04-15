package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun UpdateAvailablePromoCard(
    modifier: Modifier = Modifier,
    onOpenSettingsClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val cornerShape = RoundedCornerShape(28.dp)
    OutlinedCard(
        modifier = modifier,
        shape = cornerShape,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = scheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rules_update_promo_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.rules_update_promo_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onOpenSettingsClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.rules_update_promo_action))
            }
        }
    }
}

/**
 * Horizontal swipe (either direction) past a threshold dismisses the promo (calls [onDismiss]).
 */
@Composable
fun SwipeDismissableUpdatePromoBanner(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onOpenSettingsClick: () -> Unit
) {
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 96.dp.toPx() } }
    val dragAccumulatedPx = remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
            .offset { IntOffset(dragAccumulatedPx.floatValue.roundToInt(), 0) }
            .pointerInput(dismissThresholdPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragDelta ->
                        dragAccumulatedPx.floatValue += dragDelta
                    },
                    onDragEnd = {
                        if (dragAccumulatedPx.floatValue.absoluteValue >= dismissThresholdPx) {
                            onDismiss()
                        }
                        dragAccumulatedPx.floatValue = 0f
                    },
                    onDragCancel = {
                        dragAccumulatedPx.floatValue = 0f
                    }
                )
            }
    ) {
        UpdateAvailablePromoCard(
            modifier = Modifier.fillMaxWidth(),
            onOpenSettingsClick = onOpenSettingsClick
        )
    }
}
