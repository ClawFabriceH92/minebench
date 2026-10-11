package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.Format
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Formatage taille / durée écoulée. Chaînes attendues en locale fr-FR (virgule
 * décimale), validées hors application via un miroir JVM.
 */
class FormatTest {

    @Test
    fun size_utilise_les_paliers_decimaux() {
        assertEquals("0 o", Format.size(0))
        assertEquals("999 o", Format.size(999))
        assertEquals("1,00 Ko", Format.size(1000))
        assertEquals("1,50 Ko", Format.size(1500))
        assertEquals("1,17 Mo", Format.size(1_174_876))
        assertEquals("3,99 Mo", Format.size(3_993_003))
    }

    @Test
    fun ago_choisit_la_bonne_unite() {
        assertEquals("il y a 5 s", Format.ago(5))
        assertEquals("il y a 1 min", Format.ago(65))
        assertEquals("il y a 1 h", Format.ago(3700))
        assertEquals("il y a 1 j", Format.ago(90_000))
    }

    @Test
    fun ago_borne_les_valeurs_negatives_a_zero() {
        assertEquals("il y a 0 s", Format.ago(-3))
    }
}
