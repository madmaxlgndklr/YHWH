package com.madmaxlgndklr.yhwh.engine

/**
 * Interface for pluggable epoch systems. Each epoch implements this.
 * GameEngine calls tick() every game tick and initialize() once on new game.
 */
interface GameSystem {
    /** Called once when starting a fresh game for this epoch. Populates [world] with initial entities. */
    fun initialize(world: World)

    /** Called every tick. Must be delta-aware: all production × [delta]. */
    fun tick(world: World, delta: Long): List<GameEvent>

    /** Converts current [world] state to a [GameSnapshot] for emission. */
    fun toSnapshot(world: World, tick: Long): GameSnapshot
}
