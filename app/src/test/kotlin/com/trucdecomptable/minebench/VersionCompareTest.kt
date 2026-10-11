package com.trucdecomptable.minebench

import com.trucdecomptable.minebench.core.VersionCompare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun detecte_une_version_plus_recente() {
        assertTrue(VersionCompare.isNewer("1.0.2", "1.0.1"))
        assertTrue(VersionCompare.isNewer("1.1.0", "1.0.9"))
        assertTrue(VersionCompare.isNewer("2.0", "1.9.9"))
        assertTrue(VersionCompare.isNewer("10.0.0", "9.9.9"))
    }

    @Test
    fun egales_ou_anciennes_ne_sont_pas_plus_recentes() {
        assertFalse(VersionCompare.isNewer("1.0.1", "1.0.1"))
        assertFalse(VersionCompare.isNewer("1.0.1", "1.0.2"))
        assertFalse(VersionCompare.isNewer("1.0.0", "1.0")) // segments manquants = 0
        assertFalse(VersionCompare.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun ignore_le_prefixe_v_et_les_suffixes() {
        assertTrue(VersionCompare.isNewer("v1.0.2", "1.0.1"))
        assertFalse(VersionCompare.isNewer("1.0.1-beta", "1.0.1"))
    }
}
