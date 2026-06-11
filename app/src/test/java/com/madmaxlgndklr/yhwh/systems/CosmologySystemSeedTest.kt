package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CosmologySystemSeedTest {

    private lateinit var world: World

    @Before fun setup() { world = World() }

    @Test fun `no seed — passive energy per tick equals base rate`() {
        val system = CosmologySystem()
        system.initialize(world)
        system.tick(world, 1L)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(5.0, energy.amount.toDouble(), 0.01)
    }

    @Test fun `seed startingEnergy applied on initialize`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(startingEnergy = 100.0)
        }
        system.initialize(world)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(100.0, energy.amount.toDouble(), 0.01)
    }

    @Test fun `seed startingMatter applied on initialize`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(startingMatter = 50.0)
        }
        system.initialize(world)
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertEquals(50.0, matter.amount.toDouble(), 0.01)
    }

    @Test fun `globalMultiplier doubles passive energy per tick`() {
        val system1 = CosmologySystem()
        system1.initialize(world)
        system1.tick(world, 1L)
        val energyWithoutMultiplier = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!.amount.toDouble()
        assertEquals("Base rate should be 5.0", 5.0, energyWithoutMultiplier, 0.01)

        val world2 = World()
        val system2 = CosmologySystem().apply {
            seedBonus = SeedBonus(globalMultiplier = 2.0f)
        }
        system2.initialize(world2)
        system2.tick(world2, 1L)
        val energyWithMultiplier = world2.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!.amount.toDouble()
        assertEquals("With 2x multiplier should be 10.0, got $energyWithMultiplier", 10.0, energyWithMultiplier, 0.01)
    }

    @Test fun `globalMultiplier doubles tap production`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(globalMultiplier = 2.0f)
        }
        system.initialize(world)
        system.onTap(world)
        // BASE_TAP_MATTER = 1.0, multiplier 2.0 → 2.0
        val matter = world.get<ResourceComponent>(CosmologySystem.KEY_RES_MATTER)!!
        assertEquals(2.0, matter.amount.toDouble(), 0.01)
    }

    @Test fun `globalMultiplier 1_0 means no change`() {
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(globalMultiplier = 1.0f)
        }
        system.initialize(world)
        system.tick(world, 1L)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(5.0, energy.amount.toDouble(), 0.01)
    }
}
