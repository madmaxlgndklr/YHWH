package com.madmaxlgndklr.yhwh.ui.state

import com.madmaxlgndklr.yhwh.engine.EvolutionEvent
import com.madmaxlgndklr.yhwh.engine.EpochType

data class CosmosState(
    val epoch: EpochType = EpochType.COSMOLOGY,
    val matterLevel: Float = 0f,
    val starLevel: Float = 0f,
    val starsFormed: Boolean = false,
    val planetsFormed: Boolean = false,
    val aminoAcidLevel: Float = 0f,
    val cellLevel: Float = 0f,
    val mutationLevel: Float = 0f,
    val speciesLevel: Float = 0f,
    val activeEvent: EvolutionEvent? = null,
    val civEraLevel: Int = 0,
    val civilizationLevel: Float = 0f,
    val civilUnrestActive: Boolean = false,
    val drivePhase: Int = 0,
    val legacyLevel: Float = 0f,
)
