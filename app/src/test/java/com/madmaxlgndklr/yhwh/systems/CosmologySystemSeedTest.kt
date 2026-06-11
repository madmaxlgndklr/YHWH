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

    @Test fun `globalMultiplier scales passive energy per tick`() {
        // Use 1.5x — produces 7.5 energy, which stays below the Nebula's 10-energy cost
        // so the Nebula generator doesn't fire and consume the energy mid-test.
        val system = CosmologySystem().apply {
            seedBonus = SeedBonus(globalMultiplier = 1.5f)
        }
        system.initialize(world)
        system.tick(world, 1L)
        val energy = world.get<ResourceComponent>(CosmologySystem.KEY_RES_ENERGY)!!
        assertEquals(7.5, energy.amount.toDouble(), 0.01)
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
