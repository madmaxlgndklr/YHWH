package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core game loop. Ticks at [tickIntervalMs] and emits an immutable [GameSnapshot]
 * after each tick via [snapshot]. Only this class mutates the ECS World.
 *
 * [scope] is injectable for testing (use the TestScope from runTest).
 * Production code uses the default background scope.
 */
class GameEngine(
    private val tickIntervalMs: Long = 1000L,
    val maxOfflineTicks: Long = 28_800L,
    private val saveEveryNTicks: Int = 30,
    scope: CoroutineScope? = null
) {
    private val engineScope = scope ?: CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var world = World()
    private val systems = mutableListOf<GameSystem>()
    private var tickCount = 0L
    private var tickJob: Job? = null
    var onSaveDue: (suspend (GameSnapshot) -> Unit)? = null

    private val _snapshot = MutableStateFlow<GameSnapshot?>(null)
    val snapshot: StateFlow<GameSnapshot?> = _snapshot.asStateFlow()

    private var activeSystem: GameSystem? = null

    /** Cumulative total of every resource ever produced, across all epochs. Persisted in snapshot. */
    private val lifetimeTotals: MutableMap<String, BigDouble> = mutableMapOf()

    /** Registers [system] and sets it as the active snapshot source. */
    fun registerSystem(system: GameSystem) {
        systems.add(system)
        activeSystem = system
    }

    /**
     * Clears all systems, creates a fresh World, zeros totals, and registers [system].
     * Caller must call [stop] before this to avoid data races with the tick coroutine.
     */
    fun resetAndRegister(system: GameSystem) {
        systems.clear()
        world = World()
        tickCount = 0L
        lifetimeTotals.clear()
        _snapshot.value = null
        systems.add(system)
        activeSystem = system
    }

    /** Call on fresh game start. Initializes world via each system and emits first snapshot. */
    fun initNewGame() {
        systems.forEach { it.initialize(world) }
        emitSnapshot()
    }

    /**
     * Call on restore from save file. Re-initializes world from system defaults,
     * patches resource/upgrade state from [savedSnapshot], then applies [missedTicks]
     * as a single batch tick.
     */
    fun restore(savedSnapshot: GameSnapshot, missedTicks: Long) {
        systems.forEach { it.initialize(world) }
        savedSnapshot.resources.forEach { (typeName, amount) ->
            world.get<ResourceComponent>("res_${typeName.lowercase()}")?.amount = amount
        }
        savedSnapshot.upgrades.filter { it.purchased }.forEach { upgSnap ->
            world.get<UpgradeComponent>(upgSnap.id)?.purchased = true
        }
        savedSnapshot.generators.forEach { genSnap ->
            world.get<GeneratorComponent>(genSnap.id)?.let { gen ->
                gen.level = genSnap.level
                gen.productionRate = genSnap.productionRate
                gen.unlocked = genSnap.unlocked
            }
        }
        systems.filterIsInstance<Restorable>().forEach { it.syncStateFromWorld(world) }

        // Restore lifetime totals from the saved snapshot
        lifetimeTotals.clear()
        lifetimeTotals.putAll(savedSnapshot.lifetimeTotals)

        tickCount = savedSnapshot.tick
        val clampedDelta = missedTicks.coerceAtMost(maxOfflineTicks)
        if (clampedDelta > 0L) {
            val before = snapshotAllResources()
            activeSystem?.tick(world, clampedDelta)
            accumulateGains(before)
            tickCount += clampedDelta
        }
        emitSnapshot()
    }

    fun start() {
        tickJob = engineScope.launch {
            while (true) {
                delay(tickIntervalMs)
                val before = snapshotAllResources()
                activeSystem?.tick(world, 1L)
                accumulateGains(before)
                tickCount++
                emitSnapshot()
                if (tickCount % saveEveryNTicks == 0L) {
                    _snapshot.value?.let { onSaveDue?.invoke(it) }
                }
            }
        }
    }

    fun stop() { tickJob?.cancel() }

    /** Returns current amounts for every resource in the world, across all epochs. */
    fun snapshotAllResources(): Map<String, BigDouble> =
        world.getAll<ResourceComponent>().associate { (_, comp) -> comp.type.name to comp.amount }

    /** Transitions the game to the next epoch by registering and activating [nextSystem]. */
    fun advanceEpoch(nextSystem: GameSystem) {
        systems.add(nextSystem)
        activeSystem = nextSystem
        nextSystem.initialize(world)
        emitSnapshot()
    }

    /** Route a player tap to the active system. */
    fun onPlayerTap() {
        val before = snapshotAllResources()
        (activeSystem as? PlayerActionHandler)?.onTap(world)
        accumulateGains(before)
        emitSnapshot()
    }

    /** Route an upgrade purchase to the active system. */
    fun purchaseUpgrade(upgradeId: String) {
        val before = snapshotAllResources()
        (activeSystem as? PlayerActionHandler)?.purchaseUpgrade(world, upgradeId)
        accumulateGains(before)
        emitSnapshot()
    }

    /** Route a generator level-up to the active system. */
    fun purchaseGenerator(generatorId: String) {
        (activeSystem as? PlayerActionHandler)?.purchaseGenerator(world, generatorId)
        emitSnapshot()
    }

    private fun accumulateGains(before: Map<String, BigDouble>) {
        snapshotAllResources().forEach { (typeName, afterAmount) ->
            val gained = afterAmount - (before[typeName] ?: BigDouble.ZERO)
            if (gained > BigDouble.ZERO) {
                lifetimeTotals[typeName] = (lifetimeTotals[typeName] ?: BigDouble.ZERO) + gained
            }
        }
    }

    private fun emitSnapshot() {
        val snap = activeSystem?.toSnapshot(world, tickCount) ?: return
        _snapshot.value = snap.copy(lifetimeTotals = lifetimeTotals.toMap())
    }
}

/**
 * Optional interface for systems that handle direct player actions.
 * CosmologySystem implements this.
 */
interface PlayerActionHandler {
    fun onTap(world: World)
    fun purchaseUpgrade(world: World, upgradeId: String)
    fun purchaseGenerator(world: World, generatorId: String)
}
