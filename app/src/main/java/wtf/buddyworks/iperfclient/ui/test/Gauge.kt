package wtf.buddyworks.iperfclient.ui.test

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import wtf.buddyworks.iperfclient.ui.theme.OneUi
import wtf.buddyworks.iperfclient.ui.theme.SpeedPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Gauge marks in Mbps. Equal angular spacing between marks gives a speedometer-like log feel. */
enum class GaugeScale(val marks: List<Float>) {
    Standard(listOf(0f, 5f, 10f, 25f, 50f, 100f, 250f, 500f, 1000f)),
    High(listOf(0f, 10f, 50f, 100f, 250f, 500f, 1000f, 2500f, 5000f)),

    /** Round-trip time in milliseconds, for ping mode. */
    Latency(listOf(0f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1000f));

    fun fraction(mbps: Float): Float {
        if (mbps <= 0f) return 0f
        val n = marks.size - 1
        for (i in 0 until n) {
            if (mbps < marks[i + 1]) {
                return (i + (mbps - marks[i]) / (marks[i + 1] - marks[i])) / n
            }
        }
        return 1f + min(0.03f, (mbps / marks.last() - 1f) * 0.03f)
    }
}

private const val START_ANGLE = 135f
private const val SWEEP = 270f
private const val COMET_TAIL = 48f

/**
 * Speedometer for live throughput.
 *
 * @param armed the dial is live: the arc draws itself in and the pointer does a quick
 *   dashboard-style self-test sweep. While not armed the dial rests, dimmed.
 * @param progress fraction of a timed phase, drawn as a thin outer ring; null for an
 *   open-ended (continuous) phase, which shows a circling comet instead.
 * @param secondary a second reading (upload during a bidirectional test), drawn on the outer ring.
 */
