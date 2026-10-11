package com.trucdecomptable.minebench.core

/**
 * Comparaison de versions "x.y.z" (style semver simplifié), pour décider si une
 * version distante est plus récente que la version installée.
 *
 * Chaque version est découpée sur `.`, `-` et `+` ; de chaque segment on ne garde
 * que les chiffres de tête (donc "1.0.1-beta" => [1, 0, 1]). Un préfixe "v" est
 * ignoré. Les segments manquants valent 0 ("1.0" == "1.0.0").
 */
object VersionCompare {

    /** true si [remote] est strictement plus récente que [current]. */
    fun isNewer(remote: String, current: String): Boolean = compare(remote, current) > 0

    /** < 0 si a < b, 0 si égales, > 0 si a > b. */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split('.', '-', '+')
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
