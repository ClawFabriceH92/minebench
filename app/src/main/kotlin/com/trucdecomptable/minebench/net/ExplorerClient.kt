package com.trucdecomptable.minebench.net

import com.trucdecomptable.minebench.core.OpReturn
import com.trucdecomptable.minebench.core.OpReturnData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Résumé d'un bloc renvoyé par l'explorateur. */
data class BlockSummary(
    val id: String,
    val height: Int,
    val timestampSec: Long,
    val txCount: Int
)

/** Une sortie OP_RETURN trouvée dans une transaction du bloc. */
data class OpReturnHit(
    val txid: String,
    val voutIndex: Int,
    val data: OpReturnData
)

/** Résultat d'un scan de bloc : le bloc, le nombre de transactions examinées, les OP_RETURN. */
data class BlockOpReturns(
    val block: BlockSummary,
    val scannedTx: Int,
    val hits: List<OpReturnHit>
)

/** Un bloc récent, tel que listé par l'explorateur. */
data class RecentBlock(
    val id: String,
    val height: Int,
    val timestampSec: Long,
    val txCount: Int,
    val sizeBytes: Long,
    val nonce: Long
)

/**
 * Client d'explorateur Bitcoin en lecture seule (API Esplora, telle qu'exposée
 * par mempool.space et blockstream.info). Recherche un bloc par hauteur ou par
 * hash, puis extrait les sorties OP_RETURN de ses transactions.
 *
 * Uniquement des requêtes GET : rien n'est envoyé au réseau Bitcoin. Endpoints
 * utilisés (documentation Esplora) :
 *  - GET /block-height/{height} -> hash du bloc (texte brut) ;
 *  - GET /block/{hash}          -> métadonnées (id, height, timestamp, tx_count) ;
 *  - GET /block/{hash}/txs/{i}  -> page de 25 transactions à partir de l'index i.
 */
class ExplorerClient(private val baseUrl: String = DEFAULT_BASE_URL) {

    /** Résout une requête (hauteur en décimal, ou hash de 64 caractères hex) en hash de bloc. */
    suspend fun resolveBlockHash(query: String): String = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length == 64 && q.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            q.lowercase()
        } else {
            val height = q.toIntOrNull()
                ?: throw IllegalArgumentException("Entrez une hauteur (nombre) ou un hash de 64 caractères")
            require(height >= 0) { "Hauteur invalide" }
            get("$baseUrl/block-height/$height").trim()
        }
    }

    /**
     * Les derniers blocs connus de la chaîne (les 10 plus récents, du plus récent
     * au plus ancien). Endpoint Esplora `GET /blocks`.
     */
    suspend fun fetchLatestBlocks(): List<RecentBlock> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get("$baseUrl/blocks"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    RecentBlock(
                        id = o.optString("id", ""),
                        height = o.optInt("height", -1),
                        timestampSec = o.optLong("timestamp", 0L),
                        txCount = o.optInt("tx_count", 0),
                        sizeBytes = o.optLong("size", 0L),
                        nonce = o.optLong("nonce", 0L)
                    )
                )
            }
        }
    }

    /** Métadonnées du bloc. */
    suspend fun fetchBlock(hash: String): BlockSummary = withContext(Dispatchers.IO) {
        val o = JSONObject(get("$baseUrl/block/$hash"))
        BlockSummary(
            id = o.optString("id", hash),
            height = o.optInt("height", -1),
            timestampSec = o.optLong("timestamp", 0L),
            txCount = o.optInt("tx_count", 0)
        )
    }

    /**
     * Parcourt les transactions du bloc (pages de 25) et collecte les sorties
     * OP_RETURN, jusqu'à [maxTx] transactions examinées. La borne évite d'enchaîner
     * des dizaines de requêtes sur un gros bloc.
     */
    suspend fun scanOpReturns(hash: String, maxTx: Int = DEFAULT_MAX_TX): BlockOpReturns =
        withContext(Dispatchers.IO) {
            val block = fetchBlock(hash)
            val limit = if (block.txCount > 0) minOf(maxTx, block.txCount) else maxTx
            val hits = ArrayList<OpReturnHit>()
            var scanned = 0
            var start = 0
            while (scanned < limit) {
                val page = JSONArray(get("$baseUrl/block/$hash/txs/$start"))
                if (page.length() == 0) break
                var t = 0
                while (t < page.length() && scanned < limit) {
                    val tx = page.getJSONObject(t)
                    val txid = tx.optString("txid", "")
                    val vout = tx.optJSONArray("vout") ?: JSONArray()
                    for (v in 0 until vout.length()) {
                        val script = vout.getJSONObject(v).optString("scriptpubkey", "")
                        OpReturn.parse(script)?.let { hits.add(OpReturnHit(txid, v, it)) }
                    }
                    scanned += 1
                    t += 1
                }
                if (page.length() < PAGE_SIZE) break // dernière page
                start += page.length()
            }
            BlockOpReturns(block, scanned, hits)
        }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("User-Agent", "MineBench/1.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                throw IllegalStateException(
                    "HTTP $code" + if (!detail.isNullOrBlank()) " : ${detail.trim().take(120)}" else ""
                )
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://mempool.space/api"
        const val DEFAULT_MAX_TX = 200
        private const val PAGE_SIZE = 25
    }
}
