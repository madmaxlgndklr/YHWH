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
        world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_SCHOLARS_GUILD)!!.level = 1
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.of(100.0)
        system.tick(world, 1L)
        assertTrue(world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount > BigDouble.ZERO)
    }

    @Test fun `early settlements produces followers by consuming dominance`() {
        world.put(EvolutionSystem.KEY_RES_DOMINANCE,
            ResourceComponent(ResourceType.DOMINANCE, BigDouble.of(100.0)))
        system.purchaseGenerator(world, CivilizationSystem.KEY_GEN_EARLY_SETTLEMENTS)
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

    // --- Unrest ---

    @Test fun `unrest accumulates at 0_5 per tick in ancient era`() {
        system.tick(world, 1L)
        assertEquals(0.5f, system.toSnapshot(world, 0L).unrestLevel, 0.1f)
    }

    @Test fun `social order halves unrest accumulation`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.of(30.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_SOCIAL_ORDER)
        system.tick(world, 1L)
        assertEquals(0.25f, system.toSnapshot(world, 0L).unrestLevel, 0.05f)
    }

    @Test fun `unrest triggers civil crisis at 100`() {
        repeat(200) { system.tick(world, 1L) }
        val snap = system.toSnapshot(world, 0L)
        assertTrue(snap.civilUnrestActive)
    }

    @Test fun `crisis resets unrest to zero`() {
        repeat(200) { system.tick(world, 1L) }
        val snap = system.toSnapshot(world, 0L)
        assertEquals(0.0f, snap.unrestLevel, 0.01f)
    }

    @Test fun `crisis halves cultural exchange production`() {
        world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_CULTURAL_EXCHANGE)!!.unlocked = true
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount = BigDouble.of(1000.0)
        repeat(200) { system.tick(world, 1L) }
        assertTrue(system.toSnapshot(world, 0L).civilUnrestActive)
        val cultureBefore = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount
        system.tick(world, 1L)
        val cultureAfter = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount
        val gain = (cultureAfter - cultureBefore).toDouble()
        assertTrue("Expected gain ~0.5 but got $gain", gain < 0.9)
    }

    @Test fun `crisis ends after 30 ticks`() {
        repeat(200) { system.tick(world, 1L) }
        assertTrue(system.toSnapshot(world, 0L).civilUnrestActive)
        repeat(30) { system.tick(world, 1L) }
        assertFalse(system.toSnapshot(world, 0L).civilUnrestActive)
    }

    @Test fun `public works reduces unrest by 25`() {
        repeat(100) { system.tick(world, 1L) }
        val unrestBefore = system.toSnapshot(world, 0L).unrestLevel
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.of(50.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_PUBLIC_WORKS)
        val unrestAfter = system.toSnapshot(world, 0L).unrestLevel
        assertEquals(25f, unrestBefore - unrestAfter, 2f)
    }

    // --- Era advancement ---

    @Test fun `medieval era advance doubles all generator production`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(50.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.of(100.0)
        world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_SCHOLARS_GUILD)!!.level = 1
        val knowledgeBefore = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount
        system.tick(world, 1L)
        val gained = (world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount - knowledgeBefore).toDouble()
        assertEquals(2.0, gained, 0.1)
    }

    @Test fun `medieval era advance unlocks cultural exchange generator`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(50.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)
        assertTrue(world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_CULTURAL_EXCHANGE)!!.unlocked)
    }

    @Test fun `industrial era is blocked until medieval`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(200.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA)
        assertFalse(world.get<UpgradeComponent>(CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA)!!.purchased)
    }

    @Test fun `industrial era advance gives 4x total production`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(250.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA)
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.of(100.0)
        world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_SCHOLARS_GUILD)!!.level = 1
        val before = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount
        system.tick(world, 1L)
        val gained = (world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount - before).toDouble()
        assertEquals(4.0, gained, 0.1)
    }

    @Test fun `industrial era advance unlocks enlightened senate`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(250.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA)
        assertTrue(world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_ENLIGHTENED_SENATE)!!.unlocked)
    }

    @Test fun `era restored to medieval in syncStateFromWorld`() {
        world.get<UpgradeComponent>(CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)!!.purchased = true
        system.syncStateFromWorld(world)
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.of(100.0)
        world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_SCHOLARS_GUILD)!!.level = 1
        val before = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount
        system.tick(world, 1L)
        val gained = (world.get<ResourceComponent>(CivilizationSystem.KEY_RES_KNOWLEDGE)!!.amount - before).toDouble()
        assertEquals(2.0, gained, 0.1)
    }

    @Test fun `industrial upgrade shows unavailable in snapshot when medieval not purchased`() {
        val snap = system.toSnapshot(world, 0L)
        val industrialSnap = snap.upgrades.find { it.id == CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA }!!
        assertFalse(industrialSnap.available)
    }

    @Test fun `unrest rate increases in medieval era`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(50.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)
        system.tick(world, 1L)
        assertEquals(0.75f, system.toSnapshot(world, 0L).unrestLevel, 0.1f)
    }

    @Test fun `unrest rate increases in industrial era`() {
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(250.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA)
        system.tick(world, 1L)
        assertEquals(1.0f, system.toSnapshot(world, 0L).unrestLevel, 0.1f)
    }

    @Test fun `era multiplier and crisis multiplier compose on cultural exchange`() {
        // Set up Industrial era (4x) and trigger a crisis
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)!!.amount = BigDouble.of(250.0)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_MEDIEVAL_ERA)
        system.purchaseUpgrade(world, CivilizationSystem.KEY_UPG_INDUSTRIAL_ERA)
        // Unlock Cultural Exchange and prime Followers
        world.get<GeneratorComponent>(CivilizationSystem.KEY_GEN_CULTURAL_EXCHANGE)!!.unlocked = true
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_FOLLOWERS)!!.amount = BigDouble.of(10000.0)
        // Trigger a crisis: industrial rate is 1.0/tick, needs 100 ticks
        repeat(100) { system.tick(world, 1L) }
        assertTrue("crisis should be active", system.toSnapshot(world, 0L).civilUnrestActive)
        // Zero out culture so Scholars Guild (cost=10) cannot drain the measurement tick
        world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount = BigDouble.ZERO
        // During crisis: Cultural Exchange should produce at 4.0 * 0.5 = 2.0 per tick
        val cultureBefore = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount
        system.tick(world, 1L)
        val cultureAfter = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CULTURE)!!.amount
        val gained = (cultureAfter - cultureBefore).toDouble()
        assertEquals(2.0, gained, 0.2)
    }
}
