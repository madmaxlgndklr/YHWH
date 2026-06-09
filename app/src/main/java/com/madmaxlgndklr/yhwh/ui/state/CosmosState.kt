package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.EpochType

data class CosmosState(
    val epoch: EpochType = EpochType.COSMOLOGY,
    /** Normalized 0f–1f: min(matter / MATTER_VISUAL_THRESHOLD, 1f) */
    val matterLevel: Float = 0f,
    /** Normalized 0f–1f: min(stars / STAR_VISUAL_THRESHOLD, 1f) */
    val starLevel: Float = 0f,
    val starsFormed: Boolean = false,
    val planetsFormed: Boolean = false
)
