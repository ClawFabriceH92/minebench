package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Block
import com.trucdecomptable.minebench.core.Hex
import com.trucdecomptable.minebench.core.Sha256Md
import com.trucdecomptable.minebench.net.JobBuilder
import com.trucdecomptable.minebench.net.StratumJob
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixtures calculées hors de l'application (Python + hashlib + struct) :
 * si Kotlin produit les mêmes octets, l'assemblage du bloc est correct.
 */
class JobBuilderTest {

    private val coinb1 = "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff20"
    private val coinb2 = "ffffffff0100f2052a010000001976a914000000000000000000000000000000000000000088ac00000000"
    private val xn1 = "08000002"
    private val xn2 = "00000001"

    private val job = StratumJob(
        jobId = "1",
        prevHashHex = "0000000000000000000153a0fef7df22736bd5170f8b2f829f88945de6bcb11b",
        coinb1 = coinb1,
        coinb2 = coinb2,
        merkleBranches = emptyList(),
        versionHex = "20000000",
        nbitsHex = "170355f0",
        ntimeHex = "68c5a1b4",
        cleanJobs = true
    )

    @Test
    fun merkle_root_conforme_a_la_fixture() {
        val root = Block.merkleRoot(Sha256Md(), JobBuilder.coinbase(job, xn1, xn2), emptyList())
        assertEquals("3b608f75e44c061abc9d50505680aae79947e97aabf079767af9298de5a19bfd", Hex.encode(root))
    }

    @Test
    fun en_tete_de_80_octets_conforme_a_la_fixture() {
        val header = JobBuilder.header(job, xn1, xn2, 0L)
        assertEquals(80, header.size)
        assertEquals(
            "000000200000000000000000a053010022dff7fe17d56b73822f8b0f5d94889f1bb1bc" +
                "e63b608f75e44c061abc9d50505680aae79947e97aabf079767af9298de5a19bfd" +
                "b4a1c568f055031700000000",
            Hex.encode(header)
        )
    }

    @Test
    fun hash_du_bloc_candidat_conforme_a_la_fixture() {
        val header = JobBuilder.header(job, xn1, xn2, 0L)
        val out = ByteArray(32)
        Block.hashInternal(header, Sha256Md(), out)
        assertEquals("dbed372310fd411b803d976cf9db0db7b70fc1f29e717ba391d8dad452e1c61b", Block.displayHash(out))
    }

    @Test
    fun le_nonce_modifie_bien_l_en_tete() {
        val a = JobBuilder.header(job, xn1, xn2, 0L)
        val b = JobBuilder.header(job, xn1, xn2, 1L)
        assertEquals(Hex.encode(a, 0, 76), Hex.encode(b, 0, 76))
        assertEquals("01000000", Hex.encode(b, 76, 80))
    }
}
