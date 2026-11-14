package wtf.buddyworks.iperfclient.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.math.abs

/** One echo reply: its sequence number (from 1) and round-trip time. */
data class PingReply(val seq: Int, val ms: Double)

/**
 * ICMP echo through the system `ping` tool, which Android lets any app run. Emits one reply per
 * answered probe; stops after [count] probes (0 = until the flow is cancelled). Lost probes show
 * up as gaps in the sequence numbers.
 */
fun pingFlow(host: String, count: Int, intervalSec: Double = 0.2): Flow<PingReply> = callbackFlow {
    val v6 = runCatching { InetAddress.getByName(host) is Inet6Address }.getOrDefault(false)
    val args = buildList {
        add(if (v6) "/system/bin/ping6" else "/system/bin/ping")
        add("-n")
        if (count > 0) { add("-c"); add(count.toString()) }
        add("-i"); add("%.1f".format(java.util.Locale.ROOT, intervalSec.coerceAtLeast(0.2)))
        add("-W"); add("1")
        add(host)
    }
    val process = runCatching { ProcessBuilder(args).redirectErrorStream(true).start() }.getOrNull()
    if (process == null) {
        close()
        return@callbackFlow
    }
    launch(Dispatchers.IO) {
        runCatching {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val ms = TimeRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
                    val seq = SeqRegex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    trySend(PingReply(seq, ms))
                }
            }
        }
        close()
    }
    awaitClose { process.destroy() }
}.flowOn(Dispatchers.IO)

private val SeqRegex = Regex("""icmp_seq=(\d+)""")
private val TimeRegex = Regex("""time[=<]([\d.]+)\s*ms""")

/** Summary of a set of round-trip times. */
data class PingStats(val medianMs: Double, val minMs: Double, val jitterMs: Double, val count: Int) {
    companion object {
        fun of(samples: List<Double>): PingStats? {
            if (samples.isEmpty()) return null
            val sorted = samples.sorted()
            val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            // RFC 3550-style jitter: mean difference between consecutive probes.
            val jitter = if (samples.size < 2) 0.0 else samples.zipWithNext { a, b -> abs(b - a) }.average()
            return PingStats(median, sorted.first(), jitter, samples.size)
        }
    }
}
