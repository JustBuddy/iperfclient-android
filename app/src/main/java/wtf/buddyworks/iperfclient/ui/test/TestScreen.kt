package wtf.buddyworks.iperfclient.ui.test

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import wtf.buddyworks.iperfclient.net.ACCESS_LOCAL_NETWORK
import wtf.buddyworks.iperfclient.net.isLocalNetworkHost
import wtf.buddyworks.iperfclient.net.localNetworkPermissionMissing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.buddyworks.iperfclient.R
import wtf.buddyworks.iperfclient.data.Server
import wtf.buddyworks.iperfclient.data.TestMode
import wtf.buddyworks.iperfclient.data.TestOptions
import wtf.buddyworks.iperfclient.iperf.IperfException
import wtf.buddyworks.iperfclient.iperf.IperfPhase
import wtf.buddyworks.iperfclient.iperf.Protocol
import wtf.buddyworks.iperfclient.net.ConnectionInfo
import wtf.buddyworks.iperfclient.net.PingStats
import wtf.buddyworks.iperfclient.net.Transport
import wtf.buddyworks.iperfclient.ui.components.Chip
import wtf.buddyworks.iperfclient.ui.components.GroupCard
import wtf.buddyworks.iperfclient.ui.components.IconBadge
import wtf.buddyworks.iperfclient.ui.components.InsetDivider
import wtf.buddyworks.iperfclient.ui.components.LegendDot
import wtf.buddyworks.iperfclient.ui.components.PillButton
import wtf.buddyworks.iperfclient.ui.components.SegmentedControl
import wtf.buddyworks.iperfclient.ui.components.SettingBlock
import wtf.buddyworks.iperfclient.ui.components.SwitchRow
import wtf.buddyworks.iperfclient.ui.describeConnection
import wtf.buddyworks.iperfclient.ui.formatBytes
import wtf.buddyworks.iperfclient.ui.formatDuration
import wtf.buddyworks.iperfclient.ui.formatGaugeMbps
import wtf.buddyworks.iperfclient.ui.formatMbps
import wtf.buddyworks.iperfclient.ui.formatMs
import wtf.buddyworks.iperfclient.ui.formatPercent
import wtf.buddyworks.iperfclient.ui.theme.OneUi
import wtf.buddyworks.iperfclient.ui.theme.SpeedPalette

/**
 * Averages for continuous mode that leave out the ramp-up, so they agree with the low and peak
 * shown next to them. Until there is enough data, the plain bytes-over-time average.
 */
private fun steady(series: List<Float>, fallback: Double): Double =
    if (series.size > RAMP_SAMPLES + 4) series.subList(RAMP_SAMPLES, series.size).average() else fallback

private val SpeedTestViewModel.steadyDownMbps: Double get() = steady(downSeries, liveAvgDownMbps)
private val SpeedTestViewModel.steadyUpMbps: Double get() = steady(upSeries, liveAvgUpMbps)

/** Seconds of history the chart shows while a continuous test runs. */
private const val CONTINUOUS_WINDOW_SEC = 60.0

