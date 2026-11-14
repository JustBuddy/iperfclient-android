package wtf.buddyworks.iperfclient.iperf

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Protocol { TCP, UDP }

enum class Direction { Upload, Download, Bidirectional }

data class IperfRequest(
    val host: String,
    val port: Int = 5201,
    val protocol: Protocol = Protocol.TCP,
    val direction: Direction = Direction.Download,
    /** Test length; 0 runs until [Iperf3Client.finish] is called (iperf3's `-t 0`). */
    val durationSec: Int = 10,
    val parallel: Int = 1,
    /** UDP only: target bitrate in bits/s for all streams together (split evenly). */
    val udpBitrate: Long = 100_000_000,
    val connectTimeoutMs: Int = 5_000,
    /**
     * Retry briefly when the server reports it is busy. Right after a test the server can still
     * be wrapping it up; servers with several ports should rather move on to the next port.
     */
    val retryWhenBusy: Boolean = true,
)

/** A live reading, smoothed over the last half second. Rates are in bits per second. */
data class LiveSample(
    val elapsed: Double,
    val uploadBps: Double,
    val downloadBps: Double,
    val uploadBytes: Long,
    val downloadBytes: Long,
)

data class DirectionResult(
    /** Measured at the receiving end, which is what actually got through. */
    val bitsPerSecond: Double,
    val senderBitsPerSecond: Double,
    val receivedBytes: Long,
    /** TCP retransmits reported by the sender, when it can tell (Linux servers can). */
    val retransmits: Long?,
    val jitterMs: Double?,
    val lostPackets: Long?,
    val packets: Long?,
) {
    val lossPercent: Double?
        get() = if (lostPackets != null && packets != null && packets > 0) lostPackets * 100.0 / packets else null
}

data class IperfResult(
    val request: IperfRequest,
    val durationSec: Double,
    val upload: DirectionResult?,
    val download: DirectionResult?,
    /** TCP handshake times of the control and stream connections, a fallback for ICMP ping. */
    val handshakeMs: List<Double>,
    /** Live samples every [SAMPLE_PERIOD_MS] for charts. */
    val samples: List<LiveSample>,
    val remoteCongestion: String?,
    val remoteCpuPercent: Double?,
) {
    companion object {
        const val SAMPLE_PERIOD_MS = 250L
    }
}

class IperfException(
    message: String,
    val kind: Kind = Kind.Other,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { Busy, Unreachable, UnknownHost, Timeout, UdpBlocked, ServerError, DurationLimit, LocalNetwork, PingBlocked, AuthRequired, Protocol, Cancelled, Other }
}

enum class IperfPhase { Connecting, Negotiating, Running, Finishing }

interface IperfListener {
    fun onPhase(phase: IperfPhase) {}
    fun onSample(sample: LiveSample) {}
}

/**
 * A client for the iperf3 protocol, written from the reference implementation (esnet/iperf).
 *
 * The control connection carries one-byte state messages from the server; the client answers
 * with parameters and results as length-prefixed JSON. Each data stream is its own TCP
 * connection (or connected UDP socket) that opens with the test's cookie.
 *
 * [run] blocks, so call it from a background thread. [cancel] may be called from any thread.
 */
class Iperf3Client(private val request: IperfRequest) {

    @Volatile private var cancelled = false
    @Volatile private var control: Socket? = null
    private val streams = CopyOnWriteArrayList<DataStream>()

    /** Bytes only count while the test is running, as in iperf3. */
    @Volatile private var measuring = false
    @Volatile private var sending = false
    @Volatile private var stopped = false
    @Volatile private var finishRequested = false
    @Volatile private var reachedRunning = false

    private val handshakes = CopyOnWriteArrayList<Double>()

    /** Ends the test early but cleanly: results are exchanged and [run] returns them. */
    fun finish() {
        finishRequested = true
    }

    fun cancel() {
        cancelled = true
        sending = false
        // Tell the server so it can start the next test right away; ignore failures.
        runCatching { control?.getOutputStream()?.apply { write(CLIENT_TERMINATE); flush() } }
        closeAll()
    }

