package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InterstellarSystemTest {

    private lateinit var world: World
    private lateinit var system: InterstellarSystem

    @Before fun setup() {
        world = World()
        system = InterstellarSystem()
        system.initialize(world)
    }

    @Test fun `initialize populates research resource`() {
        val res = world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)
        assertNotNull(res)
        assertEquals(ResourceType.RESEARCH, res!!.type)
    }

    @Test fun `research institute starts unlocked`() {
        val gen = world.get<GeneratorComponent>(InterstellarSystem.KEY_GEN_RESEARCH_INSTITUTE)
        assertNotNull(gen)
        assertTrue(gen!!.unlocked)
        assertEquals(0, gen.level)
    }

    @Test fun `shipyard starts unlocked`() {
        val gen = world.get<GeneratorComponent>(InterstellarSystem.KEY_GEN_SHIPYARD)
        assertNotNull(gen)
        assertTrue(gen!!.unlocked)
        assertEquals(0, gen.level)
    }

    @Test fun `colony fleet starts locked`() {
        val gen = world.get<GeneratorComponent>(InterstellarSystem.KEY_GEN_COLONY_FLEET)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `galactic senate starts locked`() {
        val gen = world.get<GeneratorComponent>(InterstellarSystem.KEY_GEN_GALACTIC_SENATE)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `tap produces research`() {
        system.onTap(world)
        assertTrue(world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!.amount > BigDouble.ZERO)
    }

    @Test fun `advanced sensors doubles tap production`() {
        world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!
            .amount = BigDouble.of(100.0)
        system.purchaseUpgrade(world, InterstellarSystem.KEY_UPG_ADVANCED_SENSORS)
        val before = world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!.amount
        system.onTap(world)
        val gained = (world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!.amount - before).toDouble()
        assertEquals(2.0, gained, 0.01)
    }

    @Test fun `civilization seeding increases research institute production rate`() {
        val seededWorld = World()
        seededWorld.put(CivilizationSystem.KEY_RES_CIVILIZATION,
            ResourceComponent(ResourceType.CIVILIZATION, BigDouble.of(1000.0)))
        val seededSystem = InterstellarSystem()
        seededSystem.initialize(seededWorld)
        val gen = seededWorld.get<GeneratorComponent>(InterstellarSystem.KEY_GEN_RESEARCH_INSTITUTE)!!
        // floor(1000/100) * 10% = 100% bonus → 1.0 + 1.0 = 2.0
        assertEquals(2.0, gen.productionRate.toDouble(), 0.01)
    }

    @Test fun `research institute does not run at level 0`() {
        world.put(CivilizationSystem.KEY_RES_CIVILIZATION,
            ResourceComponent(ResourceType.CIVILIZATION, BigDouble.of(1000.0)))
        val civBefore = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount
        system.tick(world, delta = 1L)
        // At level 0, runGenerator() returns early — Civilization must not be consumed
        assertEquals(civBefore.toDouble(),
            world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount.toDouble(),
            0.01)
    }

    @Test fun `purchaseGenerator levels up shipyard and deducts cost`() {
        world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!
            .amount = BigDouble.of(1000.0)
        val researchBefore = world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!.amount
        system.purchaseGenerator(world, InterstellarSystem.KEY_GEN_SHIPYARD)
        val researchAfter = world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!.amount
        val gen = world.get<GeneratorComponent>(InterstellarSystem.KEY_GEN_SHIPYARD)!!
        assertEquals(1, gen.level)
        assertTrue("Research should be deducted", researchAfter < researchBefore)
    }
}
