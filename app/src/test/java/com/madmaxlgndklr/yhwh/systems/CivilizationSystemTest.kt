package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CivilizationSystemTest {

    private lateinit var world: World
    private lateinit var system: CivilizationSystem

    @Before fun setup() {
        world = World()
        system = CivilizationSystem()
        system.initialize(world)
    }

    // --- Seeding ---

    @Test fun `no dominance gives base production rate 1_0`() {
        val gen = world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_EARLY_SETTLEMENTS)!!
        assertEquals(1.0, gen.productionRate.toDouble(), 0.01)
    }

    @Test fun `1000 dominance seeds 2x production rate`() {
        val w = World()
        w.put(EvolutionSystem.KEY_RES_DOMINANCE,
            ResourceComponent(ResourceType.DOMINANCE, BigDouble.of(1000.0)))
        val s = CivilizationSystem()
        s.initialize(w)
        val gen = w.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_EARLY_SETTLEMENTS)!!
        assertEquals(2.0, gen.productionRate.toDouble(), 0.01)
    }

    @Test fun `500 dominance seeds 1_5x production rate`() {
        val w = World()
        w.put(EvolutionSystem.KEY_RES_DOMINANCE,
            ResourceComponent(ResourceType.DOMINANCE, BigDouble.of(500.0)))
        val s = CivilizationSystem()
        s.initialize(w)
        val gen = w.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_EARLY_SETTLEMENTS)!!
        assertEquals(1.5, gen.productionRate.toDouble(), 0.01)
    }

    // --- Generator initial state ---

    @Test fun `cultural exchange starts locked`() {
        assertFalse(world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_CULTURAL_EXCHANGE)!!.unlocked)
    }

    @Test fun `scholars guild starts unlocked`() {
        assertTrue(world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_SCHOLARS_GUILD)!!.unlocked)
    }

    @Test fun `enlightened senate starts locked`() {
        assertFalse(world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_ENLIGHTENED_SENATE)!!.unlocked)
    }

    // --- Tap ---

    @Test fun `tap produces followers`() {
        system.onTap(world)
        assertTrue(world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount > BigDouble.ZERO)
    }

    @Test fun `divine calling doubles tap production`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount = BigDouble.of(50.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_DIVINE_CALLING)
        val before = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount
        system.onTap(world)
        val after = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount
        assertEquals(2.0, (after - before).toDouble(), 0.01)
    }

    // --- Tick basics ---

    @Test fun `tick produces passive followers`() {
        system.tick(world, 1L)
        assertTrue(world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount >= BigDouble.ONE)
    }

    @Test fun `scholars guild does not run without culture`() {
        system.tick(world, 1L)
        assertEquals(0.0, world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount.toDouble(), 0.01)
    }

    @Test fun `scholars guild produces knowledge when culture is present`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.of(100.0)
        system.tick(world, 1L)
        assertTrue(world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount > BigDouble.ZERO)
    }

    @Test fun `early settlements produces followers by consuming dominance`() {
        world.put(EvolutionSystem.KEY_RES_DOMINANCE,
            ResourceComponent(ResourceType.DOMINANCE, BigDouble.of(100.0)))
        val dominanceBefore = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)!!.amount
        system.tick(world, 1L)
        val dominanceAfter = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)!!.amount
        val followersAfter = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount
        assertTrue("Dominance should be consumed by Early Settlements", dominanceAfter < dominanceBefore)
        assertTrue("Followers should exceed passive baseline alone", followersAfter > BigDouble.ONE)
    }

    // --- Win condition ---

    @Test fun `epoch progress is 1_0 at 1000 civilization`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(1000.0)
        assertEquals(1.0f, system.toSnapshot(world, 0L).epochProgress, 0.001f)
    }

    @Test fun `epoch progress is 0_5 at 500 civilization`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(500.0)
        assertEquals(0.5f, system.toSnapshot(world, 0L).epochProgress, 0.001f)
    }
}