    fun run(listener: IperfListener = object : IperfListener {}): IperfResult {
        require(request.parallel in 1..MAX_STREAMS) { "parallel must be 1..$MAX_STREAMS" }
        require(request.durationSec in 0..MAX_DURATION) { "duration must be 0..$MAX_DURATION s" }
        var attempt = 0
        while (true) {
            val started = System.nanoTime()
            try {
                return attempt(listener)
            } catch (e: IperfException) {
                // Between tests iperf3 closes and reopens its listening socket, so a client that
                // connects right after another test can be refused or reset. Such quick failures
                // before any data flowed are retried; slow ones (timeouts) are real.
                val quick = System.nanoTime() - started < 2_000_000_000L
                val busy = e.kind == IperfException.Kind.Busy && request.retryWhenBusy
                val transient = busy || e.kind == IperfException.Kind.Unreachable ||
                    e.kind == IperfException.Kind.ServerError || e.kind == IperfException.Kind.Other
                val limit = if (busy) BUSY_RETRIES else RETRIES
                if (!reachedRunning && quick && transient && attempt < limit && !cancelled && !finishRequested) {
                    attempt++
                    Thread.sleep(if (busy) 600L * attempt else 250L * attempt)
                    if (!cancelled) {
                        resetForRetry()
                        continue
                    }
                }
                throw e
            }
        }
    }

    private fun resetForRetry() {
        streams.clear()
        handshakes.clear()
        control = null
        stopped = false
        sending = false
        measuring = false
    }

    private fun attempt(listener: IperfListener): IperfResult {
        try {
            return runTest(listener)
        } catch (e: IperfException) {
            if (cancelled) throw IperfException("Cancelled", IperfException.Kind.Cancelled, e)
            throw e
        } catch (e: UnknownHostException) {
            throw IperfException("Unknown host ${request.host}", IperfException.Kind.UnknownHost, e)
        } catch (e: SocketTimeoutException) {
            if (cancelled) throw IperfException("Cancelled", IperfException.Kind.Cancelled, e)
            throw IperfException("The server stopped responding", IperfException.Kind.Timeout, e)
        } catch (e: IOException) {
            if (cancelled) throw IperfException("Cancelled", IperfException.Kind.Cancelled, e)
            throw IperfException(e.message ?: "Connection failed", IperfException.Kind.Other, e)
        } finally {
            sending = false
            measuring = false
            stopped = true
            closeAll()
        }
    }

    private fun runTest(listener: IperfListener): IperfResult {
        listener.onPhase(IperfPhase.Connecting)
        val address = InetAddress.getByName(request.host)
        checkCancelled()

        val ctrl = Socket()
        control = ctrl
        val connectStart = System.nanoTime()
        try {
            ctrl.connect(InetSocketAddress(address, request.port), request.connectTimeoutMs)
        } catch (e: SocketTimeoutException) {
            throw IperfException("No answer from ${request.host}:${request.port}", IperfException.Kind.Unreachable, e)
        } catch (e: IOException) {
            checkCancelled()
            throw connectFailure(e)
        }
        handshakes += (System.nanoTime() - connectStart) / 1e6
        ctrl.tcpNoDelay = true
        ctrl.soTimeout = CONTROL_TIMEOUT_MS
        val input = DataInputStream(ctrl.getInputStream())
        val output = ctrl.getOutputStream()

        val cookie = makeCookie()
        output.write(cookie)
        output.flush()
        listener.onPhase(IperfPhase.Negotiating)

        var serverResults: Map<String, Any?>? = null
        var elapsed = 0.0
        val samples = ArrayList<LiveSample>()

        loop@ while (true) {
            when (val state = readState(input)) {
                PARAM_EXCHANGE -> writeJson(output, parameters())
                CREATE_STREAMS -> createStreams(address, cookie)
                TEST_START -> Unit
                TEST_RUNNING -> {
                    elapsed = runStreams(ctrl, input, listener, samples)
                    output.write(TEST_END)
                    output.flush()
                    listener.onPhase(IperfPhase.Finishing)
                }
                EXCHANGE_RESULTS -> {
                    writeJson(output, results(elapsed))
                    serverResults = readJson(input)
                }
                DISPLAY_RESULTS -> {
                    stopped = true
                    streams.forEach { it.close() }
                    output.write(IPERF_DONE)
                    output.flush()
                    break@loop
                }
                IPERF_DONE -> break@loop
                else -> handleFailureState(state, input)
            }
        }
        runCatching { ctrl.close() }
        return buildResult(elapsed, samples, serverResults ?: emptyMap())
    }

