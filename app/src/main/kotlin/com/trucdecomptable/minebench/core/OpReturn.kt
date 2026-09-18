package com.trucdecomptable.minebench.core

/**
 * Lecture des sorties OP_RETURN d'une transaction Bitcoin.
 *
 * Le scriptPubKey d'une sortie OP_RETURN commence par l'opcode 0x6a, suivi de
 * zéro ou plusieurs "pushes" de données. On extrait ces données et on en propose
 * deux lectures : hexadécimal brut et texte UTF-8 (les octets non imprimables
 * sont remplacés par un point).
 *
 * Le parseur ne fait aucun accès réseau : il travaille sur un scriptPubKey déjà
 * récupéré (hex). La logique de push suit le script Bitcoin :
 *  - 0x01..0x4b : pousse directement N octets ;
 *  - 0x4c (PUSHDATA1) : 1 octet de longueur, puis les données ;
 *  - 0x4d (PUSHDATA2) : 2 octets de longueur (little-endian), puis les données ;
 *  - 0x4e (PUSHDATA4) : 4 octets de longueur (little-endian), puis les données ;
 *  - 0x00 (OP_0) : pousse une valeur vide ;
 *  - 0x51..0x60 (OP_1..OP_16) : pousse un petit entier 1..16.
 */
data class OpReturnData(
    /** scriptPubKey complet, en hexadécimal (minuscules). */
    val scriptHex: String,
    /** Blocs de données poussés après OP_RETURN, dans l'ordre. */
    val pushes: List<ByteArray>
) {
    /** Concaténation de tous les pushes. */
    fun combined(): ByteArray {
        val out = ByteArray(dataSize())
        var pos = 0
        for (p in pushes) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

    /** Nombre total d'octets de données. */
    fun dataSize(): Int = pushes.sumOf { it.size }

    /** Données concaténées en hexadécimal. */
    fun hex(): String = Hex.encode(combined())

    /**
     * Rendu texte : décodage UTF-8, puis remplacement des caractères de contrôle
     * et des octets invalides (U+FFFD) par un point, pour rester lisible.
     */
    fun text(): String {
        val decoded = String(combined(), Charsets.UTF_8)
        val sb = StringBuilder(decoded.length)
        for (ch in decoded) {
            sb.append(if (ch == '�' || ch.isISOControl()) '.' else ch)
        }
        return sb.toString()
    }
}

object OpReturn {

    private const val OP_RETURN = 0x6a
    private const val OP_PUSHDATA1 = 0x4c
    private const val OP_PUSHDATA2 = 0x4d
    private const val OP_PUSHDATA4 = 0x4e

    /**
     * Décode un scriptPubKey. Renvoie null si ce n'est pas une sortie OP_RETURN
     * (script vide, longueur hex impaire, ou premier octet différent de 0x6a).
     * Tolérant : s'arrête proprement sur un push tronqué ou un opcode non-push,
     * en gardant les pushes déjà lus.
     */
    fun parse(scriptHex: String): OpReturnData? {
        val clean = scriptHex.trim()
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        val b = Hex.decode(clean)
        if (b.isEmpty() || (b[0].toInt() and 0xff) != OP_RETURN) return null
        val pushes = ArrayList<ByteArray>()
        var i = 1
        while (i < b.size) {
            val op = b[i].toInt() and 0xff
            i += 1
            val len: Int = when {
                op in 0x01..0x4b -> op
                op == OP_PUSHDATA1 -> {
                    if (i + 1 > b.size) break
                    (b[i].toInt() and 0xff).also { i += 1 }
                }
                op == OP_PUSHDATA2 -> {
                    if (i + 2 > b.size) break
                    ((b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)).also { i += 2 }
                }
                op == OP_PUSHDATA4 -> {
                    if (i + 4 > b.size) break
                    ((b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8) or
                        ((b[i + 2].toInt() and 0xff) shl 16) or ((b[i + 3].toInt() and 0xff) shl 24)).also { i += 4 }
                }
                op == 0x00 -> {
                    pushes.add(ByteArray(0))
                    continue
                }
                op in 0x51..0x60 -> {
                    pushes.add(byteArrayOf((op - 0x50).toByte()))
                    continue
                }
                else -> break // opcode non-push : on arrête proprement
            }
            if (len < 0 || len > b.size - i) break // push tronqué ou longueur aberrante
            pushes.add(b.copyOfRange(i, i + len))
            i += len
        }
        return OpReturnData(clean.lowercase(), pushes)
    }
}
