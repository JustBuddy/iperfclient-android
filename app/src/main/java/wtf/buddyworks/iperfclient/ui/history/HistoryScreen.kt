package wtf.buddyworks.iperfclient.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wtf.buddyworks.iperfclient.R
import wtf.buddyworks.iperfclient.data.PhaseRecord
import wtf.buddyworks.iperfclient.data.TestMode
import wtf.buddyworks.iperfclient.data.TestRecord
import wtf.buddyworks.iperfclient.ui.PaneConfig
import wtf.buddyworks.iperfclient.ui.components.CardMargin
import wtf.buddyworks.iperfclient.ui.components.CollapsingPane
import wtf.buddyworks.iperfclient.ui.components.GroupCard
import wtf.buddyworks.iperfclient.ui.components.InsetDivider
import wtf.buddyworks.iperfclient.ui.components.LegendDot
import wtf.buddyworks.iperfclient.ui.components.SectionHeader
import wtf.buddyworks.iperfclient.ui.components.ToolbarIcon
import wtf.buddyworks.iperfclient.ui.components.groupItem
import wtf.buddyworks.iperfclient.ui.formatBytes
import wtf.buddyworks.iperfclient.ui.formatDuration
import wtf.buddyworks.iperfclient.ui.formatMbps
import wtf.buddyworks.iperfclient.ui.formatMs
import wtf.buddyworks.iperfclient.ui.formatPercent
import wtf.buddyworks.iperfclient.ui.formatWhen
import wtf.buddyworks.iperfclient.ui.test.ChartSeries
import wtf.buddyworks.iperfclient.ui.test.ThroughputChart
import wtf.buddyworks.iperfclient.ui.test.msAxisLabel
import wtf.buddyworks.iperfclient.ui.test.niceCeil
import wtf.buddyworks.iperfclient.ui.theme.OneUi
import wtf.buddyworks.iperfclient.ui.theme.SpeedPalette

