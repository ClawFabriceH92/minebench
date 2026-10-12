package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Hex
import com.trucdecomptable.minebench.core.OpReturn
import com.trucdecomptable.minebench.core.OpReturnMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Encodage d'un message OP_RETURN : comptage en octets UTF-8, limite de 80 octets,
 * bascule PUSHDATA1, troncature sûre. Valeurs validées hors application (Python).
 */
class OpReturnMessageTest {

    @Test
    fun compte_les_octets_utf8_pas_les_caracteres() {
        assertEquals(5, OpReturnMessage.byteLength("Hello"))
        assertEquals(5, OpReturnMessage.byteLength("café")) // é = 2 octets
        assertEquals(4, OpReturnMessage.byteLength("😀"))    // emoji = 4 octets
    }

    @Test
    fun script_court_utilise_un_push_direct() {
        assertEquals("6a0548656c6c6f", OpReturnMessage.buildScriptHex("Hello"))
    }

    @Test
    fun script_de_76_octets_bascule_en_pushdata1() {
        val direct = "A".repeat(75)
        val pushdata1 = "A".repeat(76)
        assertTrue(OpReturnMessage.buildScriptHex(direct).startsWith("6a4b"))
        assertTrue(OpReturnMessage.buildScriptHex(pushdata1).startsWith("6a4c4c"))
    }

    @Test
    fun round_trip_avec_le_decodeur() {
        for (msg in listOf("Hello", "café", "MineBench 2026", "gm 😀", "A".repeat(80))) {
            val script = OpReturnMessage.buildScriptHex(msg)
            val decoded = OpReturn.parse(script)!!
            assertEquals(msg.toByteArray(Charsets.UTF_8).toList(), decoded.combined().toList())
        }
    }

    @Test
    fun limite_a_80_octets() {
        assertTrue(OpReturnMessage.isValid("A".repeat(80)))
        assertFalse(OpReturnMessage.isValid("A".repeat(81)))
        assertFalse(OpReturnMessage.isValid("")) // vide non valide
    }

    @Test
    fun message_trop_long_est_refuse() {
        try {
            OpReturnMessage.buildScript("A".repeat(81))
            throw AssertionError("81 octets aurait dû être refusé")
        } catch (e: IllegalArgumentException) {
            // attendu
        }
    }

    @Test
    fun troncature_respecte_la_limite_sans_couper() {
        // 50 lettres accentuées = 100 octets -> tronqué à 40 caractères / 80 octets
        val accents = "é".repeat(50)
        val t = OpReturnMessage.truncateToLimit(accents)
        assertEquals(40, t.length)
        assertEquals(80, OpReturnMessage.byteLength(t))

        // 30 emojis = 120 octets -> tronqué à 20 octets d'emojis entiers (5 emojis = 20 octets)
        val emojis = "😀".repeat(30)
        val te = OpReturnMessage.truncateToLimit(emojis)
        assertTrue(OpReturnMessage.byteLength(te) <= 80)
        // un emoji fait 4 octets : la longueur en octets doit être un multiple de 4
        assertEquals(0, OpReturnMessage.byteLength(te) % 4)
    }

    @Test
    fun remaining_indique_les_octets_restants() {
        assertEquals(75, OpReturnMessage.remaining("Hello"))
        assertEquals(0, OpReturnMessage.remaining("A".repeat(80)))
        assertEquals(-1, OpReturnMessage.remaining("A".repeat(81)))
    }
}
