package wtf.buddyworks.iperfclient.iperf

/**
 * A small JSON reader and writer. iperf3 speaks JSON on its control channel, and the app stores
 * servers and history as JSON. Keeping this in plain Kotlin (instead of android's org.json)
 * lets the protocol engine run in JVM unit tests against a real iperf3 server.
 */
object Json {

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> quote(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> sb.append(if (value.isFinite()) number(value) else "0")
            is Float -> sb.append(if (value.isFinite()) number(value.toDouble()) else "0")
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    append(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    append(sb, v)
                }
                sb.append(']')
            }
            is FloatArray -> append(sb, value.asList())
            else -> quote(sb, value.toString())
        }
    }

    private fun number(d: Double): String =
        if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    /** Parses [text] into maps, lists, strings, doubles, booleans and nulls. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipWs()
        if (p.pos != text.length) throw IllegalArgumentException("Trailing data in JSON at ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            if (pos >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Bad JSON at $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw IllegalArgumentException("Bad JSON literal at $pos")
            pos += word.length
            return v
        }

        private fun num(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDouble()
        }

        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw IllegalArgumentException("Unterminated JSON string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[pos++]) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (s[pos] == '}') { pos++; return map }
            while (true) {
                skipWs()
                val key = str()
                skipWs()
                if (s[pos++] != ':') throw IllegalArgumentException("Expected ':' at ${pos - 1}")
                map[key] = value()
                skipWs()
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("Expected ',' or '}' at ${pos - 1}")
                }
            }
        }

        private fun arr(): List<Any?> {
            pos++
            val list = ArrayList<Any?>()
            skipWs()
            if (s[pos] == ']') { pos++; return list }
            while (true) {
                list.add(value())
                skipWs()
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("Expected ',' or ']' at ${pos - 1}")
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.asObj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()

fun Map<String, Any?>.num(key: String): Double? = (this[key] as? Number)?.toDouble()
fun Map<String, Any?>.str(key: String): String? = this[key] as? String
fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
