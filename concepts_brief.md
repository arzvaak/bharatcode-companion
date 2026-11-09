# Minecraft BharatCode Companion: builder and engineer

* **Author:** arzvaak
* **Date:** 2026-11-08
* **Status:** Anchored working hypothesis approved for independent brainstorming review. Review findings and formal design specifications are pending.

## Overview

One persistent, owner-bound Minecraft Java companion carries out building and engineering projects through addressed chat and in-game controls. The full-product design should cover phased delivery, beginning with a builder release that supports settlements in single-player, private servers, and public servers. Production and storage integrations come next. The final direction is an expert builder and engineer covering architecture, terraforming, redstone, farms, logistics, and modded factories. Combat and adventure were not selected as the product direction.

Pack-wide ATM10 automation is a long-term research goal. It is not a promise that every mod, machine, or activity can be automated. Each executable capability must be implemented and tested before it is reported as supported.

## Source requirements and authority

Existing sources: [Product scope](PRODUCT.md), [compatibility and features](docs/compatibility-and-features.md), and [interface decisions](docs/interface-decisions.md). They describe design and prototype behavior, not a working mod. No Minecraft integration, AI route, machine adapter, or claim enforcement has been verified in this task.

The decisions below record explicit selections during this brainstorming session. They supersede conflicting older design assumptions for this proposed brief, but the older files have not yet been revised. Other existing requirements remain inputs, including real materials and fuel, normal tool durability, no automatic undo, client-local credentials, server-authoritative validation, owner-logout pausing, and approval invalidation when the site or design changes.

## Goals vs. Non-Goals

### First builder release

- Support single-player, private servers, and public servers. Public-server controls, ownership, abuse limits, and diagnostics are release concerns, not optional later work.
- Include persistent summon/dismiss behavior, addressed chat, controls, job queue, building previews, material collection, portable crafting/smelting, and safe execution.
- Include settlements: approve a layout, then approve each building separately. Buildings may begin with partial supplies under the latest incremental-construction decision.
- Offer both tested building templates and original AI block layouts. Validate layouts on the server and show a ghost preview before approval. Structural validation does not establish functional correctness of an arbitrary contraption.
- Accept requests involving any ATM10 block, but execute only supported placement behavior. Unsupported special blocks and machines become disclosed manual placements, not guessed operations.
- Build supported portions and leave unsupported positions for the player. Keep the job awaiting manual completion until placement is verified. The user's latest decision replaces the earlier whole-building supply gate: construction may start with partial supplies and build whatever can safely proceed within the approved design and removal permissions. Consume only real available materials; do not invent stock or bypass placement dependencies. Materials used by the companion must be in its custody before use, but the entire bill need not be collected upfront. Hand manual-placement items to the player when available and needed; track that custody change separately from consumption and verified placement. Missing materials remain visible as outstanding work. Continue independent build steps and acquire shortages through already-authorized gathering/crafting within the project budget and current permissions; ask before acquisition outside those limits. Ancillary tool, fuel, and scaffolding readiness rules still need specification.
- Remove blocks only through an explicitly approved removal plan. Preview clearing and affected blocks; separately protect machines, inventories, and unknown behavior. Changes after approval pause work.
- Make the companion invulnerable: no damage, death, or item loss. Hazards can block movement and trigger bounded recovery or recall.
- Preserve unlimited gameplay carrying capacity. Page storage and bound transfers and processing rather than imposing a gameplay slot or weight limit. Real item identities, counts, and components remain accounted for. If persistence cannot safely accept more items or an interrupted transfer has an uncertain outcome, pause affected item operations and their dependents. Refuse new intake before taking source items where possible, preserve confirmed holdings, isolate uncertain transfers and require reconciliation. Never silently delete items, invent a balancing quantity, or blindly replay an uncertain transfer. Unaffected work may continue only if it does not depend on the failed storage or disputed items. The technical persistence and reconciliation mechanisms remain to be designed.
- Simulated gathering uses an explicit operator-controlled basic-resource table, tool requirements and durability, quantity-based timers, and server limits. Progression-gated resources are excluded unless explicitly configured.
- Require resource discovery before simulated gathering. Discovery means the owner harvests a natural source or the companion physically observes a verified natural source. Gifts and crafted items do not unlock resources. If natural origin cannot be verified, including in existing worlds without trustworthy provenance, keep the gathering unlock locked and explain the missing evidence; normal player supplies remain usable. Owner or operator attestation alone does not replace qualifying evidence. The technical verification mechanism remains open.
- Dismissal pauses physical jobs, crafting, and transfers while preserving progress and inventory. An already-started simulated trip continues while the owner remains online. Owner logout pauses all work and trip timers.
- If server protection cannot be verified, disable world-changing jobs on that server. Keep planning, chat, previews, and authorized inventory viewing available. Also allow approved internal crafting/smelting using only items already held by the companion, with no placed stations, external containers, transfers, or simulated trips. Pause those excluded actions, including ongoing trip progress, until protection is verified. Owner approval cannot bypass protection. Movement/recall permissions still need specification.
- Keep BharatCode preferred, but permit another explicitly configured and verified client-side AI provider. Credentials stay on the owner's computer. The server validates proposals; alternative providers are not silently selected.

### Next phase

