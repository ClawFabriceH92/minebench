package com.trucdecomptable.minebench.core

data class NetInfo(
    val networkHashrate: Double,
    val difficulty: Double,
    val btcPriceEur: Double,
    val origin: String,
    val fetchedAtMs: Long
) {
    companion object {
        // Relevés du 13/09/2026 (mempool.space / CoinGecko), utilisés si le réseau est indisponible.
        const val FALLBACK_HASHRATE = 9.413404138272368e20
        const val FALLBACK_DIFFICULTY = 1.274507897158431e14
        const val FALLBACK_PRICE_EUR = 66565.0

        fun fallback(): NetInfo = NetInfo(
            networkHashrate = FALLBACK_HASHRATE,
            difficulty = FALLBACK_DIFFICULTY,
            btcPriceEur = FALLBACK_PRICE_EUR,
            origin = "valeurs de repli (13/09/2026)",
            fetchedAtMs = 0L
        )
    }
}

data class EcoEstimate(
    val shareOfNetwork: Double,
    val revenueEurPerDay: Double,
    val eurPerBlock: Double,
    val daysPerBlock: Double,
    val daysToEarnOneEur: Double,
    val energyEurPerDay: Double?,
    val netEurPerDay: Double?
)

object Economics {

    const val BLOCK_REWARD_BTC = 3.125
    const val BLOCKS_PER_DAY = 144.0

    fun estimate(
        hashrate: Double,
        net: NetInfo,
        watts: Double? = null,
        kwhPriceEur: Double = 0.21
    ): EcoEstimate {
        val share = if (net.networkHashrate > 0) hashrate / net.networkHashrate else 0.0
        val revenuePerDay = share * BLOCKS_PER_DAY * BLOCK_REWARD_BTC * net.btcPriceEur
        val daysPerBlock = if (share > 0) 1.0 / (share * BLOCKS_PER_DAY) else Double.POSITIVE_INFINITY
        val energy = watts?.let { it * 24.0 / 1000.0 * kwhPriceEur }
        return EcoEstimate(
            shareOfNetwork = share,
            revenueEurPerDay = revenuePerDay,
            eurPerBlock = BLOCK_REWARD_BTC * net.btcPriceEur,
            daysPerBlock = daysPerBlock,
            daysToEarnOneEur = if (revenuePerDay > 0) 1.0 / revenuePerDay else Double.POSITIVE_INFINITY,
            energyEurPerDay = energy,
            netEurPerDay = energy?.let { revenuePerDay - it }
        )
    }
}