/** Keeps the screen on while testing and taps the haptics as phases finish. */
@Composable
fun TestEffects(vm: SpeedTestViewModel) {
    val view = LocalView.current
    DisposableEffect(vm.running) {
        view.keepScreenOn = vm.running
        onDispose { view.keepScreenOn = false }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(vm.stage) {
        if (vm.stage == Stage.Upload || vm.stage == Stage.Done) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}

@Composable
private fun paletteFor(stage: Stage): SpeedPalette = when (stage) {
    Stage.Upload -> OneUi.colors.upload
    Stage.Ping -> OneUi.colors.ping
    else -> OneUi.colors.download
}

/**
 * The gauge, start/stop control and result tiles. [compactTiles] stacks less vertical
 * space for short panes (tabletop's upper half).
 */
@Composable
fun TestHero(
    vm: SpeedTestViewModel,
    connection: ConnectionInfo,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    tiles: Boolean = true,
    idleMode: TestMode = TestMode.Full,
) {
    val colors = OneUi.colors
    val stage = vm.stage
    val dataStage = stage == Stage.Download || stage == Stage.Upload || stage == Stage.Bidirectional
    val pingMode = vm.isPingMode(idleMode)
    // The idle ping before a speed test reads on the latency scale too.
    val latencyDial = pingMode || (vm.running && stage == Stage.Ping)
    val palette = if (latencyDial) colors.ping else paletteFor(stage)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StageHeader(vm)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight, 460.dp)
            SpeedGauge(
                valueMbps = if (latencyDial) vm.livePingMs?.toFloat() ?: 0f else vm.gaugeMbps,
                palette = palette,
                armed = vm.running,
                showPointer = dataStage || (latencyDial && vm.running),
                fixedScale = if (latencyDial) GaugeScale.Latency else null,
                progress = if (vm.phaseDuration > 0) (vm.elapsed / vm.phaseDuration).coerceIn(0f, 1f) else null,
                preferHighScale = (connection.linkMbps ?: 0) > 1300,
                secondaryMbps = if (stage == Stage.Bidirectional) vm.gaugeSecondaryMbps else null,
                secondaryPalette = colors.upload,
                modifier = Modifier.size(side),
            ) {
                GaugeCenter(vm, side, onStart, if (pingMode) colors.ping else colors.download)
            }
        }
        if (tiles) {
            Spacer(Modifier.height(4.dp))
            ResultTiles(vm, pingMode, Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StageHeader(vm: SpeedTestViewModel) {
    val colors = OneUi.colors
    val error = vm.error
    val text = when {
        !vm.running && error != null -> errorTitle(error.kind)
        !vm.running && vm.stage == Stage.Done ->
            if (vm.completedRounds > 1) pluralStringResource(R.plurals.stage_done_rounds, vm.completedRounds, vm.completedRounds) else stringResource(R.string.stage_done)
        !vm.running -> stringResource(R.string.stage_ready)
        vm.stopping -> stringResource(R.string.stage_stopping)
        vm.busyRetryPort != null && vm.status != IperfPhase.Running -> stringResource(R.string.stage_busy_port, vm.busyRetryPort ?: 0)
        vm.status == IperfPhase.Connecting -> stringResource(R.string.stage_connecting)
        vm.status == IperfPhase.Negotiating -> stringResource(R.string.stage_negotiating)
        vm.status == IperfPhase.Finishing -> stringResource(R.string.stage_finishing)
        else -> stageLabel(vm.stage)
    }
    val color = when {
        !vm.running && error != null -> colors.danger
        !vm.running -> colors.textSecondary
        else -> paletteFor(vm.stage).text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            text,
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 2 }) },
            label = "stage",
        ) { label ->
            if (vm.running) Chip(label, color) else Text(label, style = OneUi.type.section, color = color)
        }
        if (vm.running && vm.sessionContinuous && vm.sessionMode == TestMode.Full) {
            Spacer(Modifier.width(8.dp))
            Chip(stringResource(R.string.round_n, vm.round), colors.textSecondary)
        }
    }
}

@Composable
fun stageLabel(stage: Stage): String = stringResource(
    when (stage) {
        Stage.Ping -> R.string.ping
        Stage.Download -> R.string.download
        Stage.Upload -> R.string.upload
        Stage.Bidirectional -> R.string.both_ways
        Stage.Idle, Stage.Done -> R.string.stage_ready
    },
)

