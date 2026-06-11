package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
data class SeedBonus(
    val globalMultiplier: Float = 1.0f,
    val startingEnergy: Double = 0.0,
    val startingMatter: Double = 0.0
)
