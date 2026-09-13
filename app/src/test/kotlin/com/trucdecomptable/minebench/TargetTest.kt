package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Block
import com.trucdecomptable.minebench.core.Hex
import com.trucdecomptable.minebench.core.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class TargetTest {

    @Test
    fun compact_diff1_correspond_a_la_cible_de_reference() {
        assertEquals(Hex.encode(Target.DIFF1.bytes), Hex.encode(Target.fromCompact(0x1d00ffffL).bytes))
    }

    @Test
    fun difficulte_du_bloc_966856_est_coherente() {
        // nbits du bloc 966856 = 386020702 (0x1702355e)
        val target = Target.fromCompact(386020702L)
        val ratio = BigDecimal(Target.DIFF1.toBigInteger())
            .divide(BigDecimal(target.toBigInteger()), MathContext(10))
        val difficulty = ratio.toDouble()
        assertEquals(1.274507897158431e14, difficulty, 1e12)
    }

    @Test
    fun le_hash_du_bloc_966856_atteint_sa_cible() {
        val target = Target.fromCompact(386020702L)
        val hashInternal = Hex.reverse(Hex.decode("00000000000000000001c94c809c87c7d4306ee93db7c6c56bd04ce57fe13b5e"))
        assertTrue(target.meets(hashInternal))
    }

    @Test
    fun un_hash_trop_grand_ne_passe_pas() {
        val target = Target.fromCompact(386020702L)
        assertFalse(target.meets(ByteArray(32) { 0xff.toByte() }))
        assertTrue(target.meets(ByteArray(32)))
    }

    @Test
    fun difficulte_1_correspond_a_la_cible_de_reference() {
        assertEquals(
            Hex.encode(Target.DIFF1.bytes),
            Hex.encode(Target.fromDifficulty(1.0).bytes)
        )
    }
}
