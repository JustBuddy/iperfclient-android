package wtf.buddyworks.iperfclient.ui.test

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import java.util.concurrent.CopyOnWriteArrayList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean
import wtf.buddyworks.iperfclient.IperfApp
import wtf.buddyworks.iperfclient.data.PhaseRecord
import wtf.buddyworks.iperfclient.data.PingRecord
import wtf.buddyworks.iperfclient.data.Server
import wtf.buddyworks.iperfclient.data.TestMode
import wtf.buddyworks.iperfclient.data.TestOptions
import wtf.buddyworks.iperfclient.data.TestRecord
import wtf.buddyworks.iperfclient.iperf.Direction
import wtf.buddyworks.iperfclient.iperf.DirectionResult
import wtf.buddyworks.iperfclient.iperf.Iperf3Client
import wtf.buddyworks.iperfclient.iperf.IperfException
import wtf.buddyworks.iperfclient.iperf.IperfListener
import wtf.buddyworks.iperfclient.iperf.IperfPhase
import wtf.buddyworks.iperfclient.iperf.IperfRequest
import wtf.buddyworks.iperfclient.iperf.IperfResult
import wtf.buddyworks.iperfclient.iperf.LiveSample
import wtf.buddyworks.iperfclient.net.PingStats
import wtf.buddyworks.iperfclient.net.pingFlow
import wtf.buddyworks.iperfclient.ui.describeConnection

/** Seconds between probes in ping mode (the fastest Android allows without root). */
const val PING_INTERVAL = 0.2

private const val PHASE_GAP_MS = 400L

/** Samples (2 s) a continuous test spends ramping up; its averages and low leave them out. */
const val RAMP_SAMPLES = 8

enum class Stage { Idle, Ping, Download, Upload, Bidirectional, Done }

data class TestError(val kind: IperfException.Kind, val detail: String)

/**
 * Runs a test session: idle ping, then the iperf3 phases the mode asks for. In continuous mode
 * a single-direction test runs open-ended until [stop], and Full mode repeats whole cycles.
 * State is Compose snapshot state so the gauge can follow it at frame rate.
 */
class SpeedTestViewModel(private val app: IperfApp) : ViewModel() {
    private val store = app.store
    private val main = Handler(Looper.getMainLooper())

    var stage by mutableStateOf(Stage.Idle)
        private set
    var status by mutableStateOf<IperfPhase?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    /** Continuous session asked to wrap up; results of the running phase are kept. */
    var stopping by mutableStateOf(false)
        private set
    var error by mutableStateOf<TestError?>(null)
        private set
    /** Port the session moved to because the previous one was busy. */
    var busyRetryPort by mutableStateOf<Int?>(null)
        private set
    var round by mutableIntStateOf(0)
        private set
    var sessionContinuous by mutableStateOf(false)
        private set
    var sessionMode by mutableStateOf(TestMode.Full)
        private set
    var sessionServer by mutableStateOf<Server?>(null)
        private set

    // Live values.
    var gaugeMbps by mutableFloatStateOf(0f)
        private set
    var gaugeSecondaryMbps by mutableStateOf<Float?>(null)
        private set
    var elapsed by mutableFloatStateOf(0f)
        private set
    /** Length of the running phase in seconds; 0 when open-ended. */
    var phaseDuration by mutableIntStateOf(0)
        private set
    var liveAvgDownMbps by mutableDoubleStateOf(0.0)
        private set
    var liveAvgUpMbps by mutableDoubleStateOf(0.0)
        private set
    var liveBytes by mutableLongStateOf(0L)
        private set
    var livePingMs by mutableStateOf<Double?>(null)
        private set
    val pingSamples = mutableStateListOf<Double>()
    val downSeries = mutableStateListOf<Float>()
    val upSeries = mutableStateListOf<Float>()
    /** Ping mode: one entry per probe in send order, NaN where no reply came. */
    val pingSeries = mutableStateListOf<Float>()
    var pingResult by mutableStateOf<PingRecord?>(null)
        private set

    // Results of the current (or last) round.
    var idlePing by mutableStateOf<PingStats?>(null)
        private set
    var pingFromTcp by mutableStateOf(false)
        private set
    var download by mutableStateOf<PhaseRecord?>(null)
        private set
    var upload by mutableStateOf<PhaseRecord?>(null)
        private set
    var completedRounds by mutableIntStateOf(0)
        private set

