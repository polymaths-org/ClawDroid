package com.clawdroid.app.ui.components

import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.clawdroid.app.R

enum class MascotAnimation {
    Idle,
    Thinking,
}

@Composable
fun FifMascot(
    modifier: Modifier = Modifier,
    contentDescription: String = "ClawDroid mascot",
    animation: MascotAnimation = MascotAnimation.Idle,
    randomize: Boolean = false,
    randomKey: Any? = Unit,
) {
    @DrawableRes val resourceId = remember(animation, randomize, randomKey) {
        if (randomize) {
            listOf(R.drawable.fifanim, R.drawable.thinking).random()
        } else {
            when (animation) {
                MascotAnimation.Idle -> R.drawable.fifanim
                MascotAnimation.Thinking -> R.drawable.thinking
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setImageResource(resourceId)
                this.contentDescription = contentDescription
            }
        },
        update = { imageView ->
            imageView.contentDescription = contentDescription
            imageView.setImageResource(resourceId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (imageView.drawable as? AnimatedImageDrawable)?.start()
            }
        },
    )
}
