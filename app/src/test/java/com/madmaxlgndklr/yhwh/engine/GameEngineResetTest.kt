package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import com.madmaxlgndklr.yhwh.systems.CosmologySystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameEngineResetTest {

    @Test fun `resetAndRegister replaces active system with fresh world`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = GameEngine(scope = this)

        engine.registerSystem(CosmologySystem())
        engine.initNewGame()

        val newCosmo = CosmologySystem()
        engine.resetAndRegister(newCosmo)
        engine.initNewGame()

        val snap = engine.snapshot.value
        assertNotNull(snap)
        assertEquals(EpochType.COSMOLOGY, snap!!.epoch)
        assertEquals(0L, snap.tick)
        val energy = snap.resources[ResourceType.ENERGY.name] ?: BigDouble.ZERO
        assertEquals(0.0, energy.toDouble(), 0.01)
    }

    @Test fun `resetAndRegister clears lifetime totals`() = runTest {
        val engine = GameEngine(scope = this)
        engine.registerSystem(CosmologySystem())
        engine.initNewGame()

        val newCosmo = CosmologySystem()
        engine.resetAndRegister(newCosmo)
        engine.initNewGame()

        val snap = engine.snapshot.value!!
        assertTrue(snap.lifetimeTotals.values.all { it == BigDouble.ZERO || it.toDouble() == 0.0 })
    }
}
