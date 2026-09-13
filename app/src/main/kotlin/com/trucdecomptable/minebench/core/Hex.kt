package com.trucdecomptable.minebench.core

/** Utilitaires hexadécimaux et endianness (little / big). */
object Hex {

    private val DIGITS = "0123456789abcdef".toCharArray()

    fun encode(b: ByteArray, from: Int = 0, to: Int = b.size): String {
        val sb = StringBuilder((to - from) * 2)
        for (i in from until to) {
            val v = b[i].toInt() and 0xff
            sb.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0f])
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        val clean = s.trim()
        require(clean.length % 2 == 0) { "hex de longueur impaire: ${clean.length}" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) or Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    fun reverse(b: ByteArray): ByteArray {
        val out = ByteArray(b.size)
        for (i in b.indices) out[i] = b[b.size - 1 - i]
        return out
    }

    /** Lit un uint32 little-endian et le rend en Long non signé. */
    fun readUInt32LE(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xffL) or
            ((b[off + 1].toLong() and 0xffL) shl 8) or
            ((b[off + 2].toLong() and 0xffL) shl 16) or
            ((b[off + 3].toLong() and 0xffL) shl 24)

    fun writeUInt32LE(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v ushr 8) and 0xff).toByte()
        b[off + 2] = ((v ushr 16) and 0xff).toByte()
        b[off + 3] = ((v ushr 24) and 0xff).toByte()
    }

    fun parseUInt32(s: String, radix: Int = 16): Long = java.lang.Long.parseLong(s.trim(), radix)
}