    private var job: Job? = null
    private var pingJob: Job? = null
    /** The port of the server's range that the last iperf3 run actually used. */
    @Volatile private var usedPort: Int? = null
    @Volatile private var client: Iperf3Client? = null
    @Volatile private var finishRequested = false

    fun start() {
        if (running) return
        val server = store.selectedServer ?: return
        val options = store.options.value
        job = viewModelScope.launch { session(server, options) }
    }

    /** Continuous sessions end cleanly and keep their results; timed tests are abandoned. */
    fun stop() {
        if (!running) return
        if (sessionMode == TestMode.Ping && sessionContinuous) {
            finishRequested = true
            stopping = true
            pingJob?.cancel()
            return
        }
        if (sessionContinuous) {
            finishRequested = true
            stopping = true
            client?.finish()
            if (stage == Stage.Ping) job?.cancel()
        } else {
            job?.cancel()
        }
    }

    /** The user said no to the local network permission, which a LAN server needs. */
    fun reportLocalNetworkDenied() {
        error = TestError(IperfException.Kind.LocalNetwork, app.getString(wtf.buddyworks.iperfclient.R.string.err_local_network_detail))
    }

    fun dismissError() {
        error = null
    }

    private suspend fun session(server: Server, options: TestOptions) {
        running = true
        stopping = false
        finishRequested = false
        error = null
        round = 0
        completedRounds = 0
        sessionContinuous = options.continuous
        sessionMode = options.mode
        sessionServer = server
        val network = describeConnection(app, app.connection.value)
        try {
            do {
                round++
                resetRound()
                runRound(server, options, network)
                completedRounds++
            } while (options.continuous && options.mode == TestMode.Full && !finishRequested)
            stage = Stage.Done
        } catch (e: CancellationException) {
            // Stopping a continuous session during ping lands here; anything measured is kept.
            if (finishRequested && completedRounds > 0) stage = Stage.Done else resetAll()
            if (!finishRequested) throw e
        } catch (e: IperfException) {
            if (e.kind == IperfException.Kind.Cancelled) {
                resetAll()
            } else {
                android.util.Log.w("iperf", "Test failed in $stage: ${e.kind}", e)
                error = TestError(e.kind, e.message ?: "")
                stage = if (completedRounds > 0) Stage.Done else Stage.Idle
            }
        } finally {
            running = false
            stopping = false
            status = null
            client = null
            busyRetryPort = null
            gaugeSecondaryMbps = null
        }
    }

    private fun resetAll() {
        resetRound()
        stage = Stage.Idle
    }

    private fun resetRound() {
        gaugeMbps = 0f
        gaugeSecondaryMbps = null
        elapsed = 0f
        liveAvgDownMbps = 0.0
        liveAvgUpMbps = 0.0
        liveBytes = 0
        livePingMs = null
        pingSamples.clear()
        downSeries.clear()
        upSeries.clear()
        pingSeries.clear()
        pingResult = null
        idlePing = null
        pingFromTcp = false
        download = null
        upload = null
    }

