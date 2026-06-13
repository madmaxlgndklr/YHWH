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

        // Upgrades: use putIfAbsent so purchased flags survive re-initialization during restore
        world.putIfAbsent(KEY_UPG_ADVANCED_SENSORS, UpgradeComponent(
            id = KEY_UPG_ADVANCED_SENSORS, purchased = false,
            costType = ResourceType.RESEARCH, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        // Sentinel effect — real halved-decay logic is applied in tick() by checking purchased flag
        world.putIfAbsent(KEY_UPG_HULL_PLATING, UpgradeComponent(
            id = KEY_UPG_HULL_PLATING, purchased = false,
            costType = ResourceType.VESSELS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        // Sentinel effect — repeatable; real vessel-restoration logic is in purchaseUpgrade()
        world.putIfAbsent(KEY_UPG_EMERGENCY_REPAIRS, UpgradeComponent(
            id = KEY_UPG_EMERGENCY_REPAIRS, purchased = false,
            costType = ResourceType.RESEARCH, costAmount = EMERGENCY_REPAIRS_COST,
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE),
            repeatable = true
        ))
        world.putIfAbsent(KEY_UPG_LONG_RANGE_COMMS, UpgradeComponent(
            id = KEY_UPG_LONG_RANGE_COMMS, purchased = false,
            costType = ResourceType.COLONIES, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_COLONY_FLEET, BigDouble.of(2.0))
        ))
        // Sentinel effect — real phase-advancement logic is in purchaseUpgrade()
        world.putIfAbsent(KEY_UPG_ION_DRIVE, UpgradeComponent(
            id = KEY_UPG_ION_DRIVE, purchased = false,
            costType = ResourceType.RESEARCH, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        // Sentinel effect — requires drivePhase >= 1 (Ion Drive purchased); real logic in purchaseUpgrade()
        world.putIfAbsent(KEY_UPG_HYPERDRIVE, UpgradeComponent(
            id = KEY_UPG_HYPERDRIVE, purchased = false,
            costType = ResourceType.COLONIES, costAmount = BigDouble.of(200.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())

        // Passive Research floor — always 2.0/tick regardless of generators
        resourceComp(world, KEY_RES_RESEARCH)?.let {
            it.amount = it.amount + BASE_RESEARCH_PER_TICK * bigDelta
        }

        // Vessel Decay — applied every tick, floored at 0
        val hullPurchased = world.get<UpgradeComponent>(KEY_UPG_HULL_PLATING)?.purchased == true
        val decayPerTick = if (hullPurchased) vesselDecayRate * 0.5f else vesselDecayRate
        val decayAmount = BigDouble.of(decayPerTick.toDouble()) * bigDelta
        resourceComp(world, KEY_RES_VESSELS)?.let { vessels ->
            vessels.amount = (vessels.amount - decayAmount).coerceAtLeast(BigDouble.ZERO)
        }

        runGenerator(world, KEY_GEN_RESEARCH_INSTITUTE, bigDelta)
        runGenerator(world, KEY_GEN_SHIPYARD, bigDelta)
        runGenerator(world, KEY_GEN_COLONY_FLEET, bigDelta)
        runGenerator(world, KEY_GEN_GALACTIC_SENATE, bigDelta)

        // Milestone events (fire once per resource type)
        resourceComp(world, KEY_RES_RESEARCH)?.let {
            if (!firstResearchFired && it.amount > BigDouble.ZERO) {
                firstResearchFired = true
                events.add(GameEvent(0L, "The stars await. Research begins.", isMilestone = true))
            }
        }
        resourceComp(world, KEY_RES_VESSELS)?.let {
            if (!firstVesselsFired && it.amount > BigDouble.ZERO) {
                firstVesselsFired = true
                events.add(GameEvent(0L, "First starship assembled and launched.", isMilestone = true))
            }
        }
        resourceComp(world, KEY_RES_COLONIES)?.let {
            if (!firstColoniesFired && it.amount > BigDouble.ZERO) {
                firstColoniesFired = true
                events.add(GameEvent(0L, "A new world settles among the stars.", isMilestone = true))
            }
        }
        resourceComp(world, KEY_RES_LEGACY)?.let {
            if (!firstLegacyFired && it.amount > BigDouble.ZERO) {
                firstLegacyFired = true
                events.add(GameEvent(0L, "Humanity's legacy endures beyond the cosmos.", isMilestone = true))
            }
        }
        return events
    }

    private fun runGenerator(world: World, key: String, delta: BigDouble) {
        val gen = world.get<GeneratorComponent>(key) ?: return
        if (!gen.unlocked) return
        if (gen.level == 0) return
        val costRes = resourceComp(world, "res_${gen.costType.name.lowercase()}") ?: return
        val totalCost = gen.costAmount * delta
        if (costRes.amount < totalCost) return
        costRes.amount = costRes.amount - totalCost
        val prodRes = resourceComp(world, "res_${gen.productionType.name.lowercase()}") ?: return
        prodRes.amount = prodRes.amount + gen.productionRate * delta * driveMultiplier
    }

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
        // Emergency Repairs: repeatable — spend 50 RESEARCH, restore 25 VESSELS
        if (upgradeId == KEY_UPG_EMERGENCY_REPAIRS) {
            val researchRes = resourceComp(world, KEY_RES_RESEARCH) ?: return
            if (researchRes.amount < EMERGENCY_REPAIRS_COST) return
            researchRes.amount = researchRes.amount - EMERGENCY_REPAIRS_COST
            resourceComp(world, KEY_RES_VESSELS)?.let { it.amount = it.amount + EMERGENCY_REPAIRS_AMOUNT }
            return
        }
        // Ion Drive: advance to Phase 1
        if (upgradeId == KEY_UPG_ION_DRIVE) {
            if (drivePhase >= 1) return
            val upg = world.get<UpgradeComponent>(KEY_UPG_ION_DRIVE) ?: return
            val researchRes = resourceComp(world, KEY_RES_RESEARCH) ?: return
            if (researchRes.amount < upg.costAmount) return
            researchRes.amount = researchRes.amount - upg.costAmount
            drivePhase = 1
            driveMultiplier = BigDouble.of(2.0)
            vesselDecayRate = 0.2f
            world.get<GeneratorComponent>(KEY_GEN_COLONY_FLEET)?.unlocked = true
            world.get<UpgradeComponent>(KEY_UPG_ION_DRIVE)?.purchased = true
            return
        }
        // Hyperdrive: advance to Phase 2, requires Ion Drive first
        if (upgradeId == KEY_UPG_HYPERDRIVE) {
            if (drivePhase < 1 || drivePhase >= 2) return
            val upg = world.get<UpgradeComponent>(KEY_UPG_HYPERDRIVE) ?: return
            val coloniesRes = resourceComp(world, KEY_RES_COLONIES) ?: return
            if (coloniesRes.amount < upg.costAmount) return
            coloniesRes.amount = coloniesRes.amount - upg.costAmount
            drivePhase = 2
            driveMultiplier = BigDouble.of(4.0)
            vesselDecayRate = 0.4f
            world.get<GeneratorComponent>(KEY_GEN_GALACTIC_SENATE)?.unlocked = true
            world.get<UpgradeComponent>(KEY_UPG_HYPERDRIVE)?.purchased = true
            return
        }
        // Standard one-time upgrades (Advanced Sensors, Hull Plating, Long-Range Comms)
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
            is UpgradeEffect.MultiplyTapProduction -> { /* applied dynamically in currentTapProduction */ }
            is UpgradeEffect.UnlockGenerator ->
                world.get<GeneratorComponent>(effect.generatorId)?.unlocked = true
            is UpgradeEffect.ManualConversion -> { }
            is UpgradeEffect.ReduceConversionCost -> { }
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

    override fun syncStateFromWorld(world: World) {
        val ionPurchased = world.get<UpgradeComponent>(KEY_UPG_ION_DRIVE)?.purchased == true
        val hyperdrivePurchased = world.get<UpgradeComponent>(KEY_UPG_HYPERDRIVE)?.purchased == true
        drivePhase = when {
            hyperdrivePurchased -> 2
            ionPurchased -> 1
            else -> 0
        }
        driveMultiplier = when (drivePhase) {
            2 -> BigDouble.of(4.0)
            1 -> BigDouble.of(2.0)
            else -> BigDouble.ONE
        }
        vesselDecayRate = when (drivePhase) {
            2 -> 0.4f
            1 -> 0.2f
            else -> 0.1f
        }
        firstResearchFired  = (resourceComp(world, KEY_RES_RESEARCH)?.amount  ?: BigDouble.ZERO) > BigDouble.ZERO
        firstVesselsFired   = (resourceComp(world, KEY_RES_VESSELS)?.amount   ?: BigDouble.ZERO) > BigDouble.ZERO
        firstColoniesFired  = (resourceComp(world, KEY_RES_COLONIES)?.amount  ?: BigDouble.ZERO) > BigDouble.ZERO
        firstLegacyFired    = (resourceComp(world, KEY_RES_LEGACY)?.amount    ?: BigDouble.ZERO) > BigDouble.ZERO
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val research = resourceComp(world, KEY_RES_RESEARCH)?.amount ?: BigDouble.ZERO
        val vessels  = resourceComp(world, KEY_RES_VESSELS)?.amount  ?: BigDouble.ZERO
        val colonies = resourceComp(world, KEY_RES_COLONIES)?.amount ?: BigDouble.ZERO
        val legacy   = resourceComp(world, KEY_RES_LEGACY)?.amount   ?: BigDouble.ZERO
        val civilization = world.get<ResourceComponent>(CivilizationSystem.KEY_RES_CIVILIZATION)?.amount ?: BigDouble.ZERO

        val resources = mapOf(
            ResourceType.RESEARCH.name to research,
            ResourceType.VESSELS.name  to vessels,
            ResourceType.COLONIES.name to colonies,
            ResourceType.LEGACY.name   to legacy,
            ResourceType.CIVILIZATION.name to civilization
        )

        val genMeta = mapOf(
            KEY_GEN_RESEARCH_INSTITUTE to "Research Institute",
            KEY_GEN_SHIPYARD           to "Shipyard",
            KEY_GEN_COLONY_FLEET       to "Colony Fleet",
            KEY_GEN_GALACTIC_SENATE    to "Galactic Senate"
        )
        val generators = genMeta.keys.mapNotNull { key ->
            world.get<GeneratorComponent>(key)?.let { gen ->
                val nextLevelCost = gen.costAmount * BigDouble.of(1.15.pow(gen.level.toDouble()))
                val available = resourceComp(world, "res_${gen.costType.name.lowercase()}")?.amount ?: BigDouble.ZERO
                GeneratorSnapshot(
                    id = gen.id, displayName = genMeta[key] ?: key,
                    productionType = gen.productionType, productionRate = gen.productionRate,
                    costType = gen.costType, costAmount = gen.costAmount,
                    unlocked = gen.unlocked, level = gen.level,
                    nextLevelCost = nextLevelCost, canAfford = available >= nextLevelCost
                )
            }
        }

        val ionPurchased = world.get<UpgradeComponent>(KEY_UPG_ION_DRIVE)?.purchased == true

        val upgMeta = mapOf(
            KEY_UPG_ADVANCED_SENSORS  to Pair("Advanced Sensors",  "×2 Research per tap"),
            KEY_UPG_HULL_PLATING      to Pair("Hull Plating",      "½ Vessel decay rate"),
            KEY_UPG_EMERGENCY_REPAIRS to Pair("Emergency Repairs", "50 🔭 → +25 🚀"),
            KEY_UPG_LONG_RANGE_COMMS  to Pair("Long-Range Comms",  "×2 Colony Fleet production"),
            KEY_UPG_ION_DRIVE         to Pair("Ion Drive",         "Advance to Ion Age"),
            KEY_UPG_HYPERDRIVE        to Pair("Hyperdrive",        "Advance to Hyperdrive Era")
        )
        val upgrades = upgMeta.keys.mapNotNull { key ->
            world.get<UpgradeComponent>(key)?.let { upg ->
                val availableResource = resources[upg.costType.name] ?: BigDouble.ZERO
                val available = when {
                    key == KEY_UPG_HYPERDRIVE && !ionPurchased -> false
                    upg.purchased && !upg.repeatable -> false
                    else -> availableResource >= upg.costAmount
                }
                UpgradeSnapshot(
                    id = upg.id,
                    displayName = upgMeta[key]!!.first,
                    description  = upgMeta[key]!!.second,
                    costType = upg.costType,
                    costAmount = upg.costAmount,
                    purchased = upg.purchased,
                    repeatable = upg.repeatable,
                    available = available
                )
            }
        }

        val epochProgress = (legacy.toDouble() / WIN_THRESHOLD).toFloat().coerceIn(0f, 1f)

        return GameSnapshot(
            tick = tick, epoch = EpochType.INTERSTELLAR,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList(),
            vesselDecayRate = vesselDecayRate,
            saveSchemaVersion = 1
        )
    }

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)
}
