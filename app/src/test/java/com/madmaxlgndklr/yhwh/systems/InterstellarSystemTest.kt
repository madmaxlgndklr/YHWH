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

    @Test fun `passive research generated each tick`() {
        system.tick(world, delta = 1L)
        // BASE_RESEARCH_PER_TICK = 2.0; no generators running (all level 0)
        assertEquals(2.0, world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!.amount.toDouble(), 0.01)
    }

    @Test fun `vessel decay reduces vessels each tick in sublight phase`() {
        world.get<ResourceComponent>(InterstellarSystem.KEY_RES_VESSELS)!!.amount = BigDouble.of(10.0)
        system.tick(world, delta = 1L)
        // Phase 0 decay = 0.1/tick
        assertEquals(9.9, world.get<ResourceComponent>(InterstellarSystem.KEY_RES_VESSELS)!!.amount.toDouble(), 0.01)
    }

    @Test fun `vessel decay stops at zero`() {
        // Vessels start at 0.0 — decay should not make it negative
        system.tick(world, delta = 1L)
        assertEquals(0.0, world.get<ResourceComponent>(InterstellarSystem.KEY_RES_VESSELS)!!.amount.toDouble(), 0.001)
    }

    @Test fun `hull plating halves vessel decay`() {
        // Cost of hull plating = 30 Vessels; start with 100 so 70 remain after purchase
        world.get<ResourceComponent>(InterstellarSystem.KEY_RES_VESSELS)!!.amount = BigDouble.of(100.0)
        system.purchaseUpgrade(world, InterstellarSystem.KEY_UPG_HULL_PLATING)
        // After purchase: 100 - 30 = 70 Vessels, hull plating purchased
        system.tick(world, delta = 1L)
        // Phase 0 decay = 0.1/tick; halved = 0.05; net = 70 - 0.05 = 69.95
        assertEquals(69.95, world.get<ResourceComponent>(InterstellarSystem.KEY_RES_VESSELS)!!.amount.toDouble(), 0.01)
    }

    @Test fun `milestone event fires on first research`() {
        val events = system.tick(world, delta = 1L)
        assertTrue(events.any { it.isMilestone && it.message.contains("Research begins") })
    }

    @Test fun `milestone event does not fire twice for research`() {
        system.tick(world, delta = 1L)
        val events2 = system.tick(world, delta = 1L)
        assertTrue(events2.none { it.message.contains("Research begins") })
    }

    @Test fun `shipyard produces vessels after purchase`() {
        world.get<ResourceComponent>(InterstellarSystem.KEY_RES_RESEARCH)!!.amount = BigDouble.of(1000.0)
        system.purchaseGenerator(world, InterstellarSystem.KEY_GEN_SHIPYARD)
        system.tick(world, delta = 1L)
        // Shipyard level 1: productionRate=1.1, driveMultiplier=1.0 → 1.1 Vessels/tick
        // minus decay 0.1 → net 1.0 Vessels
        assertTrue("Shipyard should produce Vessels",
            world.get<ResourceComponent>(InterstellarSystem.KEY_RES_VESSELS)!!.amount > BigDouble.ZERO)
    }
}
