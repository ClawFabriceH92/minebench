package com.trucdecomptable.minebench.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Dernière release publiée sur GitHub. */
data class ReleaseInfo(
    val version: String,
    val apkUrl: String,
    val releaseUrl: String,
    val notes: String
)

/**
 * Vérification des mises à jour via l'API GitHub Releases (lecture seule).
 * L'app est distribuée en APK hors Play Store : on compare la version publiée à
 * la version installée, et on propose le téléchargement de l'APK.
 */
object UpdateClient {

    const val OWNER_REPO = "ClawFabriceH92/minebench"
    const val RELEASES_PAGE = "https://github.com/$OWNER_REPO/releases/latest"
    private const val API_LATEST = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private val ASSET_VERSION = Regex("""minebench-v(.+?)\.apk""", RegexOption.IGNORE_CASE)

    /** Version extraite du nom d'asset "minebench-v<version>.apk", ou null. */
    fun versionFromAsset(assetName: String): String? =
        ASSET_VERSION.find(assetName)?.groupValues?.getOrNull(1)

    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val obj = JSONObject(get(API_LATEST))
        val assets = obj.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        var apkName = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url", "")
                apkName = name
                break
            }
        }
        val tag = obj.optString("tag_name", "")
        val releaseUrl = obj.optString("html_url", RELEASES_PAGE)
        ReleaseInfo(
            version = versionFromAsset(apkName) ?: tag.removePrefix("v").ifEmpty { "?" },
            apkUrl = apkUrl.ifEmpty { releaseUrl },
            releaseUrl = releaseUrl,
            notes = obj.optString("body", "")
        )
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "MineBench/1.0")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
