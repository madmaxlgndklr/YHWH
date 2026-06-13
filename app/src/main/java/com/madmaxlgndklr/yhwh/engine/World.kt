package com.madmaxlgndklr.yhwh.engine

/**
 * Flat-map ECS world. Each string key uniquely identifies one component
 * (e.g. "res_energy", "gen_nebula", "upg_particle_density", "epoch").
 * Keys are defined as constants in the system that owns them (CosmologySystem).
 */
class World {
    @PublishedApi internal val state: MutableMap<String, Component> = mutableMapOf()

    fun put(key: String, component: Component) { state[key] = component }

    /** Only inserts [component] if [key] is not already present. Preserves existing state during restore. */
    fun putIfAbsent(key: String, component: Component) { state.putIfAbsent(key, component) }

    inline fun <reified T : Component> get(key: String): T? = state[key] as? T

    inline fun <reified T : Component> getAll(): List<Pair<String, T>> =
        state.entries.mapNotNull { (k, v) -> (v as? T)?.let { k to it } }

    fun remove(key: String) { state.remove(key) }

    fun toSnapshot(): Map<String, Component> = state.toMap()

    companion object {
        fun fromSnapshot(snapshot: Map<String, Component>): World {
            val world = World()
            snapshot.forEach { (key, component) -> world.put(key, component) }
            return world
        }
    }
}