    private suspend fun runRound(server: Server, options: TestOptions, network: String?) {
        if (options.mode == TestMode.Ping) {
            runPingMode(server, options, network)
            return
        }
        // Idle latency first, while the line is quiet.
        stage = Stage.Ping
        withTimeoutOrNull(4_000) {
            pingFlow(server.host, count = 10, intervalSec = 0.2).collect {
                pingSamples += it.ms
                livePingMs = it.ms
            }
        }
        idlePing = PingStats.of(pingSamples.toList())
        livePingMs = null

        val openEnded = options.continuous && options.mode != TestMode.Full
        var handshakes = emptyList<Double>()
        when (options.mode) {
            TestMode.Full -> {
                val down = runDirection(server, options, Direction.Download, openEnded = false)
                handshakes = down.handshakes
                download = down.download
                if (!finishRequested) {
                    gaugeMbps = 0f
                    delay(PHASE_GAP_MS) // iperf3 reopens its listening socket between tests
                    // Stop may have been tapped during the pause.
                    if (!finishRequested) upload = runDirection(server, options, Direction.Upload, openEnded = false).upload
                }
            }
            TestMode.Download -> runDirection(server, options, Direction.Download, openEnded).also {
                handshakes = it.handshakes
                download = it.download
            }
            TestMode.Upload -> runDirection(server, options, Direction.Upload, openEnded).also {
                handshakes = it.handshakes
                upload = it.upload
            }
            TestMode.Bidirectional -> runDirection(server, options, Direction.Bidirectional, openEnded).also {
                handshakes = it.handshakes
                download = it.download
                upload = it.upload
            }
        }
        if (idlePing == null && handshakes.isNotEmpty()) {
            // ICMP got no answer (often filtered): a TCP handshake takes one round trip too.
            idlePing = PingStats.of(handshakes)
            pingFromTcp = true
        }
        gaugeMbps = 0f
        gaugeSecondaryMbps = null
        store.addRecord(
            TestRecord(
                id = System.currentTimeMillis(),
                serverName = server.name,
                host = server.host,
                port = usedPort ?: server.port,
                protocol = options.protocol,
                mode = options.mode,
                streams = options.streams,
                continuous = options.continuous,
                pingMs = idlePing?.medianMs,
                pingJitterMs = idlePing?.jitterMs,
                pingIsTcp = pingFromTcp,
                download = download,
                upload = upload,
                network = network,
            ),
        )
    }

    /**
     * Ping only: probes every [PING_INTERVAL] seconds for the set duration, or until stopped in
     * continuous mode. Lost probes are the gaps in the reply sequence numbers.
     */
    private suspend fun runPingMode(server: Server, options: TestOptions, network: String?) = coroutineScope {
        stage = Stage.Ping
        phaseDuration = if (options.continuous) 0 else options.durationSec
        val count = if (options.continuous) 0 else (options.durationSec / PING_INTERVAL).roundToInt()
        val start = SystemClock.elapsedRealtime()
        var highestSeq = 0
        val ticker = launch {
            while (true) {
                elapsed = (SystemClock.elapsedRealtime() - start) / 1000f
                delay(100)
            }
        }
        val watchdog = launch {
            delay(4_000)
            if (pingSamples.isEmpty()) {
                throw IperfException("No ping replies from ${server.host}. ICMP may be blocked on the way.", IperfException.Kind.PingBlocked)
            }
        }
        val collector = launch {
            pingFlow(server.host, count, PING_INTERVAL).collect { reply ->
                pingSamples += reply.ms
                livePingMs = reply.ms
                val index = reply.seq - 1
                while (pingSeries.size < index) pingSeries += Float.NaN
                if (index == pingSeries.size) pingSeries += reply.ms.toFloat() else if (index in pingSeries.indices) pingSeries[index] = reply.ms.toFloat()
                highestSeq = maxOf(highestSeq, reply.seq)
            }
        }
        pingJob = collector
        if (options.continuous) {
            collector.join()
        } else {
            // ping exits on its own after the last probe; don't wait long for stragglers.
            withTimeoutOrNull(options.durationSec * 1000L + 3_000) { collector.join() }
            collector.cancel()
        }
        pingJob = null
        ticker.cancel()
        watchdog.cancel()
        val duration = (SystemClock.elapsedRealtime() - start) / 1000.0
        val sent = if (count > 0 && !finishRequested) count else maxOf(highestSeq, pingSeries.size)
        while (pingSeries.size < sent) pingSeries += Float.NaN
        val samples = pingSamples.toList()
        val stats = PingStats.of(samples)
        idlePing = stats
        livePingMs = null
        val (points, step) = PingRecord.downsample(pingSeries.toList(), PING_INTERVAL)
        val record = PingRecord(
            series = points,
            stepSec = step,
            sent = sent,
            received = samples.size,
            minMs = samples.minOrNull(),
            maxMs = samples.maxOrNull(),
            durationSec = duration,
        )
        pingResult = record
        store.addRecord(
            TestRecord(
                id = System.currentTimeMillis(),
                serverName = server.name,
                host = server.host,
                port = server.port,
                protocol = options.protocol,
                mode = TestMode.Ping,
                streams = 0,
                continuous = options.continuous,
                pingMs = stats?.medianMs,
                pingJitterMs = stats?.jitterMs,
                pingIsTcp = false,
                download = null,
                upload = null,
                network = network,
                pingDetail = record,
            ),
        )
    }

