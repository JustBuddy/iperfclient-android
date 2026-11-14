package wtf.buddyworks.iperfclient.iperf

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs real tests against an iperf3 server. Skipped unless IPERF_SERVER is set,
 * e.g. IPERF_SERVER=192.168.1.10 or IPERF_SERVER=host:5201.
 */
class LiveServerTest {
    private val server: Pair<String, Int>? = System.getenv("IPERF_SERVER")?.takeIf { it.isNotBlank() }?.let {
        val host = it.substringBeforeLast(':', it)
        val port = it.substringAfterLast(':', "").toIntOrNull() ?: Iperf3Client.DEFAULT_PORT
        host to port
    }

    private fun run(direction: Direction, protocol: Protocol = Protocol.TCP, parallel: Int = 1): IperfResult {
        val (host, port) = server!!
        var samples = 0
        val result = Iperf3Client(
            IperfRequest(host, port, protocol, direction, durationSec = 3, parallel = parallel, udpBitrate = 50_000_000),
        ).run(object : IperfListener {
            override fun onSample(sample: LiveSample) { samples++ }
        })
        println("$direction $protocol x$parallel: up=${result.upload?.let { "%.1f Mbps (sender %.1f) jitter=%s loss=%s/%s".format(it.bitsPerSecond / 1e6, it.senderBitsPerSecond / 1e6, it.jitterMs, it.lostPackets, it.packets) }} " +
            "down=${result.download?.let { "%.1f Mbps (sender %.1f) retr=%s jitter=%s loss=%s/%s".format(it.bitsPerSecond / 1e6, it.senderBitsPerSecond / 1e6, it.retransmits, it.jitterMs, it.lostPackets, it.packets) }} " +
            "handshakes=${result.handshakeMs.map { "%.1f".format(it) }} samples=$samples/${result.samples.size}")
        assertTrue("live samples", samples > 10)
        return result
    }

    @Test
    fun tcpUpload() {
        assumeTrue(server != null)
        val r = run(Direction.Upload)
        assertTrue(r.upload!!.bitsPerSecond > 1e6)
    }

    @Test
    fun tcpDownloadParallel() {
        assumeTrue(server != null)
        val r = run(Direction.Download, parallel = 4)
        assertTrue(r.download!!.bitsPerSecond > 1e6)
    }

    @Test
    fun tcpBidirectional() {
        assumeTrue(server != null)
        val r = run(Direction.Bidirectional, parallel = 2)
        assertNotNull(r.upload)
        assertNotNull(r.download)
    }

    @Test
    fun udpUploadAndDownload() {
        assumeTrue(server != null)
        val up = run(Direction.Upload, Protocol.UDP)
        assertNotNull(up.upload!!.jitterMs)
        val down = run(Direction.Download, Protocol.UDP, parallel = 2)
        assertTrue(down.download!!.bitsPerSecond > 1e6)
    }

    @Test
    fun openEndedRunsUntilFinished() {
        assumeTrue(server != null)
        val (host, port) = server!!
        val client = Iperf3Client(IperfRequest(host, port, direction = Direction.Download, durationSec = 0))
        Thread { Thread.sleep(4_500); client.finish() }.start()
        val r = client.run()
        println("open-ended: %.1f s, %.1f Mbps".format(r.durationSec, r.download!!.bitsPerSecond / 1e6))
        assertTrue(r.durationSec in 4.0..6.0)
    }

    @Test
    fun backToBackSessions() {
        assumeTrue(server != null)
        val (host, port) = server!!
        var failures = 0
        repeat(6) { i ->
            for (dir in listOf(Direction.Download, Direction.Upload)) {
                try {
                    val r = Iperf3Client(IperfRequest(host, port, direction = dir, durationSec = 2, parallel = 4)).run()
                    println("round $i $dir ok %.0f Mbps".format(((r.download ?: r.upload)!!.bitsPerSecond) / 1e6))
                } catch (e: IperfException) {
                    failures++
                    println("round $i $dir FAILED ${e.kind}: ${e.message}")
                    e.cause?.printStackTrace(System.out)
                }
            }
        }
        assertTrue("failures: $failures", failures == 0)
    }

    /** IPERF_RANGE=host:5200-5209 tries each port like the app does, and reports what happened. */
    @Test
    fun publicServerRange() {
        val spec = System.getenv("IPERF_RANGE")?.takeIf { it.isNotBlank() }
        assumeTrue(spec != null)
        val host = spec!!.substringBefore(':')
        val (a, b) = spec.substringAfter(':').split('-').map { it.toInt() }
        for (port in a..b) {
            val t = System.nanoTime()
            try {
                val r = Iperf3Client(IperfRequest(host, port, direction = Direction.Download, durationSec = 3, parallel = 4)).run()
                println("port $port ok %.0f Mbps".format(r.download!!.bitsPerSecond / 1e6))
                return
            } catch (e: IperfException) {
                println("port $port ${e.kind} after %.1fs: ${e.message} / ${e.cause}".format((System.nanoTime() - t) / 1e9))
            }
        }
    }
}