/** What sits in the middle of the dial: the start button, the ping, or the live rate. */
@Composable
private fun GaugeCenter(vm: SpeedTestViewModel, side: Dp, onStart: () -> Unit, startPalette: SpeedPalette) {
    val colors = OneUi.colors
    val stage = vm.stage
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = !vm.running,
            enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 300f), initialScale = 0.6f) + fadeIn(tween(250)),
            exit = scaleOut(tween(280, easing = FastOutSlowInEasing), targetScale = 0.4f) + fadeOut(tween(220)),
        ) {
            StartButton(
                label = stringResource(if (vm.stage == Stage.Done) R.string.action_again else R.string.action_start),
                size = side * 0.34f,
                onClick = onStart,
                palette = startPalette,
            )
        }
        AnimatedVisibility(
            visible = vm.running,
            enter = fadeIn(tween(400, delayMillis = 250)) + scaleIn(tween(400, delayMillis = 250), initialScale = 0.85f),
            exit = fadeOut(tween(200)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                val numberSize = (side.value * 0.15f).coerceIn(34f, 64f).sp
                if (stage == Stage.Ping) {
                    Text(
                        vm.livePingMs?.let { formatMs(it) } ?: "–",
                        style = OneUi.type.gaugeNumber.copy(fontSize = numberSize, lineHeight = numberSize),
                        color = colors.textPrimary,
                    )
                    Text("ms", style = OneUi.type.itemValue, color = colors.ping.text)
                } else {
                    Text(
                        formatGaugeMbps(vm.gaugeMbps),
                        style = OneUi.type.gaugeNumber.copy(fontSize = numberSize, lineHeight = numberSize),
                        color = colors.textPrimary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (stage == Stage.Download || stage == Stage.Upload || stage == Stage.Bidirectional) {
                            Icon(
                                if (stage == Stage.Upload) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = paletteFor(stage).text,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text("Mbps", style = OneUi.type.itemValue, color = paletteFor(stage).text)
                    }
                    val up = vm.gaugeSecondaryMbps
                    if (stage == Stage.Bidirectional && up != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = colors.upload.text, modifier = Modifier.size(16.dp))
                            Text("${formatGaugeMbps(up)} Mbps", style = OneUi.type.itemValue, color = colors.upload.text)
                        }
                    }
                }
            }
        }
        // In the open gap between the ends of the arc, clear of the scale labels.
        AnimatedVisibility(
            visible = vm.running,
            enter = fadeIn(tween(300, delayMillis = 400)) + slideInVertically(tween(300, delayMillis = 400)) { it / 2 },
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = side * 0.07f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimerLine(vm)
                Spacer(Modifier.height(6.dp))
                StopButton(vm)
            }
        }
    }
}

@Composable
private fun TimerLine(vm: SpeedTestViewModel) {
    val colors = OneUi.colors
    val text = when {
        vm.stage == Stage.Ping && vm.sessionMode != TestMode.Ping ->
            vm.pingSamples.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.ping_count, it, it) } ?: ""
        vm.phaseDuration > 0 -> "${formatDuration(vm.elapsed.toDouble())} / ${formatDuration(vm.phaseDuration.toDouble())}"
        else -> formatDuration(vm.elapsed.toDouble())
    }.let { base ->
        if (vm.sessionMode == TestMode.Ping && vm.pingSamples.isNotEmpty()) "$base · ${pluralStringResource(R.plurals.ping_count, vm.pingSamples.size, vm.pingSamples.size)}" else base
    }
    val ping = vm.livePingMs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = OneUi.type.caption.copy(fontFeatureSettings = "tnum"), color = colors.textSecondary)
        if (vm.stage != Stage.Ping && ping != null) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.NetworkPing, contentDescription = null, tint = colors.ping.text, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(2.dp))
            Text("${formatMs(ping)} ms", style = OneUi.type.caption.copy(fontFeatureSettings = "tnum"), color = colors.textSecondary)
        }
    }
}

@Composable
private fun StopButton(vm: SpeedTestViewModel) {
    val colors = OneUi.colors
    val label = stringResource(if (vm.sessionContinuous) R.string.action_stop else R.string.action_cancel)
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.pill)
            .clickable(enabled = !vm.stopping, role = Role.Button, onClick = vm::stop)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (vm.sessionContinuous) Icons.Rounded.Stop else Icons.Rounded.Close, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = OneUi.type.button.copy(fontSize = 14.sp), color = colors.textPrimary)
    }
}