@Composable
fun HistoryListPane(
    records: List<TestRecord>,
    selectedId: Long?,
    onSelect: (TestRecord) -> Unit,
    onClear: () -> Unit,
    config: PaneConfig,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    CollapsingPane(
        title = stringResource(R.string.nav_history),
        subtitle = if (records.isEmpty()) null else pluralStringResource(R.plurals.tests_count, records.size, records.size),
        expandable = config.expandableTitle,
        listState = listState,
        topInset = config.topInset,
        bottomPadding = config.bottomPadding,
        modifier = modifier,
        actions = {
            if (records.isNotEmpty()) ToolbarIcon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_clear_history), { confirmClear = true })
        },
    ) {
        if (records.isEmpty()) {
            item(key = "empty") { EmptyHistory() }
            return@CollapsingPane
        }
        if (records.count { it.mode != TestMode.Ping } >= 2) {
            item(key = "trend") { TrendCard(records, Modifier.padding(horizontal = CardMargin)) }
        }
        item(key = "header") { SectionHeader(stringResource(R.string.history_recent)) }
        itemsIndexed(records, key = { _, r -> r.id }) { index, record ->
            HistoryRow(
                record = record,
                selected = record.id == selectedId && config.twoPane,
                isFirst = index == 0,
                isLast = index == records.lastIndex,
                onClick = { onSelect(record) },
                modifier = Modifier.padding(horizontal = CardMargin),
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_text)) },
            confirmButton = {
                TextButton({ confirmClear = false; onClear() }) { Text(stringResource(R.string.action_clear), color = OneUi.colors.danger) }
            },
            dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = OneUi.colors.card,
            shape = OneUi.cardShape,
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = OneUi.colors.textSection, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.history_empty), style = OneUi.type.body, color = OneUi.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Download and upload of the last tests as two sparklines, oldest on the left. */
@Composable
private fun TrendCard(records: List<TestRecord>, modifier: Modifier = Modifier) {
    val colors = OneUi.colors
    val recent = records.filter { it.mode != TestMode.Ping }.take(30).reversed()
    // Each line only uses the tests that measured its direction.
    val downs = recent.mapNotNull { it.download?.bitsPerSecond?.div(1e6) }
    val ups = recent.mapNotNull { it.upload?.bitsPerSecond?.div(1e6) }
    val withDown = records.mapNotNull { it.download?.bitsPerSecond }.take(30)
    val withUp = records.mapNotNull { it.upload?.bitsPerSecond }.take(30)
    GroupCard(modifier.padding(top = 4.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_trend), style = OneUi.type.section, color = colors.textSection, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.history_trend_n, recent.size), style = OneUi.type.caption, color = colors.textSecondary)
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (withDown.isNotEmpty()) AverageBlock(stringResource(R.string.avg_download), withDown.average() / 1e6, colors.download)
            if (withUp.isNotEmpty()) AverageBlock(stringResource(R.string.avg_upload), withUp.average() / 1e6, colors.upload)
        }
        Sparklines(
            listOfNotNull(
                downs.takeIf { withDown.isNotEmpty() }?.let { it to colors.download },
                ups.takeIf { withUp.isNotEmpty() }?.let { it to colors.upload },
            ),
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun AverageBlock(label: String, mbps: Double, palette: SpeedPalette) {
    val rate = formatMbps(mbps)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(palette.end, size = 8.dp)
            Spacer(Modifier.width(6.dp))
            Text(label, style = OneUi.type.caption, color = OneUi.colors.textSecondary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(rate.number, style = OneUi.type.bigNumber, color = OneUi.colors.textPrimary)
            Spacer(Modifier.width(4.dp))
            Text(rate.unit, style = OneUi.type.caption, color = palette.text, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
private fun Sparklines(lines: List<Pair<List<Double>, SpeedPalette>>, modifier: Modifier) {
    Canvas(modifier) {
        val max = niceCeil((lines.flatMap { it.first }.maxOrNull() ?: 1.0).toFloat().coerceAtLeast(1f))
        for ((values, palette) in lines) {
            if (values.size < 2) continue
            val pts = values.mapIndexed { i, v ->
                Offset(size.width * i / (values.size - 1), size.height - (v / max).toFloat() * size.height)
            }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val mx = (pts[i - 1].x + pts[i].x) / 2
                    cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                }
            }
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(palette.start.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(path, Brush.horizontalGradient(listOf(palette.start, palette.end)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(palette.end, 3.5.dp.toPx(), pts.last())
        }
    }
}

@Composable
private fun HistoryRow(
    record: TestRecord,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneUi.colors
    val context = LocalContext.current
    Column(modifier.groupItem(isFirst, isLast, if (selected) colors.selection else colors.card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeBadge(record)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (record.mode == TestMode.Ping) PingInline(record)
                    record.download?.let { RateInline(Icons.Rounded.ArrowDownward, it, colors.download) }
                    if (record.download != null && record.upload != null) Spacer(Modifier.width(14.dp))
                    record.upload?.let { RateInline(Icons.Rounded.ArrowUpward, it, colors.upload) }
                }
                val ping = record.pingMs?.takeIf { record.mode != TestMode.Ping }?.let { stringResource(R.string.ping_short, formatMs(it)) }
                Text(
                    listOfNotNull(record.serverName, formatWhen(context, record.id), ping).joinToString(" · "),
                    style = OneUi.type.itemSubtitle,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!isLast) InsetDivider(start = 74.dp)
    }
}

@Composable
private fun RateInline(icon: ImageVector, phase: PhaseRecord, palette: SpeedPalette) {
    val rate = formatMbps(phase.bitsPerSecond / 1e6)
    Icon(icon, contentDescription = null, tint = palette.text, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(2.dp))
    Text(rate.number, style = OneUi.type.itemTitle.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = OneUi.colors.textPrimary)
    Spacer(Modifier.width(3.dp))
    Text(rate.unit, style = OneUi.type.caption, color = OneUi.colors.textSecondary)
}

@Composable
private fun PingInline(record: TestRecord) {
    Icon(Icons.Rounded.NetworkPing, contentDescription = null, tint = OneUi.colors.ping.text, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(record.pingMs?.let { formatMs(it) } ?: "–", style = OneUi.type.itemTitle.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = OneUi.colors.textPrimary)
    Spacer(Modifier.width(3.dp))
    Text("ms", style = OneUi.type.caption, color = OneUi.colors.textSecondary)
    record.pingDetail?.let {
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.loss_short, formatPercent(it.lossPercent)),
            style = OneUi.type.caption,
            color = if (it.lossPercent > 0) OneUi.colors.danger else OneUi.colors.textSecondary,
        )
    }
}

@Composable
private fun ModeBadge(record: TestRecord) {
    val colors = OneUi.colors
    val (icon, palette) = when (record.mode) {
        TestMode.Full -> Icons.Rounded.Speed to colors.download
        TestMode.Download -> Icons.Rounded.ArrowDownward to colors.download
        TestMode.Upload -> Icons.Rounded.ArrowUpward to colors.upload
        TestMode.Bidirectional -> Icons.Rounded.SwapVert to colors.upload
        TestMode.Ping -> Icons.Rounded.NetworkPing to colors.ping
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(palette.start, palette.end))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (record.continuous && record.mode != TestMode.Full && record.mode != TestMode.Ping) Icons.Rounded.AllInclusive else icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Everything recorded about one test. */
@Composable
fun HistoryDetail(record: TestRecord, onDelete: () -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier, showDelete: Boolean = true) {
    val colors = OneUi.colors
    val context = LocalContext.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "head") {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(record.serverName, style = OneUi.type.headline, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatWhen(context, record.id), style = OneUi.type.body, color = colors.textSecondary)
            }
        }
        val pingDetail = record.pingDetail
        if (pingDetail != null) {
            pingDetailItems(record, pingDetail)
        } else item(key = "numbers") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BigFigure(stringResource(R.string.ping), record.pingMs?.let { formatMs(it) }, "ms", Icons.Rounded.NetworkPing, colors.ping, Modifier.weight(1f))
                BigFigure(stringResource(R.string.download), record.download?.let { formatMbps(it.bitsPerSecond / 1e6).number }, record.download?.let { formatMbps(it.bitsPerSecond / 1e6).unit } ?: "Mbps", Icons.Rounded.ArrowDownward, colors.download, Modifier.weight(1f))
                BigFigure(stringResource(R.string.upload), record.upload?.let { formatMbps(it.bitsPerSecond / 1e6).number }, record.upload?.let { formatMbps(it.bitsPerSecond / 1e6).unit } ?: "Mbps", Icons.Rounded.ArrowUpward, colors.upload, Modifier.weight(1f))
            }
        }
        val series = if (pingDetail != null) emptyList() else listOfNotNull(
            record.download?.takeIf { it.series.isNotEmpty() }?.let { ChartSeries(it.series, it.seriesStepSec, colors.download) },
            record.upload?.takeIf { it.series.isNotEmpty() }?.let { ChartSeries(it.series, it.seriesStepSec, colors.upload) },
        )
        if (series.isNotEmpty()) {
            item(key = "chart") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ThroughputChart(
                        series = series,
                        spanSec = series.maxOf { it.points.size * it.stepSec },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                    )
                }
            }
        }
        item(key = "details") {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                record.download?.let { PhaseDetails(stringResource(R.string.download), it, colors.download) }
                record.upload?.let { PhaseDetails(stringResource(R.string.upload), it, colors.upload) }
                if (pingDetail != null) {
                    DetailRow(stringResource(R.string.detail_duration), formatDuration(pingDetail.durationSec))
                    DetailRow(stringResource(R.string.detail_replies), pluralStringResource(R.plurals.lost_of, pingDetail.sent - pingDetail.received, pingDetail.sent - pingDetail.received, pingDetail.sent))
                    pingDetail.minMs?.let { DetailRow(stringResource(R.string.stat_min), "${formatMs(it)} ms") }
                    pingDetail.maxMs?.let { DetailRow(stringResource(R.string.stat_max), "${formatMs(it)} ms") }
                }
                Text(stringResource(R.string.detail_test), style = OneUi.type.section, color = colors.textSection, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                DetailRow(stringResource(R.string.detail_server), "${record.host}:${record.port}")
                if (record.mode != TestMode.Ping) {
                    val streams = pluralStringResource(R.plurals.streams_count, record.streams, record.streams)
                    val continuous = stringResource(R.string.opt_continuous)
                    DetailRow(
                        stringResource(R.string.detail_type),
                        listOfNotNull(record.protocol.name, streams, continuous.takeIf { record.continuous }).joinToString(" · "),
                    )
                }
                record.pingJitterMs?.let { DetailRow(stringResource(if (record.pingIsTcp) R.string.detail_ping_jitter_tcp else R.string.detail_ping_jitter), "${formatMs(it)} ms") }
                record.network?.let { DetailRow(stringResource(R.string.connection), it) }
            }
        }
        if (showDelete) {
            item(key = "delete") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_delete), style = OneUi.type.button, color = colors.danger)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pingDetailItems(record: TestRecord, ping: wtf.buddyworks.iperfclient.data.PingRecord) {
    item(key = "ping-numbers") {
        val colors = OneUi.colors
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BigFigure(stringResource(R.string.latency), record.pingMs?.let { formatMs(it) }, "ms", Icons.Rounded.NetworkPing, colors.ping, Modifier.weight(1f))
            BigFigure(stringResource(R.string.detail_jitter), record.pingJitterMs?.let { formatMs(it) }, "ms", Icons.Rounded.SwapVert, colors.download, Modifier.weight(1f))
            BigFigure(stringResource(R.string.loss), formatPercent(ping.lossPercent), "%", Icons.Rounded.Close, colors.upload, Modifier.weight(1f))
        }
    }
    if (ping.series.isNotEmpty()) {
        item(key = "ping-chart") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                ThroughputChart(
                    series = listOf(ChartSeries(ping.series, ping.stepSec, OneUi.colors.ping)),
                    spanSec = ping.series.size * ping.stepSec,
                    axisLabel = ::msAxisLabel,
                    fromZero = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                )
            }
        }
    }
}

