package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlin.math.pow

/**
 * Implements all Cosmology epoch game logic.
 * Implements [GameSystem] (tick loop + snapshot) and [PlayerActionHandler] (player actions).
 */
class CosmologySystem : GameSystem, PlayerActionHandler {

    companion object {
        const val KEY_RES_ENERGY = "res_energy"
        const val KEY_RES_MATTER = "res_matter"
        const val KEY_RES_HYDROGEN = "res_hydrogen"
        const val KEY_RES_STARS = "res_stars"
        const val KEY_RES_ACCRETION_DISKS = "res_accretion_disks"
        const val KEY_RES_PLANETS = "res_planets"

        const val KEY_GEN_NEBULA = "gen_nebula"
        const val KEY_GEN_FUSION = "gen_fusion"
        const val KEY_GEN_STELLAR = "gen_stellar"
        const val KEY_GEN_ACCRETION = "gen_accretion"

        const val KEY_UPG_PARTICLE_DENSITY = "upg_particle_density"
        const val KEY_UPG_NUCLEAR_IGNITION = "upg_nuclear_ignition"
        const val KEY_UPG_STELLAR_COMPRESSION = "upg_stellar_compression"
        const val KEY_UPG_PROTOPLANETARY = "upg_protoplanetary"
        const val KEY_UPG_GRAVITATIONAL_COLLAPSE = "upg_gravitational_collapse"
        const val KEY_UPG_TECTONIC_STABILIZATION = "upg_tectonic_stabilization"

        // Visual normalization thresholds for CosmosState (used by GameViewModel)
        const val MATTER_VISUAL_THRESHOLD = 1_000.0
        const val STAR_VISUAL_THRESHOLD = 100.0

        private val BASE_TAP_MATTER = BigDouble.ONE
        private val BASE_ENERGY_PER_TICK = BigDouble.of(5.0)
        private val INITIAL_COLLAPSE_COST = BigDouble.of(100.0)
    }

    // Mutable collapse cost, reduced by Tectonic Stabilization
    private var collapseCost = INITIAL_COLLAPSE_COST
    private var firstStarFired = false
    private var firstPlanetFired = false

