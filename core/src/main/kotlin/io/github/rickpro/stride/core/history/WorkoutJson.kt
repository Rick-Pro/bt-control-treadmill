package io.github.rickpro.stride.core.history

import java.util.Locale

/**
 * One walk per line of JSON, written and read without any library. The format is flat on purpose:
 * {"id":"w-1","startedAt":1,"endedAt":2,"duration":3,"distanceKm":0.5,"kcal":9,"steps":100,
 *  "stepsEstimated":true,"avgKmh":2.0,"maxKmh":3.0,"samples":[[0,1.0],[5,2.0]]}
 * Unknown keys are ignored, so a newer app can read an older file and the other way round.
 */
object WorkoutJson {
    fun encode(w: Workout): String {
        val sb = StringBuilder(128 + w.samples.size * 12)
        sb.append('{')
        sb.append("\"id\":").append(quote(w.id))
        sb.append(",\"startedAt\":").append(w.startedAtEpochMs)
        sb.append(",\"endedAt\":").append(w.endedAtEpochMs)
        sb.append(",\"duration\":").append(w.durationSeconds)
        sb.append(",\"distanceKm\":").append(number(w.distanceKm))
        sb.append(",\"kcal\":").append(w.kcal)
        sb.append(",\"steps\":").append(w.steps)
        sb.append(",\"stepsEstimated\":").append(w.stepsEstimated)
        sb.append(",\"avgKmh\":").append(number(w.avgSpeedKmh))
        sb.append(",\"maxKmh\":").append(number(w.maxSpeedKmh))
        sb.append(",\"samples\":[")
        w.samples.forEachIndexed { i, s ->
            if (i > 0) sb.append(',')
            sb.append('[').append(s.atSeconds).append(',').append(number(s.kmh)).append(']')
        }
        sb.append("]}")
        return sb.toString()
    }

    /** Null on anything that is not a complete walk line. */
    fun decode(line: String): Workout? {
        val map = try {
            Parser(line).parseDocument() as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: return null
        fun num(key: String): Double? = map[key] as? Double
        val samples = (map["samples"] as? List<*>)?.mapNotNull { item ->
            val pair = item as? List<*> ?: return@mapNotNull null
            val at = pair.getOrNull(0) as? Double ?: return@mapNotNull null
            val kmh = pair.getOrNull(1) as? Double ?: return@mapNotNull null
            SpeedSample(at.toLong(), kmh)
        } ?: emptyList()
        return Workout(
            id = map["id"] as? String ?: return null,
            startedAtEpochMs = (num("startedAt") ?: return null).toLong(),
            endedAtEpochMs = (num("endedAt") ?: return null).toLong(),
            durationSeconds = (num("duration") ?: return null).toLong(),
            distanceKm = num("distanceKm") ?: return null,
            kcal = (num("kcal") ?: return null).toInt(),
            steps = (num("steps") ?: return null).toLong(),
            stepsEstimated = map["stepsEstimated"] as? Boolean ?: false,
            avgSpeedKmh = num("avgKmh") ?: return null,
            maxSpeedKmh = num("maxKmh") ?: return null,
            samples = samples,
        )
    }

    private fun number(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val text = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')
        return if (text.isEmpty() || text == "-") "0" else text
    }

    private fun quote(text: String): String {
        val sb = StringBuilder(text.length + 2).append('"')
        for (c in text) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format(Locale.ROOT, "\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    /** A small strict JSON reader: objects become Map<String, Any?>, arrays List<Any?>, numbers Double. */
    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Any? {
            val value = parseValue()
            skipSpace()
            if (i != s.length) throw IllegalArgumentException("trailing data at $i")
            return value
        }

        private fun parseValue(): Any? {
            skipSpace()
            if (i >= s.length) throw IllegalArgumentException("unexpected end")
            return when (val c = s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) parseNumber() else throw IllegalArgumentException("bad char at $i")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // {
            skipSpace()
            if (peek() == '}') { i++; return map }
            while (true) {
                skipSpace()
                if (peek() != '"') throw IllegalArgumentException("expected key at $i")
                val key = parseString()
                skipSpace()
                if (peek() != ':') throw IllegalArgumentException("expected : at $i")
                i++
                map[key] = parseValue()
                skipSpace()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> throw IllegalArgumentException("expected , or } at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // [
            skipSpace()
            if (peek() == ']') { i++; return list }
            while (true) {
                list += parseValue()
                skipSpace()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> throw IllegalArgumentException("expected , or ] at $i")
                }
            }
        }

        private fun parseString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("bad literal at $i")
            i += word.length
            return value
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}
