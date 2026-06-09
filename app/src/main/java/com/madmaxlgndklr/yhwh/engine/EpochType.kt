package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
enum class EpochType(val displayName: String) {
    COSMOLOGY("Cosmology"),
    BIOLOGY("Biology"),
    EVOLUTION("Evolution"),
    CIVILIZATION("Civilization"),
    INTERSTELLAR("Interstellar")
}
