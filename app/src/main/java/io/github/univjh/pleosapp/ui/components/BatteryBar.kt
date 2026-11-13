package io.github.univjh.pleosapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 0–100% battery ruler: solid up to the current charge, tinted up to the target.
 * The needed amount is marked above the bar and the everyday cap below it so the two
 * labels never collide when the values are close.
 */
@Composable
fun BatteryBar(socPercent: Int, targetPercent: Int, neededPercent: Int, capPercent: Int, neededLabel: String, capLabel: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceVariant
    val now = scheme.primary
    val add = scheme.primary.copy(alpha = 0.32f)
    val needColor = scheme.secondary
    val capColor = scheme.outline
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = "지금 $socPercent%, 목표 $targetPercent%, 필요 $neededPercent%, 평소 상한 $capPercent%"
    }) {
        MarkerLabel(neededPercent, neededLabel, needColor)
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val radius = CornerRadius(14.dp.toPx())
            drawRoundRect(track, cornerRadius = radius)
            fun x(percent: Int) = size.width * percent.coerceIn(0, 100) / 100f
            // Clip to the rounded track so the fills keep its corners.
            val path = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, radius))
            }
            drawContext.canvas.save(); drawContext.canvas.clipPath(path)
            if (targetPercent > socPercent) clipRect(left = x(socPercent), right = x(targetPercent)) { drawRect(add) }
            clipRect(right = x(socPercent)) { drawRect(now) }
            drawContext.canvas.restore()
            val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
            fun marker(percent: Int, color: Color) = drawLine(color, Offset(x(percent), -6.dp.toPx()),
                Offset(x(percent), size.height + 6.dp.toPx()), strokeWidth = 3.dp.toPx(), pathEffect = dash)
            marker(neededPercent, needColor)
            marker(capPercent, capColor)
            if (targetPercent > socPercent) drawRect(now, Offset(x(targetPercent) - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
        }
        MarkerLabel(capPercent, capLabel, capColor)
    }
}

/** Centers a label on a percentage of the width, pulled inside the edges when it would overflow. */
@Composable
private fun MarkerLabel(percent: Int, text: String, color: Color) {
    Layout({ Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1) }, Modifier.fillMaxWidth()) { measurables, constraints ->
        val label = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val center = constraints.maxWidth * percent.coerceIn(0, 100) / 100
        val left = (center - label.width / 2).coerceIn(0, (constraints.maxWidth - label.width).coerceAtLeast(0))
        layout(constraints.maxWidth, label.height + 6.dp.roundToPx()) { label.place(left, 3.dp.roundToPx()) }
    }
}
