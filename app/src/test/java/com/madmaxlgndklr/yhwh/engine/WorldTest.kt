package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import org.junit.Assert.*
import org.junit.Test

class WorldTest {

    @Test fun `put and retrieve component by key`() {
        val world = World()
        val comp = ResourceComponent(ResourceType.ENERGY, BigDouble.of(100.0))
        world.put("res_energy", comp)
        val retrieved = world.get<ResourceComponent>("res_energy")
        assertNotNull(retrieved)
        assertEquals(ResourceType.ENERGY, retrieved!!.type)
    }

    @Test fun `get returns null for missing key`() {
        val world = World()
        assertNull(world.get<ResourceComponent>("missing"))
    }

    @Test fun `getAll returns all components of given type`() {
        val world = World()
        world.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.ONE))
        world.put("res_matter", ResourceComponent(ResourceType.MATTER, BigDouble.ONE))
        world.put("gen_nebula", GeneratorComponent(
            id = "gen_nebula", productionType = ResourceType.MATTER,
            productionRate = BigDouble.ONE, costType = ResourceType.ENERGY,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        val resources = world.getAll<ResourceComponent>()
        assertEquals(2, resources.size)
    }

    @Test fun `remove deletes a component`() {
        val world = World()
        world.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.ONE))
        world.remove("res_energy")
        assertNull(world.get<ResourceComponent>("res_energy"))
    }

    @Test fun `toSnapshot round-trips all components`() {
        val world = World()
        world.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.of(50.0)))
        val snapshot = world.toSnapshot()
        assertEquals(1, snapshot.size)
        assertTrue(snapshot.containsKey("res_energy"))
    }

    @Test fun `fromSnapshot restores world state`() {
        val original = World()
        original.put("res_energy", ResourceComponent(ResourceType.ENERGY, BigDouble.of(50.0)))
        val restored = World.fromSnapshot(original.toSnapshot())
        val comp = restored.get<ResourceComponent>("res_energy")
        assertNotNull(comp)
        assertEquals(50.0, comp!!.amount.toDouble(), 1e-6)
    }
}
