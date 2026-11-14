package wtf.buddyworks.iperfclient.ui.test

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.buddyworks.iperfclient.ui.theme.OneUi
import wtf.buddyworks.iperfclient.ui.theme.SpeedPalette
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Evenly spaced values; NaN marks a missing point (a lost ping), drawn as a gap marker. */
@Immutable
class ChartSeries(
    val points: List<Float>,
    val stepSec: Double,
    val palette: SpeedPalette,
    val faded: Boolean = false,
)

/**
 * Values over time as smoothed gradient areas.
 *
 * @param windowSec when set, the x axis follows the newest point and shows this many seconds
 *   (continuous mode); otherwise it spans 0..[spanSec].
 * @param axisLabel formats the grid values (Mbps by default).
 */
@Composable
fun ThroughputChart(
    series: List<ChartSeries>,
    spanSec: Double,
    modifier: Modifier = Modifier,
    windowSec: Double? = null,
    live: Boolean = false,
    axisLabel: (Float) -> String = ::rateAxisLabel,
    fromZero: Boolean = true,
) {
    val colors = OneUi.colors
    val measurer = rememberTextMeasurer()
    val end = series.maxOfOrNull { it.points.size * it.stepSec } ?: 0.0
    val xEnd = if (windowSec != null) max(windowSec, end) else max(spanSec, end)
    val xStart = if (windowSec != null) max(0.0, xEnd - windowSec) else 0.0
    val visibleMax = series.maxOfOrNull { s ->
        val from = (xStart / s.stepSec).toInt().coerceAtLeast(0)
        var m = 0f
        for (i in from until s.points.size) {
            val v = s.points[i]
            if (!v.isNaN() && v > m) m = v
        }
        m
    } ?: 0f
    // Three grid lines on round values: 0.5, 1, 2, 2.5 or 5 times a power of ten.
    val step = niceCeil(max(visibleMax * 1.05f, 0.3f) / 3f)
    val yMax by animateFloatAsState(step * 3f, tween(600, easing = FastOutSlowInEasing), label = "ymax")
    val pulse by rememberInfiniteTransition(label = "head").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse",
    )

    Canvas(modifier) {
        val labelStyle = TextStyle(fontSize = 11.sp, color = colors.textSecondary)
        val top = 6.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val h = bottom - top
        val w = size.width
        fun x(t: Double) = ((t - xStart) / (xEnd - xStart).coerceAtLeast(0.001) * w).toFloat()
        fun y(v: Float) = bottom - (v / yMax).coerceIn(0f, 1.05f) * h

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (i in 1..3) {
            val v = step * i
            val gy = y(v)
            if (gy < top - 1f) continue
            drawLine(colors.divider, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            val label = measurer.measure(axisLabel(v), labelStyle)
            drawText(label, topLeft = Offset(4.dp.toPx(), gy + 2.dp.toPx()))
        }
        drawLine(colors.divider, Offset(0f, bottom), Offset(w, bottom), strokeWidth = 1.dp.toPx())

        clipRect(0f, 0f, w, size.height) {
            for (s in series) {
                if (s.points.isEmpty()) continue
                val pts = ArrayList<Offset>(s.points.size + 1)
                val losses = ArrayList<Float>()
                val from = ((xStart / s.stepSec).toInt() - 1).coerceAtLeast(0)
                // Throughput ramps up from zero; latency just starts at its first reading.
                if (from == 0 && fromZero) pts += Offset(x(0.0), y(0f))
                for (i in from until s.points.size) {
                    val v = s.points[i]
                    val px = x((i + 1) * s.stepSec)
                    if (v.isNaN()) losses += px else pts += Offset(px, y(v))
                }
                for (lx in losses) {
                    drawLine(colors.danger.copy(alpha = 0.8f), Offset(lx, bottom), Offset(lx, bottom - 10.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                if (pts.size < 2) continue
                val line = smoothPath(pts)
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, bottom)
                    lineTo(pts.first().x, bottom)
                    close()
                }
                val alpha = if (s.faded) 0.35f else 1f
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(s.palette.start.copy(alpha = 0.34f * alpha), s.palette.end.copy(alpha = 0.02f)),
                        startY = top, endY = bottom,
                    ),
                )
                drawPath(
                    line,
                    Brush.horizontalGradient(listOf(s.palette.start, s.palette.end)),
                    alpha = alpha,
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                if (live && !s.faded) {
                    val head = pts.last()
                    drawCircle(s.palette.end.copy(alpha = 0.25f * pulse), 9.dp.toPx(), head)
                    drawCircle(s.palette.end, 4.dp.toPx(), head)
                    drawCircle(colors.card, 1.8.dp.toPx(), head)
                }
            }
        }
    }
}

private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val p0 = pts[i - 1]
        val p1 = pts[i]
        val mx = (p0.x + p1.x) / 2f
        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
    }
}

/** Rounds up to 1, 2, 2.5 or 5 times a power of ten. */
fun niceCeil(v: Float): Float {
    val exp = 10.0.pow(kotlin.math.floor(log10(v.toDouble()))).toFloat()
    val m = v / exp
    val nice = when {
        m <= 1f -> 1f
        m <= 2f -> 2f
        m <= 2.5f -> 2.5f
        m <= 5f -> 5f
        else -> 10f
    }
    return ceil(nice * exp * 1000f) / 1000f
}

private fun trim(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)

fun rateAxisLabel(mbps: Float): String = if (mbps >= 1000f) "${trim(mbps / 1000f)} G" else trim(mbps)

fun msAxisLabel(ms: Float): String = "${trim(ms)} ms"