    override fun initialize(world: World) {
        // Resources
        world.put(KEY_RES_ENERGY, ResourceComponent(ResourceType.ENERGY, BigDouble.ZERO))
        world.put(KEY_RES_MATTER, ResourceComponent(ResourceType.MATTER, BigDouble.ZERO))
        world.put(KEY_RES_HYDROGEN, ResourceComponent(ResourceType.HYDROGEN, BigDouble.ZERO))
        world.put(KEY_RES_STARS, ResourceComponent(ResourceType.STARS, BigDouble.ZERO))
        world.put(KEY_RES_ACCRETION_DISKS, ResourceComponent(ResourceType.ACCRETION_DISKS, BigDouble.ZERO))
        world.put(KEY_RES_PLANETS, ResourceComponent(ResourceType.PLANETS, BigDouble.ZERO))

        // Generators
        world.put(KEY_GEN_NEBULA, GeneratorComponent(
            id = KEY_GEN_NEBULA, productionType = ResourceType.MATTER,
            productionRate = BigDouble.ONE, costType = ResourceType.ENERGY,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        world.put(KEY_GEN_FUSION, GeneratorComponent(
            id = KEY_GEN_FUSION, productionType = ResourceType.HYDROGEN,
            productionRate = BigDouble.ONE, costType = ResourceType.MATTER,
            costAmount = BigDouble.of(5.0), unlocked = false
        ))
        world.put(KEY_GEN_STELLAR, GeneratorComponent(
            id = KEY_GEN_STELLAR, productionType = ResourceType.STARS,
            productionRate = BigDouble.ONE, costType = ResourceType.HYDROGEN,
            costAmount = BigDouble.of(10.0), unlocked = true
        ))
        world.put(KEY_GEN_ACCRETION, GeneratorComponent(
            id = KEY_GEN_ACCRETION, productionType = ResourceType.ACCRETION_DISKS,
            productionRate = BigDouble.ONE, costType = ResourceType.STARS,
            costAmount = BigDouble.of(5.0), unlocked = false
        ))

        // Upgrades
        world.put(KEY_UPG_PARTICLE_DENSITY, UpgradeComponent(
            id = KEY_UPG_PARTICLE_DENSITY, purchased = false,
            costType = ResourceType.ENERGY, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_NUCLEAR_IGNITION, UpgradeComponent(
            id = KEY_UPG_NUCLEAR_IGNITION, purchased = false,
            costType = ResourceType.MATTER, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_FUSION)
        ))
        world.put(KEY_UPG_STELLAR_COMPRESSION, UpgradeComponent(
            id = KEY_UPG_STELLAR_COMPRESSION, purchased = false,
            costType = ResourceType.HYDROGEN, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_STELLAR, BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_PROTOPLANETARY, UpgradeComponent(
            id = KEY_UPG_PROTOPLANETARY, purchased = false,
            costType = ResourceType.STARS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_ACCRETION)
        ))
        world.put(KEY_UPG_GRAVITATIONAL_COLLAPSE, UpgradeComponent(
            id = KEY_UPG_GRAVITATIONAL_COLLAPSE, purchased = true,  // available from start, no unlock needed
            costType = ResourceType.ACCRETION_DISKS, costAmount = collapseCost,
            effect = UpgradeEffect.ManualConversion(
                inputType = ResourceType.ACCRETION_DISKS,
                inputAmount = collapseCost,
                outputType = ResourceType.PLANETS,
                outputAmount = BigDouble.ONE
            ),
            repeatable = true
        ))
        world.put(KEY_UPG_TECTONIC_STABILIZATION, UpgradeComponent(
            id = KEY_UPG_TECTONIC_STABILIZATION, purchased = false,
            costType = ResourceType.STARS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.ReduceConversionCost(
                targetUpgradeId = KEY_UPG_GRAVITATIONAL_COLLAPSE,
                multiplier = BigDouble.of(0.5)
            )
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())

        // Passive energy
        resourceComp(world, KEY_RES_ENERGY)?.let {
            it.amount = it.amount + BASE_ENERGY_PER_TICK * bigDelta
        }

        // Generator chain
        runGenerator(world, KEY_GEN_NEBULA, bigDelta)
        runGenerator(world, KEY_GEN_FUSION, bigDelta)
        runGenerator(world, KEY_GEN_STELLAR, bigDelta)
        runGenerator(world, KEY_GEN_ACCRETION, bigDelta)

        // Milestones
        resourceComp(world, KEY_RES_STARS)?.let { stars ->
            if (!firstStarFired && stars.amount > BigDouble.ZERO) {
                firstStarFired = true
                events.add(GameEvent(0, "The first star ignites in the void.", isMilestone = true))
            }
        }
        resourceComp(world, KEY_RES_PLANETS)?.let { planets ->
            if (!firstPlanetFired && planets.amount > BigDouble.ZERO) {
                firstPlanetFired = true
                events.add(GameEvent(0, "A world coalesces from the accretion disk.", isMilestone = true))
            }
        }

        return events
    }

    private fun runGenerator(world: World, key: String, delta: BigDouble) {
        val gen = world.get<GeneratorComponent>(key) ?: return
        if (!gen.unlocked) return
        val costRes = resourceComp(world, "res_${gen.costType.name.lowercase()}") ?: return
        val totalCost = gen.costAmount * delta
        if (costRes.amount < totalCost) return
        costRes.amount = costRes.amount - totalCost
        val prodRes = resourceComp(world, "res_${gen.productionType.name.lowercase()}") ?: return
        prodRes.amount = prodRes.amount + gen.productionRate * delta
    }

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)

    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world)
        resourceComp(world, KEY_RES_MATTER)?.let { it.amount = it.amount + tapAmount }
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return

        when (val effect = upg.effect) {
            is UpgradeEffect.ManualConversion -> {
                // ManualConversion is repeatable but requires purchased=true to be activated first
                if (!upg.purchased) return
                val inputRes = resourceComp(world, "res_${effect.inputType.name.lowercase()}") ?: return
                if (inputRes.amount < effect.inputAmount) return
                inputRes.amount = inputRes.amount - effect.inputAmount
                val outputRes = resourceComp(world, "res_${effect.outputType.name.lowercase()}") ?: return
                outputRes.amount = outputRes.amount + effect.outputAmount
            }
            else -> {
                // One-time purchase
                if (upg.purchased) return
                val costRes = resourceComp(world, "res_${upg.costType.name.lowercase()}") ?: return
                if (costRes.amount < upg.costAmount) return
                costRes.amount = costRes.amount - upg.costAmount
                upg.purchased = true
                applyUpgradeEffect(world, effect)
            }
        }
    }

