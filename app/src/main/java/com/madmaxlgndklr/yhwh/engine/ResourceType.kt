package com.madmaxlgndklr.yhwh.engine

import kotlinx.serialization.Serializable

@Serializable
enum class ResourceType(val displayName: String, val symbol: String) {
    ENERGY("Energy", "⚡"),
    MATTER("Matter", "⬡"),
    HYDROGEN("Hydrogen", "H"),
    STARS("Stars", "★"),
    ACCRETION_DISKS("Accretion Disks", "◎"),
    PLANETS("Planets", "♁"),
    AMINO_ACIDS("Amino Acids", "🧪"),
    PROTEINS("Proteins", "🔗"),
    CELLS("Cells", "🔬"),
    ORGANISMS("Organisms", "🦠")
}