@Composable
private fun PhaseDetails(title: String, phase: PhaseRecord, palette: SpeedPalette) {
    Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(palette.end, size = 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(title, style = OneUi.type.section, color = OneUi.colors.textSection)
    }
    DetailRow(stringResource(R.string.detail_duration), formatDuration(phase.durationSec))
    DetailRow(stringResource(R.string.detail_data), formatBytes(phase.bytes))
    if (phase.series.isNotEmpty()) DetailRow(stringResource(R.string.stat_peak), formatMbps(phase.peakBps / 1e6).toString())
    phase.loadedPingMs?.let { DetailRow(stringResource(R.string.detail_loaded_latency), "${formatMs(it)} ms") }
    phase.retransmits?.let { DetailRow(stringResource(R.string.detail_retransmits), it.toString()) }
    phase.jitterMs?.let { DetailRow(stringResource(R.string.detail_jitter), "${formatMs(it)} ms") }
    phase.lossPercent?.let { DetailRow(stringResource(R.string.detail_loss), "${formatPercent(it)} %") }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = OneUi.type.body, color = OneUi.colors.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = OneUi.type.itemValue, color = OneUi.colors.textPrimary, textAlign = TextAlign.End)
    }
}

@Composable
private fun BigFigure(label: String, value: String?, unit: String, icon: ImageVector, palette: SpeedPalette, modifier: Modifier) {
    val colors = OneUi.colors
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (colors.isDark) colors.highlight else colors.background)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = palette.text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = OneUi.type.caption, color = colors.textSecondary, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "–", style = OneUi.type.bigNumber.copy(fontSize = 24.sp), color = if (value == null) colors.textSecondary else colors.textPrimary, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            Text(unit, style = OneUi.type.caption, color = palette.text, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
