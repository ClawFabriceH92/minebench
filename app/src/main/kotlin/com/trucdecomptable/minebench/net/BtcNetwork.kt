package com.trucdecomptable.minebench.net

import com.trucdecomptable.minebench.core.NetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Récupère le hashrate réseau, la difficulté et le prix du BTC (avec repli hors ligne). */
object BtcNetwork {

    suspend fun fetch(): NetInfo = withContext(Dispatchers.IO) {
        runCatching {
            val hashRaw = get("https://mempool.space/api/v1/mining/hashrate/1d")
            val hr = JSONObject(hashRaw)
            val hashrate = hr.getDouble("currentHashrate")
            val difficulty = hr.getDouble("currentDifficulty")
            val price = runCatching {
                val p = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=eur"))
                p.getJSONObject("bitcoin").getDouble("eur")
            }.getOrDefault(NetInfo.FALLBACK_PRICE_EUR)
            NetInfo(
                networkHashrate = hashrate,
                difficulty = difficulty,
                btcPriceEur = price,
                origin = "mempool.space + CoinGecko",
                fetchedAtMs = System.currentTimeMillis()
            )
        }.getOrElse { NetInfo.fallback() }
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "MineBench/1.0")
        }
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
