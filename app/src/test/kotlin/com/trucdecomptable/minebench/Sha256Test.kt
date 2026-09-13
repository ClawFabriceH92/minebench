package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Hex
import com.trucdecomptable.minebench.core.Sha256Md
import com.trucdecomptable.minebench.core.Sha256Pure
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class Sha256Test {

    @Test
    fun vecteur_abc() {
        val out = ByteArray(32)
        Sha256Pure().hash("abc".toByteArray(), out)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hex.encode(out))
    }

    @Test
    fun vecteur_vide() {
        val out = ByteArray(32)
        Sha256Pure().hash(ByteArray(0), out)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hex.encode(out))
    }

    @Test
    fun double_hash_vide() {
        val out = ByteArray(32)
        Sha256Md().doubleHash(ByteArray(0), 0, 0, out)
        assertEquals("5df6e0e2761359d30a8275058e299fcc0381534545f55cf43e41983f5d4c9456", Hex.encode(out))
    }

    @Test
    fun implementation_pure_egale_message_digest() {
        val md = Sha256Md()
        val pure = Sha256Pure()
        val a = ByteArray(32)
        val b = ByteArray(32)
        repeat(200) { i ->
            val data = ByteArray(i) { (it * 7 + i).toByte() }
            md.doubleHash(data, 0, data.size, a)
            pure.doubleHash(data, 0, data.size, b)
            assertEquals("longueur $i", Hex.encode(a), Hex.encode(b))
        }
        // cas 80 octets, taille exacte d'un en-tête de bloc
        val header = ByteArray(80) { Random(1).nextInt().toByte() }
        md.doubleHash(header, 0, 80, a)
        pure.doubleHash(header, 0, 80, b)
        assertEquals(Hex.encode(a), Hex.encode(b))
    }
}
