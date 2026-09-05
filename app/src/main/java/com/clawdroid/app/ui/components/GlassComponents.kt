package com.clawdroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.ui.theme.CardDark
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorder
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillMedium
import com.clawdroid.app.ui.theme.LavaGlow
import com.clawdroid.app.ui.theme.MoltenYellow
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite

// ── Blue gradient brush for premium CTA buttons ────────────────────────
val BlueGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(Color(0xFF0072FF), Color(0xFF00C6FF)),
)

// ── GlassCard ──────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    cornerRadius: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val isLiquid = AppConfigManager.appTheme.startsWith("liquid_glass")
    val borderColor = if (glowColor != Color.Transparent) {
        glowColor.copy(alpha = if (isLiquid) 0.55f else 0.4f)
    } else {
        if (isLiquid) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f) else GlassBorderDim
    }
    val fillBrush = if (isLiquid) {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.86f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
            ),
        )
    } else {
        Brush.verticalGradient(listOf(GlassFill, GlassFill))
    }

    Box(
        modifier = modifier
            .shadow(
                if (isLiquid) 3.dp else 8.dp,
                shape,
                ambientColor = if (isLiquid) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Black,
                spotColor = if (isLiquid) Color.White.copy(alpha = 0.08f) else Color.Black,
            )
            .clip(shape)
            .background(fillBrush, shape)
            .border(1.dp, borderColor, shape)
            .padding(16.dp),
    ) {
        content()
    }
}

// ── GlassTextField ─────────────────────────────────────────────────────

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val shape = RoundedCornerShape(14.dp)
    val isLiquid = AppConfigManager.appTheme.startsWith("liquid_glass")
    val container = if (isLiquid) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
    } else {
        GlassFill
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isLiquid) 0.68f else 0.5f), shape),
        label = if (label.isNotEmpty()) {
            { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)) }
        } else null,
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = if (isLiquid) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f) else GlassFillMedium,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

// ── GlassButton (realistic glassmorphic with premium Blue Gradient) ────

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val isLiquid = AppConfigManager.appTheme.startsWith("liquid_glass")
    val enabledBrush = if (isLiquid) {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f),
            ),
        )
    } else {
        BlueGradientHorizontal
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = if (isLiquid) 5.dp else 12.dp,
                shape = shape,
                ambientColor = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = if (isLiquid) 0.22f else 0.4f) else Color.Black,
                spotColor = if (enabled) MaterialTheme.colorScheme.secondary.copy(alpha = if (isLiquid) 0.18f else 0.3f) else Color.Black
            ),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = SoftWhite,
            disabledContainerColor = CardDark.copy(alpha = 0.5f),
            disabledContentColor = MutedGray,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) enabledBrush else Brush.horizontalGradient(listOf(CardDark.copy(alpha = 0.5f), CardDark.copy(alpha = 0.5f))),
                    shape = shape
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))
                    ),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

// ── GlowText — white text with soft shadow ─────────────────────────────

@Composable
fun GlowText(
    text: String,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                offset = Offset(0f, 2f),
                blurRadius = 12f,
            ),
        ),
        modifier = modifier,
    )
}