    private fun handleFailureState(state: Int, input: DataInputStream): Nothing {
        when (state) {
            ACCESS_DENIED -> throw IperfException("The server is busy with another test", IperfException.Kind.Busy)
            SERVER_TERMINATE -> throw IperfException("The server ended the test", IperfException.Kind.ServerError)
            SERVER_ERROR -> {
                val code = runCatching { input.readInt() }.getOrDefault(0)
                val errno = runCatching { input.readInt() }.getOrDefault(0)
                val (message, kind) = serverErrorMessage(code)
                throw IperfException(
                    if (errno > 0) "$message (code $code, errno $errno)" else "$message (code $code)",
                    kind,
                )
            }
            else -> throw IperfException("Unexpected message $state from the server", IperfException.Kind.Protocol)
        }
    }

    private fun serverErrorMessage(code: Int): Pair<String, IperfException.Kind> = when (code) {
        IE_AUTH_TEST -> "The server requires a username and password" to IperfException.Kind.AuthRequired
        IE_MAX_DURATION -> "The test is longer than the server allows" to IperfException.Kind.DurationLimit
        IE_TOTAL_RATE -> "The bitrate is above the server's limit" to IperfException.Kind.ServerError
        IE_STREAM_ACCEPT, IE_STREAM_CONNECT -> "The server could not open the data streams" to IperfException.Kind.ServerError
        else -> "The server reported an error" to IperfException.Kind.ServerError
    }

    // region Parameters and results

    private val blockSize: Int
        get() = if (request.protocol == Protocol.TCP) TCP_BLOCK else UDP_BLOCK

    private fun parameters(): Map<String, Any?> = buildMap {
        if (request.protocol == Protocol.TCP) put("tcp", true) else put("udp", true)
        put("omit", 0)
        put("time", request.durationSec)
        put("num", 0)
        put("blockcount", 0)
        put("parallel", request.parallel)
        when (request.direction) {
            Direction.Download -> put("reverse", true)
            Direction.Bidirectional -> put("bidirectional", true)
            Direction.Upload -> Unit
        }
        put("len", blockSize)
        if (request.protocol == Protocol.UDP) {
            put("bandwidth", max(1L, request.udpBitrate / request.parallel))
            put("pacing_timer", 1000)
            put("udp_counters_64bit", 1)
        }
        put("client_version", CLIENT_VERSION)
    }

    private fun results(elapsed: Double): Map<String, Any?> = mapOf(
        "cpu_util_total" to 0.0,
        "cpu_util_user" to 0.0,
        "cpu_util_system" to 0.0,
        "sender_has_retransmits" to if (request.direction == Direction.Download) -1 else 0,
        "streams" to streams.map { s ->
            mapOf(
                "id" to s.id,
                "bytes" to s.bytes.get(),
                "retransmits" to -1,
                "jitter" to s.jitter,
                "errors" to s.lost,
                "omitted_errors" to 0,
                "packets" to s.packets,
                "omitted_packets" to 0,
                "start_time" to 0.0,
                "end_time" to elapsed,
            )
        },
    )

