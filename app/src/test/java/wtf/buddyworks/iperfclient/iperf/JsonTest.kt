package wtf.buddyworks.iperfclient.iperf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {
    @Test
    fun roundTripsNestedValues() {
        val value = mapOf(
            "tcp" to true,
            "time" to 10,
            "rate" to 1.5,
            "name" to "a \"quoted\" \\ name\n",
            "streams" to listOf(mapOf("id" to 1, "bytes" to 123456789012L), mapOf("id" to 3)),
            "none" to null,
        )
        val parsed = Json.parse(Json.write(value)).asObj()
        assertEquals(true, parsed.bool("tcp"))
        assertEquals(10.0, parsed.num("time")!!, 0.0)
        assertEquals(1.5, parsed.num("rate")!!, 0.0)
        assertEquals("a \"quoted\" \\ name\n", parsed.str("name"))
        assertEquals(123456789012.0, parsed["streams"].asList()[0].asObj().num("bytes")!!, 0.0)
        assertNull(parsed["none"])
    }

    @Test
    fun writesWholeDoublesAsIntegers() {
        assertEquals("{\"a\":5,\"b\":0.25}", Json.write(mapOf("a" to 5.0, "b" to 0.25)))
    }

    @Test
    fun parsesIperfStyleResults() {
        val text = """{"cpu_util_total":1.2e1,"sender_has_retransmits":1,"streams":[{"id":1,"bytes":1000,"retransmits":-1,"jitter":0,"end_time":10.000412}]}"""
        val obj = Json.parse(text).asObj()
        assertEquals(12.0, obj.num("cpu_util_total")!!, 0.0)
        assertEquals(-1.0, obj["streams"].asList()[0].asObj().num("retransmits")!!, 0.0)
    }

    @Test
    fun cookieHasIperfShape() {
        val cookie = Iperf3Client.makeCookie()
        assertEquals(37, cookie.size)
        assertEquals(0.toByte(), cookie[36])
        assert(cookie.take(36).all { it.toInt().toChar() in "abcdefghijklmnopqrstuvwxyz234567" })
    }
}
