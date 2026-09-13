package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Economics
import com.trucdecomptable.minebench.core.NetInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomicsTest {

    private val net = NetInfo(
        networkHashrate = 9.413404138272368e20,
        difficulty = 1.274507897158431e14,
        btcPriceEur = 66565.0,
        origin = "test",
        fetchedAtMs = 0L
    )

    @Test
    fun estimation_telephone_30_mh() {
        val eco = Economics.estimate(30e6, net)
        assertEquals(3.19e-14, eco.shareOfNetwork, 0.05e-14)
        assertEquals(9.5e-7, eco.revenueEurPerDay, 0.3e-7)
        assertTrue(eco.daysPerBlock > 5.0e8)
        assertTrue(eco.daysToEarnOneEur > 1_000_000)
    }

    @Test
    fun estimation_asic_4_th() {
        val eco = Economics.estimate(4e12, net)
        assertEquals(0.1273, eco.revenueEurPerDay, 0.005)
        // environ 1,63 million de jours, soit ~4 500 ans
        assertTrue(eco.daysPerBlock in 1.5e6..1.8e6)
    }

    @Test
    fun cout_electrique_est_soustrait() {
        val eco = Economics.estimate(30e6, net, watts = 8.0, kwhPriceEur = 0.21)
        assertEquals(0.04032, eco.energyEurPerDay!!, 0.001)
        assertTrue(eco.netEurPerDay!! < 0)
    }
}