Prioritize production and storage integration: verified recipe readers, selected machine adapters, and AE2/Refined Storage integrations. Existing capability tiers, reservations, machine trust, real energy/fluid/progression constraints, and manual fallback are design inputs rather than tested implementation claims.

### Final engineering direction

- Manage routine actions and recovery within an approved project design, work zone, resource budget, and machine permissions. Ask before destructive changes, expanded scope, or additional spending. Preserve settlement layout and individual-building approval requirements.
- Build and verify functional systems. Commission farms, redstone, and factories against observable expected outputs and safety checks. If tests cannot run, report "built, not verified" rather than completion. Throughput optimization to a guaranteed target was not selected.
- Allow bounded trial and correction directly at the approved site. There is no automatic undo. Exclude high-risk experiments that may explode, spread damage, or affect blocks outside the work zone. Unknown hazard classification must be resolved in the design.
- Let AI propose reusable workflows composed only of fixed server-validated actions, test them within approved budgets, and save successful ones. AI cannot add low-level capabilities, invent permissions, or execute arbitrary generated code. New low-level mechanics require tested integrations.
- Aim to expand toward pack-wide engineering automation through tested capabilities, with explicit unsupported states and player assistance.

### Non-goals and prohibitions

- No mod implementation, installation, server launch, or credential handling is authorized by this documentation task.
- No universal compatibility claim, hidden world lookup, creative material generation, portable machine emulation, protection bypass, or automatic undo.
- No claim that an original AI design works because its blocks can be placed.
- No combat/boss/adventure feature commitment from the selected expert-builder direction.
- No hazardous experiment outside an enforceable safety boundary.

## Target baseline and evidence limits

The selected pack is All the Mods 10. The user requested research of its current stable release. The official [changelog index](https://raw.githubusercontent.com/AllTheMods/ATM-10/main/CHANGELOG.md), fetched on 2026-11-08, lists 8.5 dated 2026-11-04 as its newest entry. Its [8.5 changes](https://raw.githubusercontent.com/AllTheMods/ATM-10/main/changelogs/CHANGELOG-ATM10-8.4-8.5.md) were also fetched. The CurseForge file listing returned only cookie text, so published release status and its exact manifest have not been verified. Treat 8.5 as provisional, not a confirmed stable pin.

The [official pack page](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10) identifies NeoForge and the Minecraft 1.21 family. Search results suggest Minecraft 1.21.1; an exact release manifest, loader build, and mod versions must be verified before implementation-specific contracts are fixed. Older compatibility research includes version-mixed sources and must not be treated as verified ATM10 API contracts.

## Analysis

All seven Goldfish lenses have returned document-review evidence. Independent verification found qualified usable coverage, but the Conductor runs stopped with changes requested rather than passing. The final repair supplied the previously missing developed architecture findings. The parent adopts the verifier's factual qualifications in the [review disposition](docs/goldfish-review-disposition.md). No implementation, API, feasibility, or release-readiness approval follows from these reviews. The subsequent owner change to incremental construction supersedes findings that require a complete-material start gate; that changed policy has not received a fresh independent review.

Known questions to stress-test include the breadth of a first release with settlements and public servers; exact authority for approvals and reusable workflows; crash-safe item accounting; protection identity and revocation; discovery provenance; safe experimentation without undo; and invulnerable, unlimited-storage companions in server economies.

## Alternatives weighed

- The user selected full-product specs with phased delivery instead of first-release-only or interface-only specs.
- The user selected a builder-first release, with production/storage next, instead of a survival-helper or production-first release.
- The user selected settlements in the first release instead of limiting it to individual small structures.
- The user selected templates plus original AI layouts, not template-only construction.
- The user selected capability-checked requests for any ATM10 block with manual completion, rather than vanilla-only placement or waiting for full support.
- Superseding the earlier complete-material gate, the user now permits construction with partial supplies: build what can safely proceed in the approved design. Companion-used materials must be held before use, not necessarily all collected before starting.
- The user selected invulnerability instead of damage, emergency dismissal, or incapacitation.
- The user retained unlimited carrying with bounded processing instead of gameplay inventory caps.
- The user selected natural-source discovery as a prerequisite for simulated gathering; merely receiving a resource does not qualify.
- The user selected disabling world-changing jobs when server protection cannot be verified, rather than allowing owner consent to override unknown protection.
- The user allowed explicitly configured alternative client-side AI providers while retaining client-local credentials.
- The user selected expert building/engineering instead of a general combat-and-adventure companion.
- The user selected project-level routine autonomy, functional verification, and bounded on-site experimentation; high-risk experiments are excluded.
- The user selected AI-authored reusable workflows from supported actions. Arbitrary code execution and invented low-level integrations are outside that choice.

## Key decisions and staging for next steps

1. Run independent read-only Goldfish reviews against this hypothesis and the three source documents. Preserve objections, evidence, and uncertainty.
2. Synthesize findings here, separating explicit user decisions from proposed safeguards and unresolved questions. Do not silently narrow the selected scope.
3. Ask the user to resolve consequential objections and confirm the refined brief.
4. After brief alignment, proceed to formal design drafting under the EGM handoff gate. Proposed spec subjects include release boundaries, execution and approvals, persistence and material accounting, AI/client/server contracts, building and settlements, engineering workflows, UI states, compatibility, server operations, and acceptance scenarios. This list is a proposal, not a completed specification.
