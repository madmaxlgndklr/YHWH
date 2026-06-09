package com.madmaxlgndklr.yhwh.persistence

import com.madmaxlgndklr.yhwh.engine.GameSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class SaveData(
    val version: Int = 1,
    val lastTickTimestamp: Long,
    val snapshot: GameSnapshot
)
