package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdvanceEpochTest {

    private fun baseSnapshot(epoch: EpochType = EpochType.COSMOLOGY) = GameSnapshot(
        tick = 0, epoch = epoch,
        resources = emptyMap(), generators = emptyList(),
        upgrades = emptyList(), epochProgress = 0f, events = emptyList()
    )

    private fun makeSystem(epoch: EpochType) = object : GameSystem {
        override fun initialize(world: World) {}
        override fun tick(world: World, delta: Long): List<GameEvent> = emptyList()
        override fun toSnapshot(world: World, tick: Long): GameSnapshot = baseSnapshot(epoch)
    }

    @Test fun `advanceEpoch changes snapshot epoch to Biology`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L)
        engine.registerSystem(makeSystem(EpochType.COSMOLOGY))
        engine.initNewGame()
        assertEquals(EpochType.COSMOLOGY, engine.snapshot.value?.epoch)

        engine.advanceEpoch(makeSystem(EpochType.BIOLOGY))
        assertEquals(EpochType.BIOLOGY, engine.snapshot.value?.epoch)
    }

    @Test fun `restore calls syncStateFromWorld on Restorable systems`() = runTest {
        var synced = false
        val restorableSystem = object : GameSystem, Restorable {
            override fun initialize(world: World) {}
            override fun tick(world: World, delta: Long): List<GameEvent> = emptyList()
            override fun toSnapshot(world: World, tick: Long): GameSnapshot = baseSnapshot()
            override fun syncStateFromWorld(world: World) { synced = true }
        }
        val engine = GameEngine(tickIntervalMs = 1000L)
        engine.registerSystem(restorableSystem)
        engine.restore(baseSnapshot(), missedTicks = 0L)
        assertTrue(synced)
    }
}
