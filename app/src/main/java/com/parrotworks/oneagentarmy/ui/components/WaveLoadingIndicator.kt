package com.parrotworks.oneagentarmy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// A snake-like traveling sine wave - a themed replacement for the stock spinner.
@Composable
fun WaveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "wavePhase",
    )

    Canvas(modifier = modifier.size(width = 44.dp, height = 24.dp)) {
        val amplitude = size.height / 3f
        val centerY = size.height / 2f
        val wavelength = size.width / 1.5f
        val strokeWidth = 3.dp.toPx()

        fun wavePath(phaseOffset: Float): Path {
            val path = Path()
            var x = 0f
            while (x <= size.width) {
                val y = centerY + amplitude *
                    sin((2f * PI.toFloat() * x / wavelength) - phase + phaseOffset)
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 2f
            }
            return path
        }

        // Trailing ghost wave for depth, then the main wave on top.
        drawPath(
            path = wavePath(phaseOffset = PI.toFloat() / 2f),
            color = color.copy(alpha = 0.35f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawPath(
            path = wavePath(phaseOffset = 0f),
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
