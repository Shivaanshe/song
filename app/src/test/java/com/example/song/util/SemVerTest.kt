package com.example.song.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun parse_validVersionStrings() {
        val v1 = SemVer.parse("v3.10.0")
        assertNotNull(v1)
        assertEquals(3, v1!!.major)
        assertEquals(10, v1.minor)
        assertEquals(0, v1.patch)

        val v2 = SemVer.parse("3.4.0")
        assertNotNull(v2)
        assertEquals(3, v2!!.major)
        assertEquals(4, v2.minor)
        assertEquals(0, v2.patch)

        val v3 = SemVer.parse("V1.2.3-beta1")
        assertNotNull(v3)
        assertEquals(1, v3!!.major)
        assertEquals(2, v3.minor)
        assertEquals(3, v3.patch)
    }

    @Test
    fun parse_invalidVersionStrings() {
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("   "))
        assertNull(SemVer.parse("invalid"))
    }

    @Test
    fun compareTo_evaluates3_10_0_greaterThan_3_4_0() {
        val v3_10 = SemVer.parse("v3.10.0")!!
        val v3_4 = SemVer.parse("3.4.0")!!

        assertTrue(v3_10 > v3_4)
        assertTrue(v3_10.isNewerThan(v3_4))
        assertFalse(v3_4 > v3_10)
    }

    @Test
    fun compareTo_equalVersions() {
        val v1 = SemVer.parse("v3.4.0")!!
        val v2 = SemVer.parse("3.4.0")!!

        assertEquals(0, v1.compareTo(v2))
        assertFalse(v1.isNewerThan(v2))
    }
}