    private fun buildResult(elapsed: Double, samples: List<LiveSample>, server: Map<String, Any?>): IperfResult {
        val serverStreams = server["streams"].asList().map { it.asObj() }.associateBy { (it.num("id") ?: -1.0).toInt() }
        val serverHasRetransmits = (server.num("sender_has_retransmits") ?: 0.0) >= 1.0
        val udp = request.protocol == Protocol.UDP

        fun serverDuration(ids: List<Int>): Double {
            val end = ids.mapNotNull { serverStreams[it]?.num("end_time") }.maxOrNull()
            return if (end != null && end > 0.1) end else elapsed
        }

        val up = streams.filter { it.sender }.takeIf { it.isNotEmpty() }?.let { list ->
            val ids = list.map { it.id }
            val sent = list.sumOf { it.bytes.get() }
            val received = ids.sumOf { (serverStreams[it]?.num("bytes") ?: 0.0).toLong() }
            val serverTime = serverDuration(ids)
            DirectionResult(
                bitsPerSecond = if (received > 0) received * 8 / serverTime else sent * 8 / elapsed.coerceAtLeast(0.001),
                senderBitsPerSecond = sent * 8 / elapsed.coerceAtLeast(0.001),
                receivedBytes = if (received > 0) received else sent,
                retransmits = null,
                jitterMs = if (udp) ids.mapNotNull { serverStreams[it]?.num("jitter") }.average().takeIf { !it.isNaN() }?.times(1000) else null,
                lostPackets = if (udp) ids.sumOf { (serverStreams[it]?.num("errors") ?: 0.0).toLong() } else null,
                packets = if (udp) ids.sumOf { (serverStreams[it]?.num("packets") ?: 0.0).toLong() } else null,
            )
        }
        val down = streams.filter { !it.sender }.takeIf { it.isNotEmpty() }?.let { list ->
            val ids = list.map { it.id }
            val received = list.sumOf { it.bytes.get() }
            val sentByServer = ids.sumOf { (serverStreams[it]?.num("bytes") ?: 0.0).toLong() }
            val retrans = ids.mapNotNull { serverStreams[it]?.num("retransmits") }.filter { it >= 0 }
            DirectionResult(
                bitsPerSecond = received * 8 / elapsed.coerceAtLeast(0.001),
                senderBitsPerSecond = if (sentByServer > 0) sentByServer * 8 / serverDuration(ids) else 0.0,
                receivedBytes = received,
                retransmits = if (!udp && serverHasRetransmits && retrans.isNotEmpty()) retrans.sum().toLong() else null,
                jitterMs = if (udp) list.map { it.jitter }.average() * 1000 else null,
                lostPackets = if (udp) list.sumOf { it.lost } else null,
                packets = if (udp) list.sumOf { it.packets } else null,
            )
        }
        return IperfResult(
            request = request,
            durationSec = elapsed,
            upload = up,
            download = down,
            handshakeMs = handshakes.toList(),
            samples = samples,
            remoteCongestion = server.str("congestion_used"),
            remoteCpuPercent = server.num("cpu_util_total"),
        )
    }

    // endregion

    // region Streams

    private fun createStreams(address: InetAddress, cookie: ByteArray) {
        val senders: List<Boolean> = when (request.direction) {
            Direction.Upload -> List(request.parallel) { true }
            Direction.Download -> List(request.parallel) { false }
            // iperf3 creates the client's sending streams first, then the receiving ones.
            Direction.Bidirectional -> List(request.parallel) { true } + List(request.parallel) { false }
        }
        senders.forEachIndexed { index, sender ->
            checkCancelled()
            // iperf3 numbers streams 1, 3, 4, 5, ... (a historical quirk both ends rely on).
            val id = if (index == 0) 1 else index + 2
            val stream = if (request.protocol == Protocol.TCP) {
                TcpStream(id, sender).also { it.connect(address, cookie) }
            } else {
                UdpStream(id, sender).also { it.connect(address) }
            }
            streams += stream
        }
    }

