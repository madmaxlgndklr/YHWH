package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameEngineTest {

    private fun makeTestSystem(initialSnapshot: GameSnapshot) = object : GameSystem {
        var tickCount = 0
        var lastDelta = 0L

        override fun initialize(world: World) {}

        override fun tick(world: World, delta: Long): List<GameEvent> {
            tickCount++
            lastDelta = delta
            return emptyList()
        }

        override fun toSnapshot(world: World, tick: Long): GameSnapshot =
            initialSnapshot.copy(tick = tick)
    }

    private fun baseSnapshot() = GameSnapshot(
        tick = 0,
        epoch = EpochType.COSMOLOGY,
        resources = mapOf(ResourceType.ENERGY.name to BigDouble.ZERO),
        generators = emptyList(),
        upgrades = emptyList(),
        epochProgress = 0f,
        events = emptyList()
    )

    @Test fun `engine emits initial snapshot before first tick`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L)
        val system = makeTestSystem(baseSnapshot())
        engine.registerSystem(system)
        engine.initNewGame()

        val snapshot = engine.snapshot.first { it != null }
        assertEquals(EpochType.COSMOLOGY, snapshot!!.epoch)
    }

    @Test fun `engine increments tick on each cycle`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L, scope = this)
        val system = makeTestSystem(baseSnapshot())
        engine.registerSystem(system)
        engine.initNewGame()
        engine.start()

        advanceTimeBy(3100L)
        assertTrue(system.tickCount >= 3)
        engine.stop()
    }

    @Test fun `restore applies offline delta`() = runTest {
        val engine = GameEngine(tickIntervalMs = 1000L)
        val system = makeTestSystem(baseSnapshot())
        engine.registerSystem(system)
        engine.restore(baseSnapshot(), missedTicks = 100L)

        assertEquals(100L, system.lastDelta)
    }
}
