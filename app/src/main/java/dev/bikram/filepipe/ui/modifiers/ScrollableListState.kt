package dev.bikram.filepipe.ui.modifiers

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberContentOverflowScrollEnabled(
    listState: LazyListState,
    additionalScrollEnabled: Boolean = false,
    ignoredBottomPadding: Dp = 0.dp,
): Boolean {
    val density = LocalDensity.current
    val ignoredBottomPaddingPx = with(density) { ignoredBottomPadding.roundToPx() }
    val scrollEnabled by remember(listState, additionalScrollEnabled, ignoredBottomPaddingPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            when {
                additionalScrollEnabled -> {
                    true
                }

                layoutInfo.totalItemsCount == 0 || visibleItems.isEmpty() -> {
                    false
                }

                visibleItems.size < layoutInfo.totalItemsCount -> {
                    true
                }

                else -> {
                    val firstItemTop = visibleItems.minOf { item -> item.offset }
                    val lastItemBottom = visibleItems.maxOf { item -> item.offset + item.size }
                    val contentHeight = lastItemBottom - firstItemTop
                    val effectiveBottomPadding =
                        (layoutInfo.afterContentPadding - ignoredBottomPaddingPx).coerceAtLeast(0)
                    val viewportHeight =
                        layoutInfo.viewportSize.height -
                            layoutInfo.beforeContentPadding -
                            effectiveBottomPadding
                    contentHeight > viewportHeight.coerceAtLeast(0)
                }
            }
        }
    }
    return scrollEnabled
}
