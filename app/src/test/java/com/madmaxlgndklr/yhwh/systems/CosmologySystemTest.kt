package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CosmologySystemTest {

    private lateinit var world: World
    private lateinit var system: CosmologySystem

    @Before fun setup() {
        world = World()
        system = CosmologySystem()
        system.initialize(world)
    }

    @Test fun `initialize populates energy resource`() {
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)
        assertNotNull(energy)
        assertEquals(ResourceType.ENERGY, energy!!.type)
    }

    @Test fun `initialize populates nebula generator unlocked`() {
        val nebula = world.get<GeneratorComponent>(CosmologySystem.KEY_GEN_NEBULA)
        assertNotNull(nebula)
        assertTrue(nebula!!.unlocked)
    }

    @Test fun `initialize leaves fusion generator locked`() {
        val fusion = world.get<GeneratorComponent>(CosmologySystem.KEY_GEN_FUSION)
        assertNotNull(fusion)
        assertFalse(fusion!!.unlocked)
    }

    @Test fun `tick with delta 1 produces energy passively`() {
        system.tick(world, delta = 1L)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertTrue(energy.amount > BigDouble.ZERO)
    }

    @Test fun `nebula generator produces matter when energy is sufficient`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!.amount = BigDouble.of(1000.0)
        system.tick(world, delta = 1L)
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertTrue(matter.amount > BigDouble.ZERO)
    }

    @Test fun `onTap adds matter to resource pool`() {
        system.onTap(world)
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertTrue(matter.amount > BigDouble.ZERO)
    }

    @Test fun `purchaseUpgrade particle density doubles tap production`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!.amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, CosmologySystem.KEY_UPG_PARTICLE_DENSITY)
        val upg = world.get<UpgradeComponent>(CosmologySystem.KEY_UPG_PARTICLE_DENSITY)!!
        assertTrue(upg.purchased)
    }

    @Test fun `purchaseUpgrade nuclear ignition unlocks fusion generator`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!.amount = BigDouble.of(1000.0)
        system.purchaseUpgrade(world, CosmologySystem.KEY_UPG_NUCLEAR_IGNITION)
        val fusion = world.get<GeneratorComponent>(CosmologySystem.KEY_GEN_FUSION)!!
        assertTrue(fusion.unlocked)
    }

    @Test fun `epochProgress is 0 with no planets`() {
        val snapshot = system.toSnapshot(world, tick = 0)
        assertEquals(0f, snapshot.epochProgress, 0.01f)
    }

    @Test fun `epochProgress is 1 when planets greater than 0`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_PLANETS)!!.amount = BigDouble.ONE
        val snapshot = system.toSnapshot(world, tick = 0)
        assertEquals(1f, snapshot.epochProgress, 0.01f)
    }

    @Test fun `gravitational collapse converts disks to planet`() {
        world.get<ResourceComponent>(CosmologySystem.KEY_RES_ACCRETION_DISKS)!!.amount = BigDouble.of(200.0)
        world.get<UpgradeComponent>(CosmologySystem.KEY_UPG_GRAVITATIONAL_COLLAPSE)!!.purchased = true
        system.purchaseUpgrade(world, CosmologySystem.KEY_UPG_GRAVITATIONAL_COLLAPSE)
        val planets = world.get<ResourceComponent>(CosmologySystem.KEY_RES_PLANETS)!!
        assertTrue(planets.amount >= BigDouble.ONE)
    }
}
