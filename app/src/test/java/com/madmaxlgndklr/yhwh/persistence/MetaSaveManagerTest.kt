package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.SeedBonus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class MetaSaveManagerTest {

    private lateinit var tempFile: File
    private lateinit var manager: MetaSaveManager

    @Before fun setup() {
        tempFile = File.createTempFile("meta_test", ".json")
        manager = MetaSaveManager(tempFile)
    }

    @After fun cleanup() { tempFile.delete() }

    @Test fun `load returns default when file absent`() {
        tempFile.delete()
        val meta = manager.load()
        assertEquals(0, meta.restartCount)
        assertEquals(1.0f, meta.seedBonus.globalMultiplier, 0.001f)
        assertEquals(0.0, meta.seedBonus.startingEnergy, 0.001)
        assertEquals(0.0, meta.seedBonus.startingMatter, 0.001)
    }

    @Test fun `save and load round-trips correctly`() {
        val bonus = SeedBonus(globalMultiplier = 1.35f, startingEnergy = 50.0, startingMatter = 25.0)
        val meta = MetaSave(restartCount = 3, seedBonus = bonus)
        manager.save(meta)
        val loaded = manager.load()
        assertEquals(3, loaded.restartCount)
        assertEquals(1.35f, loaded.seedBonus.globalMultiplier, 0.001f)
        assertEquals(50.0, loaded.seedBonus.startingEnergy, 0.001)
        assertEquals(25.0, loaded.seedBonus.startingMatter, 0.001)
    }

    @Test fun `load returns default when file is corrupt`() {
        tempFile.writeText("not valid json {{{")
        val meta = manager.load()
        assertEquals(0, meta.restartCount)
        assertEquals(1.0f, meta.seedBonus.globalMultiplier, 0.001f)
    }

    @Test fun `save overwrites previous save`() {
        manager.save(MetaSave(restartCount = 1, seedBonus = SeedBonus()))
        manager.save(MetaSave(restartCount = 5, seedBonus = SeedBonus(globalMultiplier = 1.65f)))
        val loaded = manager.load()
        assertEquals(5, loaded.restartCount)
        assertEquals(1.65f, loaded.seedBonus.globalMultiplier, 0.001f)
    }
}
