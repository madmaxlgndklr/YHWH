package com.madmaxlgndklr.yhwh.systems

import com.madmaxlgndklr.yhwh.engine.*
import com.madmaxlgndklr.yhwh.engine.math.BigDouble
import kotlin.math.floor
import kotlin.math.pow

/**
 * Implements all Civilization epoch game logic.
 * Implements [GameSystem] (tick loop + snapshot) and [PlayerActionHandler] (player actions).
 */
class CivilizationSystem : GameSystem, PlayerActionHandler, Restorable {

    companion object {
        const val KEY_RES_FOLLOWERS = "res_followers"
        const val KEY_RES_CULTURE = "res_culture"
        const val KEY_RES_KNOWLEDGE = "res_knowledge"
        const val KEY_RES_CIVILIZATION = "res_civilization"

        const val KEY_GEN_EARLY_SETTLEMENTS = "gen_early_settlements"
        const val KEY_GEN_CULTURAL_EXCHANGE = "gen_cultural_exchange"
        const val KEY_GEN_SCHOLARS_GUILD = "gen_scholars_guild"
        const val KEY_GEN_ENLIGHTENED_SENATE = "gen_enlightened_senate"

        const val KEY_UPG_DIVINE_CALLING = "upg_divine_calling"
        const val KEY_UPG_SOCIAL_ORDER = "upg_social_order"
        const val KEY_UPG_PUBLIC_WORKS = "upg_public_works"
        const val KEY_UPG_CULTURAL_RENAISSANCE = "upg_cultural_renaissance"
        const val KEY_UPG_MEDIEVAL_ERA = "upg_medieval_era"
        const val KEY_UPG_INDUSTRIAL_ERA = "upg_industrial_era"

        const val KNOWLEDGE_VISUAL_THRESHOLD = 200.0
        const val WIN_THRESHOLD = 1000.0
        const val MAX_UNREST = 100f
        const val CRISIS_DURATION = 30

        private val BASE_TAP_FOLLOWERS = BigDouble.ONE
        private val BASE_FOLLOWERS_PER_TICK = BigDouble.ONE
        private val PUBLIC_WORKS_COST = BigDouble.of(50.0)
        private const val PUBLIC_WORKS_REDUCTION = 25f
    }

    private var firstCivilizationFired: Boolean = false
    private var unrestLevel: Float = 0f
    private var civilUnrestActive: Boolean = false
    private var civilUnrestTicks: Int = 0
    private var eraLevel: Int = 0
    private var eraMultiplier: BigDouble = BigDouble.ONE

    override fun initialize(world: World) {
        // Seeding: read Dominance from previous epoch
        val dominance = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)
            ?.amount?.toDouble() ?: 0.0
        val seedingBonus = BigDouble.of(1.0 + floor(dominance / 100.0) * 0.1)

        // Resources — all start at ZERO
        world.put(KEY_RES_FOLLOWERS, ResourceComponent(ResourceType.FOLLOWERS, BigDouble.ZERO))
        world.put(KEY_RES_CULTURE, ResourceComponent(ResourceType.CULTURE, BigDouble.ZERO))
        world.put(KEY_RES_KNOWLEDGE, ResourceComponent(ResourceType.KNOWLEDGE, BigDouble.ZERO))
        world.put(KEY_RES_CIVILIZATION, ResourceComponent(ResourceType.CIVILIZATION, BigDouble.ZERO))

        // Generators
        world.put(KEY_GEN_EARLY_SETTLEMENTS, GeneratorComponent(
            id = KEY_GEN_EARLY_SETTLEMENTS,
            productionType = ResourceType.FOLLOWERS,
            productionRate = seedingBonus,
            costType = ResourceType.DOMINANCE,
            costAmount = BigDouble.ONE,
            unlocked = true, level = 0
        ))
        world.put(KEY_GEN_CULTURAL_EXCHANGE, GeneratorComponent(
            id = KEY_GEN_CULTURAL_EXCHANGE,
            productionType = ResourceType.CULTURE,
            productionRate = BigDouble.ONE,
            costType = ResourceType.FOLLOWERS,
            costAmount = BigDouble.of(2.0),
            unlocked = true, level = 0
        ))
        world.put(KEY_GEN_SCHOLARS_GUILD, GeneratorComponent(
            id = KEY_GEN_SCHOLARS_GUILD,
            productionType = ResourceType.KNOWLEDGE,
            productionRate = BigDouble.ONE,
            costType = ResourceType.CULTURE,
            costAmount = BigDouble.of(10.0),
            unlocked = true, level = 0
        ))
        world.put(KEY_GEN_ENLIGHTENED_SENATE, GeneratorComponent(
            id = KEY_GEN_ENLIGHTENED_SENATE,
            productionType = ResourceType.CIVILIZATION,
            productionRate = BigDouble.ONE,
            costType = ResourceType.KNOWLEDGE,
            costAmount = BigDouble.of(5.0),
            unlocked = true, level = 0
        ))

