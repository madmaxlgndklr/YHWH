package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvolutionSystemTest {

    private lateinit var world: World
    private lateinit var system: EvolutionSystem

    @Before fun setup() {
        world = World()
        system = EvolutionSystem()
        system.initialize(world)
    }

    @Test fun `initialize populates genes resource`() {
        val genes = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)
        assertNotNull(genes)
        assertEquals(ResourceType.GENES, genes!!.type)
    }

    @Test fun `initialize populates gene pool generator unlocked`() {
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_PRIMORDIAL_GENE_POOL)
        assertNotNull(gen)
        assertTrue(gen!!.unlocked)
    }

    @Test fun `initialize leaves mutation engine locked`() {
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `initialize leaves ecosystem architect locked`() {
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_ECOSYSTEM_ARCHITECT)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `tap produces genes`() {
        system.onTap(world)
        val genes = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
        assertTrue(genes.amount > BigDouble.ZERO)
    }

    @Test fun `organism seeding increases gene pool production rate`() {
        val seededWorld = World()
        seededWorld.put(BiologySystem.KEY_RES_ORGANISMS,
            ResourceComponent(ResourceType.ORGANISMS, BigDouble.of(300.0)))
        val seededSystem = EvolutionSystem()
        seededSystem.initialize(seededWorld)
        // floor(300/100) * 10% = 30% bonus → 1.3x
        val gen = seededWorld.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_PRIMORDIAL_GENE_POOL)!!
        assertEquals(1.3, gen.productionRate.toDouble(), 0.01)
    }

    @Test fun `rna replication upgrade unlocks mutation engine`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_RNA_REPLICATION)
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)!!
        assertTrue(gen.unlocked)
    }

    @Test fun `niche colonization upgrade unlocks ecosystem architect`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_NICHE_COLONIZATION)
        val gen = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_ECOSYSTEM_ARCHITECT)!!
        assertTrue(gen.unlocked)
    }

    @Test fun `hypermutation doubles mutation engine production`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
            .amount = BigDouble.of(1000.0)
        val before = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)!!.productionRate
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_HYPERMUTATION)
        val after = world.get<GeneratorComponent>(EvolutionSystem.KEY_GEN_MUTATION_ENGINE)!!.productionRate
        assertEquals(before.toDouble() * 2.0, after.toDouble(), 0.01)
    }

    @Test fun `adaptive immunity can be purchased and locks hypermutation`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_MUTATIONS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY)
        val snap = system.toSnapshot(world, 0)
        val hypermutationSnap = snap.upgrades.first { it.id == EvolutionSystem.KEY_UPG_HYPERMUTATION }
        assertFalse(hypermutationSnap.available)
    }

    @Test fun `hypermutation can be purchased and locks adaptive immunity`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_GENES)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_HYPERMUTATION)
        val snap = system.toSnapshot(world, 0)
        val adaptiveSnap = snap.upgrades.first { it.id == EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY }
        assertFalse(adaptiveSnap.available)
    }

    @Test fun `epochProgress is 0 with no dominance`() {
        val snap = system.toSnapshot(world, 0)
        assertEquals(0f, snap.epochProgress, 0.01f)
    }

    @Test fun `epochProgress is 1 with 1000 dominance`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)!!
            .amount = BigDouble.of(1000.0)
        val snap = system.toSnapshot(world, 0)
        assertEquals(1f, snap.epochProgress, 0.01f)
    }

    @Test fun `apex dominance converts species to dominance`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_SPECIES)!!
            .amount = BigDouble.of(200.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_APEX_DOMINANCE)
        val dominance = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)!!
        assertTrue(dominance.amount >= BigDouble.ONE)
    }

    @Test fun `event fires after first delay ticks`() {
        system.tick(world, delta = EvolutionSystem.EVENT_FIRST_DELAY_TICKS.toLong())
        val snap = system.toSnapshot(world, 0)
        assertNotNull(snap.activeEvent)
    }

    @Test fun `event does not fire before first delay ticks`() {
        system.tick(world, delta = (EvolutionSystem.EVENT_FIRST_DELAY_TICKS - 1).toLong())
        val snap = system.toSnapshot(world, 0)
        assertNull(snap.activeEvent)
    }

    @Test fun `ice age event duration reduced by adaptive immunity`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_MUTATIONS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY)
        // Trigger event
        system.tick(world, delta = EvolutionSystem.EVENT_FIRST_DELAY_TICKS.toLong())
        var snap = system.toSnapshot(world, 0)
        val eventDuration = snap.eventTicksRemaining
        // Ice Age with immunity should be shorter
        assertTrue(eventDuration <= 15)
    }

    @Test fun `syncStateFromWorld restores fork state from world`() {
        world.get<ResourceComponent>(EvolutionSystem.KEY_RES_MUTATIONS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, EvolutionSystem.KEY_UPG_ADAPTIVE_IMMUNITY)
        // New system, restored from world
        val restoredSystem = EvolutionSystem()
        restoredSystem.initialize(world)
        restoredSystem.syncStateFromWorld(world)
        // After sync, hypermutation should still be locked
        val snap = restoredSystem.toSnapshot(world, 0)
        val hypermutationSnap = snap.upgrades.first { it.id == EvolutionSystem.KEY_UPG_HYPERMUTATION }
        assertFalse(hypermutationSnap.available)
    }
}
