package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BiologySystemTest {

    private lateinit var world: World
    private lateinit var system: BiologySystem

    @Before fun setup() {
        world = World()
        system = BiologySystem()
        system.initialize(world)
    }

    @Test fun `initialize populates amino acids resource`() {
        val aa = world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)
        assertNotNull(aa)
        assertEquals(ResourceType.AMINO_ACIDS, aa!!.type)
    }

    @Test fun `initialize populates prebiotic soup generator unlocked`() {
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_PREBIOTIC_SOUP)
        assertNotNull(gen)
        assertTrue(gen!!.unlocked)
    }

    @Test fun `initialize leaves protein synthesizer locked`() {
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_PROTEIN_SYNTHESIZER)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `initialize leaves organism incubator locked`() {
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_ORGANISM_INCUBATOR)
        assertNotNull(gen)
        assertFalse(gen!!.unlocked)
    }

    @Test fun `tap produces amino acids`() {
        system.onTap(world)
        val aa = world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)!!
        assertTrue(aa.amount > BigDouble.ZERO)
    }

    @Test fun `planet seeding increases prebiotic soup rate`() {
        val worldWithPlanets = World()
        worldWithPlanets.put(CosmologySystem.KEY_RES_PLANETS,
            ResourceComponent(ResourceType.PLANETS, BigDouble.of(3.0)))
        val seededSystem = BiologySystem()
        seededSystem.initialize(worldWithPlanets)
        val gen = worldWithPlanets.get<GeneratorComponent>(BiologySystem.KEY_GEN_PREBIOTIC_SOUP)!!
        assertEquals(1.3, gen.productionRate.toDouble(), 0.01)
    }

    @Test fun `prebiotic soup produces amino acids when energy available`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)
            ?: world.put(CosmologySystem.KEY_RES_ENERGY,
                ResourceComponent(ResourceType.ENERGY, BigDouble.of(1000.0)))
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
            .amount = BigDouble.of(1000.0)
        system.tick(world, delta = 1L)
        val aa = world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)!!
        assertTrue(aa.amount > BigDouble.ZERO)
    }

    @Test fun `rna world upgrade unlocks protein synthesizer`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_AMINO_ACIDS)!!
            .amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, BiologySystem.KEY_UPG_RNA_WORLD)
        val gen = world.get<GeneratorComponent>(BiologySystem.KEY_GEN_PROTEIN_SYNTHESIZER)!!
        assertTrue(gen.unlocked)
    }

    @Test fun `epochProgress is 0 with no organisms`() {
        val snap = system.toSnapshot(world, tick = 0)
        assertEquals(0f, snap.epochProgress, 0.01f)
    }

    @Test fun `epochProgress is 1 with 1000 organisms`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)!!
            .amount = BigDouble.of(1000.0)
        val snap = system.toSnapshot(world, tick = 0)
        assertEquals(1f, snap.epochProgress, 0.01f)
    }

    @Test fun `evolutionary pressure converts cells to organism`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_CELLS)!!
            .amount = BigDouble.of(200.0)
        system.purchaseUpgrade(world, BiologySystem.KEY_UPG_EVOLUTIONARY_PRESSURE)
        val organisms = world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)!!
        assertTrue(organisms.amount >= BigDouble.ONE)
    }

    @Test fun `syncStateFromWorld sets firstOrganismFired from world state`() {
        world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)!!
            .amount = BigDouble.of(5.0)
        system.syncStateFromWorld(world)
        val events = system.tick(world, delta = 1L)
        assertTrue(events.none { it.isMilestone })
    }
}
