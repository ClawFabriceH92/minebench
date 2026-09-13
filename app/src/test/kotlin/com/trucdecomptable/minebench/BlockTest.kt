package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Block
import com.trucdecomptable.minebench.core.Hex
import com.trucdecomptable.minebench.core.Sha256Md
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérification sur un vrai bloc Bitcoin (height 966856) : l'en-tête reconstruit
 * doit redonner exactement l'identifiant publié par le réseau.
 */
class BlockTest {

    private val blockHash = "00000000000000000001c94c809c87c7d4306ee93db7c6c56bd04ce57fe13b5e"
    private val prevHashRpc = "0000000000000000000153a0fef7df22736bd5170f8b2f829f88945de6bcb11b"
    private val merkleRpc = "5211d1364f3534bd976c9239772c4c8dc2d02610ddcdb40c49775d29e79e01b8"

    @Test
    fun en_tete_du_bloc_966856_donne_le_bon_hash() {
        val header = Block.header(
            version = 536870912L,
            prevHashInternal = Block.prevHashFromRpcHex(prevHashRpc),
            merkleRootInternal = Hex.reverse(Hex.decode(merkleRpc)),
            ntime = 1789322292L,
            nbits = 386020702L,
            nonce = 1399497323L
        )
        val out = ByteArray(32)
        Block.hashInternal(header, Sha256Md(), out)
        assertEquals(blockHash, Block.displayHash(out))
    }

    @Test
    fun conversion_prevhash_stratum_aller_retour() {
        val internal = Block.prevHashFromRpcHex(prevHashRpc)
        val stratumHex = Block.prevHashToStratumHex(internal)
        assertEquals(64, stratumHex.length)
        assertEquals(Hex.encode(internal), Hex.encode(Block.prevHashFromStratumHex(stratumHex)))
    }

    @Test
    fun merkle_root_sans_branche_est_le_double_sha_du_coinbase() {
        val coinbase = "cafebabe".toByteArray()
        val expected = Sha256Md().doubleHash(coinbase)
        val got = Block.merkleRoot(Sha256Md(), coinbase, emptyList())
        assertEquals(Hex.encode(expected), Hex.encode(got))
    }

    @Test
    fun merkle_root_avec_branche_enchaine_bien() {
        val coinbase = ByteArray(32) { it.toByte() }
        val branch = ByteArray(32) { (it + 100).toByte() }
        val sha = Sha256Md()
        val first = Block.merkleRoot(sha, coinbase, emptyList())
        val buf = first + branch
        val expected = Sha256Md().doubleHash(buf)
        val got = Block.merkleRoot(Sha256Md(), coinbase, listOf(branch))
        assertEquals(Hex.encode(expected), Hex.encode(got))
        assertTrue(expected.isNotEmpty())
    }
}
