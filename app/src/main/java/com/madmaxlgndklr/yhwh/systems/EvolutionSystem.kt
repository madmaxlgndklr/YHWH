package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlin.math.floor
import kotlin.math.pow

class EvolutionSystem : GameSystem, PlayerActionHandler, Restorable {

    companion object {
        const val KEY_RES_GENES = "res_genes"
        const val KEY_RES_MUTATIONS = "res_mutations"
        const val KEY_RES_SPECIES = "res_species"
        const val KEY_RES_DOMINANCE = "res_dominance"

        const val KEY_GEN_PRIMORDIAL_GENE_POOL = "gen_primordial_gene_pool"
        const val KEY_GEN_MUTATION_ENGINE = "gen_mutation_engine"
        const val KEY_GEN_NATURAL_SELECTION_CHAMBER = "gen_natural_selection_chamber"
        const val KEY_GEN_ECOSYSTEM_ARCHITECT = "gen_ecosystem_architect"

        const val KEY_UPG_GENETIC_DRIFT = "upg_genetic_drift"
        const val KEY_UPG_RNA_REPLICATION = "upg_rna_replication"
        const val KEY_UPG_NICHE_COLONIZATION = "upg_niche_colonization"
        const val KEY_UPG_ADAPTIVE_IMMUNITY = "upg_adaptive_immunity"
        const val KEY_UPG_HYPERMUTATION = "upg_hypermutation"
        const val KEY_UPG_APEX_DOMINANCE = "upg_apex_dominance"

        const val MUTATION_VISUAL_THRESHOLD = 500.0
        const val SPECIES_VISUAL_THRESHOLD = 200.0
        const val WIN_THRESHOLD = 1000.0

        const val EVENT_FIRST_DELAY_TICKS = 90   // grace(30) + first interval(60)
        const val EVENT_INTERVAL_TICKS = 60

        private val BASE_TAP_GENES = BigDouble.ONE
        private val BASE_GENES_PER_TICK = BigDouble.ONE
        private val APEX_COST = BigDouble.of(100.0)
        private const val DECAY_RATE_NORMAL = 0.5
        private const val DECAY_RATE_IMMUNE = 0.25
    }

    private var forked = false
    private var chosenPath: String? = null
    private var firstDominanceFired = false
    private var activeEvent: EvolutionEvent? = null
    private var eventTicksRemaining: Int = 0
    private var ticksUntilNextEvent: Int = EVENT_FIRST_DELAY_TICKS