/** A big glowing start button with ripples rolling outwards, inviting a tap. */
@Composable
fun StartButton(label: String, size: Dp, onClick: () -> Unit, palette: SpeedPalette = OneUi.colors.download) {
    val colors = OneUi.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
    val ripple = rememberInfiniteTransition(label = "ripple")
    val wave by ripple.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wave")
    val breathe by ripple.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breathe",
    )
    val a by animateColorAsState(palette.start, tween(400), label = "start-a")
    val b by animateColorAsState(palette.end, tween(400), label = "start-b")
    Box(Modifier.size(size * 1.5f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.toPx() / 2f
            for (i in 0 until 3) {
                val t = (wave + i / 3f) % 1f
                val r = base * (1f + t * 0.48f)
                drawCircle(
                    Brush.sweepGradient(listOf(a, b, a), center = center),
                    radius = r,
                    alpha = (1f - t) * 0.38f,
                    style = Stroke(width = 2.dp.toPx() * (1.4f - t)),
                )
            }
            drawCircle(b.copy(alpha = 0.10f + 0.08f * breathe), radius = base * (1.06f + 0.05f * breathe))
        }
        Box(
            Modifier
                .size(size)
                .scale(scale)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset.Infinite))
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = OneUi.type.headline.copy(fontSize = (size.value * 0.17f).coerceIn(18f, 34f).sp, letterSpacing = 0.5.sp),
                color = Color.White,
            )
        }
    }
}

/** Ping, download and upload results; the running one shows its live value. */
@Composable
fun ResultTiles(vm: SpeedTestViewModel, pingMode: Boolean, modifier: Modifier = Modifier) {
    if (pingMode) {
        PingTiles(vm, modifier)
        return
    }
    val colors = OneUi.colors
    val stage = vm.stage
    val live = vm.running
    val continuous = vm.sessionContinuous && vm.sessionMode != TestMode.Full
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ping = vm.idlePing
        ResultTile(
            title = stringResource(R.string.ping),
            icon = Icons.Rounded.NetworkPing,
            palette = colors.ping,
            value = when {
                live && stage == Stage.Ping -> vm.livePingMs?.let { formatMs(it) }
                else -> ping?.medianMs?.let { formatMs(it) }
            },
            unit = "ms",
            detail = ping?.let { stringResource(if (vm.pingFromTcp) R.string.jitter_tcp else R.string.jitter_value, formatMs(it.jitterMs)) },
            active = live && stage == Stage.Ping,
            modifier = Modifier.weight(1f),
        )
        val down = vm.download
        val downLive = live && (stage == Stage.Download || stage == Stage.Bidirectional)
        ResultTile(
            title = stringResource(R.string.download),
            icon = Icons.Rounded.ArrowDownward,
            palette = colors.download,
            value = when {
                downLive && continuous -> formatMbps(vm.steadyDownMbps).number
                downLive -> formatMbps(vm.gaugeMbps.toDouble()).number
                else -> down?.let { formatMbps(it.bitsPerSecond / 1e6).number }
            },
            unit = when {
                downLive && continuous -> formatMbps(vm.steadyDownMbps).unit
                downLive -> formatMbps(vm.gaugeMbps.toDouble()).unit
                else -> down?.let { formatMbps(it.bitsPerSecond / 1e6).unit } ?: "Mbps"
            },
            detail = when {
                downLive && continuous -> stringResource(R.string.average)
                down?.lossPercent != null -> stringResource(R.string.loss_short, formatPercent(down.lossPercent))
                else -> down?.loadedPingMs?.let { "${formatMs(it)} ms" }
            },
            active = downLive,
            modifier = Modifier.weight(1f),
            detailIcon = Icons.Rounded.NetworkPing.takeIf { !(downLive && continuous) && down?.lossPercent == null },
            detailDescription = down?.loadedPingMs?.let { stringResource(R.string.loaded_ms, formatMs(it)) },
        )
        val up = vm.upload
        val upLive = live && (stage == Stage.Upload || stage == Stage.Bidirectional)
        val upNow = if (stage == Stage.Bidirectional) vm.gaugeSecondaryMbps ?: 0f else vm.gaugeMbps
        ResultTile(
            title = stringResource(R.string.upload),
            icon = Icons.Rounded.ArrowUpward,
            palette = colors.upload,
            value = when {
                upLive && continuous -> formatMbps(vm.steadyUpMbps).number
                upLive -> formatMbps(upNow.toDouble()).number
                else -> up?.let { formatMbps(it.bitsPerSecond / 1e6).number }
            },
            unit = when {
                upLive && continuous -> formatMbps(vm.steadyUpMbps).unit
                upLive -> formatMbps(upNow.toDouble()).unit
                else -> up?.let { formatMbps(it.bitsPerSecond / 1e6).unit } ?: "Mbps"
            },
            detail = when {
                upLive && continuous -> stringResource(R.string.average)
                up?.lossPercent != null -> stringResource(R.string.loss_short, formatPercent(up.lossPercent))
                else -> up?.loadedPingMs?.let { "${formatMs(it)} ms" }
            },
            active = upLive,
            modifier = Modifier.weight(1f),
            detailIcon = Icons.Rounded.NetworkPing.takeIf { !(upLive && continuous) && up?.lossPercent == null },
            detailDescription = up?.loadedPingMs?.let { stringResource(R.string.loaded_ms, formatMs(it)) },
        )
    }
}

