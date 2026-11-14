package wtf.buddyworks.iperfclient.data

import wtf.buddyworks.iperfclient.iperf.Json
import wtf.buddyworks.iperfclient.iperf.Protocol
import wtf.buddyworks.iperfclient.iperf.asList
import wtf.buddyworks.iperfclient.iperf.asObj
import wtf.buddyworks.iperfclient.iperf.bool
import wtf.buddyworks.iperfclient.iperf.num
import wtf.buddyworks.iperfclient.iperf.str

/** An iperf3 server. Public servers often run several instances on a port range; busy ones are skipped. */
data class Server(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 5201,
    val portEnd: Int = port,
    val note: String? = null,
) {
    val ports: IntRange get() = port..maxOf(port, portEnd)

    val address: String
        get() = if (portEnd > port) "$host:$port–$portEnd" else if (port == 5201) host else "$host:$port"

    fun toJson(): Map<String, Any?> = mapOf("id" to id, "name" to name, "host" to host, "port" to port, "portEnd" to portEnd, "note" to note)

    companion object {
        fun fromJson(o: Map<String, Any?>): Server? {
            val host = o.str("host") ?: return null
            val port = o.num("port")?.toInt() ?: 5201
            return Server(
                id = o.str("id") ?: host,
                name = o.str("name") ?: host,
                host = host,
                port = port,
                portEnd = o.num("portEnd")?.toInt() ?: port,
                note = o.str("note"),
            )
        }

        /** Well-known public iperf3 servers, offered on first start. */
        val Public = listOf(
            Server("pub-online", "Scaleway", "ping.online.net", 5200, 5209, "Paris, France"),
            Server("pub-bouygues", "Bouygues Telecom", "bouygues.iperf.fr", 5200, 5209, "Paris, France"),
            Server("pub-he", "Hurricane Electric", "iperf.he.net", 5201, 5201, "Fremont, USA"),
            Server("pub-serverius", "Serverius", "speedtest.serverius.net", 5002, 5002, "Dronten, Netherlands"),
        )
    }
}

enum class TestMode { Full, Download, Upload, Bidirectional, Ping }

data class TestOptions(
    val mode: TestMode = TestMode.Full,
    val protocol: Protocol = Protocol.TCP,
    val streams: Int = 4,
    val durationSec: Int = 10,
    val udpMbps: Int = 100,
    /** Keep measuring until stopped: one open-ended session, or repeated cycles in Full mode. */
    val continuous: Boolean = false,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "mode" to mode.name, "protocol" to protocol.name, "streams" to streams,
        "duration" to durationSec, "udpMbps" to udpMbps, "continuous" to continuous,
    )

    companion object {
        val Streams = listOf(1, 2, 4, 8, 16)
        val Durations = listOf(5, 10, 15, 30, 60)
        val UdpRates = listOf(10, 50, 100, 250, 500, 1000)

        fun fromJson(o: Map<String, Any?>) = TestOptions(
            mode = o.str("mode")?.let { runCatching { TestMode.valueOf(it) }.getOrNull() } ?: TestMode.Full,
            protocol = o.str("protocol")?.let { runCatching { Protocol.valueOf(it) }.getOrNull() } ?: Protocol.TCP,
            streams = o.num("streams")?.toInt()?.coerceIn(1, 32) ?: 4,
            durationSec = o.num("duration")?.toInt()?.coerceIn(1, 3600) ?: 10,
            udpMbps = o.num("udpMbps")?.toInt()?.coerceIn(1, 10_000) ?: 100,
            continuous = o.bool("continuous") ?: false,
        )
    }
}

/** One direction of a finished test. */
data class PhaseRecord(
    val bitsPerSecond: Double,
    val durationSec: Double,
    val bytes: Long,
    val retransmits: Long? = null,
    val jitterMs: Double? = null,
    val lossPercent: Double? = null,
    /** Loaded latency: median ping while this direction was busy. */
    val loadedPingMs: Double? = null,
    /** Throughput in Mbps, one point every [seriesStepSec]. */
    val series: List<Float> = emptyList(),
    val seriesStepSec: Double = 0.25,
) {
    val peakBps: Double get() = (series.maxOrNull()?.toDouble() ?: 0.0) * 1e6

    fun toJson(): Map<String, Any?> = mapOf(
        "bps" to bitsPerSecond, "duration" to durationSec, "bytes" to bytes, "retransmits" to retransmits,
        "jitter" to jitterMs, "loss" to lossPercent, "loadedPing" to loadedPingMs,
        "series" to series, "step" to seriesStepSec,
    )

    companion object {
        fun fromJson(o: Map<String, Any?>) = PhaseRecord(
            bitsPerSecond = o.num("bps") ?: 0.0,
            durationSec = o.num("duration") ?: 0.0,
            bytes = o.num("bytes")?.toLong() ?: 0,
            retransmits = o.num("retransmits")?.toLong(),
            jitterMs = o.num("jitter"),
            lossPercent = o.num("loss"),
            loadedPingMs = o.num("loadedPing"),
            series = o["series"].asList().mapNotNull { (it as? Number)?.toFloat() },
            seriesStepSec = o.num("step") ?: 0.25,
        )

        /** Keeps long continuous sessions small on disk: at most [max] points. */
        fun downsample(points: List<Float>, step: Double, max: Int = 480): Pair<List<Float>, Double> {
            if (points.size <= max) return points to step
            val factor = (points.size + max - 1) / max
            return points.chunked(factor) { it.average().toFloat() } to step * factor
        }
    }
}

