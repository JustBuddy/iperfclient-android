package wtf.buddyworks.iperfclient.ui

import android.content.Context
import android.text.format.DateUtils
import wtf.buddyworks.iperfclient.R
import wtf.buddyworks.iperfclient.net.ConnectionInfo
import wtf.buddyworks.iperfclient.net.Transport
import java.util.Locale
import kotlin.math.abs

/** A bitrate split into number and unit, e.g. ("943", "Mbps") or ("1.24", "Gbps"). */
data class Rate(val number: String, val unit: String) {
    override fun toString() = "$number $unit"
}

fun formatMbps(mbps: Double): Rate {
    val v = abs(mbps)
    return when {
        v >= 1000 -> Rate(String.format(Locale.getDefault(), if (v >= 10_000) "%.1f" else "%.2f", mbps / 1000), "Gbps")
        v >= 100 -> Rate(String.format(Locale.getDefault(), "%.0f", mbps), "Mbps")
        v >= 10 -> Rate(String.format(Locale.getDefault(), "%.1f", mbps), "Mbps")
        v >= 0.995 -> Rate(String.format(Locale.getDefault(), "%.2f", mbps), "Mbps")
        v > 0 -> Rate(String.format(Locale.getDefault(), "%.0f", mbps * 1000), "Kbps")
        else -> Rate("0", "Mbps")
    }
}

/** A number on a fixed Mbps unit, for steady gauge readouts that should not jump between units. */
fun formatGaugeMbps(mbps: Float): String = when {
    mbps >= 100 -> String.format(Locale.getDefault(), "%.0f", mbps)
    mbps >= 10 -> String.format(Locale.getDefault(), "%.1f", mbps)
    else -> String.format(Locale.getDefault(), "%.2f", mbps)
}

fun formatMs(ms: Double): String = when {
    ms >= 100 -> String.format(Locale.getDefault(), "%.0f", ms)
    ms >= 10 -> String.format(Locale.getDefault(), "%.1f", ms)
    else -> String.format(Locale.getDefault(), "%.1f", ms)
}

fun formatPercent(p: Double): String =
    String.format(Locale.getDefault(), if (p == 0.0 || p >= 10) "%.0f" else if (p >= 1) "%.1f" else "%.2f", p)

fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1000 && i < units.lastIndex) {
        v /= 1000
        i++
    }
    return if (i == 0) "$bytes B" else String.format(Locale.getDefault(), if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

fun formatDuration(seconds: Double): String {
    val s = seconds.toLong()
    return if (s >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }
}

fun formatWhen(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

fun describeConnection(context: Context, info: ConnectionInfo): String? {
    val parts = buildList {
        when (info.transport) {
            Transport.WiFi -> {
                add(info.wifiGeneration?.let { context.getString(R.string.net_wifi_gen, it) } ?: context.getString(R.string.net_wifi))
                info.bandGhz?.let { add("$it GHz") }
                info.linkMbps?.let { add(context.getString(R.string.net_link, it)) }
            }
            Transport.Ethernet -> add(context.getString(R.string.net_ethernet))
            Transport.Cellular -> add(context.getString(R.string.net_mobile))
            Transport.None -> return null
        }
        if (info.vpn) add(context.getString(R.string.net_vpn))
    }
    return parts.joinToString(" · ")
}
