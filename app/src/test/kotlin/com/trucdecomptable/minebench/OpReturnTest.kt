package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Hex
import com.trucdecomptable.minebench.core.OpReturn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Décodage OP_RETURN. Les scripts et résultats attendus ont été validés hors
 * application (parseur de référence Python + miroir JVM) avant d'être figés ici.
 */
class OpReturnTest {

    @Test
    fun push_direct_donne_le_texte() {
        val d = OpReturn.parse("6a094d696e6542656e6368")!!
        assertEquals(1, d.pushes.size)
        assertEquals("4d696e6542656e6368", d.hex())
        assertEquals("MineBench", d.text())
        assertEquals(9, d.dataSize())
    }

    @Test
    fun pushdata1_lit_la_longueur_sur_un_octet() {
        val payload = ByteArray(76) { it.toByte() }
        val script = "6a4c4c" + Hex.encode(payload)
        val d = OpReturn.parse(script)!!
        assertEquals(1, d.pushes.size)
        assertEquals(76, d.dataSize())
        assertEquals(Hex.encode(payload), d.hex())
    }

    @Test
    fun plusieurs_pushes_sont_concatenes() {
        val d = OpReturn.parse("6a0568656c6c6f05776f726c64")!!
        assertEquals(2, d.pushes.size)
        assertEquals("helloworld", d.text())
        assertEquals("68656c6c6f776f726c64", d.hex())
    }

    @Test
    fun script_non_op_return_renvoie_null() {
        // scriptPubKey P2PKH classique : ne commence pas par 0x6a
        assertNull(OpReturn.parse("76a914" + "00".repeat(20) + "88ac"))
    }

    @Test
    fun op_return_nu_est_valide_sans_donnees() {
        val d = OpReturn.parse("6a")!!
        assertTrue(d.pushes.isEmpty())
        assertEquals("", d.hex())
        assertEquals(0, d.dataSize())
    }

    @Test
    fun push_tronque_s_arrete_proprement() {
        // annonce 5 octets mais n'en fournit que 2 : on s'arrête sans planter
        val d = OpReturn.parse("6a0568656c")!!
        assertTrue(d.pushes.isEmpty())
    }

    @Test
    fun octets_non_imprimables_deviennent_des_points() {
        // 4 octets : 0x00, 0xff, 0x10, 0x41 ('A'). Seul 'A' est imprimable.
        val d = OpReturn.parse("6a0400ff1041")!!
        assertEquals("...A", d.text())
        assertEquals("00ff1041", d.hex())
    }

    @Test
    fun hex_en_majuscules_est_accepte() {
        val d = OpReturn.parse("6A094D696E6542656E6368")!!
        assertEquals("MineBench", d.text())
        assertEquals("6a094d696e6542656e6368", d.scriptHex)
    }
}
