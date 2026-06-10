package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
enum class EvolutionEvent(val displayName: String) {
    ICE_AGE("Ice Age"),
    ASTEROID_IMPACT("Asteroid Impact"),
    VOLCANIC_WINTER("Volcanic Winter")
}