    /** Runs the data streams for the test duration. Returns the measured time in seconds. */
    private fun runStreams(ctrl: Socket, input: InputStream, listener: IperfListener, samples: MutableList<LiveSample>): Double {
        listener.onPhase(IperfPhase.Running)
        reachedRunning = true
        val start = System.nanoTime()
        measuring = true
        sending = true
        streams.forEach { it.start(start) }

        ctrl.soTimeout = TICK_MS.toInt()
        val window = ArrayDeque<LongArray>() // [time, upBytes, downBytes]
        var nextSample = start + IperfResult.SAMPLE_PERIOD_MS * 1_000_000
        var nextEmit = start
        val end = if (request.durationSec == 0) Long.MAX_VALUE else start + request.durationSec * 1_000_000_000L
        while (true) {
            checkCancelled()
            // The control socket stays quiet during the test unless the server gives up.
            try {
                val b = input.read()
                if (b < 0) throw IperfException("The server closed the connection", IperfException.Kind.ServerError)
                handleFailureState(b.toByte().toInt(), DataInputStream(input))
            } catch (_: SocketTimeoutException) {
                // Normal: nothing from the server.
            }
            val now = System.nanoTime()
            val up = streams.filter { it.sender }.sumOf { it.bytes.get() }
            val down = streams.filter { !it.sender }.sumOf { it.bytes.get() }
            window.addLast(longArrayOf(now, up, down))
            while (window.size > 2 && now - window.first()[0] > SMOOTHING_NS) window.removeFirst()
            val first = window.first()
            val dt = (now - first[0]) / 1e9
            val sample = LiveSample(
                elapsed = (now - start) / 1e9,
                uploadBps = if (dt > 0) (up - first[1]) * 8 / dt else 0.0,
                downloadBps = if (dt > 0) (down - first[2]) * 8 / dt else 0.0,
                uploadBytes = up,
                downloadBytes = down,
            )
            if (now >= nextEmit) {
                listener.onSample(sample)
                nextEmit = now + EMIT_NS
            }
            if (now >= nextSample) {
                samples += sample
                nextSample += IperfResult.SAMPLE_PERIOD_MS * 1_000_000
            }
            if (now >= end || finishRequested) break
        }
        sending = false
        measuring = false
        val elapsed = (System.nanoTime() - start) / 1e9
        ctrl.soTimeout = CONTROL_TIMEOUT_MS
        return elapsed
    }

    private abstract inner class DataStream(val id: Int, val sender: Boolean) {
        val bytes = AtomicLong()
        @Volatile var packets = 0L
        @Volatile var lost = 0L
        @Volatile var jitter = 0.0 // seconds
        private var thread: Thread? = null

        fun start(startNanos: Long) {
            thread = Thread({
                try {
                    if (sender) send(startNanos) else receive()
                } catch (_: IOException) {
                    // Sockets close when the test ends or is cancelled.
                }
            }, "iperf-stream-$id").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
                start()
            }
        }