@Composable
private fun ResultTile(
    title: String,
    icon: ImageVector,
    palette: SpeedPalette,
    value: String?,
    unit: String,
    detail: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
    detailIcon: ImageVector? = null,
    detailDescription: String? = null,
) {
    val colors = OneUi.colors
    val borderAlpha by animateFloatAsState(if (active) 1f else 0f, tween(300), label = "border")
    val lift by animateFloatAsState(if (active) 1.03f else 1f, spring(dampingRatio = 0.6f), label = "lift")
    Column(
        modifier
            .scale(lift)
            .clip(RoundedCornerShape(22.dp))
            .background(if (colors.isDark) colors.highlight else colors.background)
            .border(1.5.dp, Brush.linearGradient(listOf(palette.start.copy(alpha = borderAlpha), palette.end.copy(alpha = borderAlpha))), RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(Brush.linearGradient(listOf(palette.start, palette.end)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.width(5.dp))
            Text(title, style = OneUi.type.caption.copy(fontSize = 12.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        // Only the first appearance animates; live updates just change the digits. Number and
        // unit share one line that shrinks to fit, so the tile never reflows.
        AnimatedContent(
            value != null,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically { it / 2 } + scaleIn(initialScale = 0.8f)) togetherWith
                    fadeOut(tween(150)) using SizeTransform(clip = false)
            },
            label = "tile",
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.CenterStart,
        ) { has ->
            val text = buildAnnotatedString {
                append(if (has) value ?: "–" else "–")
                withStyle(SpanStyle(fontSize = 0.55.em, fontWeight = FontWeight.Normal, color = palette.text)) {
                    append("\u2009")
                    append(unit)
                }
            }
            BasicText(
                text,
                style = OneUi.type.bigNumber.copy(lineHeight = 30.sp, color = if (has) colors.textPrimary else colors.textSecondary),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp, stepSize = 1.sp),
            )
        }
        Row(
            Modifier
                .height(18.dp)
                .then(if (detailDescription != null) Modifier.semantics(mergeDescendants = true) { contentDescription = detailDescription } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detail != null && detailIcon != null) {
                Icon(detailIcon, contentDescription = null, tint = colors.ping.text, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
            }
            BasicText(
                detail ?: "",
                style = OneUi.type.caption.copy(color = colors.textSecondary, fontFeatureSettings = "tnum"),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            )
        }
    }
}

/** Ping mode: latency, jitter and loss, live while running. */
@Composable
private fun PingTiles(vm: SpeedTestViewModel, modifier: Modifier = Modifier) {
    val colors = OneUi.colors
    val live = vm.running
    val samples = vm.pingSamples
    val liveStats = if (live) PingStats.of(samples.toList()) else vm.idlePing
    val series = vm.pingSeries
    val lossPercent = when {
        !live -> vm.pingResult?.lossPercent
        series.isEmpty() -> null
        else -> series.count { it.isNaN() } * 100.0 / series.size
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultTile(
            title = stringResource(R.string.latency),
            icon = Icons.Rounded.NetworkPing,
            palette = colors.ping,
            value = (if (live) vm.livePingMs else liveStats?.medianMs)?.let { formatMs(it) },
            unit = "ms",
            detail = liveStats?.let { stringResource(R.string.median_value, formatMs(it.medianMs)) },
            active = live,
            modifier = Modifier.weight(1f),
        )
        ResultTile(
            title = stringResource(R.string.detail_jitter),
            icon = Icons.Rounded.SwapVert,
            palette = colors.download,
            value = liveStats?.let { formatMs(it.jitterMs) },
            unit = "ms",
            detail = liveStats?.let { stringResource(R.string.range_value, formatMs(it.minMs), formatMs(samples.maxOrNull() ?: it.minMs)) },
            active = false,
            modifier = Modifier.weight(1f),
        )
        ResultTile(
            title = stringResource(R.string.loss),
            icon = Icons.Rounded.Close,
            palette = colors.upload,
            value = lossPercent?.let { formatPercent(it) },
            unit = "%",
            detail = if (series.isNotEmpty()) series.count { it.isNaN() }.let { lost -> pluralStringResource(R.plurals.lost_short, lost, lost, series.size) } else null,
            active = false,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Whether the hero shows ping mode: the running/finished session's mode, else the chosen one. */
fun SpeedTestViewModel.isPingMode(idleMode: TestMode): Boolean =
    (if (running || stage == Stage.Done) sessionMode else idleMode) == TestMode.Ping

/** The selected server and the current connection; tapping opens the server list. */
@Composable
fun ServerCard(server: Server?, connection: ConnectionInfo, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = OneUi.colors
    val context = LocalContext.current
    GroupCard(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Outlined.Dns, colors.accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(server?.name ?: stringResource(R.string.no_server), style = OneUi.type.itemTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(server?.address ?: stringResource(R.string.no_server_hint), style = OneUi.type.itemSubtitle, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
        }
        InsetDivider(start = 74.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when (connection.transport) {
                Transport.WiFi -> Icons.Outlined.Wifi
                Transport.Cellular -> Icons.Outlined.CellTower
                Transport.Ethernet -> Icons.Outlined.SettingsEthernet
                Transport.None -> Icons.Outlined.WifiOff
            }
            IconBadge(icon, colors.download.end)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.connection), style = OneUi.type.itemTitle, color = colors.textPrimary)
                Text(
                    describeConnection(context, connection) ?: stringResource(R.string.net_none),
                    style = OneUi.type.itemSubtitle,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Test settings. Locked while a test runs. */
@Composable
fun OptionsCard(options: TestOptions, enabled: Boolean, onChange: (TestOptions) -> Unit, modifier: Modifier = Modifier) {
    GroupCard(modifier, contentPadding = 0.dp) {
        Spacer(Modifier.height(6.dp))
        SettingBlock(stringResource(R.string.opt_mode)) {
            SegmentedControl(
                listOf(stringResource(R.string.mode_full), stringResource(R.string.download), stringResource(R.string.upload), stringResource(R.string.mode_both), stringResource(R.string.ping)),
                options.mode.ordinal,
                { onChange(options.copy(mode = TestMode.entries[it])) },
                enabled = enabled,
            )
        }
        SwitchRow(
            title = stringResource(R.string.opt_continuous),
            subtitle = stringResource(
                when (options.mode) {
                    TestMode.Full -> R.string.opt_continuous_full
                    TestMode.Ping -> R.string.opt_continuous_ping
                    else -> R.string.opt_continuous_single
                },
            ),
            checked = options.continuous,
            onCheckedChange = { onChange(options.copy(continuous = it)) },
            enabled = enabled,
        )
        val iperf = options.mode != TestMode.Ping
        val openEnded = options.continuous && options.mode != TestMode.Full
        if (iperf || !openEnded) InsetDivider(start = 20.dp)
        AnimatedVisibility(iperf, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                SettingBlock(stringResource(R.string.opt_protocol)) {
                    SegmentedControl(listOf("TCP", "UDP"), options.protocol.ordinal, { onChange(options.copy(protocol = Protocol.entries[it])) }, enabled = enabled)
                }
                SettingBlock(stringResource(R.string.opt_streams)) {
                    SegmentedControl(
                        TestOptions.Streams.map { it.toString() },
                        TestOptions.Streams.indexOf(options.streams),
                        { onChange(options.copy(streams = TestOptions.Streams[it])) },
                        enabled = enabled,
                    )
                }
            }
        }
        AnimatedVisibility(!openEnded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SettingBlock(stringResource(if (options.mode == TestMode.Full) R.string.opt_duration_each else R.string.opt_duration)) {
                SegmentedControl(
                    TestOptions.Durations.map { "${it} s" },
                    TestOptions.Durations.indexOf(options.durationSec),
                    { onChange(options.copy(durationSec = TestOptions.Durations[it])) },
                    enabled = enabled,
                )
            }
        }
        AnimatedVisibility(iperf && options.protocol == Protocol.UDP, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SettingBlock(stringResource(R.string.opt_udp_rate), trailing = "Mbps") {
                SegmentedControl(
                    TestOptions.UdpRates.map { if (it >= 1000) "${it / 1000}G" else it.toString() },
                    TestOptions.UdpRates.indexOf(options.udpMbps),
                    { onChange(options.copy(udpMbps = TestOptions.UdpRates[it])) },
                    enabled = enabled,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Live (or last) throughput over time, plus running totals in continuous mode. */
@Composable
fun ChartCard(vm: SpeedTestViewModel, pingMode: Boolean, modifier: Modifier = Modifier, chartHeight: Dp = 180.dp) {
    if (pingMode) {
        LatencyChartCard(vm, modifier, chartHeight)
        return
    }
    val colors = OneUi.colors
    val continuous = vm.sessionContinuous && vm.sessionMode != TestMode.Full
    val hasData = vm.downSeries.isNotEmpty() || vm.upSeries.isNotEmpty()
    GroupCard(modifier) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chart_title), style = OneUi.type.section, color = colors.textSection, modifier = Modifier.weight(1f))
            if (vm.downSeries.isNotEmpty()) {
                LegendDot(colors.download.end)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.download), style = OneUi.type.caption, color = colors.textSecondary)
                Spacer(Modifier.width(10.dp))
            }
            if (vm.upSeries.isNotEmpty()) {
                LegendDot(colors.upload.end)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.upload), style = OneUi.type.caption, color = colors.textSecondary)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (hasData) {
                val step = 0.25
                val stage = vm.stage
                ThroughputChart(
                    series = buildList {
                        if (vm.downSeries.isNotEmpty()) add(ChartSeries(vm.downSeries, step, colors.download, faded = stage == Stage.Upload))
                        if (vm.upSeries.isNotEmpty()) add(ChartSeries(vm.upSeries, step, colors.upload))
                    },
                    spanSec = if (vm.phaseDuration > 0) vm.phaseDuration.toDouble() else 10.0,
                    windowSec = if (continuous) CONTINUOUS_WINDOW_SEC else null,
                    live = vm.running && stage != Stage.Ping,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    stringResource(R.string.chart_empty),
                    style = OneUi.type.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        if (continuous && hasData) {
            ContinuousStats(vm)
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun LatencyChartCard(vm: SpeedTestViewModel, modifier: Modifier, chartHeight: Dp) {
    val colors = OneUi.colors
    val series = vm.pingSeries
    GroupCard(modifier) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.latency), style = OneUi.type.section, color = colors.textSection, modifier = Modifier.weight(1f))
            if (series.any { it.isNaN() }) {
                LegendDot(colors.danger, size = 8.dp)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.lost), style = OneUi.type.caption, color = colors.textSecondary)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (series.isNotEmpty()) {
                ThroughputChart(
                    series = listOf(ChartSeries(series, PING_INTERVAL, colors.ping)),
                    spanSec = if (vm.phaseDuration > 0) vm.phaseDuration.toDouble() else 10.0,
                    windowSec = if (vm.sessionContinuous) CONTINUOUS_WINDOW_SEC else null,
                    live = vm.running,
                    axisLabel = ::msAxisLabel,
                    fromZero = false,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    stringResource(R.string.chart_empty_ping),
                    style = OneUi.type.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        val samples = vm.pingSamples
        if (samples.isNotEmpty()) {
            val stats = PingStats.of(samples.toList())
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Stat(stringResource(R.string.stat_min), "${formatMs(samples.min())} ms", Modifier.weight(1f))
                Stat(stringResource(R.string.stat_median), stats?.let { "${formatMs(it.medianMs)} ms" } ?: "–", Modifier.weight(1f))
                Stat(stringResource(R.string.stat_max), "${formatMs(samples.max())} ms", Modifier.weight(1f))
                Stat(stringResource(R.string.detail_jitter), stats?.let { "${formatMs(it.jitterMs)} ms" } ?: "–", Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ContinuousStats(vm: SpeedTestViewModel) {
    val colors = OneUi.colors
    // One row per measured direction; a two-way session gets both, marked by colour.
    val rows = when (vm.sessionMode) {
        TestMode.Upload -> listOf(Triple(vm.upSeries.toList(), vm.steadyUpMbps, colors.upload))
        TestMode.Bidirectional -> listOf(
            Triple(vm.downSeries.toList(), vm.steadyDownMbps, colors.download),
            Triple(vm.upSeries.toList(), vm.steadyUpMbps, colors.upload),
        )
        else -> listOf(Triple(vm.downSeries.toList(), vm.steadyDownMbps, colors.download))
    }
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { index, (series, avg, palette) ->
            val settled = if (series.size > RAMP_SAMPLES) series.drop(RAMP_SAMPLES) else series
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (rows.size > 1) LegendDot(palette.end, size = 8.dp)
                Stat(stringResource(R.string.stat_avg), formatMbps(avg).toString(), Modifier.weight(1f))
                Stat(stringResource(R.string.stat_low), settled.minOrNull()?.let { formatMbps(it.toDouble()).toString() } ?: "–", Modifier.weight(1f))
                Stat(stringResource(R.string.stat_peak), series.maxOrNull()?.let { formatMbps(it.toDouble()).toString() } ?: "–", Modifier.weight(1f))
                if (index == 0) {
                    Stat(stringResource(R.string.stat_data), formatBytes(vm.liveBytes), Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (vm.running) {
        Text(
            stringResource(R.string.continuous_hint),
            style = OneUi.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = OneUi.type.itemValue, color = OneUi.colors.textPrimary, maxLines = 1)
        Text(label, style = OneUi.type.caption, color = OneUi.colors.textSecondary, maxLines = 1)
    }
}

@Composable
fun ErrorCard(error: TestError, onDismiss: () -> Unit, onOpenServers: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OneUi.colors
    val title = errorTitle(error.kind)
    GroupCard(modifier) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.danger, modifier = Modifier.padding(top = 2.dp).size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = OneUi.type.itemTitle.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                Text(error.detail, style = OneUi.type.itemSubtitle, color = colors.textSecondary)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_dismiss), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.padding(start = 54.dp, top = 10.dp, bottom = 14.dp)) {
            if (error.kind == IperfException.Kind.LocalNetwork) {
                val context = LocalContext.current
                PillButton(stringResource(R.string.action_open_settings), {
                    onDismiss()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                })
            } else {
                PillButton(stringResource(R.string.action_change_server), onOpenServers, filled = false)
            }
        }
    }
}

/** Width cap for cards on wide panes, so text lines stay readable. */
val MaxCardWidth = 640.dp

fun Modifier.cardWidth(): Modifier = this.widthIn(max = MaxCardWidth)

/**
 * Starts a test, first asking for the local network permission ("Nearby devices") when the
 * server is on the LAN and Android 17 would otherwise block the connection.
 */
@Composable
fun rememberStartAction(vm: SpeedTestViewModel, server: Server?): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.reportLocalNetworkDenied()
    }
    return remember(server, launcher) {
        {
            scope.launch {
                val host = server?.host
                if (host != null && localNetworkPermissionMissing(context) && isLocalNetworkHost(host)) {
                    launcher.launch(ACCESS_LOCAL_NETWORK)
                } else {
                    vm.start()
                }
            }
        }
    }
}

@Composable
fun errorTitle(kind: IperfException.Kind): String = stringResource(
    when (kind) {
        IperfException.Kind.Busy -> R.string.err_busy
        IperfException.Kind.Unreachable -> R.string.err_unreachable
        IperfException.Kind.UnknownHost -> R.string.err_unknown_host
        IperfException.Kind.Timeout -> R.string.err_timeout
        IperfException.Kind.UdpBlocked -> R.string.err_udp
        IperfException.Kind.AuthRequired -> R.string.err_auth
        IperfException.Kind.DurationLimit -> R.string.err_duration
        IperfException.Kind.LocalNetwork -> R.string.err_local_network
        IperfException.Kind.PingBlocked -> R.string.err_ping_blocked
        else -> R.string.err_generic
    },
)
