package com.madmaxlgndklr.yhwh.engine

import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlinx.serialization.Serializable

/** Base marker for all ECS components. Each component is pure data. */
@Serializable
sealed interface Component

@Serializable
data class ResourceComponent(
    val type: ResourceType,
    var amount: BigDouble
) : Component

@Serializable
data class GeneratorComponent(
    val id: String,
    val productionType: ResourceType,
    var productionRate: BigDouble,
    val costType: ResourceType,
    var costAmount: BigDouble,
    var unlocked: Boolean,
    var level: Int = 1
) : Component

@Serializable
data class UpgradeComponent(
    val id: String,
    var purchased: Boolean,
    val costType: ResourceType,
    var costAmount: BigDouble,
    val effect: UpgradeEffect,
    val repeatable: Boolean = false
) : Component

@Serializable
data class EpochComponent(
    var epoch: EpochType,
    var progress: Float,
    var tick: Long
) : Component

/** Describes what an upgrade does when applied. */
@Serializable
sealed class UpgradeEffect {
    /** Multiply a generator's production rate by [multiplier]. */
    @Serializable
    data class MultiplyProduction(val generatorId: String, val multiplier: BigDouble) : UpgradeEffect()

    /** Set a generator's [unlocked] flag to true. */
    @Serializable
    data class UnlockGenerator(val generatorId: String) : UpgradeEffect()

    /** Multiply the tap production for the manual fluctuation action. */
    @Serializable
    data class MultiplyTapProduction(val multiplier: BigDouble) : UpgradeEffect()

    /** Manual conversion: spend [inputAmount] of [inputType], gain [outputAmount] of [outputType]. */
    @Serializable
    data class ManualConversion(
        val inputType: ResourceType,
        var inputAmount: BigDouble,
        val outputType: ResourceType,
        val outputAmount: BigDouble
    ) : UpgradeEffect()

    /** Reduce the cost of a ManualConversion upgrade by [multiplier] (e.g., 0.5 = -50%). */
    @Serializable
    data class ReduceConversionCost(val targetUpgradeId: String, val multiplier: BigDouble) : UpgradeEffect()
}