    private fun applyUpgradeEffect(world: World, effect: UpgradeEffect) {
        when (effect) {
            is UpgradeEffect.UnlockGenerator ->
                world.get<GeneratorComponent>(effect.generatorId)?.unlocked = true
            is UpgradeEffect.MultiplyProduction ->
                world.get<GeneratorComponent>(effect.generatorId)?.let {
                    it.productionRate = it.productionRate * effect.multiplier
                }
            is UpgradeEffect.MultiplyTapProduction -> { /* applied dynamically in currentTapProduction */ }
            is UpgradeEffect.ReduceConversionCost -> {
                val target = world.get<UpgradeComponent>(effect.targetUpgradeId) ?: return
                target.costAmount = target.costAmount * effect.multiplier
                val targetEffect = target.effect
                if (targetEffect is UpgradeEffect.ManualConversion) {
                    targetEffect.inputAmount = targetEffect.inputAmount * effect.multiplier
                }
                collapseCost = collapseCost * effect.multiplier
            }
            is UpgradeEffect.ManualConversion -> { /* handled in purchaseUpgrade */ }
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

    private fun currentTapProduction(world: World): BigDouble {
        val upg = world.get<UpgradeComponent>(KEY_UPG_PARTICLE_DENSITY)
        return if (upg?.purchased == true && upg.effect is UpgradeEffect.MultiplyTapProduction) {
            BASE_TAP_MATTER * (upg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_MATTER
        }
    }

    /** Called after restore() has patched all world state. Syncs instance flags from world. */
    fun syncStateFromWorld(world: World) {
        val stars = world.get<ResourceComponent>(KEY_RES_STARS)?.amount ?: BigDouble.ZERO
        val planets = world.get<ResourceComponent>(KEY_RES_PLANETS)?.amount ?: BigDouble.ZERO
        firstStarFired = stars > BigDouble.ZERO
        firstPlanetFired = planets > BigDouble.ZERO
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val resources = mapOf(
            ResourceType.ENERGY.name to (resourceComp(world, KEY_RES_ENERGY)?.amount ?: BigDouble.ZERO),
            ResourceType.MATTER.name to (resourceComp(world, KEY_RES_MATTER)?.amount ?: BigDouble.ZERO),
            ResourceType.HYDROGEN.name to (resourceComp(world, KEY_RES_HYDROGEN)?.amount ?: BigDouble.ZERO),
            ResourceType.STARS.name to (resourceComp(world, KEY_RES_STARS)?.amount ?: BigDouble.ZERO),
            ResourceType.ACCRETION_DISKS.name to (resourceComp(world, KEY_RES_ACCRETION_DISKS)?.amount ?: BigDouble.ZERO),
            ResourceType.PLANETS.name to (resourceComp(world, KEY_RES_PLANETS)?.amount ?: BigDouble.ZERO),
        )

        val genMeta = mapOf(
            KEY_GEN_NEBULA to "Nebula Condenser",
            KEY_GEN_FUSION to "Hydrogen Fusion",
            KEY_GEN_STELLAR to "Stellar Nursery",
            KEY_GEN_ACCRETION to "Accretion Engine"
        )
        val generators = genMeta.keys.mapNotNull { key ->
            world.get<GeneratorComponent>(key)?.let { gen ->
                GeneratorSnapshot(
                    id = gen.id, displayName = genMeta[key] ?: key,
                    productionType = gen.productionType, productionRate = gen.productionRate,
                    costType = gen.costType, costAmount = gen.costAmount,
                    unlocked = gen.unlocked, level = gen.level
                )
            }
        }

        val upgMeta = mapOf(
            KEY_UPG_PARTICLE_DENSITY to Pair("Particle Density", "x2 Matter per tap"),
            KEY_UPG_NUCLEAR_IGNITION to Pair("Nuclear Ignition", "Unlock Hydrogen Fusion"),
            KEY_UPG_STELLAR_COMPRESSION to Pair("Stellar Compression", "x2 Stars/tick"),
            KEY_UPG_PROTOPLANETARY to Pair("Protoplanetary Disk", "Unlock Accretion Engine"),
            KEY_UPG_GRAVITATIONAL_COLLAPSE to Pair("Gravitational Collapse", "100 Disks -> 1 Planet"),
            KEY_UPG_TECTONIC_STABILIZATION to Pair("Tectonic Stabilization", "-50% Planet formation cost")
        )
        val upgrades = upgMeta.keys.mapNotNull { key ->
            world.get<UpgradeComponent>(key)?.let { upg ->
                val availableResource = resources[upg.costType.name] ?: BigDouble.ZERO
                UpgradeSnapshot(
                    id = upg.id, displayName = upgMeta[key]!!.first,
                    description = upgMeta[key]!!.second,
                    costType = upg.costType, costAmount = upg.costAmount,
                    purchased = upg.purchased, repeatable = upg.repeatable,
                    available = availableResource >= upg.costAmount
                )
            }
        }

        val planets = resources[ResourceType.PLANETS.name] ?: BigDouble.ZERO
        val epochProgress = if (planets >= BigDouble.ONE) 1f else 0f

        return GameSnapshot(
            tick = tick, epoch = EpochType.COSMOLOGY,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList()
        )
    }
}
