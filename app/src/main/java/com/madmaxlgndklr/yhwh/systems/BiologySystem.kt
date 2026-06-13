package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlin.math.pow

class BiologySystem : GameSystem, PlayerActionHandler, Restorable {

    companion object {
        const val KEY_RES_AMINO_ACIDS = "res_amino_acids"
        const val KEY_RES_PROTEINS = "res_proteins"
        const val KEY_RES_CELLS = "res_cells"
        const val KEY_RES_ORGANISMS = "res_organisms"

        const val KEY_GEN_PREBIOTIC_SOUP = "gen_prebiotic_soup"
        const val KEY_GEN_PROTEIN_SYNTHESIZER = "gen_protein_synthesizer"
        const val KEY_GEN_CELL_DIVISION = "gen_cell_division"
        const val KEY_GEN_ORGANISM_INCUBATOR = "gen_organism_incubator"

        const val KEY_UPG_CATALYST_ENZYME = "upg_catalyst_enzyme"
        const val KEY_UPG_RNA_WORLD = "upg_rna_world"
        const val KEY_UPG_LIPID_MEMBRANE = "upg_lipid_membrane"
        const val KEY_UPG_MULTICELLULARITY = "upg_multicellularity"
        const val KEY_UPG_EVOLUTIONARY_PRESSURE = "upg_evolutionary_pressure"
        const val KEY_UPG_HORIZONTAL_GENE_TRANSFER = "upg_horizontal_gene_transfer"

        const val AMINO_ACID_VISUAL_THRESHOLD = 500.0
        const val CELL_VISUAL_THRESHOLD = 200.0
        const val WIN_THRESHOLD = 1000.0

        private val BASE_TAP_AMINO_ACIDS = BigDouble.ONE
        private val BASE_AMINO_ACIDS_PER_TICK = BigDouble.of(2.0)
        private val INITIAL_EVOLUTION_COST = BigDouble.of(100.0)
    }

    private var evolutionCost = INITIAL_EVOLUTION_COST
    private var firstOrganismFired = false

