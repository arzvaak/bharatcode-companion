# Minecraft BharatCode Companion

<!-- impeccable:product-schema 1 -->

## Platform

web

This platform value describes the standalone HTML design prototype only. The actual product is a Minecraft Java mod; version and loader remain undecided. No mod implementation is authorized in this design task.

## Stack

Delegated: dependency-free HTML/CSS/JavaScript for interface prototypes. User requested several NPC interface screens and delegated further decisions based on the confirmed scope. Actual Java/mod stack remains undecided.

## Users and purpose

Minecraft players, including public-server players, command one persistent player-style NPC through normal chat addressed by name/mention or right-click controls. MineColonies-like embodied work, backed by BharatCode DeepSeek, is the reference interaction. NPC has a custom name, player-style skin, customizable personality, unlimited carrying capacity, and real material accounting.

## Confirmed gameplay

- Walk and place/break blocks at player-like pace with moderate extended reach. Temporary scaffolding reaches high sections and is removed afterward.
- Tools use normal durability; automatically craft replacements from real materials or request missing supplies. No hunger/death upkeep.
- Craft an early-game summoning item; reusable summon/dismiss preserves the same NPC, inventory and progress.
- Simulated resource trips: disappear/teleport to a conceptual resource dimension, return after time based on quantity, rarity and tools. No real dimension generation/mining simulation needed. Basic natural resources only; crafting/smelting produces processed items. Recall returns partial earned materials. Gathering timers continue while dismissed, but pause on owner logout.
- When supplies are missing, ask whether to gather or wait for player supplies. Collect all materials before starting each approved building.
- AI-designed structures, built-in visual blueprint catalog, admin-installed schematics. Save AI designs personally; share through an admin-approved public catalog.
- Ask about design before drafting. Ghost preview; conversational revisions, move/rotate, material swaps, alternative designs. Missing imported blocks produce proposed replacements for approval.
- Renovations, decorations, repairs only on request, demolition with normal drops, functional contraptions, terrain shaping. Known functional blueprints first; original AI contraptions later.
- Whole settlements: approve layout first, then approve each building and its materials separately.
- No rollback history/undo feature selected: stop further work and explicitly request corrections. If site changes after approval, pause and ask before overwriting.
- Queue/reorder/pause/resume/cancel jobs. On a new order during work, ask queue or switch. Try bounded stuck recovery, then ask. Manual recall and stuck-recovery teleport allowed.
- Follow/stay/home; craft/smelt; sort and deliver items. Ask permission for newly discovered containers and remember it.
- Portable crafting/smelting consumes real ingredients and fuel. Read installed recipes including modded recipes. Broad machine automation and large modpack compatibility are core goals, not verified capabilities.
- Explain recipes/progression, inspect nearby world, diagnose builds/farms and offer repair plans for approval. Find resources/locations by physically exploring and remembering, not hidden world lookup. Scouting objective set by player with server distance/chunk limits.
- Remember jobs/progress, named places, building preferences and conversations. Conversational remember/forget controls.
- Right-click job screen, compact in-game HUD, milestone chat messages.

## Multiplayer, privacy and authority

- One companion per owner; trusted players can give orders. Owner logout pauses work. Assigned work zones and server claim/protection permissions restrict all world edits.
- Each player brings their own BharatCode API key. This supersedes an earlier shared server-key choice. Key stays on owner's computer; client contacts AI; server validates NPC actions. Integration route is not yet verified. Trusted players can use owner's allowance only with explicit permission; never receive the key itself.
- Send relevant addressed messages and required task/world/inventory context after setup disclosure; exclude unrelated chat. Do not promise remote provider deletion when forgetting local memory.
- AI failure/quota loss does not stop approved executable jobs. Predefined menu jobs, inventory and basic controls remain usable without AI. Jobs pause if they need a new AI decision.

## Delegated proposed additions

These are coordinator decisions, not historical user selections: expose capability-level compatibility; separate registered-recipe discovery from verified machine adapters; reserve materials for approved jobs; expose ingredient chains, shortages, and production bottlenecks; require permission for unfamiliar machines/storage; do not bypass machine energy/fluid/progression rules through portable crafting. Exact pack/loader/version remains open. No universal compatibility claim.

## Evidence and deliverable boundary

Conversation supplies requirements; illustrative screenshots and prototype data are not a running mod or real server telemetry. No Minecraft build, pathfinding, API integration or mod compatibility has been tested. Current task: expand the feature proposal and produce several reviewable interface prototype screens, not implement the mod.

## Principles

- Physical work and real supplies, with explicitly simulated resource trips.
- AI proposes and interprets; authoritative server code validates and executes permitted actions.
- Ask at consequential boundaries without interrupting every block action.
- Show unsupported capabilities and actionable blockers honestly.
