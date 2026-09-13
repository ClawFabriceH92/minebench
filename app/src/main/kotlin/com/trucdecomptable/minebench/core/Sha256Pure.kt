package com.trucdecomptable.minebench.core

/**
 * SHA-256 en Kotlin pur, sans MessageDigest : sert de point de comparaison
 * dans le benchmark (JIT face à l'implémentation native).
 */
class Sha256Pure {

    private val w = IntArray(64)
    private val h = IntArray(8)
    private val pad = ByteArray(128)
    private val first = ByteArray(32)

    fun hash(input: ByteArray, out: ByteArray, outOffset: Int = 0) {
        reset()
        compressAll(input, 0, input.size)
        finish(out, outOffset)
    }

    fun doubleHash(input: ByteArray, offset: Int, length: Int, out: ByteArray, outOffset: Int = 0) {
        reset()
        compressAll(input, offset, length)
        finish(first, 0)
        reset()
        compressAll(first, 0, 32)
        finish(out, outOffset)
    }

    private fun reset() {
        h[0] = 0x6a09e667; h[1] = -0x4498517b; h[2] = 0x3c6ef372; h[3] = -0x5ab00ac6
        h[4] = 0x510e527f; h[5] = -0x64fa9774; h[6] = 0x1f83d9ab; h[7] = 0x5be0cd19
    }

    private fun compressAll(data: ByteArray, offset: Int, length: Int) {
        var i = offset
        val end = offset + length
        while (i + 64 <= end) {
            compress(data, i)
            i += 64
        }
        val rem = end - i
        java.util.Arrays.fill(pad, 0, 128, 0.toByte())
        System.arraycopy(data, i, pad, 0, rem)
        pad[rem] = 0x80.toByte()
        val bitLen = length.toLong() * 8L
        val blockCount = if (rem < 56) 1 else 2
        val lenPos = blockCount * 64 - 8
        for (k in 0 until 8) {
            pad[lenPos + k] = ((bitLen ushr (56 - 8 * k)) and 0xff).toByte()
        }
        for (b in 0 until blockCount) compress(pad, b * 64)
    }

    private val k = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e
    )

    private fun compress(data: ByteArray, off: Int) {
        val w = this.w
        for (i in 0 until 16) {
            val j = off + i * 4
            w[i] = ((data[j].toInt() and 0xff) shl 24) or ((data[j + 1].toInt() and 0xff) shl 16) or
                ((data[j + 2].toInt() and 0xff) shl 8) or (data[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val x = w[i - 15]
            val y = w[i - 2]
            val s0 = ror(x, 7) xor ror(x, 18) xor (x ushr 3)
            val s1 = ror(y, 17) xor ror(y, 19) xor (y ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (i in 0 until 64) {
            val s1 = ror(e, 6) xor ror(e, 11) xor ror(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + k[i] + w[i]
            val s0 = ror(a, 2) xor ror(a, 13) xor ror(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    private fun finish(out: ByteArray, outOffset: Int) {
        for (i in 0 until 8) {
            val v = h[i]
            out[outOffset + i * 4] = ((v ushr 24) and 0xff).toByte()
            out[outOffset + i * 4 + 1] = ((v ushr 16) and 0xff).toByte()
            out[outOffset + i * 4 + 2] = ((v ushr 8) and 0xff).toByte()
            out[outOffset + i * 4 + 3] = (v and 0xff).toByte()
        }
    }

    private fun ror(v: Int, n: Int): Int = (v ushr n) or (v shl (32 - n))
}
