package com.drwho174.infusioncalc.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSeedTest {
    private val drugs = CatalogSeed.drugs()

    @Test
    fun `catalog contains the requested drugs`() {
        val ids = drugs.map { it.id }.toSet()
        listOf(
            "norepinephrine",
            "epinephrine",
            "dobutamine",
            "levosimendan",
            "propofol",
            "dexmedetomidine",
            "rocuronium",
            "nitroglycerin",
            "dopamine",
            "midazolam",
            "fentanyl",
        ).forEach { id ->
            assertTrue("missing $id", id in ids)
        }
        assertEquals(11, drugs.size)
    }

    @Test
    fun `every drug has a scheme and at least one preset`() {
        drugs.forEach { drug ->
            assertTrue("${drug.id}: no schemes", drug.schemes.isNotEmpty())
            assertTrue("${drug.id}: no presets", drug.presets.isNotEmpty())
            assertEquals("${drug.id}: default scheme", drug.schemes.first().id, drug.defaultSchemeId)
            assertTrue("${drug.id}: built-in", drug.isBuiltIn)
        }
    }

    @Test
    fun `dose ranges are ordered`() {
        drugs.forEach { drug ->
            drug.schemes.forEach { scheme ->
                val min = scheme.doseMin
                val max = scheme.doseMax
                if (min != null || max != null) {
                    assertNotNull("${drug.id}: no min", min)
                    assertNotNull("${drug.id}: no max", max)
                    assertTrue("${drug.id}: min < max", min!! < max!!)
                    assertTrue("${drug.id}: min > 0", min.signum() > 0)
                }
            }
        }
    }

    @Test
    fun `every preset has a positive computable concentration`() {
        drugs.forEach { drug ->
            drug.presets.forEach { preset ->
                val concentration = preset.concentrationIn()
                assertNotNull("${drug.id}/${preset.id}: no concentration", concentration)
                assertTrue("${drug.id}/${preset.id}: concentration > 0", concentration!!.signum() > 0)
            }
        }
    }

    @Test
    fun `presets are convertible to the scheme unit`() {
        drugs.forEach { drug ->
            val scheme = drug.schemes.first()
            drug.presets.forEach { preset ->
                val concentration = preset.concentrationIn(scheme.amountUnit)
                assertNotNull(
                    "${drug.id}/${preset.id}: units ${preset.amountUnit} -> ${scheme.amountUnit}",
                    concentration,
                )
            }
        }
    }

    @Test
    fun `exactly one default preset per drug`() {
        drugs.forEach { drug ->
            val defaults = drug.presets.count { it.isDefault }
            assertEquals("${drug.id}: default presets", 1, defaults)
        }
    }
}