    private class DirectionOutcome(val download: PhaseRecord?, val upload: PhaseRecord?, val handshakes: List<Double>)

    /**
     * Runs one iperf3 test (or, when the server caps test length, back-to-back rounds that
     * together act as one open-ended test) and measures loaded latency alongside.
     */
    private suspend fun runDirection(server: Server, options: TestOptions, direction: Direction, openEnded: Boolean): DirectionOutcome = coroutineScope {
        stage = when (direction) {
            Direction.Download -> Stage.Download
            Direction.Upload -> Stage.Upload
            Direction.Bidirectional -> Stage.Bidirectional
        }
        elapsed = 0f
        phaseDuration = if (openEnded) 0 else options.durationSec
        val series = if (direction == Direction.Upload) upSeries else downSeries
        series.clear()
        if (direction == Direction.Bidirectional) upSeries.clear()

        val loaded = CopyOnWriteArrayList<Double>()
        val pinger = launch {
            delay(800) // let the streams ramp up first
            pingFlow(server.host, count = 0, intervalSec = 0.5).collect {
                loaded += it.ms
                livePingMs = it.ms
            }
        }

        val results = ArrayList<IperfResult>()
        var timeOffset = 0.0
        var bytesOffsetDown = 0L
        var bytesOffsetUp = 0L
        try {
            var durationLimited = false
            while (true) {
                val duration = if (openEnded && !durationLimited) 0 else options.durationSec
                val offset = timeOffset
                val downOff = bytesOffsetDown
                val upOff = bytesOffsetUp
                val listener = object : IperfListener {
                    override fun onPhase(phase: IperfPhase) {
                        main.post { status = phase }
                    }

                    override fun onSample(sample: LiveSample) {
                        main.post { applySample(sample, direction, offset, downOff, upOff) }
                    }
                }
                val result = try {
                    runOnPorts(server) { port ->
                        IperfRequest(
                            host = server.host,
                            port = port,
                            protocol = options.protocol,
                            direction = direction,
                            durationSec = duration,
                            parallel = options.streams,
                            udpBitrate = options.udpMbps * 1_000_000L,
                            retryWhenBusy = server.ports.first == server.ports.last,
                        )
                    }.let { it.run(listener, this@SpeedTestViewModel) }
                } catch (e: IperfException) {
                    if (e.kind == IperfException.Kind.DurationLimit && openEnded && !durationLimited) {
                        durationLimited = true
                        continue
                    }
                    throw e
                }
                results += result
                if (!openEnded || finishRequested) break
                // Server caps test length: chain the next round onto the same timeline.
                timeOffset += result.durationSec
                bytesOffsetDown += result.download?.receivedBytes ?: 0
                bytesOffsetUp += result.upload?.receivedBytes ?: 0
            }
        } finally {
            pinger.cancel()
            livePingMs = null
        }
        val loadedStats = PingStats.of(loaded.toList())
        DirectionOutcome(
            download = merge(results.mapNotNull { r -> r.download?.let { it to r.durationSec } }, downSeries, loadedStats, openEnded),
            upload = merge(results.mapNotNull { r -> r.upload?.let { it to r.durationSec } }, upSeries, loadedStats, openEnded),
            handshakes = results.flatMap { it.handshakeMs },
        )
    }

    private fun applySample(s: LiveSample, direction: Direction, offset: Double, downOffset: Long, upOffset: Long) {
        if (!running) return
        val t = (offset + s.elapsed)
        elapsed = t.toFloat()
        val down = (s.downloadBps / 1e6).toFloat()
        val up = (s.uploadBps / 1e6).toFloat()
        when (direction) {
            Direction.Download -> gaugeMbps = down
            Direction.Upload -> gaugeMbps = up
            Direction.Bidirectional -> {
                gaugeMbps = down
                gaugeSecondaryMbps = up
            }
        }
        val downBytes = downOffset + s.downloadBytes
        val upBytes = upOffset + s.uploadBytes
        liveBytes = downBytes + upBytes
        if (t > 0.2) {
            liveAvgDownMbps = downBytes * 8 / t / 1e6
            liveAvgUpMbps = upBytes * 8 / t / 1e6
        }
        val step = IperfResult.SAMPLE_PERIOD_MS / 1000.0
        if (direction != Direction.Upload) {
            while ((downSeries.size + 1) * step <= t) downSeries += down
        }
        if (direction != Direction.Download) {
            while ((upSeries.size + 1) * step <= t) upSeries += up
        }
    }

