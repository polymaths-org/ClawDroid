package com.clawdroid.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.SoftWhite

data class WizardStep(
    val index: Int,
    val title: String,
    val subtitle: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScaffold(
    steps: List<WizardStep>,
    currentStep: Int,
    onBack: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (steps.isEmpty()) 0f else (currentStep + 1).toFloat() / steps.size,
        animationSpec = tween(400),
    )

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = { Text(steps.getOrNull(currentStep)?.title ?: "", color = SoftWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0) onBack() else onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SoftWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlack),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack)
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            StepProgressBar(steps.size, currentStep, progress)

            if (steps.getOrNull(currentStep)?.subtitle?.isNotBlank() == true) {
                Text(
                    text = steps[currentStep].subtitle,
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { width -> direction * width } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { width -> -direction * width } + fadeOut(tween(300)))
                },
                label = "step_transition",
            ) { _ ->
                content()
            }
        }
    }
}

@Composable
private fun StepProgressBar(
    totalSteps: Int,
    currentStep: Int,
    progress: Float,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps@ for (i in 0 until totalSteps) {
                val isCompleted = i < currentStep
                val isCurrent = i == currentStep
                val stepColor = when {
                    isCompleted -> NeonCyan
                    isCurrent -> EmberOrange
                    else -> GlassBorderDim
                }
                val stepSize = if (isCurrent) 28.dp else 24.dp

                Box(
                    modifier = Modifier
                        .size(stepSize)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted || isCurrent) stepColor.copy(alpha = 0.2f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(stepColor),
                        )
                    }
                }

                if (i < totalSteps - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(GlassBorderDim),
                    ) {
                        if (isCompleted) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(NeonCyan),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WizardActionRow(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String = "Next",
    canGoBack: Boolean = true,
    canGoNext: Boolean = true,
    isLastStep: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (canGoBack) {
            GlassButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back", color = SoftWhite, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (canGoNext) {
            GlassButton(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (isLastStep) "Complete Setup" else nextLabel,
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
