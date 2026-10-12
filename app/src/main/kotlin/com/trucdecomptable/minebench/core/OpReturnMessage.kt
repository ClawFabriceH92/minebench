package com.trucdecomptable.minebench.core

/**
 * Préparation d'un message à inscrire dans une sortie OP_RETURN.
 *
 * La règle de relais standard de Bitcoin limite la charge utile d'un OP_RETURN
 * à 80 octets (le scriptPubKey complet fait au plus 83 octets : 0x6a + opcode de
 * push + données). On compte donc en OCTETS UTF-8, pas en caractères : une lettre
 * accentuée occupe 2 octets, un emoji 4. Cette classe fournit le comptage, la
 * troncature sûre (jamais au milieu d'un caractère) et la construction du script.
 *
 * Aucun accès réseau, aucune signature : uniquement l'encodage du message.
 */
object OpReturnMessage {

    /** Taille maximale de la charge utile d'un OP_RETURN, en octets (politique standard). */
    const val MAX_DATA_BYTES = 80

    private const val OP_RETURN = 0x6a
    private const val OP_PUSHDATA1 = 0x4c
    private const val MAX_DIRECT_PUSH = 0x4b // 75 : au-delà, il faut PUSHDATA1

    /** Longueur du message en octets UTF-8. */
    fun byteLength(message: String): Int = message.toByteArray(Charsets.UTF_8).size

    /** Octets encore disponibles avant d'atteindre la limite (peut être négatif si dépassement). */
    fun remaining(message: String): Int = MAX_DATA_BYTES - byteLength(message)

    /** true si le message tient dans la limite (et n'est pas vide). */
    fun isValid(message: String): Boolean {
        val n = byteLength(message)
        return n in 1..MAX_DATA_BYTES
    }

    /**
     * Tronque le message pour qu'il tienne dans [limit] octets UTF-8, sans jamais
     * couper un caractère au milieu (les caractères hors BMP, comme les emoji, sont
     * traités en un bloc).
     */
    fun truncateToLimit(message: String, limit: Int = MAX_DATA_BYTES): String {
        val sb = StringBuilder()
        var total = 0
        var i = 0
        while (i < message.length) {
            val cp = message.codePointAt(i)
            val count = Character.charCount(cp)
            val piece = message.substring(i, i + count)
            val n = piece.toByteArray(Charsets.UTF_8).size
            if (total + n > limit) break
            sb.append(piece)
            total += n
            i += count
        }
        return sb.toString()
    }

    /**
     * Construit le scriptPubKey OP_RETURN pour ce message.
     * @throws IllegalArgumentException si le message est vide ou dépasse [MAX_DATA_BYTES].
     */
    fun buildScript(message: String): ByteArray {
        val data = message.toByteArray(Charsets.UTF_8)
        require(data.isNotEmpty()) { "Message vide" }
        require(data.size <= MAX_DATA_BYTES) {
            "Message de ${data.size} octets, maximum $MAX_DATA_BYTES"
        }
        return if (data.size <= MAX_DIRECT_PUSH) {
            ByteArray(2 + data.size).also {
                it[0] = OP_RETURN.toByte()
                it[1] = data.size.toByte()
                System.arraycopy(data, 0, it, 2, data.size)
            }
        } else {
            ByteArray(3 + data.size).also {
                it[0] = OP_RETURN.toByte()
                it[1] = OP_PUSHDATA1.toByte()
                it[2] = data.size.toByte()
                System.arraycopy(data, 0, it, 3, data.size)
            }
        }
    }

    /** scriptPubKey OP_RETURN en hexadécimal. */
    fun buildScriptHex(message: String): String = Hex.encode(buildScript(message))
}
