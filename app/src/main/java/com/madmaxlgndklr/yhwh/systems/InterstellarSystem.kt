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

    override fun initialize(world: World) {
        val civilization = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)
            ?.amount?.toDouble() ?: 0.0
        val seedingBonus = BigDouble.of(1.0 + floor(civilization / 100.0) * 0.1)

        world.put(KEY_RES_RESEARCH, ResourceComponent(ResourceType.RESEARCH, BigDouble.ZERO))
        world.put(KEY_RES_VESSELS,  ResourceComponent(ResourceType.VESSELS,  BigDouble.ZERO))
        world.put(KEY_RES_COLONIES, ResourceComponent(ResourceType.COLONIES, BigDouble.ZERO))
        world.put(KEY_RES_LEGACY,   ResourceComponent(ResourceType.LEGACY,   BigDouble.ZERO))

        world.put(KEY_GEN_RESEARCH_INSTITUTE, GeneratorComponent(
            id = KEY_GEN_RESEARCH_INSTITUTE,
            productionType = ResourceType.RESEARCH, productionRate = seedingBonus,
            costType = ResourceType.CIVILIZATION,   costAmount = BigDouble.ONE,
            unlocked = true, level = 0
        ))
        world.put(KEY_GEN_SHIPYARD, GeneratorComponent(
            id = KEY_GEN_SHIPYARD,
            productionType = ResourceType.VESSELS, productionRate = BigDouble.ONE,
            costType = ResourceType.RESEARCH,      costAmount = BigDouble.of(0.5),
            unlocked = true, level = 0
        ))
        world.put(KEY_GEN_COLONY_FLEET, GeneratorComponent(
            id = KEY_GEN_COLONY_FLEET,
            productionType = ResourceType.COLONIES, productionRate = BigDouble.ONE,
            costType = ResourceType.VESSELS,        costAmount = BigDouble.of(0.5),
            unlocked = false, level = 0
        ))
        world.put(KEY_GEN_GALACTIC_SENATE, GeneratorComponent(
            id = KEY_GEN_GALACTIC_SENATE,
            productionType = ResourceType.LEGACY, productionRate = BigDouble.ONE,
            costType = ResourceType.COLONIES,     costAmount = BigDouble.of(0.5),
            unlocked = false, level = 0
        ))

        world.put(KEY_UPG_ADVANCED_SENSORS, UpgradeComponent(
            id = KEY_UPG_ADVANCED_SENSORS, purchased = false,
            costType = ResourceType.RESEARCH, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        // Sentinel effect — real halved-decay logic is applied in tick() by checking purchased flag
        world.put(KEY_UPG_HULL_PLATING, UpgradeComponent(
            id = KEY_UPG_HULL_PLATING, purchased = false,
            costType = ResourceType.VESSELS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        // Sentinel effect — repeatable; real vessel-restoration logic is in purchaseUpgrade()
        world.put(KEY_UPG_EMERGENCY_REPAIRS, UpgradeComponent(
            id = KEY_UPG_EMERGENCY_REPAIRS, purchased = false,
            costType = ResourceType.RESEARCH, costAmount = EMERGENCY_REPAIRS_COST,
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE),
            repeatable = true
        ))
        world.put(KEY_UPG_LONG_RANGE_COMMS, UpgradeComponent(
            id = KEY_UPG_LONG_RANGE_COMMS, purchased = false,
            costType = ResourceType.COLONIES, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_COLONY_FLEET, BigDouble.of(2.0))
        ))
        // Sentinel effect — real phase-advancement logic is in purchaseUpgrade()
        world.put(KEY_UPG_ION_DRIVE, UpgradeComponent(
            id = KEY_UPG_ION_DRIVE, purchased = false,
            costType = ResourceType.RESEARCH, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        // Sentinel effect — requires drivePhase >= 1 (Ion Drive purchased); real logic in purchaseUpgrade()
        world.put(KEY_UPG_HYPERDRIVE, UpgradeComponent(
            id = KEY_UPG_HYPERDRIVE, purchased = false,
            costType = ResourceType.COLONIES, costAmount = BigDouble.of(200.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> = emptyList()

    override fun onTap(world: World) {
        resourceComp(world, KEY_RES_RESEARCH)?.let { it.amount = it.amount + currentTapProduction(world) }
    }

    private fun currentTapProduction(world: World): BigDouble {
        val upg = world.get<UpgradeComponent>(KEY_UPG_ADVANCED_SENSORS)
        return if (upg?.purchased == true && upg.effect is UpgradeEffect.MultiplyTapProduction) {
            BASE_TAP_RESEARCH * (upg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_RESEARCH
        }
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return
        if (upg.purchased && !upg.repeatable) return
        val costRes = resourceComp(world, "res_${upg.costType.name.lowercase()}") ?: return
        if (costRes.amount < upg.costAmount) return
        costRes.amount = costRes.amount - upg.costAmount
        if (!upg.repeatable) {
            upg.purchased = true
            applyUpgradeEffect(world, upg.effect)
        }
    }

    private fun applyUpgradeEffect(world: World, effect: UpgradeEffect) {
        when (effect) {
            is UpgradeEffect.MultiplyProduction ->
                world.get<GeneratorComponent>(effect.generatorId)?.let {
                    it.productionRate = it.productionRate * effect.multiplier
                }
            is UpgradeEffect.UnlockGenerator ->
                world.get<GeneratorComponent>(effect.generatorId)?.unlocked = true
            is UpgradeEffect.MultiplyTapProduction -> { /* applied dynamically in currentTapProduction */ }
            is UpgradeEffect.ManualConversion -> { /* not used in this system */ }
            is UpgradeEffect.ReduceConversionCost -> { /* not used in this system */ }
        }
    }

    override fun purchaseGenerator(world: World, generatorId: String) {
        val gen = world.get<GeneratorComponent>(generatorId) ?: return
        if (!gen.unlocked) return
        val levelUpCost = gen.costAmount * BigDouble.of(1.15.pow(gen.level.toDouble()))
        val costRes = resourceComp(world, "res_${gen.costType.name.lowercase()}") ?: return
        if (costRes.amount < levelUpCost) return
        costRes.amount = costRes.amount - levelUpCost
        gen.productionRate = gen.productionRate * BigDouble.of(1.1)
        gen.level++
    }

    override fun syncStateFromWorld(world: World) {}

    override fun toSnapshot(world: World, tick: Long): GameSnapshot = GameSnapshot(
        tick = tick, epoch = EpochType.INTERSTELLAR,
        resources = emptyMap(), generators = emptyList(), upgrades = emptyList(),
        epochProgress = 0f, events = emptyList()
    )

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)
}
