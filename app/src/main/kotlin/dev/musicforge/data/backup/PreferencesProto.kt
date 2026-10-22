package dev.musicforge.data.backup

import java.io.ByteArrayOutputStream

/**
 * Reads a DataStore `*.preferences_pb` file without pulling in protobuf.
 *
 * Wire format (androidx.datastore.preferences.PreferencesProto):
 * ```
 * PreferenceMap { map<string, Value> preferences = 1; }
 * Value { oneof { bool boolean = 1; float float = 2; int32 integer = 3; int64 long = 4;
 *                 string string = 5; StringSet string_set = 6; double double = 7; bytes bytes = 8; } }
 * StringSet { repeated string strings = 1; }
 * ```
 */
object PreferencesProto {
    sealed interface Value {
        data class Bool(val v: Boolean) : Value
        data class F32(val v: Float) : Value
        data class I32(val v: Int) : Value
        data class I64(val v: Long) : Value
        data class Str(val v: String) : Value
        data class StrSet(val v: Set<String>) : Value
        data class F64(val v: Double) : Value
        data class Bytes(val v: ByteArray) : Value {
            override fun equals(other: Any?) = other is Bytes && other.v.contentEquals(v)
            override fun hashCode() = v.contentHashCode()
        }
    }

    fun parse(bytes: ByteArray): Map<String, Value> {
        val out = LinkedHashMap<String, Value>()
        val r = Reader(bytes, 0, bytes.size)
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            if (field == 1 && wire == WIRE_LEN) {
                val entry = r.sub()
                var key: String? = null
                var value: Value? = null
                while (entry.hasMore()) {
                    val (f, w) = entry.tag()
                    when {
                        f == 1 && w == WIRE_LEN -> key = entry.string()
                        f == 2 && w == WIRE_LEN -> value = parseValue(entry.sub())
                        else -> entry.skip(w)
                    }
                }
                if (key != null && value != null) out[key] = value
            } else {
                r.skip(wire)
            }
        }
        return out
    }

    private fun parseValue(r: Reader): Value? {
        var value: Value? = null
        while (r.hasMore()) {
            val (f, w) = r.tag()
            value = when (f) {
                1 -> Value.Bool(r.varint() != 0L)
                2 -> Value.F32(java.lang.Float.intBitsToFloat(r.fixed32()))
                3 -> Value.I32(r.varint().toInt())
                4 -> Value.I64(r.varint())
                5 -> Value.Str(r.string())
                6 -> {
                    val set = r.sub()
                    val strings = LinkedHashSet<String>()
                    while (set.hasMore()) {
                        val (sf, sw) = set.tag()
                        if (sf == 1 && sw == WIRE_LEN) strings += set.string() else set.skip(sw)
                    }
                    Value.StrSet(strings)
                }
                7 -> Value.F64(java.lang.Double.longBitsToDouble(r.fixed64()))
                8 -> Value.Bytes(r.bytes())
                else -> { r.skip(w); value }
            }
        }
        return value
    }

    /** Inverse of [parse]; used by tests to build fixtures. */
    fun write(values: Map<String, Value>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((key, value) in values) {
            val v = ByteArrayOutputStream()
            when (value) {
                is Value.Bool -> { v.tag(1, WIRE_VARINT); v.varint(if (value.v) 1 else 0) }
                is Value.F32 -> { v.tag(2, WIRE_I32); v.fixed32(java.lang.Float.floatToIntBits(value.v)) }
                is Value.I32 -> { v.tag(3, WIRE_VARINT); v.varint(value.v.toLong()) }
                is Value.I64 -> { v.tag(4, WIRE_VARINT); v.varint(value.v) }
                is Value.Str -> v.lenField(5, value.v.toByteArray())
                is Value.StrSet -> {
                    val s = ByteArrayOutputStream()
                    value.v.forEach { s.lenField(1, it.toByteArray()) }
                    v.lenField(6, s.toByteArray())
                }
                is Value.F64 -> { v.tag(7, WIRE_I64); v.fixed64(java.lang.Double.doubleToLongBits(value.v)) }
                is Value.Bytes -> v.lenField(8, value.v)
            }
            val entry = ByteArrayOutputStream()
            entry.lenField(1, key.toByteArray())
            entry.lenField(2, v.toByteArray())
            out.lenField(1, entry.toByteArray())
        }
        return out.toByteArray()
    }

    private const val WIRE_VARINT = 0
    private const val WIRE_I64 = 1
    private const val WIRE_LEN = 2
    private const val WIRE_I32 = 5

    private class Reader(private val buf: ByteArray, private var pos: Int, private val end: Int) {
        fun hasMore() = pos < end

        fun tag(): Pair<Int, Int> {
            val t = varint().toInt()
            return (t ushr 3) to (t and 7)
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                check(pos < end) { "truncated varint" }
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            error("malformed varint")
        }

        fun fixed32(): Int {
            check(pos + 4 <= end) { "truncated fixed32" }
            var v = 0
            for (i in 0 until 4) v = v or ((buf[pos + i].toInt() and 0xff) shl (8 * i))
            pos += 4
            return v
        }

        fun fixed64(): Long {
            check(pos + 8 <= end) { "truncated fixed64" }
            var v = 0L
            for (i in 0 until 8) v = v or ((buf[pos + i].toLong() and 0xff) shl (8 * i))
            pos += 8
            return v
        }

        fun bytes(): ByteArray {
            val len = varint().toInt()
            check(len >= 0 && pos + len <= end) { "bad length $len" }
            return buf.copyOfRange(pos, pos + len).also { pos += len }
        }

        fun string() = String(bytes(), Charsets.UTF_8)

        fun sub(): Reader {
            val len = varint().toInt()
            check(len >= 0 && pos + len <= end) { "bad length $len" }
            return Reader(buf, pos, pos + len).also { pos += len }
        }

        fun skip(wire: Int) {
            when (wire) {
                WIRE_VARINT -> varint()
                WIRE_I64 -> pos += 8
                WIRE_LEN -> { val len = varint().toInt(); pos += len }
                WIRE_I32 -> pos += 4
                else -> error("unsupported wire type $wire")
            }
            check(pos <= end) { "truncated field" }
        }
    }

    private fun ByteArrayOutputStream.varint(value: Long) {
        var v = value
        while (v and 0x7fL.inv() != 0L) {
            write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
        write(v.toInt())
    }

    private fun ByteArrayOutputStream.tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())

    private fun ByteArrayOutputStream.lenField(field: Int, data: ByteArray) {
        tag(field, WIRE_LEN)
        varint(data.size.toLong())
        write(data)
    }

    private fun ByteArrayOutputStream.fixed32(v: Int) {
        for (i in 0 until 4) write((v ushr (8 * i)) and 0xff)
    }

    private fun ByteArrayOutputStream.fixed64(v: Long) {
        for (i in 0 until 8) write(((v ushr (8 * i)) and 0xff).toInt())
    }
}