    override fun initialize(world: World) {
        val organisms = world.get<ResourceComponent>(BiologySystem.KEY_RES_ORGANISMS)
            ?.amount?.toDouble() ?: 0.0
        val seedingBonus = BigDouble.of(1.0 + floor(organisms / 100.0) * 0.1)

        world.put(KEY_RES_GENES, ResourceComponent(ResourceType.GENES, BigDouble.ZERO))
        world.put(KEY_RES_MUTATIONS, ResourceComponent(ResourceType.MUTATIONS, BigDouble.ZERO))
        world.put(KEY_RES_SPECIES, ResourceComponent(ResourceType.SPECIES, BigDouble.ZERO))
        world.put(KEY_RES_DOMINANCE, ResourceComponent(ResourceType.DOMINANCE, BigDouble.ZERO))

        world.put(KEY_GEN_PRIMORDIAL_GENE_POOL, GeneratorComponent(
            id = KEY_GEN_PRIMORDIAL_GENE_POOL,
            productionType = ResourceType.GENES,
            productionRate = seedingBonus,
            costType = ResourceType.ORGANISMS,
            costAmount = BigDouble.ONE,
            unlocked = true, level = 0
        ))
        world.put(KEY_GEN_MUTATION_ENGINE, GeneratorComponent(
            id = KEY_GEN_MUTATION_ENGINE,
            productionType = ResourceType.MUTATIONS,
            productionRate = BigDouble.ONE,
            costType = ResourceType.GENES,
            costAmount = BigDouble.of(2.0),
            unlocked = false
        ))
        world.put(KEY_GEN_NATURAL_SELECTION_CHAMBER, GeneratorComponent(
            id = KEY_GEN_NATURAL_SELECTION_CHAMBER,
            productionType = ResourceType.SPECIES,
            productionRate = BigDouble.ONE,
            costType = ResourceType.MUTATIONS,
            costAmount = BigDouble.of(10.0),
            unlocked = true, level = 0
        ))
        world.put(KEY_GEN_ECOSYSTEM_ARCHITECT, GeneratorComponent(
            id = KEY_GEN_ECOSYSTEM_ARCHITECT,
            productionType = ResourceType.DOMINANCE,
            productionRate = BigDouble.ONE,
            costType = ResourceType.SPECIES,
            costAmount = BigDouble.of(5.0),
            unlocked = false
        ))

        world.put(KEY_UPG_GENETIC_DRIFT, UpgradeComponent(
            id = KEY_UPG_GENETIC_DRIFT, purchased = false,
            costType = ResourceType.GENES, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_RNA_REPLICATION, UpgradeComponent(
            id = KEY_UPG_RNA_REPLICATION, purchased = false,
            costType = ResourceType.GENES, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_MUTATION_ENGINE)
        ))
        world.put(KEY_UPG_NICHE_COLONIZATION, UpgradeComponent(
            id = KEY_UPG_NICHE_COLONIZATION, purchased = false,
            costType = ResourceType.SPECIES, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_ECOSYSTEM_ARCHITECT)
        ))
        // Adaptive Immunity: behavioral only (checked in tick). MultiplyTapProduction(1.0) is a no-op sentinel.
        world.put(KEY_UPG_ADAPTIVE_IMMUNITY, UpgradeComponent(
            id = KEY_UPG_ADAPTIVE_IMMUNITY, purchased = false,
            costType = ResourceType.MUTATIONS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        world.put(KEY_UPG_HYPERMUTATION, UpgradeComponent(
            id = KEY_UPG_HYPERMUTATION, purchased = false,
            costType = ResourceType.GENES, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_MUTATION_ENGINE, BigDouble.of(2.0))
        ))
        // purchased = true makes it usable as a repeatable from the start
        world.put(KEY_UPG_APEX_DOMINANCE, UpgradeComponent(
            id = KEY_UPG_APEX_DOMINANCE, purchased = true,
            costType = ResourceType.SPECIES, costAmount = APEX_COST,
            effect = UpgradeEffect.ManualConversion(
                inputType = ResourceType.SPECIES,
                inputAmount = APEX_COST,
                outputType = ResourceType.DOMINANCE,
                outputAmount = BigDouble.ONE
            ),
            repeatable = true
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())
        val intDelta = delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        // Species decay
        val immunityPurchased = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
        val decayRate = if (immunityPurchased) DECAY_RATE_IMMUNE else DECAY_RATE_NORMAL
        resourceComp(world, KEY_RES_SPECIES)?.let { species ->
            species.amount = (species.amount - BigDouble.of(decayRate * delta)).coerceAtLeast(BigDouble.ZERO)
        }

        // Active event countdown
        if (activeEvent != null) {
            eventTicksRemaining = (eventTicksRemaining - intDelta).coerceAtLeast(0)
            if (eventTicksRemaining == 0) {
                events.add(GameEvent(0, "${activeEvent!!.displayName} has ended.", false))
                activeEvent = null
            }
        }

        // Event trigger
        if (activeEvent == null) {
            ticksUntilNextEvent = (ticksUntilNextEvent - intDelta).coerceAtLeast(0)
            if (ticksUntilNextEvent == 0) {
                val newEvent = EvolutionEvent.entries.random()
                activeEvent = newEvent
                val immune = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
                eventTicksRemaining = when (newEvent) {
                    EvolutionEvent.ICE_AGE -> if (immune) 15 else 30
                    EvolutionEvent.ASTEROID_IMPACT -> if (immune) 10 else 20
                    EvolutionEvent.VOLCANIC_WINTER -> if (immune) 22 else 45
                }
                ticksUntilNextEvent = EVENT_INTERVAL_TICKS
                events.add(GameEvent(0, "${newEvent.displayName} has begun!", true))
            }
        }

        // Passive gene baseline
        resourceComp(world, KEY_RES_GENES)?.let {
            it.amount = it.amount + BASE_GENES_PER_TICK * bigDelta
        }

        // Generators with event debuffs
        val iceMultiplier = if (activeEvent == EvolutionEvent.ICE_AGE) BigDouble.of(0.5) else BigDouble.ONE
        val asteroidMultiplier = if (activeEvent == EvolutionEvent.ASTEROID_IMPACT) BigDouble.of(0.25) else BigDouble.ONE
        val volcanicMultiplier = if (activeEvent == EvolutionEvent.VOLCANIC_WINTER) BigDouble.of(0.5) else BigDouble.ONE

        runGenerator(world, KEY_GEN_PRIMORDIAL_GENE_POOL, bigDelta, iceMultiplier)
        runGenerator(world, KEY_GEN_MUTATION_ENGINE, bigDelta, asteroidMultiplier)
        runGenerator(world, KEY_GEN_NATURAL_SELECTION_CHAMBER, bigDelta, volcanicMultiplier)
        runGenerator(world, KEY_GEN_ECOSYSTEM_ARCHITECT, bigDelta)

        // Dominance milestone
        resourceComp(world, KEY_RES_DOMINANCE)?.let { dom ->
            if (!firstDominanceFired && dom.amount > BigDouble.ZERO) {
                firstDominanceFired = true
                events.add(GameEvent(0, "Dominance established. The ecosystem bends to your will.", true))
            }
        }

        return events
    }

    private fun runGenerator(
        world: World, key: String, delta: BigDouble,
        productionMultiplier: BigDouble = BigDouble.ONE
    ) {
        val gen = world.get<GeneratorComponent>(key) ?: return
        if (!gen.unlocked) return
        if (gen.level == 0) return
        val costRes = resourceComp(world, "res_${gen.costType.name.lowercase()}") ?: return
        val totalCost = gen.costAmount * delta
        if (costRes.amount < totalCost) return
        costRes.amount = costRes.amount - totalCost
        val prodRes = resourceComp(world, "res_${gen.productionType.name.lowercase()}") ?: return
        prodRes.amount = prodRes.amount + gen.productionRate * delta * productionMultiplier
    }

    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world)
        resourceComp(world, KEY_RES_GENES)?.let { it.amount = it.amount + tapAmount }
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return
        when (val effect = upg.effect) {
            is UpgradeEffect.ManualConversion -> {
                // Apex Dominance repeatable conversion
                if (!upg.purchased) return
                val inputRes = resourceComp(world, "res_${effect.inputType.name.lowercase()}") ?: return
                if (inputRes.amount < effect.inputAmount) return
                inputRes.amount = inputRes.amount - effect.inputAmount
                val outputRes = resourceComp(world, "res_${effect.outputType.name.lowercase()}") ?: return
                outputRes.amount = outputRes.amount + effect.outputAmount
            }
            else -> {
                // Fork gate: if the other fork path was chosen, deny purchase
                if (upgradeId == KEY_UPG_ADAPTIVE_IMMUNITY || upgradeId == KEY_UPG_HYPERMUTATION) {
                    if (forked && chosenPath != upgradeId) return
                }
                if (upg.purchased) return
                val costRes = resourceComp(world, "res_${upg.costType.name.lowercase()}") ?: return
                if (costRes.amount < upg.costAmount) return
                costRes.amount = costRes.amount - upg.costAmount
                upg.purchased = true
                if (upgradeId == KEY_UPG_ADAPTIVE_IMMUNITY || upgradeId == KEY_UPG_HYPERMUTATION) {
                    forked = true
                    chosenPath = upgradeId
                }
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
            is UpgradeEffect.ManualConversion -> { /* handled in purchaseUpgrade */ }
            is UpgradeEffect.ReduceConversionCost -> { /* not used in Evolution */ }
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
        // Reads KEY_UPG_GENETIC_DRIFT specifically. KEY_UPG_ADAPTIVE_IMMUNITY also uses
        // MultiplyTapProduction(1.0) as a no-op sentinel — do not iterate all purchased
        // MultiplyTapProduction effects; check by key instead.
        val upg = world.get<UpgradeComponent>(KEY_UPG_GENETIC_DRIFT)
        return if (upg?.purchased == true && upg.effect is UpgradeEffect.MultiplyTapProduction) {
            BASE_TAP_GENES * (upg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_GENES
        }
    }

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)

    override fun syncStateFromWorld(world: World) {
        val adaptiveImmunityPurchased = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
        val hypermutationPurchased = world.get<UpgradeComponent>(KEY_UPG_HYPERMUTATION)?.purchased == true
        when {
            adaptiveImmunityPurchased -> { forked = true; chosenPath = KEY_UPG_ADAPTIVE_IMMUNITY }
            hypermutationPurchased -> { forked = true; chosenPath = KEY_UPG_HYPERMUTATION }
        }
        val dominance = resourceComp(world, KEY_RES_DOMINANCE)?.amount ?: BigDouble.ZERO
        firstDominanceFired = dominance > BigDouble.ZERO
        // activeEvent and eventTicksRemaining are in GameSnapshot but Restorable only receives World.
        // On restore the active event resets; ticksUntilNextEvent restarts at EVENT_FIRST_DELAY_TICKS.
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val genes = resourceComp(world, KEY_RES_GENES)?.amount ?: BigDouble.ZERO
        val mutations = resourceComp(world, KEY_RES_MUTATIONS)?.amount ?: BigDouble.ZERO
        val species = resourceComp(world, KEY_RES_SPECIES)?.amount ?: BigDouble.ZERO
        val dominance = resourceComp(world, KEY_RES_DOMINANCE)?.amount ?: BigDouble.ZERO

        val resources = mapOf(
            ResourceType.GENES.name to genes,
            ResourceType.MUTATIONS.name to mutations,
            ResourceType.SPECIES.name to species,
            ResourceType.DOMINANCE.name to dominance
        )

        val genMeta = mapOf(
            KEY_GEN_PRIMORDIAL_GENE_POOL to "Primordial Gene Pool",
            KEY_GEN_MUTATION_ENGINE to "Mutation Engine",
            KEY_GEN_NATURAL_SELECTION_CHAMBER to "Natural Selection Chamber",
            KEY_GEN_ECOSYSTEM_ARCHITECT to "Ecosystem Architect"
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

        val adaptiveImmunityPurchased = world.get<UpgradeComponent>(KEY_UPG_ADAPTIVE_IMMUNITY)?.purchased == true
        val hypermutationPurchased = world.get<UpgradeComponent>(KEY_UPG_HYPERMUTATION)?.purchased == true

        val upgMeta = mapOf(
            KEY_UPG_GENETIC_DRIFT to Pair("Genetic Drift", "×2 Genes per tap"),
            KEY_UPG_RNA_REPLICATION to Pair("RNA Replication", "Unlock Mutation Engine"),
            KEY_UPG_NICHE_COLONIZATION to Pair("Niche Colonization", "Unlock Ecosystem Architect"),
            KEY_UPG_ADAPTIVE_IMMUNITY to Pair("Adaptive Immunity", "½ decay rate · ½ event duration"),
            KEY_UPG_HYPERMUTATION to Pair("Hypermutation", "×2 Mutation Engine production"),
            KEY_UPG_APEX_DOMINANCE to Pair("Apex Dominance", "100 Species → 1 Dominance")
        )
        val upgrades = upgMeta.keys.mapNotNull { key ->
            world.get<UpgradeComponent>(key)?.let { upg ->
                val availableResource = resources[upg.costType.name] ?: BigDouble.ZERO
                val forkLocked = (key == KEY_UPG_ADAPTIVE_IMMUNITY && hypermutationPurchased) ||
                        (key == KEY_UPG_HYPERMUTATION && adaptiveImmunityPurchased)
                val available = when {
                    forkLocked -> false
                    upg.repeatable -> availableResource >= upg.costAmount
                    upg.purchased -> false
                    else -> availableResource >= upg.costAmount
                }
                UpgradeSnapshot(
                    id = upg.id,
                    displayName = upgMeta[key]!!.first,
                    description = upgMeta[key]!!.second,
                    costType = upg.costType,
                    costAmount = upg.costAmount,
                    purchased = upg.purchased,
                    repeatable = upg.repeatable,
                    available = available
                )
            }
        }

        val epochProgress = (dominance.toDouble() / WIN_THRESHOLD).toFloat().coerceIn(0f, 1f)

        return GameSnapshot(
            tick = tick, epoch = EpochType.EVOLUTION,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList(),
            activeEvent = activeEvent, eventTicksRemaining = eventTicksRemaining,
            saveSchemaVersion = 1
        )
    }
}
