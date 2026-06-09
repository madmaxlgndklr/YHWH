package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
data class GameEvent(
    val tick: Long,
    val message: String,
    val isMilestone: Boolean = false
)