    override fun initialize(world: World) {
        val planetCount = world.get<ResourceComponent>(CosmologySystem.KEY_RES_PLANETS)
            ?.amount?.toDouble() ?: 0.0
        val prebioticRate = BigDouble.of(1.0 + planetCount * 0.1)

        world.put(KEY_RES_AMINO_ACIDS, ResourceComponent(ResourceType.AMINO_ACIDS, BigDouble.ZERO))
        world.put(KEY_RES_PROTEINS, ResourceComponent(ResourceType.PROTEINS, BigDouble.ZERO))
        world.put(KEY_RES_CELLS, ResourceComponent(ResourceType.CELLS, BigDouble.ZERO))
        world.put(KEY_RES_ORGANISMS, ResourceComponent(ResourceType.ORGANISMS, BigDouble.ZERO))

        world.put(KEY_GEN_PREBIOTIC_SOUP, GeneratorComponent(
            id = KEY_GEN_PREBIOTIC_SOUP, productionType = ResourceType.AMINO_ACIDS,
            productionRate = prebioticRate, costType = ResourceType.ENERGY,
            costAmount = BigDouble.of(10.0), unlocked = true, level = 0
        ))
        world.put(KEY_GEN_PROTEIN_SYNTHESIZER, GeneratorComponent(
            id = KEY_GEN_PROTEIN_SYNTHESIZER, productionType = ResourceType.PROTEINS,
            productionRate = BigDouble.ONE, costType = ResourceType.AMINO_ACIDS,
            costAmount = BigDouble.of(2.0), unlocked = false
        ))
        world.put(KEY_GEN_CELL_DIVISION, GeneratorComponent(
            id = KEY_GEN_CELL_DIVISION, productionType = ResourceType.CELLS,
            productionRate = BigDouble.ONE, costType = ResourceType.PROTEINS,
            costAmount = BigDouble.of(10.0), unlocked = true, level = 0
        ))
        world.put(KEY_GEN_ORGANISM_INCUBATOR, GeneratorComponent(
            id = KEY_GEN_ORGANISM_INCUBATOR, productionType = ResourceType.ORGANISMS,
            productionRate = BigDouble.ONE, costType = ResourceType.CELLS,
            costAmount = BigDouble.of(5.0), unlocked = false
        ))

        world.put(KEY_UPG_CATALYST_ENZYME, UpgradeComponent(
            id = KEY_UPG_CATALYST_ENZYME, purchased = false,
            costType = ResourceType.AMINO_ACIDS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_RNA_WORLD, UpgradeComponent(
            id = KEY_UPG_RNA_WORLD, purchased = false,
            costType = ResourceType.AMINO_ACIDS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_PROTEIN_SYNTHESIZER)
        ))
        world.put(KEY_UPG_LIPID_MEMBRANE, UpgradeComponent(
            id = KEY_UPG_LIPID_MEMBRANE, purchased = false,
            costType = ResourceType.PROTEINS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_CELL_DIVISION, BigDouble.of(2.0))
        ))
        world.put(KEY_UPG_MULTICELLULARITY, UpgradeComponent(
            id = KEY_UPG_MULTICELLULARITY, purchased = false,
            costType = ResourceType.CELLS, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.UnlockGenerator(KEY_GEN_ORGANISM_INCUBATOR)
        ))
        world.put(KEY_UPG_EVOLUTIONARY_PRESSURE, UpgradeComponent(
            id = KEY_UPG_EVOLUTIONARY_PRESSURE, purchased = true,
            costType = ResourceType.CELLS, costAmount = evolutionCost,
            effect = UpgradeEffect.ManualConversion(
                inputType = ResourceType.CELLS,
                inputAmount = evolutionCost,
                outputType = ResourceType.ORGANISMS,
                outputAmount = BigDouble.ONE
            ),
            repeatable = true
        ))
        world.put(KEY_UPG_HORIZONTAL_GENE_TRANSFER, UpgradeComponent(
            id = KEY_UPG_HORIZONTAL_GENE_TRANSFER, purchased = false,
            costType = ResourceType.CELLS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.ReduceConversionCost(
                targetUpgradeId = KEY_UPG_EVOLUTIONARY_PRESSURE,
                multiplier = BigDouble.of(0.5)
            )
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())

        // Passive amino acid generation — guarantees steady progress even without Prebiotic Soup
        resourceComp(world, KEY_RES_AMINO_ACIDS)?.let {
            it.amount = it.amount + BASE_AMINO_ACIDS_PER_TICK * bigDelta
        }

        runGenerator(world, KEY_GEN_PREBIOTIC_SOUP, bigDelta)
        runGenerator(world, KEY_GEN_PROTEIN_SYNTHESIZER, bigDelta)
        runGenerator(world, KEY_GEN_CELL_DIVISION, bigDelta)
        runGenerator(world, KEY_GEN_ORGANISM_INCUBATOR, bigDelta)

        resourceComp(world, KEY_RES_ORGANISMS)?.let { orgs ->
            if (!firstOrganismFired && orgs.amount > BigDouble.ZERO) {
                firstOrganismFired = true
                events.add(GameEvent(0, "Life stirs for the first time.", isMilestone = true))
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
        prodRes.amount = prodRes.amount + gen.productionRate * delta
    }

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)

    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world)
        resourceComp(world, KEY_RES_AMINO_ACIDS)?.let { it.amount = it.amount + tapAmount }
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return
        when (val effect = upg.effect) {
            is UpgradeEffect.ManualConversion -> {
                if (!upg.purchased) return
                val inputRes = resourceComp(world, "res_${effect.inputType.name.lowercase()}") ?: return
                if (inputRes.amount < effect.inputAmount) return
                inputRes.amount = inputRes.amount - effect.inputAmount
                val outputRes = resourceComp(world, "res_${effect.outputType.name.lowercase()}") ?: return
                outputRes.amount = outputRes.amount + effect.outputAmount
            }
            else -> {
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
                evolutionCost = evolutionCost * effect.multiplier
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
        val upg = world.get<UpgradeComponent>(KEY_UPG_CATALYST_ENZYME)
        return if (upg?.purchased == true && upg.effect is UpgradeEffect.MultiplyTapProduction) {
            BASE_TAP_AMINO_ACIDS * (upg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_AMINO_ACIDS
        }
    }

    override fun syncStateFromWorld(world: World) {
        val organisms = world.get<ResourceComponent>(KEY_RES_ORGANISMS)?.amount ?: BigDouble.ZERO
        firstOrganismFired = organisms > BigDouble.ZERO
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val aminoAcids = resourceComp(world, KEY_RES_AMINO_ACIDS)?.amount ?: BigDouble.ZERO
        val proteins = resourceComp(world, KEY_RES_PROTEINS)?.amount ?: BigDouble.ZERO
        val cells = resourceComp(world, KEY_RES_CELLS)?.amount ?: BigDouble.ZERO
        val organisms = resourceComp(world, KEY_RES_ORGANISMS)?.amount ?: BigDouble.ZERO

        val resources = mapOf(
            ResourceType.AMINO_ACIDS.name to aminoAcids,
            ResourceType.PROTEINS.name to proteins,
            ResourceType.CELLS.name to cells,
            ResourceType.ORGANISMS.name to organisms
        )

        val genMeta = mapOf(
            KEY_GEN_PREBIOTIC_SOUP to "Prebiotic Soup",
            KEY_GEN_PROTEIN_SYNTHESIZER to "Protein Synthesizer",
            KEY_GEN_CELL_DIVISION to "Cell Division Chamber",
            KEY_GEN_ORGANISM_INCUBATOR to "Organism Incubator"
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

        val upgMeta = mapOf(
            KEY_UPG_CATALYST_ENZYME to Pair("Catalyst Enzyme", "x2 Amino Acids per tap"),
            KEY_UPG_RNA_WORLD to Pair("RNA World", "Unlock Protein Synthesizer"),
            KEY_UPG_LIPID_MEMBRANE to Pair("Lipid Membrane", "x2 Cells/tick"),
            KEY_UPG_MULTICELLULARITY to Pair("Multicellularity", "Unlock Organism Incubator"),
            KEY_UPG_EVOLUTIONARY_PRESSURE to Pair("Evolutionary Pressure", "100 Cells -> 1 Organism"),
            KEY_UPG_HORIZONTAL_GENE_TRANSFER to Pair("Horizontal Gene Transfer", "-50% Evolution cost")
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

        val epochProgress = if (organisms >= BigDouble.of(WIN_THRESHOLD)) 1f else 0f

        return GameSnapshot(
            tick = tick, epoch = EpochType.BIOLOGY,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList(),
            saveSchemaVersion = 1
        )
    }
}
