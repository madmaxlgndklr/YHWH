package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.SeedBonus
import kotlinx.serialization.Serializable

@Serializable
data class MetaSave(
    val restartCount: Int = 0,
    val seedBonus: SeedBonus = SeedBonus()
)
