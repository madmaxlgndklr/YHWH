package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlin.math.floor
import kotlin.math.pow

class InterstellarSystem : GameSystem, PlayerActionHandler, Restorable {

    companion object {
        const val KEY_RES_RESEARCH   = "res_research"
        const val KEY_RES_VESSELS    = "res_vessels"
        const val KEY_RES_COLONIES   = "res_colonies"
        const val KEY_RES_LEGACY     = "res_legacy"

        const val KEY_GEN_RESEARCH_INSTITUTE = "gen_research_institute"
        const val KEY_GEN_SHIPYARD           = "gen_shipyard"
        const val KEY_GEN_COLONY_FLEET       = "gen_colony_fleet"
        const val KEY_GEN_GALACTIC_SENATE    = "gen_galactic_senate"

        const val KEY_UPG_ADVANCED_SENSORS  = "upg_advanced_sensors"
        const val KEY_UPG_HULL_PLATING      = "upg_hull_plating"
        const val KEY_UPG_EMERGENCY_REPAIRS = "upg_emergency_repairs"
        const val KEY_UPG_LONG_RANGE_COMMS  = "upg_long_range_comms"
        const val KEY_UPG_ION_DRIVE         = "upg_ion_drive"
        const val KEY_UPG_HYPERDRIVE        = "upg_hyperdrive"

        const val COLONY_VISUAL_THRESHOLD = 200.0
        const val WIN_THRESHOLD           = 1000.0

        private val BASE_TAP_RESEARCH        = BigDouble.ONE
        private val BASE_RESEARCH_PER_TICK   = BigDouble.of(2.0)
        private val EMERGENCY_REPAIRS_COST   = BigDouble.of(50.0)
        private val EMERGENCY_REPAIRS_AMOUNT = BigDouble.of(25.0)
    }

    private var drivePhase: Int = 0
    private var driveMultiplier: BigDouble = BigDouble.ONE
    private var vesselDecayRate: Float = 0.1f
    private var firstResearchFired  = false
    private var firstVesselsFired   = false
    private var firstColoniesFired  = false
    private var firstLegacyFired    = false

    override fun initialize(world: World) {}

    override fun tick(world: World, delta: Long): List<GameEvent> = emptyList()

    override fun onTap(world: World) {}

    override fun purchaseUpgrade(world: World, upgradeId: String) {}

    override fun purchaseGenerator(world: World, generatorId: String) {}

    override fun syncStateFromWorld(world: World) {}

    override fun toSnapshot(world: World, tick: Long): GameSnapshot = GameSnapshot(
        tick = tick, epoch = EpochType.INTERSTELLAR,
        resources = emptyMap(), generators = emptyList(), upgrades = emptyList(),
        epochProgress = 0f, events = emptyList()
    )

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)
}
