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
    private val world = World()
    private val systems = mutableListOf<GameSystem>()
    private var tickCount = 0L
    private var tickJob: Job? = null
    var onSaveDue: (suspend (GameSnapshot) -> Unit)? = null

    private val _snapshot = MutableStateFlow<GameSnapshot?>(null)
    val snapshot: StateFlow<GameSnapshot?> = _snapshot.asStateFlow()

    private var activeSystem: GameSystem? = null

    /** Registers [system] and sets it as the active snapshot source. In Phase 1 only one system
     *  is registered per epoch; re-registering replaces the active snapshot source. */
    fun registerSystem(system: GameSystem) {
        systems.add(system)
        activeSystem = system  // last registered wins; callers set epoch-appropriate system
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
        // Re-initialize world with default entity layout
        systems.forEach { it.initialize(world) }
        // Patch resource amounts from saved state
        savedSnapshot.resources.forEach { (typeName, amount) ->
            world.get<ResourceComponent>("res_${typeName.lowercase()}")?.amount = amount
        }
        // Patch purchased upgrade flags
        savedSnapshot.upgrades.filter { it.purchased }.forEach { upgSnap ->
            world.get<UpgradeComponent>(upgSnap.id)?.purchased = true
        }
        // Restore generator state (level, productionRate, unlocked) from snapshot
        savedSnapshot.generators.forEach { genSnap ->
            world.get<GeneratorComponent>(genSnap.id)?.let { gen ->
                gen.level = genSnap.level
                gen.productionRate = genSnap.productionRate
                gen.unlocked = genSnap.unlocked
            }
        }
        // Let any Restorable system sync internal state from the restored world
        systems.filterIsInstance<Restorable>().forEach { it.syncStateFromWorld(world) }
        tickCount = savedSnapshot.tick
        // Apply offline progress as a single batch tick
        val clampedDelta = missedTicks.coerceAtMost(maxOfflineTicks)
        if (clampedDelta > 0L) {
            activeSystem?.tick(world, clampedDelta)
            tickCount += clampedDelta
        }
        emitSnapshot()
    }

    fun start() {
        tickJob = engineScope.launch {
            while (true) {
                delay(tickIntervalMs)
                activeSystem?.tick(world, 1L)
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
        (activeSystem as? PlayerActionHandler)?.onTap(world)
        emitSnapshot()
    }

    /** Route an upgrade purchase to the active system. */
    fun purchaseUpgrade(upgradeId: String) {
        (activeSystem as? PlayerActionHandler)?.purchaseUpgrade(world, upgradeId)
        emitSnapshot()
    }

    /** Route a generator level-up to the active system. */
    fun purchaseGenerator(generatorId: String) {
        (activeSystem as? PlayerActionHandler)?.purchaseGenerator(world, generatorId)
        emitSnapshot()
    }

    private fun emitSnapshot() {
        val snap = activeSystem?.toSnapshot(world, tickCount) ?: return
        _snapshot.value = snap
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
