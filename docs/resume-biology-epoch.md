# Resume Prompt: Biology Epoch Design

## What this is
You are resuming a brainstorming session for the YHWH God Simulator Android project. The Biology epoch design spec has been written and committed. You need to:
1. Run a spec self-review (inline — check for placeholders, contradictions, ambiguity, scope)
2. Ask the user to review the spec at `docs/superpowers/specs/2026-06-10-biology-epoch-design.md`
3. Once approved, invoke `superpowers:writing-plans` to create the implementation plan

## Project context
- Repo: `/home/madmaxlgndklr/Git/sandbox/YHWH`
- Android Kotlin project, Jetpack Compose UI, ECS game engine
- Current game version: `v0.1.11` — Supabase auth + cloud save sync complete
- Only Cosmology epoch is currently playable. Biology is being designed as the second epoch.

## What was designed (all approved by user)
See full spec at `docs/superpowers/specs/2026-06-10-biology-epoch-design.md`

**Summary:**
- Resource chain: Amino Acids → Proteins → Cells → Organisms
- 4 generators: Prebiotic Soup, Protein Synthesizer, Cell Division Chamber, Organism Incubator
- 6 upgrades mirroring Cosmology pattern (tap upgrade, 2 unlocks, multiplier, manual conversion, cost reducer)
- Win condition: 1,000 Organisms
- Planet seeding: each Cosmology Planet gives +10% Prebiotic Soup production rate
- Epoch transition: ViewModel-driven — `dismissEpochTransition()` calls `engine.advanceEpoch(BiologySystem())`
- New `Restorable` interface replaces hardcoded `CosmologySystem` cast in `GameEngine.restore()`
- CosmosCanvas visuals: ocean background color, organic particles, cell membrane rings

## Key files in current codebase
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/GameEngine.kt` — needs `advanceEpoch()` + `Restorable` fix
- `app/src/main/java/com/madmaxlgndklr/yhwh/engine/ResourceType.kt` — needs 4 new resource types
- `app/src/main/java/com/madmaxlgndklr/yhwh/systems/CosmologySystem.kt` — implement `Restorable`
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/GameViewModel.kt` — epoch-aware init + dismissEpochTransition
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/components/CosmosCanvas.kt` — Biology visuals
- `app/src/main/java/com/madmaxlgndklr/yhwh/ui/state/CosmosState.kt` — new fields

## Immediate next step
Run self-review on the spec, commit any fixes, then ask user to review before invoking writing-plans.