        // Upgrades
        world.put(KEY_UPG_DIVINE_CALLING, UpgradeComponent(
            id = KEY_UPG_DIVINE_CALLING, purchased = false,
            costType = ResourceType.FOLLOWERS, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.of(2.0))
        ))
        // Social Order: behavioral sentinel (unlocks Cultural Exchange in Task 3+)
        world.put(KEY_UPG_SOCIAL_ORDER, UpgradeComponent(
            id = KEY_UPG_SOCIAL_ORDER, purchased = false,
            costType = ResourceType.CULTURE, costAmount = BigDouble.of(30.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        // Public Works: repeatable sentinel (reduces unrest in Task 3)
        world.put(KEY_UPG_PUBLIC_WORKS, UpgradeComponent(
            id = KEY_UPG_PUBLIC_WORKS, purchased = false,
            costType = ResourceType.CULTURE, costAmount = PUBLIC_WORKS_COST,
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE),
            repeatable = true
        ))
        // Cultural Renaissance: doubles Cultural Exchange production
        world.put(KEY_UPG_CULTURAL_RENAISSANCE, UpgradeComponent(
            id = KEY_UPG_CULTURAL_RENAISSANCE, purchased = false,
            costType = ResourceType.KNOWLEDGE, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyProduction(KEY_GEN_CULTURAL_EXCHANGE, BigDouble.of(2.0))
        ))
        // Medieval Era: era advancement sentinel (Task 4)
        world.put(KEY_UPG_MEDIEVAL_ERA, UpgradeComponent(
            id = KEY_UPG_MEDIEVAL_ERA, purchased = false,
            costType = ResourceType.CIVILIZATION, costAmount = BigDouble.of(50.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
        // Industrial Era: era advancement sentinel (Task 4), requires Medieval Era first
        world.put(KEY_UPG_INDUSTRIAL_ERA, UpgradeComponent(
            id = KEY_UPG_INDUSTRIAL_ERA, purchased = false,
            costType = ResourceType.CIVILIZATION, costAmount = BigDouble.of(200.0),
            effect = UpgradeEffect.MultiplyTapProduction(BigDouble.ONE)
        ))
    }

    override fun tick(world: World, delta: Long): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val bigDelta = BigDouble.of(delta.toDouble())
        val intDelta = delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        // Unrest accumulation (era-aware rate)
        val socialOrderPurchased = world.get<UpgradeComponent>(KEY_UPG_SOCIAL_ORDER)?.purchased == true
        val unrestRate = when (eraLevel) {
            2 -> if (socialOrderPurchased) 0.5f else 1.0f
            1 -> if (socialOrderPurchased) 0.375f else 0.75f
            else -> if (socialOrderPurchased) 0.25f else 0.5f
        }
        unrestLevel = (unrestLevel + unrestRate * intDelta).coerceAtMost(MAX_UNREST)

        // Crisis trigger
        if (unrestLevel >= MAX_UNREST && !civilUnrestActive) {
            civilUnrestActive = true
            civilUnrestTicks = CRISIS_DURATION + 1
            unrestLevel = 0f
            events.add(GameEvent(0, "Civil unrest erupts across the lands!", isMilestone = true))
        }

        // Crisis countdown
        if (civilUnrestActive) {
            civilUnrestTicks = (civilUnrestTicks - intDelta).coerceAtLeast(0)
            if (civilUnrestTicks == 0) {
                civilUnrestActive = false
                events.add(GameEvent(0, "Order restored.", isMilestone = false))
            }
        }

        resourceComp(world, KEY_RES_FOLLOWERS)?.let {
            it.amount = it.amount + BASE_FOLLOWERS_PER_TICK * bigDelta
        }

        val crisisMultiplier = if (civilUnrestActive) BigDouble.of(0.5) else BigDouble.ONE
        runGenerator(world, KEY_GEN_EARLY_SETTLEMENTS, bigDelta)
        runGenerator(world, KEY_GEN_CULTURAL_EXCHANGE, bigDelta, crisisMultiplier)
        runGenerator(world, KEY_GEN_SCHOLARS_GUILD, bigDelta)
        runGenerator(world, KEY_GEN_ENLIGHTENED_SENATE, bigDelta)

        resourceComp(world, KEY_RES_CIVILIZATION)?.let { civ ->
            if (!firstCivilizationFired && civ.amount > BigDouble.ZERO) {
                firstCivilizationFired = true
                events.add(GameEvent(0, "The first great civilization rises.", true))
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
        prodRes.amount = prodRes.amount + gen.productionRate * delta * eraMultiplier * productionMultiplier
    }

    override fun onTap(world: World) {
        val tapAmount = currentTapProduction(world)
        resourceComp(world, KEY_RES_FOLLOWERS)?.let { it.amount = it.amount + tapAmount }
    }

    private fun currentTapProduction(world: World): BigDouble {
        val upg = world.get<UpgradeComponent>(KEY_UPG_DIVINE_CALLING)
        return if (upg?.purchased == true && upg.effect is UpgradeEffect.MultiplyTapProduction) {
            BASE_TAP_FOLLOWERS * (upg.effect as UpgradeEffect.MultiplyTapProduction).multiplier
        } else {
            BASE_TAP_FOLLOWERS
        }
    }

    override fun purchaseUpgrade(world: World, upgradeId: String) {
        // Public Works: repeatable unrest reduction
        if (upgradeId == KEY_UPG_PUBLIC_WORKS) {
            val cultRes = resourceComp(world, KEY_RES_CULTURE) ?: return
            if (cultRes.amount < PUBLIC_WORKS_COST) return
            cultRes.amount = cultRes.amount - PUBLIC_WORKS_COST
            unrestLevel = (unrestLevel - PUBLIC_WORKS_REDUCTION).coerceAtLeast(0f)
            return
        }
        // Medieval era advancement
        if (upgradeId == KEY_UPG_MEDIEVAL_ERA) {
            if (eraLevel >= 1) return
            val upg = world.get<UpgradeComponent>(KEY_UPG_MEDIEVAL_ERA) ?: return
            val civRes = resourceComp(world, KEY_RES_CIVILIZATION) ?: return
            if (civRes.amount < upg.costAmount) return
            civRes.amount = civRes.amount - upg.costAmount
            eraLevel = 1
            eraMultiplier = BigDouble.of(2.0)
            world.get<GeneratorComponent>(KEY_GEN_CULTURAL_EXCHANGE)?.unlocked = true
            world.get<UpgradeComponent>(KEY_UPG_MEDIEVAL_ERA)?.purchased = true
            return
        }
        // Industrial era advancement
        if (upgradeId == KEY_UPG_INDUSTRIAL_ERA) {
            if (eraLevel < 1 || eraLevel >= 2) return
            val upg = world.get<UpgradeComponent>(KEY_UPG_INDUSTRIAL_ERA) ?: return
            val civRes = resourceComp(world, KEY_RES_CIVILIZATION) ?: return
            if (civRes.amount < upg.costAmount) return
            civRes.amount = civRes.amount - upg.costAmount
            eraLevel = 2
            eraMultiplier = BigDouble.of(4.0)
            world.get<GeneratorComponent>(KEY_GEN_ENLIGHTENED_SENATE)?.unlocked = true
            world.get<UpgradeComponent>(KEY_UPG_INDUSTRIAL_ERA)?.purchased = true
            return
        }
        // Normal one-time upgrades
        val upg = world.get<UpgradeComponent>(upgradeId) ?: return
        if (upg.purchased && !upg.repeatable) return
        val costRes = resourceComp(world, "res_${upg.costType.name.lowercase()}") ?: return
        if (costRes.amount < upg.costAmount) return
        costRes.amount = costRes.amount - upg.costAmount
        if (!upg.repeatable) {
            upg.purchased = true
            applyUpgradeEffect(world, upg.effect) // repeatable effects applied in their own handlers above
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
            is UpgradeEffect.ManualConversion -> { /* not used in Civilization */ }
            is UpgradeEffect.ReduceConversionCost -> { /* not used in Civilization */ }
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

    private fun resourceComp(world: World, key: String) = world.get<ResourceComponent>(key)

    override fun syncStateFromWorld(world: World) {
        val medievalPurchased = world.get<UpgradeComponent>(KEY_UPG_MEDIEVAL_ERA)?.purchased == true
        val industrialPurchased = world.get<UpgradeComponent>(KEY_UPG_INDUSTRIAL_ERA)?.purchased == true
        eraLevel = when {
            industrialPurchased -> 2
            medievalPurchased -> 1
            else -> 0
        }
        eraMultiplier = when (eraLevel) {
            2 -> BigDouble.of(4.0)
            1 -> BigDouble.of(2.0)
            else -> BigDouble.ONE
        }
        val civ = resourceComp(world, KEY_RES_CIVILIZATION)?.amount ?: BigDouble.ZERO
        firstCivilizationFired = civ > BigDouble.ZERO
        // unrestLevel and civilUnrestActive reset to 0/false on restore (same as Evolution event reset)
    }

    override fun toSnapshot(world: World, tick: Long): GameSnapshot {
        val followers = resourceComp(world, KEY_RES_FOLLOWERS)?.amount ?: BigDouble.ZERO
        val culture = resourceComp(world, KEY_RES_CULTURE)?.amount ?: BigDouble.ZERO
        val knowledge = resourceComp(world, KEY_RES_KNOWLEDGE)?.amount ?: BigDouble.ZERO
        val civilization = resourceComp(world, KEY_RES_CIVILIZATION)?.amount ?: BigDouble.ZERO
        val dominance = world.get<ResourceComponent>(EvolutionSystem.KEY_RES_DOMINANCE)?.amount ?: BigDouble.ZERO

        val resources = mapOf(
            ResourceType.FOLLOWERS.name to followers,
            ResourceType.CULTURE.name to culture,
            ResourceType.KNOWLEDGE.name to knowledge,
            ResourceType.CIVILIZATION.name to civilization,
            ResourceType.DOMINANCE.name to dominance
        )

        val genMeta = mapOf(
            KEY_GEN_EARLY_SETTLEMENTS to "Early Settlements",
            KEY_GEN_CULTURAL_EXCHANGE to "Cultural Exchange",
            KEY_GEN_SCHOLARS_GUILD to "Scholars Guild",
            KEY_GEN_ENLIGHTENED_SENATE to "Enlightened Senate"
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

        val medievalPurchased = world.get<UpgradeComponent>(KEY_UPG_MEDIEVAL_ERA)?.purchased == true

        val upgMeta = mapOf(
            KEY_UPG_DIVINE_CALLING to Pair("Divine Calling", "×2 Followers per tap"),
            KEY_UPG_SOCIAL_ORDER to Pair("Social Order", "½ Unrest accumulation rate"),
            KEY_UPG_PUBLIC_WORKS to Pair("Public Works", "-${PUBLIC_WORKS_REDUCTION.toInt()} Unrest"),
            KEY_UPG_CULTURAL_RENAISSANCE to Pair("Cultural Renaissance", "×2 Cultural Exchange production"),
            KEY_UPG_MEDIEVAL_ERA to Pair("Medieval Era", "Advance to Medieval Era"),
            KEY_UPG_INDUSTRIAL_ERA to Pair("Industrial Era", "Advance to Industrial Era")
        )
        val upgrades = upgMeta.keys.mapNotNull { key ->
            world.get<UpgradeComponent>(key)?.let { upg ->
                val availableResource = resources[upg.costType.name] ?: BigDouble.ZERO
                val available = when {
                    key == KEY_UPG_INDUSTRIAL_ERA && !medievalPurchased -> false
                    upg.purchased && !upg.repeatable -> false
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

        val epochProgress = (civilization.toDouble() / WIN_THRESHOLD).toFloat().coerceIn(0f, 1f)

        return GameSnapshot(
            tick = tick, epoch = EpochType.CIVILIZATION,
            resources = resources, generators = generators, upgrades = upgrades,
            epochProgress = epochProgress, events = emptyList(),
            unrestLevel = unrestLevel, civilUnrestActive = civilUnrestActive,
            saveSchemaVersion = 1
        )
    }
}
