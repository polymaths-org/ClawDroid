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
    val borderColor = if (glowColor != Color.Transparent) {
        glowColor.copy(alpha = 0.4f)
    } else {
        GlassBorderDim
    }

    Box(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(GlassFill, shape)
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
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, GlassBorderDim, shape),
        label = if (label.isNotEmpty()) {
            { Text(label, color = MutedGray) }
        } else null,
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = MutedGray.copy(alpha = 0.5f)) }
        } else null,
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        textStyle = TextStyle(color = SoftWhite),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = GlassFillMedium,
            unfocusedContainerColor = GlassFill,
            disabledContainerColor = GlassFill,
            cursorColor = NeonCyan,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = NeonCyan,
            unfocusedLabelColor = MutedGray,
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
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = if (enabled) Color(0xFF0072FF).copy(alpha = 0.4f) else Color.Black,
                spotColor = if (enabled) Color(0xFF00C6FF).copy(alpha = 0.3f) else Color.Black
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
                    brush = if (enabled) BlueGradientHorizontal else Brush.horizontalGradient(listOf(CardDark.copy(alpha = 0.5f), CardDark.copy(alpha = 0.5f))),
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
            color = SoftWhite,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = NeonCyan.copy(alpha = 0.4f),
                offset = Offset(0f, 2f),
                blurRadius = 12f,
            ),
        ),
        modifier = modifier,
    )
}
