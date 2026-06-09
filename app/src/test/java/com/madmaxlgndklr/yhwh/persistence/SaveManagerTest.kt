package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveManagerTest {

    @get:Rule val tempFolder = TemporaryFolder()
    private lateinit var saveFile: File
    private lateinit var manager: SaveManager

    private fun baseSnapshot(tick: Long = 0L) = GameSnapshot(
        tick = tick,
        epoch = EpochType.COSMOLOGY,
        resources = mapOf(ResourceType.ENERGY.name to BigDouble.of(42.0)),
        generators = emptyList(),
        upgrades = emptyList(),
        epochProgress = 0f,
        events = emptyList()
    )

    @Before fun setup() {
        saveFile = File(tempFolder.root, "yhwh_save.json")
        manager = SaveManager(saveFile)
    }

    @Test fun `save then load returns same snapshot`() {
        manager.save(baseSnapshot(tick = 10L))
        val loaded = manager.load()
        assertNotNull(loaded)
        assertEquals(10L, loaded!!.snapshot.tick)
        assertEquals(42.0, loaded.snapshot.resources[ResourceType.ENERGY.name]!!.toDouble(), 1e-6)
    }

    @Test fun `load returns null when no file exists`() {
        assertNull(manager.load())
    }

    @Test fun `missedTicks calculates offline delta`() {
        val now = System.currentTimeMillis()
        val fiveSecondsAgo = now - 5_000L
        manager.save(baseSnapshot(), overrideTimestamp = fiveSecondsAgo)
        val loaded = manager.load()!!
        val missed = manager.computeMissedTicks(loaded.lastTickTimestamp, tickIntervalMs = 1000L)
        assertTrue("Expected ~5 missed ticks, got $missed", missed in 4..6)
    }

    @Test fun `missedTicks is capped at max`() {
        val tenHoursAgo = System.currentTimeMillis() - 10 * 3_600_000L
        manager.save(baseSnapshot(), overrideTimestamp = tenHoursAgo)
        val loaded = manager.load()!!
        val missed = manager.computeMissedTicks(
            loaded.lastTickTimestamp,
            tickIntervalMs = 1000L,
            maxTicks = 28_800L
        )
        assertEquals(28_800L, missed)
    }
}