    private fun merge(parts: List<Pair<DirectionResult, Double>>, series: List<Float>, loaded: PingStats?, steady: Boolean): PhaseRecord? {
        if (parts.isEmpty()) return null
        val time = parts.sumOf { it.second }
        // A phase stopped before it really began measured nothing worth keeping.
        if (time < 0.5) return null
        val bytes = parts.sumOf { it.first.receivedBytes }
        val (points, step) = PhaseRecord.downsample(series.toList(), IperfResult.SAMPLE_PERIOD_MS / 1000.0)
        val lost = parts.mapNotNull { it.first.lostPackets }
        val packets = parts.mapNotNull { it.first.packets }
        return PhaseRecord(
            // Continuous sessions report the average without the TCP ramp-up, as shown live.
            bitsPerSecond = if (steady && series.size > RAMP_SAMPLES + 4) {
                series.subList(RAMP_SAMPLES, series.size).average() * 1e6
            } else {
                parts.sumOf { it.first.bitsPerSecond * it.second } / time
            },
            durationSec = time,
            bytes = bytes,
            retransmits = parts.mapNotNull { it.first.retransmits }.takeIf { it.isNotEmpty() }?.sum(),
            jitterMs = parts.mapNotNull { it.first.jitterMs }.takeIf { it.isNotEmpty() }?.average(),
            lossPercent = if (packets.isNotEmpty() && packets.sum() > 0) lost.sum() * 100.0 / packets.sum() else null,
            loadedPingMs = loaded?.medianMs,
            series = points,
            seriesStepSec = step,
        )
    }

    /** Tries each port of the server's range until one is not busy. */
    private class PortRunner(val server: Server, val make: (Int) -> IperfRequest) {
        suspend fun run(listener: IperfListener, vm: SpeedTestViewModel): IperfResult {
            var lastBusy: IperfException? = null
            for (port in server.ports) {
                while (true) {
                    val c = Iperf3Client(make(port))
                    vm.client = c
                    if (vm.finishRequested) c.finish()
                    val started = AtomicBoolean(false)
                    val tracking = object : IperfListener {
                        override fun onPhase(phase: IperfPhase) {
                            if (phase == IperfPhase.Running) started.set(true)
                            listener.onPhase(phase)
                        }

                        override fun onSample(sample: LiveSample) = listener.onSample(sample)
                    }
                    val t0 = SystemClock.elapsedRealtime()
                    try {
                        return blocking(onCancel = c::cancel) { c.run(tracking) }.also { vm.usedPort = port }
                    } catch (e: IperfException) {
                        android.util.Log.i("iperf", "${server.host}:$port failed after ${SystemClock.elapsedRealtime() - t0} ms (started=${started.get()}): ${e.kind} ${e.message}")
                        if (e.kind == IperfException.Kind.Busy) {
                            lastBusy = e
                            if (port != server.ports.last) vm.main.post { vm.busyRetryPort = port + 1 }
                            break
                        }
                        // Public servers run one instance per port; if this one misbehaved before
                        // any data flowed, the next instance of the range is worth a try.
                        if (!started.get() && port != server.ports.last && e.kind != IperfException.Kind.Cancelled &&
                            e.kind != IperfException.Kind.LocalNetwork && e.kind != IperfException.Kind.UnknownHost && !vm.finishRequested
                        ) {
                            lastBusy = e
                            vm.main.post { vm.busyRetryPort = port + 1 }
                            break
                        }
                        throw e
                    } finally {
                        vm.client = null
                    }
                }
            }
            throw lastBusy ?: IperfException("The server is busy", IperfException.Kind.Busy)
        }
    }

    private fun runOnPorts(server: Server, make: (Int) -> IperfRequest) = PortRunner(server, make)

    override fun onCleared() {
        client?.cancel()
    }
}

/** Runs a blocking call off the main thread; cancelling the coroutine calls [onCancel] to unblock it. */
private suspend fun <T> blocking(onCancel: () -> Unit, block: () -> T): T = coroutineScope {
    val work = async(Dispatchers.IO) { block() }
    try {
        work.await()
    } catch (e: CancellationException) {
        onCancel()
        throw e
    }
}
