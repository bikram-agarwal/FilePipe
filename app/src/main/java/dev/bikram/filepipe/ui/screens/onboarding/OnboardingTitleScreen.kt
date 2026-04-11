package dev.bikram.filepipe.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.components.AppIconImage

@Composable
fun OnboardingTitleScreen(
    onLetsBegan: () -> Unit
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
    val iconEnter = remember {
        fadeIn(tween(durationMillis = 320)) +
            slideInVertically(tween(durationMillis = 320)) { fullHeight -> fullHeight / 3 }
    }
    val blockEnter = remember {
        fadeIn(tween(durationMillis = 280)) +
            slideInVertically(tween(durationMillis = 280)) { fullHeight -> fullHeight / 2 }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to scheme.primaryContainer.copy(alpha = 0.45f),
                        0.55f to scheme.surface.copy(alpha = 0f)
                    )
                )
            )
            .systemBarsPadding()
    ) {
        AnimatedVisibility(
            visible = iconVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = iconEnter
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconImage(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(percent = 25))
                )
                Spacer(Modifier.height(24.dp))
                AnimatedVisibility(
                    visible = titleVisible,
                    enter = blockEnter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = bylineVisible,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            enter = blockEnter
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = scheme.surfaceContainerHigh
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.me_600),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_byline),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = buttonVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
            enter = blockEnter
        ) {
            Button(
                onClick = onLetsBegan,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 24.dp,
                    vertical = 16.dp
                )
            ) {
                Text(
                    text = stringResource(R.string.onboarding_lets_begin),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }
        }
    }
}
