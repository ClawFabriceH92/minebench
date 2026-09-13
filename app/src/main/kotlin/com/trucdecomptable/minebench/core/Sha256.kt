package com.trucdecomptable.minebench.core

import java.security.MessageDigest

/**
 * SHA-256 via MessageDigest. Sur Android, ce chemin descend dans l'implémentation
 * native (BoringSSL / Conscrypt) qui utilise les extensions crypto ARMv8 quand elles existent.
 */
class Sha256Md {
    private val md: MessageDigest = MessageDigest.getInstance("SHA-256")
    private val first = ByteArray(32)

    fun hash(data: ByteArray): ByteArray {
        md.reset()
        return md.digest(data)
    }

    /** Double SHA-256 (Bitcoin) d'un tampon, résultat écrit dans out (32 octets). */
    fun doubleHash(data: ByteArray, offset: Int, length: Int, out: ByteArray, outOffset: Int = 0) {
        md.reset()
        md.update(data, offset, length)
        md.digest(first, 0, 32)
        md.reset()
        md.update(first, 0, 32)
        md.digest(out, outOffset, 32)
    }

    fun doubleHash(data: ByteArray): ByteArray {
        val out = ByteArray(32)
        doubleHash(data, 0, data.size, out)
        return out
    }
}