@Composable
fun SpeedGauge(
    valueMbps: Float,
    palette: SpeedPalette,
    armed: Boolean,
    showPointer: Boolean,
    progress: Float?,
    modifier: Modifier = Modifier,
    preferHighScale: Boolean = false,
    /** Use this scale instead of choosing between the throughput scales. */
    fixedScale: GaugeScale? = null,
    secondaryMbps: Float? = null,
    secondaryPalette: SpeedPalette? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = OneUi.colors
    var autoScale by remember { mutableStateOf(if (preferHighScale) GaugeScale.High else GaugeScale.Standard) }
    val scale = fixedScale ?: autoScale
    LaunchedEffect(armed, preferHighScale) {
        // Each test starts on the scale the link suggests; at rest the dial shows it too.
        autoScale = if (preferHighScale) GaugeScale.High else GaugeScale.Standard
    }
    LaunchedEffect(valueMbps > 950f, secondaryMbps?.let { it > 950f }) {
        if (fixedScale == null && (valueMbps > 950f || (secondaryMbps ?: 0f) > 950f)) autoScale = GaugeScale.High
    }

    // The dial draws itself in when it first appears and again each time a test arms it.
    val reveal = remember { Animatable(0f) }
    val selfTest = remember { Animatable(0f) }
    LaunchedEffect(armed) {
        if (armed) reveal.snapTo(0f)
        reveal.animateTo(1f, tween(if (armed) 650 else 900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(armed) {
        if (armed) {
            selfTest.snapTo(0f)
            selfTest.animateTo(1f, tween(520, delayMillis = 120, easing = FastOutSlowInEasing))
            selfTest.animateTo(0f, tween(620, easing = FastOutSlowInEasing))
        }
    }

    val target = if (showPointer) scale.fraction(valueMbps) else 0f
    val pointer by animateFloatAsState(target, spring(dampingRatio = 0.55f, stiffness = 55f), label = "pointer")
    val secondaryTarget = secondaryMbps?.let { scale.fraction(it) } ?: 0f
    val secondary by animateFloatAsState(secondaryTarget, spring(dampingRatio = 0.7f, stiffness = 60f), label = "secondary")
    val ring by animateFloatAsState(progress ?: 0f, tween(300, easing = LinearEasing), label = "ring")
    val activeAlpha by animateFloatAsState(if (armed) 1f else 0.45f, tween(400), label = "dim")

    val infinite = rememberInfiniteTransition(label = "gauge")
    val glow by infinite.animateFloat(
        0.65f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val comet by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "comet",
    )

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val shown = maxOf(pointer, selfTest.value).coerceIn(0f, 1.03f)
            val stroke = size.minDimension * 0.068f
            val outerGap = stroke * 1.05f
            val ringWidth = stroke * 0.30f
            val radius = size.minDimension / 2f - outerGap - ringWidth - stroke * 0.2f
            val c = center

            // Outer ring: phase progress, upload level in a bidirectional test, or a comet when open-ended.
            val ringRadius = radius + outerGap
            drawArcAt(c, ringRadius, START_ANGLE, SWEEP * reveal.value, colors.track.copy(alpha = activeAlpha), ringWidth)
            if (armed && secondaryMbps != null && secondaryPalette != null) {
                if (secondary > 0.002f) {
                    rotate(START_ANGLE, c) {
                        drawArcAt(c, ringRadius, 0f, SWEEP * secondary.coerceAtMost(1f), gradient(secondaryPalette, c), ringWidth)
                    }
                }
            } else if (armed && progress == null && showPointer) {
                // The head enters at the start of the arc while the tail grows in behind it, and
                // each lap ends once the tail has slid off the end, so the loop never jumps.
                val head = (SWEEP + COMET_TAIL) * comet
                val from = maxOf(0f, head - COMET_TAIL)
                val to = minOf(SWEEP, head)
                if (to > from) {
                    // Tail as one gradient, rotated so it fades out behind the head.
                    val tail = Brush.sweepGradient(
                        0f to palette.end.copy(alpha = 0f),
                        COMET_TAIL / 360f to palette.end.copy(alpha = 0.95f),
                        1f to palette.end.copy(alpha = 0f),
                        center = c,
                    )
                    rotate(START_ANGLE + head - COMET_TAIL, c) {
                        drawArc(
                            tail, from - (head - COMET_TAIL), to - from, false,
                            topLeft = c - Offset(ringRadius, ringRadius), size = Size(ringRadius * 2, ringRadius * 2),
                            style = Stroke(ringWidth, cap = StrokeCap.Butt),
                        )
                    }
                }
                if (head in 0f..SWEEP) {
                    val edge = minOf(head, SWEEP - head).coerceIn(0f, 10f) / 10f
                    val angle = Math.toRadians((START_ANGLE + head).toDouble())
                    val p = c + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * ringRadius
                    drawCircle(palette.end.copy(alpha = 0.25f * edge), ringWidth * 1.6f, p)
                    drawCircle(palette.end.copy(alpha = edge), ringWidth * 0.62f, p)
                }
            } else if (armed && ring > 0.002f) {
                rotate(START_ANGLE, c) {
                    drawArcAt(c, ringRadius, 0f, SWEEP * ring.coerceAtMost(1f), colors.textSecondary.copy(alpha = 0.55f), ringWidth)
                }
            }

            // Track.
            drawArcAt(c, radius, START_ANGLE, SWEEP * maxOf(reveal.value, 0.001f), colors.track.copy(alpha = activeAlpha), stroke)

            // Value arc with a soft breathing glow.
            if (shown > 0.003f) {
                val brush = gradient(palette, c)
                rotate(START_ANGLE, c) {
                    val sweep = SWEEP * shown.coerceAtMost(1f)
                    drawArcAt(c, radius, 0f, sweep, brush, stroke * 2.6f, alpha = 0.07f * glow)
                    drawArcAt(c, radius, 0f, sweep, brush, stroke * 1.7f, alpha = 0.14f * glow)
                    drawArcAt(c, radius, 0f, sweep, brush, stroke)
                }
            }

            // Ticks inside the arc, lit once the pointer has passed them.
            val marks = scale.marks.size - 1
            val minor = 4
            val total = marks * minor
            val tickOuter = radius - stroke * 0.95f
            for (i in 0..total) {
                val f = i.toFloat() / total
                if (f > reveal.value + 0.001f) break
                val major = i % minor == 0
                val len = if (major) stroke * 0.55f else stroke * 0.28f
                val angle = Math.toRadians((START_ANGLE + SWEEP * f).toDouble())
                val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                val lit = f <= shown && shown > 0.003f
                val color = if (lit) lerp(palette.start, palette.end, f) else colors.textSecondary.copy(alpha = if (major) 0.55f else 0.3f)
                drawLine(
                    color = color.copy(alpha = color.alpha * activeAlpha),
                    start = c + dir * tickOuter,
                    end = c + dir * (tickOuter - len),
                    strokeWidth = if (major) stroke * 0.11f else stroke * 0.07f,
                    cap = StrokeCap.Round,
                )
            }

            // Pointer: a tapered blade riding just inside the arc, with a bright tip.
            if (showPointer || selfTest.value > 0f) {
                drawPointer(c, radius, stroke, shown, palette, glow)
            }
        }

        // Scale labels crossfade when the dial switches to the multi-gigabit scale.
        Crossfade(scale, animationSpec = tween(450), label = "scale") { s ->
            ScaleLabels(s, reveal.value, activeAlpha)
        }
        content()
    }
}

@Composable
private fun ScaleLabels(scale: GaugeScale, reveal: Float, alpha: Float) {
    val measurer = rememberTextMeasurer()
    val color = OneUi.colors.textSecondary
    Canvas(Modifier.fillMaxSize()) {
        val stroke = size.minDimension * 0.068f
        val radius = size.minDimension / 2f - stroke * 1.05f - stroke * 0.30f - stroke * 0.2f
        val labelRadius = radius - stroke * 2.15f
        val style = TextStyle(fontSize = (size.minDimension / 24f).toSp(), fontWeight = FontWeight.Medium, color = color.copy(alpha = color.alpha * alpha))
        val n = scale.marks.size - 1
        scale.marks.forEachIndexed { i, mark ->
            val f = i.toFloat() / n
            if (f > reveal + 0.001f) return@forEachIndexed
            val text = if (scale == GaugeScale.Latency && mark >= 1000f) "1s" else if (mark >= 1000f) "${(mark / 1000f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}G" else mark.toInt().toString()
            val angle = Math.toRadians((START_ANGLE + SWEEP * f).toDouble())
            val layout = measurer.measure(text, style)
            val p = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * labelRadius
            drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
    }
}

private fun gradient(p: SpeedPalette, c: Offset) = Brush.sweepGradient(
    0f to p.start,
    0.75f to p.end,
    0.88f to p.end,
    1f to p.start,
    center = c,
)

private fun DrawScope.drawArcAt(c: Offset, r: Float, start: Float, sweep: Float, color: Color, width: Float) {
    drawArc(color, start, sweep, false, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(width, cap = StrokeCap.Round))
}

private fun DrawScope.drawArcAt(c: Offset, r: Float, start: Float, sweep: Float, brush: Brush, width: Float, alpha: Float = 1f) {
    drawArc(brush, start, sweep, false, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2), alpha = alpha, style = Stroke(width, cap = StrokeCap.Round))
}

private fun DrawScope.drawPointer(c: Offset, radius: Float, stroke: Float, f: Float, palette: SpeedPalette, glow: Float) {
    val angle = Math.toRadians((START_ANGLE + SWEEP * f).toDouble())
    val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
    val normal = Offset(-dir.y, dir.x)
    val tipR = radius - stroke * 1.15f
    val baseR = radius * 0.50f
    val tip = c + dir * tipR
    val base = c + dir * baseR
    val baseHalf = stroke * 0.26f
    val tipHalf = stroke * 0.07f
    val path = Path().apply {
        moveTo(base.x + normal.x * baseHalf, base.y + normal.y * baseHalf)
        lineTo(tip.x + normal.x * tipHalf, tip.y + normal.y * tipHalf)
        lineTo(tip.x - normal.x * tipHalf, tip.y - normal.y * tipHalf)
        lineTo(base.x - normal.x * baseHalf, base.y - normal.y * baseHalf)
        close()
    }
    val color = lerp(palette.start, palette.end, f.coerceIn(0f, 1f))
    drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = 0f), color), start = base, end = tip))
    drawCircle(color.copy(alpha = 0.22f * glow), radius = stroke * 0.55f, center = tip)
    drawCircle(color, radius = stroke * 0.2f, center = tip)
    drawCircle(Color.White.copy(alpha = 0.9f), radius = stroke * 0.08f, center = tip)
}
