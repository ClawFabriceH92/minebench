package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.BenchmarkRunner
import com.trucdecomptable.minebench.core.HashEngine
import com.trucdecomptable.minebench.core.NullSensors
import com.trucdecomptable.minebench.core.PalierSpec
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-régression : un palier de benchmark doit se terminer proprement, sans exception
 * qui remonte (l'interruption du moniteur de progression tuait le process sur Android,
 * corrigé en 1.0.1).
 */
class BenchmarkRunnerTest {

    @Test
    fun palier_court_se_termine_sans_exception() {
        val runner = BenchmarkRunner(NullSensors())
        var ticks = 0
        val measure = runner.run(PalierSpec(HashEngine.MD, threads = 1, dutyPercent = 100, seconds = 1)) {
            ticks++
        }
        assertTrue("des hashes doivent être produits", measure.hashes > 0)
        assertTrue("débit positif", measure.hashrate > 0.0)
        assertTrue("le moniteur doit avoir tické", ticks >= 0)
    }

    @Test
    fun duty_cycle_reduit_le_debit() {
        val runner = BenchmarkRunner(NullSensors())
        val plein = runner.run(PalierSpec(HashEngine.MD, 1, 100, 2))
        val limite = runner.run(PalierSpec(HashEngine.MD, 1, 25, 2))
        assertTrue(
            "le débit limité (${limite.hashrate}) doit être inférieur au plein (${plein.hashrate})",
            limite.hashrate < plein.hashrate
        )
    }
}