/** A ping-only test: every probe in order, lost ones as NaN. */
data class PingRecord(
    val series: List<Float>,
    val stepSec: Double,
    val sent: Int,
    val received: Int,
    val minMs: Double?,
    val maxMs: Double?,
    val durationSec: Double,
) {
    val lossPercent: Double get() = if (sent > 0) (sent - received).coerceAtLeast(0) * 100.0 / sent else 0.0

    fun toJson(): Map<String, Any?> = mapOf(
        // JSON has no NaN: lost probes are stored as -1.
        "series" to series.map { if (it.isNaN()) -1f else it }, "step" to stepSec,
        "sent" to sent, "received" to received, "min" to minMs, "max" to maxMs, "duration" to durationSec,
    )

    companion object {
        fun fromJson(o: Map<String, Any?>) = PingRecord(
            series = o["series"].asList().mapNotNull { (it as? Number)?.toFloat()?.let { v -> if (v < 0f) Float.NaN else v } },
            stepSec = o.num("step") ?: 0.2,
            sent = o.num("sent")?.toInt() ?: 0,
            received = o.num("received")?.toInt() ?: 0,
            minMs = o.num("min"),
            maxMs = o.num("max"),
            durationSec = o.num("duration") ?: 0.0,
        )

        /** Keeps long sessions small on disk: buckets of probes become their mean (NaN if all were lost). */
        fun downsample(points: List<Float>, step: Double, max: Int = 600): Pair<List<Float>, Double> {
            if (points.size <= max) return points to step
            val factor = (points.size + max - 1) / max
            return points.chunked(factor) { c -> c.filterNot { it.isNaN() }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: Float.NaN } to step * factor
        }
    }
}

data class TestRecord(
    val id: Long,
    val serverName: String,
    val host: String,
    val port: Int,
    val protocol: Protocol,
    val mode: TestMode,
    val streams: Int,
    val continuous: Boolean,
    val pingMs: Double?,
    val pingJitterMs: Double?,
    /** True when ping came from TCP handshakes because ICMP got no answer. */
    val pingIsTcp: Boolean,
    val download: PhaseRecord?,
    val upload: PhaseRecord?,
    val network: String?,
    val pingDetail: PingRecord? = null,
) {
    fun toJson(): Map<String, Any?> = mapOf(
        "id" to id, "server" to serverName, "host" to host, "port" to port, "protocol" to protocol.name,
        "mode" to mode.name, "streams" to streams, "continuous" to continuous,
        "ping" to pingMs, "pingJitter" to pingJitterMs, "pingTcp" to pingIsTcp,
        "download" to download?.toJson(), "upload" to upload?.toJson(), "network" to network,
        "pingDetail" to pingDetail?.toJson(),
    )

    companion object {
        fun fromJson(o: Map<String, Any?>): TestRecord? {
            val id = o.num("id")?.toLong() ?: return null
            return TestRecord(
                id = id,
                serverName = o.str("server") ?: "",
                host = o.str("host") ?: "",
                port = o.num("port")?.toInt() ?: 5201,
                protocol = runCatching { Protocol.valueOf(o.str("protocol") ?: "TCP") }.getOrDefault(Protocol.TCP),
                mode = runCatching { TestMode.valueOf(o.str("mode") ?: "Full") }.getOrDefault(TestMode.Full),
                streams = o.num("streams")?.toInt() ?: 1,
                continuous = o.bool("continuous") ?: false,
                pingMs = o.num("ping"),
                pingJitterMs = o.num("pingJitter"),
                pingIsTcp = o.bool("pingTcp") ?: false,
                download = o["download"]?.asObj()?.let(PhaseRecord::fromJson),
                upload = o["upload"]?.asObj()?.let(PhaseRecord::fromJson),
                network = o.str("network"),
                pingDetail = o["pingDetail"]?.asObj()?.let(PingRecord::fromJson),
            )
        }

        fun listFromJson(text: String): List<TestRecord> =
            runCatching { Json.parse(text).asList().mapNotNull { fromJson(it.asObj()) } }.getOrDefault(emptyList())
    }
}