        abstract fun send(startNanos: Long)
        abstract fun receive()
        abstract fun close()
    }

    private inner class TcpStream(id: Int, sender: Boolean) : DataStream(id, sender) {
        private val socket = Socket()

        fun connect(address: InetAddress, cookie: ByteArray) {
            val t = System.nanoTime()
            try {
                socket.connect(InetSocketAddress(address, request.port), request.connectTimeoutMs)
            } catch (e: IOException) {
                checkCancelled()
                throw connectFailure(e)
            }
            handshakes += (System.nanoTime() - t) / 1e6
            if (sender) socket.sendBufferSize = sendBufferFor(handshakes.first())
            socket.getOutputStream().apply {
                write(cookie)
                flush()
            }
        }

        override fun send(startNanos: Long) {
            val out: OutputStream = socket.getOutputStream()
            val buf = ByteArray(blockSize).also { SecureRandom().nextBytes(it) }
            while (sending) {
                out.write(buf)
                if (measuring) bytes.addAndGet(buf.size.toLong())
            }
        }

        override fun receive() {
            val input = socket.getInputStream()
            val buf = ByteArray(blockSize)
            while (!stopped) {
                val n = input.read(buf)
                if (n < 0) break
                if (measuring) bytes.addAndGet(n.toLong())
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private inner class UdpStream(id: Int, sender: Boolean) : DataStream(id, sender) {
        private val socket = DatagramSocket()

        fun connect(address: InetAddress) {
            socket.connect(InetSocketAddress(address, request.port))
            socket.receiveBufferSize = max(socket.receiveBufferSize, 4 * 1024 * 1024)
            socket.sendBufferSize = max(socket.sendBufferSize, 1024 * 1024)
            socket.soTimeout = UDP_CONNECT_TIMEOUT_MS
            socket.send(DatagramPacket(UDP_CONNECT_MSG, UDP_CONNECT_MSG.size))
            val buf = ByteArray(blockSize + 64)
            val packet = DatagramPacket(buf, buf.size)
            // In reverse mode a data packet may overtake the reply; allow a few.
            repeat(3) {
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    throw IperfException("No UDP reply from the server. A firewall may be blocking UDP.", IperfException.Kind.UdpBlocked, e)
                } catch (e: PortUnreachableException) {
                    throw IperfException("The server's UDP port is closed", IperfException.Kind.UdpBlocked, e)
                }
                if (packet.length == 4 && isConnectReply(buf)) return
            }
            throw IperfException("Unexpected UDP reply from the server", IperfException.Kind.Protocol)
        }

        override fun send(startNanos: Long) {
            val size = blockSize
            val buf = ByteArray(size).also { SecureRandom().nextBytes(it) }
            val header = ByteBuffer.wrap(buf)
            val packet = DatagramPacket(buf, size)
            val bytesPerSecond = max(1L, request.udpBitrate / request.parallel) / 8.0
            val epochNanos = System.currentTimeMillis() * 1_000_000L - System.nanoTime()
            var sentBytes = 0L
            var count = 0L
            while (sending) {
                val now = System.nanoTime()
                val allowed = (now - startNanos) / 1e9 * bytesPerSecond
                if (sentBytes + size > allowed) {
                    val waitNs = ((sentBytes + size - allowed) / bytesPerSecond * 1e9).toLong()
                    LockSupport.parkNanos(min(waitNs, 2_000_000L))
                    continue
                }
                count++
                val wall = epochNanos + now
                header.putInt(0, (wall / 1_000_000_000L).toInt())
                header.putInt(4, ((wall % 1_000_000_000L) / 1000).toInt())
                header.putLong(8, count)
                try {
                    socket.send(packet)
                } catch (_: PortUnreachableException) {
                    LockSupport.parkNanos(5_000_000L)
                    continue
                }
                sentBytes += size
                packets = count
                if (measuring) bytes.addAndGet(size.toLong())
            }
        }

        override fun receive() {
            socket.soTimeout = 200
            val buf = ByteArray(65_536)
            val packet = DatagramPacket(buf, buf.size)
            val header = ByteBuffer.wrap(buf)
            val epochNanos = System.currentTimeMillis() * 1_000_000L - System.nanoTime()
            var prevTransit = 0.0
            var first = true
            var highest = 0L
            var outOfOrder = 0L
            while (!stopped) {
                try {
                    packet.length = buf.size
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                if (!measuring || packet.length < 16) continue
                bytes.addAndGet(packet.length.toLong())
                val sec = header.getInt(0).toLong() and 0xffffffffL
                val usec = header.getInt(4).toLong() and 0xffffffffL
                val seq = header.getLong(8)
                val arrival = (epochNanos + System.nanoTime()) / 1e9
                val transit = arrival - (sec + usec / 1e6)
                if (first) {
                    prevTransit = transit
                    first = false
                }
                val d = abs(transit - prevTransit)
                prevTransit = transit
                jitter += (d - jitter) / 16.0
                if (seq >= highest + 1) {
                    if (seq > highest + 1) lost += (seq - 1) - highest
                    highest = seq
                } else {
                    outOfOrder++
                    if (lost > 0) lost--
                }
                packets = highest
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    // endregion

    /**
     * Android lets each socket's send buffer grow to 8 MB, which an upload fills in an instant:
     * with four streams that is 32 MB "sent" before the network has carried any of it, and the
     * live reading overshoots. A buffer sized for 2.5 Gbit/s at three times the idle round trip
     * still keeps the link full, but stays small enough that sender-side counting is honest.
     */
    private fun sendBufferFor(rttMs: Double): Int {
        val bytes = MAX_EXPECTED_BPS / 8 * (rttMs.coerceAtLeast(2.0) * 3 / 1000) / request.parallel
        return bytes.toInt().coerceIn(MIN_SEND_BUFFER, MAX_SEND_BUFFER)
    }

    // region Wire helpers

    private fun readState(input: InputStream): Int {
        val b = input.read()
        if (b < 0) {
            checkCancelled()
            throw IperfException("The server closed the connection", IperfException.Kind.ServerError)
        }
        return b.toByte().toInt()
    }

    private fun writeJson(out: OutputStream, value: Any?) {
        val bytes = Json.write(value).toByteArray(Charsets.UTF_8)
        out.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
        out.write(bytes)
        out.flush()
    }

    private fun readJson(input: DataInputStream): Map<String, Any?> {
        val size = input.readInt()
        if (size <= 0 || size > MAX_JSON) throw IperfException("Bad results from the server", IperfException.Kind.Protocol)
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return try {
            Json.parse(String(bytes, Charsets.UTF_8)).asObj()
        } catch (e: IllegalArgumentException) {
            throw IperfException("Bad results from the server", IperfException.Kind.Protocol, e)
        }
    }

    /** Android reports a missing local network permission as EPERM on connect. */
    private fun connectFailure(e: IOException): IperfException {
        val message = e.message ?: "Connection refused"
        val denied = message.contains("EPERM") || message.contains("not permitted", ignoreCase = true)
        return IperfException(message, if (denied) IperfException.Kind.LocalNetwork else IperfException.Kind.Unreachable, e)
    }

    private fun checkCancelled() {
        if (cancelled) throw IperfException("Cancelled", IperfException.Kind.Cancelled)
    }

    private fun closeAll() {
        stopped = true
        streams.forEach { it.close() }
        runCatching { control?.close() }
    }

    // endregion

    companion object {
        const val DEFAULT_PORT = 5201
        const val MAX_STREAMS = 32
        const val MAX_DURATION = 86_400
        private const val RETRIES = 4
        private const val BUSY_RETRIES = 2
        const val CLIENT_VERSION = "3.17.1"

        private const val TEST_START = 1
        private const val TEST_RUNNING = 2
        private const val TEST_END = 4
        private const val PARAM_EXCHANGE = 9
        private const val CREATE_STREAMS = 10
        private const val SERVER_TERMINATE = 11
        private const val CLIENT_TERMINATE = 12
        private const val EXCHANGE_RESULTS = 13
        private const val DISPLAY_RESULTS = 14
        private const val IPERF_DONE = 16
        private const val ACCESS_DENIED = -1
        private const val SERVER_ERROR = -2

        private const val IE_TOTAL_RATE = 27
        private const val IE_MAX_DURATION = 37
        private const val IE_AUTH_TEST = 142
        private const val IE_STREAM_CONNECT = 203
        private const val IE_STREAM_ACCEPT = 204

        private const val COOKIE_SIZE = 37
        private const val MAX_EXPECTED_BPS = 2.5e9
        private const val MIN_SEND_BUFFER = 512 * 1024
        private const val MAX_SEND_BUFFER = 8 * 1024 * 1024
        private const val TCP_BLOCK = 128 * 1024
        private const val UDP_BLOCK = 1400
        private const val MAX_JSON = 8 * 1024 * 1024
        private const val CONTROL_TIMEOUT_MS = 20_000
        private const val UDP_CONNECT_TIMEOUT_MS = 4_000
        private const val TICK_MS = 25L
        private const val EMIT_NS = 66_000_000L
        private const val SMOOTHING_NS = 600_000_000L

        /** "9876" on the wire, whatever the byte order (iperf.h, UDP_CONNECT_MSG). */
        private val UDP_CONNECT_MSG = byteArrayOf(0x39, 0x38, 0x37, 0x36)

        private fun isConnectReply(b: ByteArray): Boolean {
            val v = ByteBuffer.wrap(b, 0, 4).int
            return v == 0x36373839 || // "6789"
                v == 987654321 || Integer.reverseBytes(v) == 987654321 // legacy servers
        }

        private const val COOKIE_CHARS = "abcdefghijklmnopqrstuvwxyz234567"

        internal fun makeCookie(): ByteArray {
            val random = SecureRandom()
            return ByteArray(COOKIE_SIZE) { i ->
                if (i == COOKIE_SIZE - 1) 0 else COOKIE_CHARS[random.nextInt(COOKIE_CHARS.length)].code.toByte()
            }
        }
    }
}
