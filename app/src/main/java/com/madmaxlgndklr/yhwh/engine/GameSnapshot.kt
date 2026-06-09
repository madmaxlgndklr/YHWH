package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.serialization.Serializable

@Serializable
data class GeneratorSnapshot(
    val id: String,
    val displayName: String,
    val productionType: ResourceType,
    val productionRate: BigDouble,
    val costType: ResourceType,
    val costAmount: BigDouble,
    val unlocked: Boolean,
    val level: Int
)

@Serializable
data class UpgradeSnapshot(
    val id: String,
    val displayName: String,
    val description: String,
    val costType: ResourceType,
    val costAmount: BigDouble,
    val purchased: Boolean,
    val repeatable: Boolean,
    /** True if the player can afford and requirements are met. */
    val available: Boolean
)

/** Immutable snapshot of all game state, emitted by GameEngine each tick. */
@Serializable
data class GameSnapshot(
    val tick: Long,
    val epoch: EpochType,
    val resources: Map<ResourceType, BigDouble>,
    val generators: List<GeneratorSnapshot>,
    val upgrades: List<UpgradeSnapshot>,
    /** 0f–1f. Reaches 1.0 when epoch win condition is met. */
    val epochProgress: Float,
    /** Events generated this tick only (not cumulative). */
    val events: List<GameEvent>
)
