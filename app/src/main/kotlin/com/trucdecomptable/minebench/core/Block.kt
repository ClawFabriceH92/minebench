package com.trucdecomptable.minebench.core

import java.math.BigInteger

/**
 * Cible de difficulté. Stockée en 32 octets big-endian (ordre d'affichage).
 * Le hash SHA256d brut est en ordre interne : pour le comparer, on le lit à l'envers.
 */
class Target(val bytes: ByteArray) {

    init {
        require(bytes.size == 32) { "target doit faire 32 octets" }
    }

    fun meets(hashInternal: ByteArray): Boolean {
        for (i in 0 until 32) {
            val hb = hashInternal[31 - i].toInt() and 0xff
            val tb = bytes[i].toInt() and 0xff
            if (hb < tb) return true
            if (hb > tb) return false
        }
        return true
    }

    fun toBigInteger(): BigInteger = BigInteger(1, bytes)

    companion object {

        /** Cible de difficulté 1 : 0x00000000FFFF0000...0000 */
        val DIFF1: Target = build {
            val b = ByteArray(32)
            b[4] = 0xff.toByte(); b[5] = 0xff.toByte()
            b
        }

        /** nBits compact -> cible (mantissa x 256^(exposant-3)). */
        fun fromCompact(bits: Long): Target {
            val exponent = ((bits ushr 24) and 0xffL).toInt()
            val mantissa = bits and 0xffffffL
            var v = BigInteger.valueOf(mantissa)
            v = if (exponent >= 3) v.shiftLeft(8 * (exponent - 3)) else v.shiftRight(8 * (3 - exponent))
            return Target(to32(v))
        }

        /** difficulté (1 = diff1) -> cible = diff1 / difficulté. */
        fun fromDifficulty(difficulty: Double): Target {
            if (difficulty <= 0.0) return DIFF1
            val scaled = BigInteger.valueOf((difficulty * 1_000_000_000_000.0).toLong())
            val value = DIFF1.toBigInteger().multiply(BigInteger.valueOf(1_000_000_000_000L)).divide(scaled)
            return Target(to32(value))
        }

        private inline fun build(f: () -> ByteArray): Target = Target(f())

        private fun to32(v: BigInteger): ByteArray {
            val raw = v.toByteArray()
            val out = ByteArray(32)
            if (raw.size >= 32) {
                System.arraycopy(raw, raw.size - 32, out, 0, 32)
            } else {
                System.arraycopy(raw, 0, out, 32 - raw.size, raw.size)
            }
            return out
        }
    }
}

/** Assemblage des blocs Bitcoin : header 80 octets, merkle root, conversions Stratum. */
object Block {

    const val HEADER_SIZE = 80

    fun header(
        version: Long,
        prevHashInternal: ByteArray,
        merkleRootInternal: ByteArray,
        ntime: Long,
        nbits: Long,
        nonce: Long
    ): ByteArray {
        val h = ByteArray(HEADER_SIZE)
        Hex.writeUInt32LE(h, 0, version)
        System.arraycopy(prevHashInternal, 0, h, 4, 32)
        System.arraycopy(merkleRootInternal, 0, h, 36, 32)
        Hex.writeUInt32LE(h, 68, ntime)
        Hex.writeUInt32LE(h, 72, nbits)
        Hex.writeUInt32LE(h, 76, nonce)
        return h
    }

    /** Hash d'un header en ordre interne (le hash "affiché" est l'inverse). */
    fun hashInternal(header: ByteArray, sha: Sha256Md, out: ByteArray) {
        sha.doubleHash(header, 0, HEADER_SIZE, out)
    }

    fun displayHash(hashInternal: ByteArray): String = Hex.encode(Hex.reverse(hashInternal))

    /** prevhash tel que fourni par Stratum : 8 mots de 4 octets big-endian, écrits little-endian. */
    fun prevHashFromStratumHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length == 64) { "prevhash Stratum attendu sur 64 caractères" }
        val out = ByteArray(32)
        for (i in 0 until 8) {
            val word = java.lang.Long.parseLong(clean.substring(i * 8, i * 8 + 8), 16)
            Hex.writeUInt32LE(out, i * 4, word)
        }
        return out
    }

    /** prevhash tel que fourni par l'API RPC de Bitcoin (ordre d'affichage). */
    fun prevHashFromRpcHex(hex: String): ByteArray = Hex.reverse(Hex.decode(hex))

    /** Conversion inverse, utilisée par les tests de cohérence. */
    fun prevHashToStratumHex(prevHashInternal: ByteArray): String {
        val sb = StringBuilder(64)
        for (i in 0 until 8) {
            val word = Hex.readUInt32LE(prevHashInternal, i * 4)
            sb.append(String.format("%08x", word))
        }
        return sb.toString()
    }

    /** Merkle root : sha256d(coinbase) puis sha256d(root || branche) pour chaque branche. */
    fun merkleRoot(sha: Sha256Md, coinbase: ByteArray, branches: List<ByteArray>): ByteArray {
        val root = ByteArray(32)
        sha.doubleHash(coinbase, 0, coinbase.size, root)
        if (branches.isEmpty()) return root
        val buf = ByteArray(64)
        for (branch in branches) {
            System.arraycopy(root, 0, buf, 0, 32)
            System.arraycopy(branch, 0, buf, 32, 32)
            sha.doubleHash(buf, 0, 64, root)
        }
        return root
    }
}
