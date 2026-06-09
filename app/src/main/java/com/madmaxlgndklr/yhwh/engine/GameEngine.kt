package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.systems.CosmologySystem
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
        // Let CosmologySystem sync any internal state from the restored world
        systems.filterIsInstance<CosmologySystem>().forEach { it.syncStateFromWorld(world) }
        tickCount = savedSnapshot.tick
        // Apply offline progress as a single batch tick
        val clampedDelta = missedTicks.coerceAtMost(maxOfflineTicks)
        if (clampedDelta > 0L) {
            systems.forEach { it.tick(world, clampedDelta) }
            tickCount += clampedDelta
        }
        emitSnapshot()
    }

    fun start() {
        tickJob = engineScope.launch {
            while (true) {
                delay(tickIntervalMs)
                systems.forEach { it.tick(world, 1L) }
                tickCount++
                emitSnapshot()
                if (tickCount % saveEveryNTicks == 0L) {
                    _snapshot.value?.let { onSaveDue?.invoke(it) }
                }
            }
        }
    }

    fun stop() { tickJob?.cancel() }

    /** Route a player tap to all systems that implement [PlayerActionHandler]. */
    fun onPlayerTap() {
        systems.filterIsInstance<PlayerActionHandler>().forEach { it.onTap(world) }
        emitSnapshot()
    }

    /** Route an upgrade purchase to all systems that implement [PlayerActionHandler]. */
    fun purchaseUpgrade(upgradeId: String) {
        systems.filterIsInstance<PlayerActionHandler>().forEach { it.purchaseUpgrade(world, upgradeId) }
        emitSnapshot()
    }

    /** Route a generator level-up to all systems that implement [PlayerActionHandler]. */
    fun purchaseGenerator(generatorId: String) {
        systems.filterIsInstance<PlayerActionHandler>().forEach { it.purchaseGenerator(world, generatorId) }
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
