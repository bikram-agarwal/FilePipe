package dev.bikram.filepipe.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.AppIconImage
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.ui.theme.reducedMotionEnterTransition
import kotlinx.coroutines.delay

@Composable
fun OnboardingTitleScreen(
    onLetsBegan: () -> Unit,
) {
    var iconVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var bylineVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        iconVisible = true
        delay(140)
        titleVisible = true
        delay(120)
        bylineVisible = true
        delay(100)
        buttonVisible = true
    }

    val scheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val enterSpatialSpec = motionScheme.defaultSpatialSpec<IntOffset>()
    val enterFadeSpec = motionScheme.defaultEffectsSpec<Float>()
    val iconEnter =
        remember(enterSpatialSpec, enterFadeSpec) {
            fadeIn(animationSpec = enterFadeSpec) +
                slideInVertically(animationSpec = enterSpatialSpec) { fullHeight -> fullHeight / 3 }
        }
    val blockEnter =
        remember(enterSpatialSpec, enterFadeSpec) {
            fadeIn(animationSpec = enterFadeSpec) +
                slideInVertically(animationSpec = enterSpatialSpec) { fullHeight -> fullHeight / 2 }
        }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Edge-to-edge like every other screen: no reserved bar strips. Centered content sits in the
        // full window; the bottom actions/byline and the scrollable stack carry the system bar insets
        // so they clear the bars while the background still runs under them.
        val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val useTwoColumns = maxHeight < 480.dp && maxWidth > maxHeight
        val useScrollableStack = maxHeight < 560.dp && !useTwoColumns

        val iconBlock: @Composable () -> Unit = {
            AnimatedVisibility(
                visible = iconVisible,
                enter = reducedMotionEnterTransition(iconEnter),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = if (useTwoColumns) 16.dp else 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIconImage(
                        modifier =
                            Modifier
                                .size(if (useTwoColumns) 96.dp else 120.dp)
                                .clip(RoundedCornerShape(percent = 25)),
                    )
                    Spacer(Modifier.height(if (useTwoColumns) 16.dp else 24.dp))
                    AnimatedVisibility(
                        visible = titleVisible,
                        enter = reducedMotionEnterTransition(blockEnter),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(if (useTwoColumns) 4.dp else 8.dp))
                            Text(
                                text = stringResource(R.string.app_tagline),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        val bylinePill: @Composable () -> Unit = {
            AnimatedVisibility(
                visible = bylineVisible,
                enter = reducedMotionEnterTransition(blockEnter),
            ) {
                Surface(
                    shape = pillShape,
                    color = scheme.surfaceContainerHigh,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.me_600),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_byline),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val beginButton: @Composable () -> Unit = {
            AnimatedVisibility(
                visible = buttonVisible,
                enter = reducedMotionEnterTransition(blockEnter),
            ) {
                FilePipeButton(
                    onClick = onLetsBegan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = pillShape,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 24.dp,
                            vertical = 16.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_lets_begin),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FilePipeMaterialRoundedSymbol(
                        name = "arrow_forward",
                        contentDescription = null,
                        autoMirror = true,
                    )
                }
            }
        }

        if (useTwoColumns) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    iconBlock()
                    Spacer(Modifier.height(16.dp))
                    bylinePill()
                }
                Column(
                    modifier =
                        Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    OnboardingBottomActions {
                        beginButton()
                    }
                }
            }
        } else if (useScrollableStack) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 32.dp,
                            end = 32.dp,
                            top = 24.dp + statusBarInset,
                            bottom = 24.dp + navBarInset,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                iconBlock()
                bylinePill()
                OnboardingBottomActions {
                    beginButton()
                }
            }
        } else {
            Box(modifier = Modifier.align(Alignment.Center)) {
                iconBlock()
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
            ) {
                bylinePill()
            }
            OnboardingBottomActions(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        // Sits a constant 16.dp above the system bar, so the tall 3-button bar and
                        // the short gesture handle both get the same visual gap.
                        .padding(bottom = 16.dp),
            ) {
                beginButton()
            }
        }
    }
}
