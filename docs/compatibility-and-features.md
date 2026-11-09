# Compatibility and Feature Decisions

**Status:** design research and scope expansion. Not an implementation plan. No Minecraft loader, version, or modpack has been selected. No adapter has been built or tested. No mods were installed, no server was run, and no private configuration or credentials are included.

**Authority:** `PRODUCT.md` is the authoritative scope. This document expands the delegated areas — compatibility, remaining feature decisions, and prototype screen contracts — and records decisions. Where this document and `PRODUCT.md` differ, `PRODUCT.md` wins.

**Companion artifacts:** the standalone prototype at `prototype/index.html` currently implements five screens (Jobs, Build, Blueprints, Production, Settings) with example data; its Production screen demonstrates part of contracts S2 and S3. The contracts in §13 define the compatibility-specific screens S1–S7 for review. This document specifies their content, states, and copy; it does not implement them, and the prototype is not a running mod.

---

## 1. Summary of decisions

The companion NPC works in a world it cannot fully understand. Mods disagree about what a recipe is, how a machine is operated, what a unit of energy means, and who is allowed to touch a block. The design below accepts that and makes the disagreement visible instead of hiding it.

Decisions:

1. Compatibility is reported per world and per job as a **capability report**, not as a pack certification. "Works with All the Mods" is never a claim the product makes. "In this world, I can plan these recipe types, operate these machines, and I am allowed in this claim" is.
2. Every job is classified by three questions: **Is the recipe known? Can the station be operated? Am I allowed?** The answers produce a verdict: `portable`, `machine-assisted`, `network-assisted`, `player-assisted`, `blocked`, or `unknown`.
3. The **server recipe manager is the only authoritative recipe source**. JEI and EMI are display layers. A recipe shown in a viewer is not proof that the NPC can execute it.
4. Mod support is expressed in **capability tiers**: C0 core (vanilla), C1 recipe readers, C2 machine adapters, C3 network adapters, C4 protection adapters, plus a manual fallback. C1 does not imply C2; C3 does not imply C2. Each adapter declares its mod, version range, capabilities, and test status.
5. Ingredient chains are planned as a DAG of typed steps — craft, smelt, machine, network, gather, supply, unknown — with blockers, alternatives, byproducts, containers, and confidence labels. Unknowns are shown as unknowns.
6. Materials are **reserved**, not silently consumed. A reservation ledger prevents two jobs from promising the same stack, survives logout and restart, and reconciles against reality before work starts.
7. Permissions are layered: **world claims**, **network ACLs** (AE2/RS), **NPC trust records** for individual machines and containers, and **owner consent** for trusted players using the owner's AI allowance. No layer can be bypassed by a lower one.
8. Real constraints are honored: Create stress, speed, and heat; Mekanism tiers, gases, fluids, and energy; AE2 channels, energy, CPU, patterns, and security; Refined Storage controller energy, patterns, and security.
9. **Portable crafting has an allowlist.** It may craft crafting-table recipes and run furnace-family smelting with real fuel. It may never emulate a machine, a multiblock, a processing pattern, or a network autocraft.
10. When the NPC cannot proceed, it **asks for supplies or asks the player to run the step**, with exact inputs, outputs, and instructions. It does not improvise a shortcut.
11. The player's BharatCode key stays on the player's computer. The server validates NPC actions and never receives the key. Trusted players can spend the owner's allowance only with explicit, scoped consent. Owner logout pauses all NPC work — AI calls, approved executable jobs, predefined menu jobs, simulated trips, inventory transfers, and reservation clocks. An AI outage while the owner is online is different: approved executable jobs (including trips) and predefined menu jobs continue, and only steps that need a new AI decision pause.
12. Resource trips are **simulated**. There is no generated resource dimension. Trip yields are basic natural resources only — vanilla by default, plus configured tags; modded ores are not assumed. Trips do not return processed items such as ingots; smelting and crafting are separate steps, smelting uses real fuel, and both use real materials.
13. Extra ideas are explicitly classified **Adopt**, **Defer**, or **Reject** in §12, with reasons.
14. The **generic surface comes first**: vanilla recipe types, tags, standard item handlers, and fuel values need no mod-specific code. Adapters are added only where the generic surface cannot see (§4.6). A large pack is one generic core plus a few adapters, not one adapter per mod.

The honest product sentence is: *"I can tell you what I can and cannot do in this world, and I will ask when I don't know."*

---

## 2. Scope, vocabulary, and labels

### 2.1 In scope

- Compatibility architecture for recipe discovery, ingredient planning, material reservations, machine permissions, energy/fluid/progression constraints, and fallback.
- Feature decisions delegated by the user, including the coordinator's proposed additions in `PRODUCT.md` §"Delegated proposed additions".
- Interface contracts for reviewable prototype screens.
- Compatibility acceptance scenarios that can be run later on a test server.

### 2.2 Out of scope

- Choosing a loader, Minecraft version, or modpack.
- Building or installing mods, adapters, a server, or a client.
- Reading private configs, credentials, or server files.
- Claiming that any named pack, including All the Mods, is compatible.
- Real dimension generation or real mining simulation.
- A universal machine API or reflection-based GUI automation.

### 2.3 Vocabulary

| Term | Meaning in this document |
|---|---|
| Registered recipe | A recipe loaded by the server's recipe manager under a recipe type and serializer. Datapacks and mods add these. |
| Recipe viewer | JEI, EMI, or REI. A client-side display of items and recipes. Not an execution API. |
| Adapter | Code that reads a mod family's recipes, operates its machines, or checks its permissions. Proposed and untested unless stated. |
| Capability tier | C0–C4 or CM. Describes what the NPC can do in a world, not which mods are installed. |
| Machine step | A recipe step that requires a block, multiblock, energy, fluid, gas, or catalyst outside the NPC's portable stations. |
| Ingredient chain | A directed acyclic graph of steps from raw or stored resources to the target item. |
| Reservation | A soft claim on specific resources for an approved job. It does not move items by itself. |
| Trust record | An NPC-local permission to read, insert into, extract from, or craft with a specific block, block type, container, or network. |
| Claim check | A world-protection check before an edit or interaction. |
| Verdict | The per-job or per-step classification: portable, machine-assisted, network-assisted, player-assisted, blocked, unknown. |
| Portable | Allowed in the NPC's own crafting grid or furnace-family station, using real ingredients and fuel. |
| Generic surface | What the NPC can read or do without mod-specific code: vanilla recipe types, tags, standard item handlers, fuel values, block presence. |
| [D] | Documented in a fetched source. |
| [P] | Proposed adapter or behavior. Not implemented, not tested. |
| [O] | Open question or unknown. |

---

## 3. Evidence base and what it does not establish

Sources were fetched on 2026-11-07. They are listed in §16. Most are developer wikis, official guides, or official documentation sites. The Mekanism wiki is the official community wiki and is version-caveated.

### 3.1 What the evidence supports

| Area | Documented mechanism | Source |
|---|---|---|
| JEI | Recipe display plugin; mods register recipe categories and wrappers for viewing. | [S2] |
| EMI | Client-side API; recipes, workstations, catalysts, recipe tree, and `supportsRecipeTree`. | [S4] |
| Create | Stress capacity vs. impact and speed scaling; basins auto-run valid recipes; mixer needs 32 RPM minimum; press compacts; crafter assembles recipes; fan bulk processing can incinerate items. | [S5]–[S11] |
| Mekanism | Four machine tiers; factories with 3/5/7 slots; ore processing tiers from 1× to 5× using gases, fluids, and multiblocks. | [S23], [S24] |
| AE2 | Channels (8 normal, 32 dense, 8 ad-hoc) with configurable channel modes; FE→AE conversion; network energy buffer; autocrafting CPU/pattern provider; security terminal permissions. | [S12]–[S15] |
| Refined Storage | Controller energy; per-tick consumption; pattern-per-intermediate autocrafting; processing patterns; Security Manager permissions. | [S16]–[S19] |
| FTB | Chunk claims; player and server teams; block breaking/placing/interaction settings; permission nodes. | [S20]–[S22] |

### 3.2 What the evidence does not establish

- That any adapter works. No code exists.
- That APIs are stable across Minecraft versions, loaders, or mod updates.
- That a pack uses default configs. Energy, channels, claims, and security can be disabled or changed.
- That JEI or EMI presence gives the server recipe knowledge. EMI's API is explicitly client-side [S4]; JEI's documented integration surface is a display plugin [S2].
- That AE2, Refined Storage, or FTB expose a stable third-party API for the exact queries the NPC needs. The docs describe player-facing behavior, not a public query contract.
- That Create or Mekanism expose a stable machine-automation capability. Their docs describe machine behavior, not an NPC-facing API.
- That All the Mods, or any named pack, is compatible. Pack composition and versions vary by release.
- That a recipe viewer's displayed recipe is executable on the server. Viewer data can include recipes the server cannot run, and can omit server-side requirements.

### 3.3 Why no loader or version is chosen

Adapter code is version- and loader-specific. Choosing a version now would make the design look more precise than the evidence allows. The design therefore defines **capability contracts** and **adapter declarations**, and leaves the concrete API mapping to the implementation phase, after a pack and version are selected.

---

## 4. Compatibility model

### 4.1 Capability tiers

| Tier | Name | What the NPC can do | What it needs | Example | Status |
|---|---|---|---|---|---|
| C0 | Core | Read vanilla recipe types; craft and smelt portably; place/break blocks; use inventories and containers with permission. | Server recipe manager, item tags, block capabilities. | Planks → sticks; raw iron → iron ingot in a furnace. | Baseline; no mod adapter. |
| C1 | Recipe reader | Read a mod's recipe types and expose them as machine steps; no machine operation. | Per-mod recipe-type adapter. | Read Create mixing recipes; read Mekanism enrichment recipes. | Proposed [P], untested. |
| C2 | Machine adapter | Insert inputs, extract outputs, read machine state, wait for completion, respect energy/fluid/gas. | Per-mod and per-version block capability, plus integration tests. | Operate a Mekanism Enrichment Chamber. | Proposed [P], untested. |
| C3 | Network adapter | Query stock, request autocrafting, reserve logical stock, respect network security. | AE2/RS API or documented device interaction, plus security checks. | Request 64 iron ingots from an AE2 network. | Proposed [P], untested. |
| C4 | Protection adapter | Check claim ownership and block-interaction permissions before edits. | FTB Chunks/Teams API or equivalent. | Check an FTB claim before placing a block. | Mechanics documented [D]; adapter untested [P]. |
| CM | Manual fallback | Ask the player to run a step or supply items; provide exact instructions. | Chat and screen UI. | Player runs the Mechanical Mixer. | Always available. |

Tier rules:

- **C1 does not imply C2.** Knowing a machine's recipes does not mean the NPC can operate the machine. A C1-only job is `player-assisted` for machine steps.
- **C3 does not imply C2.** A network can autocraft an item without the NPC understanding the machine inside the network. The network's patterns are the contract.
- **C2 without C1 is narrow.** The NPC can operate a known machine but cannot discover new recipes for it.
- **C4 is checked before every edit or interaction**, not only at job approval.
- **CM is always available.** No job should dead-end without an actionable request.

### 4.2 Per-job and per-step verdicts

| Verdict | Meaning | Typical UI treatment |
|---|---|---|
| `portable` | All steps can be done with portable stations and real ingredients/fuel. | Green; "I can do this myself." |
| `machine-assisted` | At least one step needs a machine the NPC can operate (C2), or the player can run it (CM). | Amber; shows machine cards. |
| `network-assisted` | At least one step is a network autocraft request (C3). | Blue; shows network, pattern, and permission. |
| `player-assisted` | At least one step requires the player to run a machine or supply items. | Amber; shows a request or step card. |
| `blocked` | A known hard blocker: missing permission, claim denial, unsupported recipe type, insufficient energy. | Red; explains the blocker and alternatives. |
| `unknown` | The recipe type, machine, or permission state cannot be determined. | Grey; asks the player; never guesses. |

A job has one overall verdict; each step also carries its own. The overall verdict is the worst step verdict, with `unknown` treated as worse than `player-assisted` but better than `blocked` only when the player can resolve it. The screen must show both, because a job can be "machine-assisted, one step unknown."

### 4.3 Capability report

The NPC reports capabilities per world:

- Recipe knowledge: C0 available; which mod recipe readers are loaded and their status.
- Machines: which machine families are detected nearby or in the work zone; whether the adapter is absent, read-only, operational, or failed.
- Networks: AE2/RS detected; security state; energy state; whether the NPC has a trust record.
- Protection: FTB claim status and team permissions; other protection systems unknown.
- Unsupported: recipe types and machines with no adapter, with a reason.
- Last checked: timestamp and reload generation.

The report is refreshed on world load, datapack reload, mod list change, and on demand. It is cached per world and invalidated when the server reports a reload.

In a large kitchen-sink pack such as an All the Mods release, the expected first-run report is mostly `unknown`: vanilla recipes work, most mod recipe types have no adapter, and the unsupported list is long. That is the honest result, not a failure. The UI should summarize it ("I can plan vanilla crafting and smelting; I can read Create recipes if the adapter loads; everything else is a player-run step") instead of listing a hundred rows first.

### 4.4 Adapter contract

Every adapter declares:

| Field | Purpose |
|---|---|
| `adapterId`, `modId`, `modName` | Identity. |
| `versionRange`, `minecraftRange`, `loader` | What it was written against. |
| `tier` | C1, C2, C3, or C4. |
| `recipeTypes[]` | Recipe types read (C1). |
| `capabilities[]` | Operations: read stock, insert, extract, craft, check claim, check permission. |
| `permissionsRequired[]` | What the NPC must be allowed to do. |
| `knownLimitations[]` | Documented gaps, e.g., "cannot read multiblock internals." |
| `testStatus` | `untested`, `unit`, `integration`, `pack-tested`. |
| `evidenceLinks[]` | Documentation used. |
| `lastChecked` | Health check timestamp. |

An adapter that fails its health check is downgraded, not silently skipped. Steps that depended on it become `unknown` or `player-assisted`.

### 4.5 Degradation rules

1. Adapter missing → the recipe type is `unknown`; offer a player-run step.
2. Adapter present but health check fails → mark `adapter-failed`; do not attempt operation; offer manual fallback.
3. Permission denied → `blocked` with the specific permission and who can grant it.
4. Claim denied → `blocked` with the claim owner/team and a request to move the zone or grant access.
5. Energy/fluid below reserve → pause the job before starting the step; ask for resupply.
6. Recipe reload changes the plan → invalidate reservations for unstarted steps; re-preflight; ask before continuing.
7. Owner logout → freeze all execution, not only AI-dependent steps: AI calls, approved executable steps, predefined menu jobs, simulated trips, inventory transfers, and reservation clocks. Controls stay visible for review, but nothing executes until the owner logs back in.
8. AI failure or quota loss while the owner is online → approved executable steps (including gathering trips) and predefined menu jobs continue; a step that needs a new AI decision pauses. Inventory access and basic controls remain usable.

### 4.6 Generic surface versus adapter surface

The NPC should use the largest surface that needs no mod-specific code, and add adapters only where the generic surface cannot see. This is the practical meaning of broad modpack compatibility: a pack with fifty mods is not fifty adapters; it is one generic core plus adapters for the few systems the job actually touches.

| Surface | Generic (no mod-specific code) | Adapter required | Why |
|---|---|---|---|
| Recipes | Vanilla recipe types; tags; fuel values; item/fluid stacks and components | Mod recipe types, multiblock recipes, gases, catalysts | A mod recipe type has its own schema; guessing it is how the NPC invents recipes. |
| Stations | Crafting table, furnace family, campfire | Create/Mekanism machines, multiblocks, networks | The generic surface has no concept of rotation, stress, heat, energy, or gas. |
| Stock | Block inventories with a standard item handler; player inventory | AE2/RS logical stock; machine internal buffers | Network stock is logical and secured; machine buffers are not standard containers. |
| Permissions | None generic | FTB Chunks/Teams; AE2/RS security | Protection is mod-specific and must be queried, not inferred. |
| Progress | Recipe presence, block presence, observed tier | Quest/stage gates | Gates are pack-specific and often invisible to the server API. |

A generic datapack JSON reader is a tempting middle path: read the recipe JSON files a mod ships and parse the common `type`/`ingredients`/`result` shape. It is proposed [P] and unreliable. Mod recipe schemas differ, and a successful parse does not prove the server registered the recipe. It can be a diagnostic aid ("I found a file that looks like this recipe"), never an execution path. It does not upgrade C1.

The rule: **generic first, adapter second, ask third.** A step that the generic surface cannot see is `unknown` or `player-assisted` until an adapter is declared and tested.

---

## 5. Recipe discovery

### 5.1 Server truth versus viewer display

The server's recipe manager is authoritative. It knows which recipe types are registered, which recipes are loaded, and what their inputs and outputs are. The NPC plans from that data.

JEI and EMI are viewers:

- EMI's API is "completely client side" and is an abstraction layer over vanilla recipes for display and recipe trees [S4].
- JEI's documented integration path is a display plugin: mods register recipe categories and wrappers so players can view them [S2].
- Neither is documented as a server-side machine-automation or recipe-execution API.

Consequences:

- A recipe visible in JEI/EMI is **not** proof that the NPC can craft it.
- The NPC must not require JEI/EMI to be installed on the server.
- If the player's client has a viewer, the NPC may reference it in conversation ("you can see this in JEI"), but the plan comes from the server.
- A viewer-only recipe with no server recipe type is `unknown`, not portable.

### 5.2 What can be read generically (C0)

- Vanilla crafting (shaped, shapeless), smelting, smoking, blasting, campfire cooking, stonecutting, and smithing recipe types, subject to the portable boundary in §10.
- Item tags and fluid tags for alternatives.
- Item and fluid stacks, counts, and components/NBT.
- Block inventories and simple container capabilities.
- Fuel values for furnace-family stations, read from the server.

C0 does not include mod machine recipes, mod multiblocks, gases, or network autocrafting.

### 5.3 Modded recipe types (C1)

A C1 adapter reads a mod's recipe type and converts it into planner steps. It must:

- Identify the recipe type and serializer.
- Extract inputs and outputs, including fluids, gases, and catalysts.
- Extract station requirements (block, multiblock, tier).
- Extract energy/heat requirements where documented.
- Mark chance-based outputs and byproducts.
- Declare what it cannot read.

If a recipe type is not covered by an adapter, the NPC says so. It does not guess a conversion from a similar mod.

### 5.4 Tags, alternatives, and NBT

- Ingredients may be tags ("any plank"). The planner resolves tags against the server tag manager and shows the alternatives.
- The NPC chooses one alternative deterministically (see §6.3) and tells the player which it chose.
- NBT/components matter. Two items with the same ID but different components are different resources. The planner must not substitute silently.
- Fluid and gas amounts use the unit the mod uses (usually mB). Do not convert between units without a documented ratio.

### 5.5 Reloads and datapacks

Recipe reloads, datapack changes, and mod updates can invalidate plans and reservations. On reload:

- Invalidate unstarted plans.
- Recompute reservations against the new recipes.
- If a reserved item is no longer an ingredient, release it.
- If a started step's recipe disappeared, pause and ask; do not continue with stale data.

### 5.6 Honesty rules

- The NPC never invents a recipe. AI proposals are validated against the server recipe manager before execution.
- If the AI proposes a recipe that the server does not have, the NPC says it cannot verify the recipe and asks.
- The NPC distinguishes "I know this recipe," "I have an adapter that claims to know it," and "I am guessing." It does not guess.

---

## 6. Ingredient-chain planning

### 6.1 Step types

| Step | Meaning | Execution |
|---|---|---|
| `craft` | Portable crafting-table recipe. | NPC crafts with real items. |
| `smelt` | Portable furnace-family recipe. | NPC consumes real fuel. |
| `machine` | Recipe requires a machine, multiblock, energy, fluid, gas, or catalyst. | C2 operates; otherwise player-assisted. |
| `network` | Request an autocraft from AE2/RS. | C3 requests with permission. |
| `gather` | Simulated resource trip for basic natural resources only. | Timer; partial recall; no dimension; a processed item needs a separate `smelt`/`craft` step. |
| `supply` | Ask the player for items. | Player delivers; NPC verifies. |
| `unknown` | Recipe type or station not understood. | Ask; never execute. |

### 6.2 Planner rules

1. Start from the target item and quantity.
2. Prefer existing stock before crafting. Show stock, reserved, and free amounts.
3. Expand recursively. For each ingredient, choose a recipe according to §6.3.
4. Apply the portable test (§10) to every recipe. If it fails, the step is `machine`, `network`, `supply`, or `unknown`.
5. A machine step is executable only if a C2 adapter exists and the machine is present, permitted, and powered. Otherwise it becomes `player-assisted`.
6. A network step is executable only if a C3 adapter exists, a pattern exists, the NPC has permission, and the network has energy/channels. Otherwise `supply` or `blocked`.
7. Gather steps are allowed only for basic natural resources in the configured simulated-trip table; a trip never returns a processed item such as an ingot. A processed target is a gather step for its raw resource followed by an explicit `smelt` step with real fuel or a `craft` step, or a `supply` step. A resource outside the table is `supply`.
8. Detect cycles. If a cycle is unavoidable, mark `unknown` and ask; do not loop.
9. Credit byproducts to the plan. Do not count a byproduct as free input unless the recipe guarantees it.
10. Catalysts are not consumed. Containers are returned (bucket, bottle, etc.) and tracked.
11. Chance outputs use an expected count and a guaranteed minimum; the UI must show both.
12. Cap the plan: maximum depth 12, maximum 400 steps, 2-second planning budget. Beyond the cap, ask the player to narrow the job. Numbers are configurable.
13. Label every step with confidence: `documented`, `estimated`, or `unknown`.

### 6.3 Determinism and alternatives

When multiple recipes produce the same item, choose in this order:

1. The recipe the owner explicitly chose for this job.
2. The recipe used by an approved blueprint or catalog entry.
3. The recipe with the fewest missing resources.
4. The recipe with the lowest estimated energy/time cost.
5. Lexicographic recipe ID, as a stable tie-breaker.

The UI shows the alternatives and lets the owner change the choice before approval. The NPC never silently switches recipes mid-job.

### 6.4 Byproducts, catalysts, containers, and chance

- **Byproducts:** credited to the job's output ledger; may satisfy later steps or be returned to the owner.
- **Catalysts:** required at the station but not consumed; the planner checks presence, not quantity.
- **Containers:** a bucket, bottle, or cell returned by a recipe is tracked and reserved for return.
- **Chance:** "expected 2, guaranteed 1" is shown as two numbers. The job does not promise the expected amount.
- **Leftovers:** a basin or machine may retain leftovers; the planner does not assume the machine is empty. It checks or asks.

### 6.5 Blocked states

A plan is blocked when:

- A required recipe type has no adapter and no player-run path.
- A required machine is missing, unpowered, or not permitted.
- A required ingredient is unavailable and cannot be gathered or supplied.
- A claim denies the work zone.
- The plan exceeds caps.
- The owner has not approved the job.

Blocked is not failure. It is a state with a reason and an action.

### 6.6 Re-planning triggers

- Stock change beyond a threshold.
- Machine removed, unpowered, or replaced.
- Permission or trust revoked.
- Claim changed.
- Recipe reload.
- Owner logout/login.
- A step fails or times out.
- The player supplies a partial amount.

On re-plan, preserve reservations that are still valid; release the rest; show a diff of what changed.

### 6.7 Worked example: Create brass

Target: 4 brass ingots.

Documented facts:

- Brass mixing requires a heated or super-heated Blaze Burner under the basin [S6].
- A Mechanical Mixer needs a Basin one block below, at least 32 RPM, and 4 SU per RPM [S8].
- Stress capacity must be at least total impact, and impact scales with speed [S5].

Plan:

1. Resolve the brass mixing recipe from the server. Do not guess the copper/zinc ratio; read it.
2. Check stock for copper and zinc ingots. If missing, add a gather step for raw ore followed by a smelt step with real fuel, or a supply step.
3. Machine step: Mechanical Mixer + Basin + heated Blaze Burner.
4. Check rotation: a power source with enough SU at the required speed. If insufficient, add a "power" blocker and ask the owner to add capacity or lower speed within the mixer's minimum.
5. Check heat: Blaze Burner present and fed. If not, add a fuel/supply step.
6. Output: brass ingots; check output space and container.
7. Verdict: `machine-assisted` if C2 exists; `player-assisted` otherwise. It is never `portable`.

The NPC must not "craft brass" in its own grid. That would bypass the mixer, the heat, and the stress system.

### 6.8 Worked example: Mekanism 3× ore processing

Target: 3 ingots from 1 raw ore (Tier 2).

Documented chain [S24]:

1. Raw ore + oxygen → Purification Chamber → 2–3 clumps.
2. Clumps → Crusher → dirty dusts.
3. Dirty dusts → Enrichment Chamber → dusts.
4. Dusts → Energized Smelter → ingots.
5. Oxygen comes from water → Electrolytic Separator, with an Electric Pump.

Plan implications:

- The planner must include the oxygen sub-chain, not just the ore machines.
- Each machine needs energy; the planner estimates and checks it.
- Machine tiers and upgrades affect throughput, not the recipe chain.
- If the NPC has C1 but not C2, it shows four player-run machine steps with exact inputs and outputs.
- Portable smelting yields 1×, not 3×. The NPC must not offer portable smelting as an equivalent.

---

## 7. Material reservations

### 7.1 What a reservation is, and is not

A reservation is a soft claim on a quantity of a resource for an approved job. It prevents two jobs from promising the same stack. It does not move items, does not prove the items exist at execution time, and does not grant permission to open a container.

### 7.2 Ledger

| Field | Meaning |
|---|---|
| `reservationId` | Unique ID. |
| `jobId` | Approved job. |
| `resourceKey` | Item/fluid/gas ID plus components/NBT. |
| `amount`, `unit` | Quantity and unit (item count, mB). |
| `location` | NPC inventory, container position, network ID, or tank. |
| `state` | `proposed`, `reserved`, `extracted`, `consumed`, `released`, `stale`. |
| `createdAt`, `expiresAt` | Timestamps. |
| `approvedBy` | Owner or delegated player. |
| `evidence` | How the amount was determined. |

### 7.3 Lifecycle

1. **Plan:** reservations are `proposed`; they do not block other jobs.
2. **Approve:** the owner approves the job; reservations become `reserved`; revalidate against stock and permissions.
3. **Start:** if the resource is in a network or shared container, extract it to the NPC's inventory or a dedicated buffer, subject to permission. Mark `extracted`.
4. **Execute:** consume as steps run; record actual amounts; return containers and byproducts.
5. **Complete:** release the remainder; close the ledger.
6. **Cancel/fail:** release all uncommitted reservations; keep an audit record.
7. **Logout:** freeze all execution, not only AI steps; pause expiry clocks; do not consume.
8. **Restart:** reconcile with actual inventories; if a reservation cannot be reconciled, mark `stale` and pause the job.

### 7.4 Conflicts and priority

- Sum of reservations at a location must not exceed available stock.
- Owner-approved jobs are served FIFO. Trusted-player jobs cannot preempt an owner job.
- The owner can override priority, but the UI must show which job loses its reservation.
- Partial reservations are allowed; a job waits until its full requirement is reserved or supplied.
- A reservation that has not started within its expiry window is released and the owner is notified.

### 7.5 Networks and containers

- AE2/RS stock is logical. A reservation there is advisory until extracted. At job start, extract to the NPC's buffer if permitted; otherwise pause and ask.
- Network security applies: AE2 security terminal permissions and RS Security Manager permissions [S15], [S19]. A reservation does not override them.
- Network energy reserve applies before extraction or autocrafting (§9.5).
- Containers in unloaded chunks are `unknown`; the NPC does not assume contents.

### 7.6 Logout, crash, and reconciliation

- Owner logout freezes reservations and pauses expiry. It does not release them, because the owner may log back in and continue.
- On login, reconcile: check each reservation against the recorded location and amount. If the world changed, show a diff and ask.
- On server crash, the ledger is reloaded from persistent storage. Stale entries are marked, not silently dropped.
- If the NPC died or was recalled, its inventory is checked before reconciliation; missing items are reported, not conjured.

### 7.7 Anti-patterns

- Reserving without owner approval.
- Holding reservations indefinitely after a job stalls.
- Treating `reserved` as `consumed`.
- Extracting from an owner's network without permission.
- Silently using another job's reserved materials.
- Promising a chance output as guaranteed.

---

## 8. Machine permissions and protection

### 8.1 Layers

| Layer | Question | Documented examples |
|---|---|---|
| World protection | May I edit or interact here? | FTB Chunks claims; FTB Teams block breaking/placing/interaction [S20], [S21]. |
| Network ACL | May I use this storage/crafting network? | AE2 security terminal; RS Security Manager [S15], [S19]. |
| NPC trust | Have I been allowed to use this machine/container? | Proposed [P]; no universal mod ACL. |
| Owner consent | May this trusted player spend my AI allowance? | `PRODUCT.md`; proposed flow [P]. |
| Server permission | Is the action allowed by the server? | Server-side validation; operator config. |

A lower layer never overrides a higher one. A trust record does not grant claim access. A claim does not grant network access. An owner consent does not grant machine trust.

### 8.2 Documented ACLs

**AE2 security terminal** [S15]:

- Network-level permissions: deposit, withdraw, craft, build, security.
- The placing player has full control and cannot exclude themselves.
- A blank biometric card sets default behavior for unregistered players.
- The security terminal does not prevent physical tampering; claims or other protection are still needed.

**Refined Storage Security Manager** [S19]:

- Network-wide restriction once placed, including for the placing player until cards are inserted.
- Permissions: Insert, Extract, Autocrafting, Open, Build, Security.
- A Fallback Security Card defines behavior for players without a card.
- Multiple managers: an action is denied if any manager blocks it.
- Operators bypass security.

**FTB Chunks / Teams** [S20], [S21], [S22]:

- Claims protect chunks; team members and allies can be allowed to access/build.
- Team settings control mob griefing, block breaking, block placing, and block interaction.
- FTB XMod Compat exposes nodes such as `ftbchunks.max_claimed`, `ftbchunks.no_wilderness`, and `ftbchunks.chunk_load_offline`.
- FTB Teams also supports **server teams**, typically used with FTB Chunks for server-level protection. A public server may protect land through a server team rather than a player team [S21].

**Create and Mekanism**: the fetched documentation describes machine behavior, not a per-machine ownership or ACL system. The NPC must not assume one exists. This is the main reason for the NPC trust layer.

### 8.3 NPC trust model

A trust record grants a specific action on a specific target:

| Field | Meaning |
|---|---|
| `trustId` | Unique ID. |
| `scope` | `block` (position + dimension), `type` (block ID), `container`, or `network`. |
| `actions` | `read`, `insert`, `extract`, `craft`. |
| `grantedBy` | Owner or delegated player. |
| `createdAt`, `expiresAt` | Timestamps. |
| `status` | `active`, `revoked`, `expired`, `invalid`. |
| `reason` | Why it was granted. |

Rules:

- First use of an unfamiliar machine or container prompts the owner.
- The default grant is narrow: this block, this job. The owner can promote to block type or network.
- A block replaced by a different block invalidates the trust record.
- Revocation pauses any job using the target; it does not silently continue.
- Trusted players inherit the owner's existing trust records for machines, but cannot create new trust records unless the owner delegates that permission.
- The NPC never uses a machine it has no trust record for, even if the machine is in a claim the owner controls.

### 8.4 Trusted players and the owner key

`PRODUCT.md` requires: one companion per owner; trusted players can give orders; the key stays on the owner's computer; the server validates actions; trusted players can use the owner's allowance only with explicit permission and never receive the key.

Proposed flow [P]:

1. Trusted player issues an order in chat or via the NPC screen.
2. Server checks the player's trusted status, claim access, and job permissions.
3. If the order needs AI, the server asks the owner's client for consent.
4. The owner's client shows the request with scope: one job, a time window, or until revoked; estimated allowance; what the AI will see.
5. On approval, the owner's client makes the AI call with the owner's key. The key never goes to the server or the trusted player.
6. The AI proposal returns to the server. The server validates it against recipes, permissions, inventory, and claims before executing.
7. The audit log records who ordered, who consented, what was executed, and what was refused.

Context sent to the AI is limited to the addressed message, the current job and step, relevant inventory/container summaries, and the capability snapshot. Unrelated chat is excluded. When the owner forgets a local memory, the NPC says that this removes the local record; it does not promise deletion from the remote provider.

If the owner is offline:

- All work pauses, not only AI-dependent work: AI calls, approved executable steps, predefined menu jobs, simulated trips, and inventory transfers. No AI call is queued to run later without consent.
- The menu and basic controls stay visible so a trusted player can read status or prepare an order, but selecting them does not execute work until the owner logs back in.
- Gathering timers and reservation expiry clocks pause on owner logout, per `PRODUCT.md`.

AI failure or quota loss while the owner is online is a different case: approved executable steps (including trips) and predefined menu jobs continue, and only a step that needs a new AI decision pauses.

### 8.5 Claims and work zones

- A job has an assigned work zone. Every edit checks the claim at the target block, not just the zone center.
- FTB Chunks is the documented example: claims protect chunks, teams and allies can be granted access, and block interaction is a separate permission [S20], [S21].
- If the claim owner is the owner or an allowed team/ally, proceed.
- If the claim is unknown or another player's, deny and ask. Never test the claim by attempting the edit.
- A server-team claim is not a player claim. If the protection belongs to a server team or an unrecognized team, ask the owner or an operator; do not infer access from the block owner.
- Wilderness rules may forbid building; check server config where documented, otherwise ask.
- Moving the work zone is an owner decision, not an NPC workaround.

### 8.6 Destructive operations

Some documented machines can destroy items or change the world in ways the player may not expect:

- Create's Encased Fan bulk blasting can incinerate incompatible items [S10].
- Emptying a tank or network can remove another player's stock.
- Breaking a block with drops can change a build.
- Demolition is in scope only on request, with normal drops [S1].

Rules:

- Destructive steps require an explicit confirmation that names the risk.
- The NPC does not "test" a machine with the owner's valuable items.
- The NPC does not empty a network or tank without a specific, scoped permission.
- The NPC stops and asks if the world changed after approval, per `PRODUCT.md`.

### 8.7 Audit log

Every permission decision is logged: claim check, network ACL check, trust grant/revoke, owner consent, and refusal. The log is visible to the owner and to operators. It records the reason, not just the result.

---

## 9. Real energy, fluid, and progression constraints

### 9.1 Create

Documented [S5]–[S11]:

- Stress capacity must be at least total stress impact. If impact exceeds capacity, the system overstresses and all components stop.
- Impact and capacity scale with speed. A water wheel at 5 RPM gives 20 SU; at 10 RPM, 40 SU.
- Mechanical Mixer: 4 SU per RPM, minimum 32 RPM, needs a Basin below.
- Mechanical Press: compacts or presses at a constant speed regardless of power; above a Basin it compacts.
- Mechanical Crafter: needs rotation; assembles recipes; invalid recipes are ejected.
- Encased Fan: 2 SU per RPM as a component; bulk washing/smoking/blasting requires water/fire/lava/heat and can incinerate incompatible items.
- Basin: holds up to 16 of each item and 1000 mB of each fluid; brass mixing requires a heated or super-heated Blaze Burner.

NPC implications:

- The planner computes required SU at the intended speed and compares it to available capacity.
- The NPC checks the minimum RPM for the machine; it cannot "slow down" a mixer below 32 RPM.
- The NPC checks heat and fuel for heated recipes.
- The NPC cannot hand-crank a Create machine. A machine step is never portable.
- If stress is insufficient, the NPC asks for more generators or a lower-impact setup; it does not start and hope.

### 9.2 Mekanism

Documented [S23], [S24]:

- Four tiers: basic, advanced, elite, ultimate. Factories process 3, 5, and 7 stacks at once in basic, advanced, and elite tiers.
- Ore processing tiers: 1× smelting, 2× enrichment, 3× purification + crusher + enrichment + smelter, 4× chemical injection, 5× dissolution/washer/crystallizer.
- Higher tiers require gases and fluids: oxygen, hydrogen chloride, sulfuric acid, water.
- Tier 3 and 4 use multiblocks such as the Thermal Evaporation Plant.
- Machines have configurable input/eject sides.
- Energy units and conversions vary by Mekanism version; older docs use Joules, newer versions use FE. Do not hard-code a ratio.

NPC implications:

- The planner must include gas/fluid sub-chains, not just the ore machines.
- The NPC checks machine tier and multiblock presence; it cannot substitute a lower tier and claim the same yield.
- Energy is estimated per step and checked live.
- The NPC does not assume a machine's side configuration; it reads or asks.
- A C1-only adapter can show the chain but cannot run it.

### 9.3 Applied Energistics 2

Documented [S12]–[S15]:

- Channels: normal cables and most devices carry 8 channels; dense cables carry 32; ad-hoc networks (no controller) support 8 devices; each device uses one channel.
- Channel routing is shortest-path; bad layouts bottleneck.
- Channel capacity is configurable. AE2 10.0.0+ adds a `channels` config option and the operator command `/ae2 channelmode` with `default`, `x2`, `x3`, `x4`, and `infinite` modes [S12]. A pack can double, quadruple, or remove the limit.
- Energy: AE2 converts Forge Energy at 2 FE = 1 AE (Forge) or TechReborn Energy at 1 E = 2 AE (Fabric). Conversion is one-way.
- Networks have a buffer of 25 AE per cable/machine/part; ME Controller stores 8,000 AE; Energy Cells store 200k AE.
- Autocrafting: crafting CPU, pattern provider, molecular assembler for crafting patterns; processing patterns for machines.
- Security terminal: deposit, withdraw, craft, build, security permissions.

NPC implications:

- A network request must check channel availability, energy buffer, CPU capacity, pattern existence, and security permission.
- Read the channel mode before computing capacity; never assume 8/32. If the mode cannot be read, ask.
- The NPC must not assume infinite energy; a small network can run out mid-insert.
- The NPC does not write patterns. It requests existing patterns.
- A network step is `network-assisted`, not `portable`, even if the final item is a crafting-table item.

### 9.4 Refined Storage

Documented [S16]–[S19]:

- A Controller powers the network. If energy is enabled in config, each network needs at least one Controller; multiple controllers can meet higher demand.
- Energy is consumed per tick; more devices consume more.
- If a controller runs out, the network tries another; if none, it shuts down.
- Autocrafting requires a Pattern Grid and a pattern for every intermediate item. If a sub-item has no pattern, the task will not start.
- Processing patterns define up to 9 inputs and 9 outputs and are inserted into an Autocrafter facing the machine; the output must return to the network.
- Security Manager permissions: Insert, Extract, Autocrafting, Open, Build, Security; fallback card; operators bypass.

NPC implications:

- Check controller energy and security before requesting.
- Check that a pattern exists for the target and every intermediate; if not, ask the player to encode it.
- The NPC does not encode patterns in v1 (see §12, Defer).
- A processing pattern is not a portable recipe; it is a network contract.

### 9.5 Budget policy

- Preflight estimates energy, fluid, gas, and stress for the whole job.
- At execution, check live values before each step.
- Keep a reserve: default 10% of the network's buffer or 1,000 FE/AE equivalent, whichever is higher; configurable. Do not drain below the reserve without explicit owner consent.
- If the reserve would be breached, pause before the step and ask.
- Do not compete with the owner's own crafting: if the network is busy, wait or ask.
- Label all estimates as estimates. Never present an estimate as a guarantee.

### 9.6 Progression gates

- Machine tier, multiblock completion, pattern existence, and quest/stage locks can all block a recipe.
- The NPC can observe what the server exposes: recipe availability, block presence, machine tier, pattern existence, permissions.
- The NPC cannot see arbitrary quest state unless an adapter exists. If it cannot see a gate, it asks.
- The NPC does not skip a gate by substituting a lower-tier machine or a portable recipe.

---

## 10. Portable crafting boundary

### 10.1 Definition

Portable crafting is what the NPC can do with its own crafting grid and furnace-family stations, using real ingredients and real fuel. It is not a generic "make item" button.

Portable station set (decision D-12):

- Crafting table (2×2 and 3×3 recipes).
- Furnace.
- Smoker.
- Blast furnace.
- Campfire.

Not portable by default:

- Stonecutter (requires a station).
- Smithing table (requires a station).
- Modded stations (never portable unless explicitly added to the allowlist in a future decision).
- Any machine, multiblock, or network.

### 10.2 Allowlist test

A recipe is portable only if all of the following are true:

1. Its recipe type is in the portable allowlist: vanilla crafting, smelting, smoking, blasting, or campfire cooking.
2. Its station is in the portable station set, or it has no station.
3. It has no machine, multiblock, energy, fluid, gas, or catalyst requirement.
4. It is not a processing pattern or network autocraft.
5. Its inputs and outputs fit the NPC's inventory and stack limits.
6. The NPC has the real ingredients and, for smelting, real fuel.

If any condition fails, the step is `machine`, `network`, `supply`, or `unknown`. There is no "close enough" path.

### 10.3 Decision table

| Recipe type | Portable? | Why | NPC behavior |
|---|---|---|---|
| Vanilla shaped/shapeless crafting | Yes | Crafting table is portable. | Craft with real items. |
| Vanilla furnace/smoker/blast furnace/campfire | Yes | Portable heat; real fuel. | Consume fuel; track containers. |
| Vanilla stonecutting | No (station) | Requires a stonecutter block. | Use station if trusted; otherwise player-run. |
| Vanilla smithing | No (station) | Requires a smithing table. | Use station if trusted; otherwise player-run. |
| Modded crafting-table recipe, no catalyst | Conditional | Same station, but mod semantics may differ. | Portable only if the recipe type is vanilla crafting and no catalyst is required. |
| Modded crafting-table recipe with catalyst | No | Catalyst is an extra requirement. | Machine/station step or player-run. |
| Create mixing/pressing/crushing | No | Kinetic machine, basin, stress, heat. | Machine step; never portable. |
| Create bulk washing/smoking/blasting | No | Fan + water/fire/lava/heat; destructive risk. | Machine step with warning. |
| Create mechanical crafting | No (machine) | Mechanical Crafters and rotation. | Machine step; vanilla crafting recipe may still be portable on its own, but not through the crafter. |
| Mekanism enrichment/purification/injection/dissolution | No | Machine, energy, gas/fluid. | Machine step; never portable. |
| AE2/RS autocrafting | No | Network, patterns, CPU, security. | Network step; never portable. |
| Any multiblock recipe | No | Structure requirement. | Machine step or blocked. |
| Unknown recipe type | No | Not understood. | `unknown`; ask. |

### 10.4 Modded crafting recipes

A modded recipe registered under vanilla crafting is portable only if:

- The server's recipe type is the vanilla crafting type.
- The recipe has no catalyst, machine, energy, fluid, or gas requirement.
- The outputs and inputs are ordinary item stacks the NPC can handle.

EMI's `getCatalysts()` concept is a useful warning: some recipes look like crafting recipes but require an extra workstation or catalyst [S4]. If a catalyst is present, the recipe is not portable.

### 10.5 Smelting and fuel

- The NPC consumes real fuel. Fuel values come from the server; the NPC does not invent a fuel value.
- The NPC tracks fuel items and container returns.
- A modded smelting recipe is portable only if it is registered under a vanilla furnace-family type and has no machine requirement.
- Machine smelting (Mekanism Energized Smelter, Create bulk blasting) is not portable, even when the output is the same item.

### 10.6 Bypass examples and expected refusals

| Request | Wrong behavior | Correct behavior |
|---|---|---|
| "Craft brass." | NPC crafts brass in its grid. | "Brass needs a Mechanical Mixer and a heated Blaze Burner. I can prepare the copper and zinc, then you run the mixer, or I can operate it if you trust me with it." |
| "Make 3× iron from this ore." | NPC smelts once and calls it 3×. | "Three-times processing needs a Purification Chamber, oxygen, a Crusher, an Enrichment Chamber, and a Smelter. Portable smelting gives one ingot." |
| "Use my AE2 to make processors." | NPC fabricates a pattern. | "I can request this if a pattern exists and you allow me to craft on the network. I can't write patterns." |
| "Use the Create press." | NPC treats pressing as crafting. | "Pressing is a machine step. I can insert the items and wait, or you can run it." |
| "Smelt this modded ore." | NPC assumes furnace behavior. | Read the recipe type. If it is a machine recipe, machine step; if it is furnace-family and portable, smelt. |

### 10.7 Refusal copy

Refusals are specific and actionable:

- "I can't do that by hand. It needs a [machine]. Here is what I can do: prepare the inputs, or wait while you run it."
- "That recipe is in JEI, but JEI is a viewer. I don't have a reader for this machine's recipe type."
- "I can smelt this in a furnace, but that gives one ingot. The 3× chain needs machines."

---

## 11. Fallback to player supplies and player-run steps

### 11.1 Triggers

- Missing ingredient and no gather path.
- Unsupported recipe type or machine.
- No trust record and the owner is unavailable.
- Claim denied.
- Insufficient energy, fluid, or gas.
- Plan exceeds caps or has an unavoidable cycle.
- A step failed or timed out.
- The player's requested item is ambiguous.

### 11.2 Supply request contract

A supply request states:

- Resource and amount, with alternatives/tags.
- Why it is needed and which step it unblocks.
- Options: gather (simulated trip with ETA and partial-recall policy), wait for the player, or player runs the step. A trip yields basic natural resources only. For a processed item such as an ingot, the gather option means raw-material gathering followed by an explicit smelt step with real fuel or a craft step, shown with a combined estimate and its own partial-return policy.
- Whether partial delivery is accepted.
- Where to deliver (NPC inventory, container, or meeting point).
- Deadline or expiry.

Example:

> "I need 8 iron ingots for the warehouse roof. I can gather raw iron in about 9 minutes, then smelt it here with real fuel; about 12 minutes total, and I may come back with fewer. Or you can put 8 iron ingots in my inventory. I'll also accept 1 iron block."

### 11.3 Player-run machine step

When the NPC has C1 but not C2, it gives a step card:

- Machine name and position.
- Exact inputs and amounts.
- Expected outputs and byproducts.
- Energy/heat/fluid notes.
- Safety notes (e.g., fan incineration risk).
- A "done" action that triggers verification.

The NPC verifies the output in the player's inventory, the machine's output slot, or the network. It does not assume success because the player clicked "done."

### 11.4 Partial fulfillment

- Accept partial deliveries; re-plan the remainder.
- Do not restart the whole chain because one ingredient arrived early.
- If the player supplies a substitute, ask before using it. Tags may allow alternatives; exact items may not.
- If the player supplies the wrong item, return it or ask; do not silently consume it.

### 11.5 Copy rules

- Say what is missing, why, and what the player can do.
- Do not blame the player or the mod.
- Do not pretend an estimate is a guarantee.
- Do not offer a bypass that violates §10.
- Offer the smallest useful next action.

---

## 12. Feature decisions: Adopt, Defer, Reject

### 12.1 Adopt

| ID | Decision | Rationale | Depends on |
|---|---|---|---|
| A-01 | Capability report per world, refreshed on reload. | Honest compatibility; no pack-wide claims. | Adapter health checks. |
| A-02 | Per-job preflight with verdict and blockers. | Prevents starting jobs that cannot finish. | Chain planner. |
| A-03 | Server recipe manager is authoritative; viewers are display only. | Avoids client-side dependency and false craftability. | None. |
| A-04 | Capability tiers C0–C4 + CM, with adapter declarations. | Makes partial support explicit. | Adapter contract. |
| A-05 | Typed ingredient-chain steps with confidence labels. | Makes unknowns visible; supports planning. | Recipe readers. |
| A-06 | Deterministic alternative selection with owner override. | Reproducible plans; no silent recipe switching. | Chain planner. |
| A-07 | Reservation ledger with lifecycle, expiry, and reconciliation. | Prevents double-booking and phantom stock. | Persistent storage. |
| A-08 | Reserve at approval, revalidate at start, extract network stock with permission. | Turns a soft plan into a real commitment. | Network adapters. |
| A-09 | NPC trust records for machines/containers, scoped and revocable. | Mods lack a universal ACL; the NPC needs its own. | Permission UI. |
| A-10 | Layered permission checks: claim → network → trust → owner consent. | No layer overrides another. | C4 + C3 + trust. |
| A-11 | Claim check on every edit, not just zone approval. | Claims can change; builds move. | C4 adapter. |
| A-12 | Energy/fluid/gas preflight and live checks with a reserve. | Prevents mid-job starvation and network drain. | C2/C3 adapters. |
| A-13 | Portable allowlist; machine steps explicit. | Prevents bypassing machines. | C0 recipe types. |
| A-14 | Player supply request with gather/wait/run-step options. | Keeps jobs moving without cheating. | UI/chat. |
| A-15 | Player-run step card with verification. | C1-only worlds remain usable. | UI. |
| A-16 | Partial fulfillment and re-plan. | Real players deliver partial stacks. | Planner. |
| A-17 | Compatibility snapshot stored with job approval. | Audit and reproducibility. | Persistence. |
| A-18 | Simulated trips with vanilla-default resource table and configured tags. | Honest about modded resources. | Config/datapack. |
| A-19 | Trusted-player AI consent, scoped and revocable; key stays client-local. | Matches `PRODUCT.md` privacy and authority. | Client/server flow. |
| A-20 | Logout freezes jobs, trips, reservations, and AI calls. | Matches `PRODUCT.md`; prevents unattended spending. | Persistence. |
| A-21 | Audit log for permission and compatibility decisions. | Accountability on public servers. | Persistence. |
| A-22 | Admin diagnostics screen with adapter status and reasons. | Operators need to debug compatibility. | Capability report. |
| A-23 | Container/remainder/byproduct accounting. | Real material accounting. | Planner/ledger. |
| A-24 | Tag alternatives with deterministic choice. | Modpacks rely on tags. | Recipe reader. |
| A-25 | Generic surface first; adapters only for what the generic surface cannot see. | Keeps large packs usable without a per-mod adapter for everything. | No new dependency; policy in §4.6. |

### 12.2 Defer

| ID | Idea | Why defer | Revisit when |
|---|---|---|---|
| D-01 | Writing AE2/RS patterns automatically. | High risk; patterns are player-authored contracts; security implications. | After C3 is stable and tested. |
| D-02 | Direct machine GUI automation or reflection. | Brittle across versions; violates "documented mechanisms" stance. | Never as a primary path; only documented APIs. |
| D-03 | A universal machine API across mods. | No such API exists; would overpromise. | If a common standard emerges. |
| D-04 | NPC-owned AE2/RS network or storage. | Out of scope; ownership/security complexity. | After single-owner networks are tested. |
| D-05 | Cross-mod energy conversion or buying energy. | Units and ratios vary; could break economies. | With explicit pack config support. |
| D-06 | Automatic machine repair execution. | `PRODUCT.md` allows diagnose and repair plans; execution is riskier. | After repair planning is validated. |
| D-07 | Multi-NPC shared reservations. | One companion per owner in scope; coordination adds complexity. | If multiple companions are added. |
| D-08 | Quest/game-stage integration (KubeJS, GameStages). | No adapter yet; gates vary by pack. | When a pack is chosen. |
| D-09 | Auto-generating simulated-trip tables for modded ores. | Would imply modded resource knowledge the NPC doesn't have. | With pack-specific config. |
| D-10 | Public catalog compatibility filters. | Catalog is in scope, but compatibility metadata needs testing first. | After pack-tested adapters exist. |
| D-11 | Recipe-viewer server sync or data extraction. | Viewer APIs are client-side; syncing is fragile. | If a viewer exposes a stable server API. |
| D-12 | Automatic substitution of missing modded blocks. | `PRODUCT.md` requires proposed replacements for approval. | Never automatic; keep approval. |
| D-13 | Generic datapack JSON recipe reader as an execution path. | Schemas differ and a parse does not prove registration; diagnostic only for now. | After a pack is chosen and its recipe schemas are inspected. |

### 12.3 Reject

| ID | Idea | Why reject |
|---|---|---|
| R-01 | "Works with all mods" or "All the Mods compatible" claim. | Not testable; would be false. |
| R-02 | Portable crafting that emulates machine recipes. | Bypasses energy, heat, stress, progression, and balance. |
| R-03 | Sending the player's key to the server or trusted players. | Violates `PRODUCT.md` privacy. |
| R-04 | Hidden world lookup for resources or structures. | `PRODUCT.md` requires physical exploration and memory. |
| R-05 | Simulating machine processing without the machine. | Same as R-02. |
| R-06 | Reflection/GUI automation as the main compatibility path. | Brittle, unsafe, and unverifiable. |
| R-07 | Bypassing claims or network security. | Violates server authority and player trust. |
| R-08 | Auto-downloading mods, configs, or credentials. | Out of scope; security risk. |

### 12.4 Additional scope decisions

| ID | Decision |
|---|---|
| S-01 | Portable stations are crafting table, furnace, smoker, blast furnace, and campfire. Stonecutter and smithing table require a station. |
| S-02 | Modded crafting recipes are portable only under the vanilla crafting type with no catalyst. |
| S-03 | Fuel is a real item with a server-provided value; no creative fuel. |
| S-04 | Simulated trips default to vanilla natural resources and configured tags; modded ores require config. |
| S-05 | Network reservations are advisory until extracted; extraction requires permission. |
| S-06 | Energy reserve defaults to 10% or 1,000 units, whichever is higher; configurable. |
| S-07 | Plan caps: depth 12, 400 steps, 2 seconds; configurable. |
| S-08 | Trusted players inherit machine trust but cannot create new trust without delegation. |
| S-09 | Owner logout freezes all execution, reservations, and expiry clocks. |
| S-10 | Unknown recipe types are `unknown`, never guessed. |
| S-11 | The NPC does not write patterns in v1. |
| S-12 | The NPC does not operate a machine without a trust record. |
| S-13 | The NPC does not empty a network or tank without a scoped permission. |
| S-14 | Compatibility snapshots are stored with job approvals for audit. |
| S-15 | "All the Mods" is a target class, not a certification. |

---

## 13. Prototype screen contracts

These are contracts for standalone HTML prototypes. They use mock data; no live server is required. Each screen must work with keyboard only, show status with text as well as color, and survive a narrow viewport.

### S1. Compatibility Report

**Purpose:** show what the NPC can do in this world, honestly.

**Entry:** right-click NPC → "What can you use here?"; job screen link; admin diagnostics.

**Layout:**

```
+--------------------------------------------------------------+
| Compatibility report — [NPC name]              [Refresh]     |
| This is not a compatibility guarantee.                       |
+--------------------------------------------------------------+
| Recipe knowledge                                             |
|  [x] Vanilla crafting, smelting, smoking, blasting           |
|  [~] Create recipes — read-only (no machine operation)       |
|  [ ] Mekanism recipes — no reader installed                  |
+--------------------------------------------------------------+
| Machines                                                     |
|  Create: 3 machines nearby — read-only                       |
|  Mekanism: 0 machines detected                               |
+--------------------------------------------------------------+
| Networks                                                     |
|  AE2: network found — security unknown — energy 62%          |
|  Refined Storage: none detected                              |
+--------------------------------------------------------------+
| Protection                                                   |
|  FTB Chunks: claim owned by you — build allowed              |
|  Other protection: unknown                                   |
+--------------------------------------------------------------+
| Unsupported                                                  |
|  Mekanism recipe types — no adapter                           |
|  [Show details]                                              |
+--------------------------------------------------------------+
| [Run a job preflight]  [Show unsupported details]            |
+--------------------------------------------------------------+
```

**Fields:** adapterId, mod, version range, tier, status (`available`, `read-only`, `failed`, `absent`), evidence, lastChecked, claim status, network security state, energy percent.

**States:** loading; no mods; partial; adapter failed; permission unknown.

**Copy:** "I can plan vanilla crafting and smelting anywhere. I found Create machines, but I can only read their recipes right now; I can't operate them. I can still prepare a step for you to run."

**Do not:** claim a pack name is supported; show a green check for an untested adapter; hide unsupported entries.

### S2. Job Preflight / Ingredient Chain

**Purpose:** let the owner review the plan, blockers, and reservations before starting.

**Entry:** after design approval; "Review materials" on a job.

**Layout:**

```
+--------------------------------------------------------------+
| Warehouse roof — 64 stairs              Verdict: machine-assisted |
+--------------------------------------------------------------+
| Chain                              | Summary                  |
|  [Gather] 64 stone                 | Total cost: 64 stone     |
|    -> [Craft] 64 stairs            | Missing: 0               |
|  [Machine] Stonecutter (optional)  | Reserved: 64 stone       |
|                                    | Machine steps: 1         |
|                                    | Energy: 0 FE             |
|                                    | ETA: 6 min               |
+--------------------------------------------------------------+
| Blockers                                                     |
|  None                                                        |
+--------------------------------------------------------------+
| [Approve and reserve] [Change recipe] [Ask for supplies]     |
+--------------------------------------------------------------+
```

**Fields:** stepId, type, recipeId, station, inputs, outputs, byproducts, energy, fluid, gas, time, confidence, blocker, reservation state.

**States:** satisfiable; needs supply; needs machine; network-assisted; blocked; unknown; too large.

**Copy:** "This needs a stonecutter. I don't carry one. I can gather the stone and craft the stairs if you let me use your stonecutter, or you can run that step."

**Do not:** hide a blocker behind a green summary; show an estimate as exact; allow approval when a hard blocker exists.

### S3. Machine Permission

**Purpose:** ask before first use of an unfamiliar machine, container, or network.

**Entry:** first interaction; job preflight link; owner settings.

**Layout:**

```
+--------------------------------------------------------------+
| May I use this machine?                                      |
| Mechanical Mixer at 120, 64, -34                              |
+--------------------------------------------------------------+
| I want to: insert 4 copper and 4 zinc, then take the output. |
| Why: to make brass for the workshop job.                     |
| Risk: the mixer needs rotation and heat; I will check both.  |
+--------------------------------------------------------------+
| Remember: (o) this block  ( ) all mixers  ( ) this network   |
| For: (o) this job  ( ) 1 hour  ( ) until I revoke            |
+--------------------------------------------------------------+
| [Allow] [Deny]                                               |
+--------------------------------------------------------------+
```

**Fields:** trustId, target, scope, actions, reason, risk, grantedBy, expiresAt.

**States:** pending; granted; denied; expired; invalidated by block change.

**Copy:** "I haven't used this machine before. May I put 4 copper and 4 zinc into it and take the brass? I'll remember this block, not every mixer."

**Do not:** pre-select "all machines"; hide the risk; allow a trusted player to grant owner-level trust without delegation.

### S4. Supply Request

**Purpose:** turn a shortage into a clear choice.

**Entry:** blocked preflight; mid-job shortage; chat.

**Layout:**

```
+--------------------------------------------------------------+
| I need 8 iron ingots                                         |
| For: warehouse roof — 64 stairs                              |
+--------------------------------------------------------------+
| Options                                                      |
| (o) Gather raw iron + smelt — about 12 min, may be fewer     |
| ( ) I'll wait — put 8 ingots in my inventory; partial ok     |
| ( ) You run this step — I'll show the machine and inputs     |
+--------------------------------------------------------------+
| Trip brings raw iron only; smelting uses real fuel.          |
| I'll also accept: 1 iron block                               |
+--------------------------------------------------------------+
| [Choose] [Cancel job]                                        |
+--------------------------------------------------------------+
```

**Fields:** requestId, resource, amount, alternatives, reason, options, processing, eta, partialPolicy, deliveryLocation.

**States:** open; partially fulfilled; fulfilled; expired; cancelled.

**Copy:** "I need 8 iron ingots. I can gather raw iron in about 9 minutes, then smelt it here with real fuel; about 12 minutes total, and I may come back with fewer. Or you can put 8 iron ingots in my inventory. I'll also accept 1 iron block."

**Do not:** promise the ETA as exact; refuse partial delivery; hide the raw-material or fuel risk.

### S5. Reservation Ledger

**Purpose:** show what is promised to which job, and what is actually free.

**Entry:** job screen → Materials; NPC inventory → Reservations.

**Layout:**

```
+--------------------------------------------------------------+
| Materials                                                    |
+--------------------------------------------------------------+
| Resource       Needed  Reserved  Free   Where      State     |
| Iron ingot       64       64       0    NPC inv    reserved  |
| Copper ingot      8        8       0    AE2        reserved  |
| Zinc ingot        8        0       8    Chest      free      |
+--------------------------------------------------------------+
| [Release] [Reassign] [Refresh]                               |
+--------------------------------------------------------------+
```

**Fields:** reservationId, resourceKey, amount, unit, location, state, jobId, expiresAt.

**States:** reserved; extracted; consumed; released; stale; conflict.

**Copy:** "64 iron ingots are reserved for the warehouse job. 32 are in my inventory and 32 are in your AE2 network. I won't use them for another job."

**Do not:** show reserved as consumed; hide stale reservations; allow reassignment without owner confirmation.

### S6. Unsupported / Blocked Diagnostics

**Purpose:** explain why the NPC cannot proceed, with evidence.

**Entry:** "Why can't you?" from any blocked state.

**Layout:**

```
+--------------------------------------------------------------+
| Why I can't do this                                          |
+--------------------------------------------------------------+
| I tried: make 3x iron from raw ore                           |
| Missing: a reader for Mekanism machine recipes               |
| Evidence: I can see the item in JEI, but JEI is a viewer.    |
| I don't have an adapter for this machine's recipe type.      |
+--------------------------------------------------------------+
| What I can do                                                |
| [Prepare a player-run step] [Ask the owner] [Copy report]    |
+--------------------------------------------------------------+
```

**Fields:** blockerCode, detail, evidence, suggestedAction, adapterStatus.

**States:** unsupported recipe; missing machine; permission denied; claim denied; energy low; unknown.

**Copy:** "I can see this recipe in JEI, but JEI is a viewer. I don't have a reader for this machine's recipe type, so I can't plan it. I can give you the inputs and instructions."

**Do not:** blame the player; invent a workaround; show a generic error without a next action.

### S7. Trusted-Player Allowance Consent

**Purpose:** let the owner grant a trusted player scoped use of the AI allowance without sharing the key.

**Entry:** owner settings → Trusted players; trusted player requests an AI job.

**Layout:**

```
+--------------------------------------------------------------+
| Allow Ravi to use your AI allowance?                         |
+--------------------------------------------------------------+
| Scope: (o) this job  ( ) 1 hour  ( ) until revoked           |
| Estimated use: one planning request                          |
| Your key stays on your computer. Ravi never sees it.         |
| If you log out, all my work pauses, including this job.      |
+--------------------------------------------------------------+
| [Allow] [Deny]                                               |
+--------------------------------------------------------------+
```

**Fields:** consentId, player, scope, expiresAt, status, estimatedUse.

**States:** pending; granted; denied; revoked; expired.

**Copy:** "Ravi can ask me to use your BharatCode allowance for this job. Your key stays on your computer; Ravi never sees it. If you log out, all my work pauses, including this job."

**Do not:** imply the key is shared; allow indefinite consent by default; let a trusted player approve their own request.

---

## 14. Compatibility acceptance scenarios

Status for all scenarios: **not executed**. They are written to be run later on a test server once a loader, version, and pack are chosen. "Evidence" says whether the mechanic is documented, proposed, or unknown.

| ID | Scenario | Setup | Expected | Evidence |
|---|---|---|---|---|
| CS-01 | Vanilla baseline | No mods beyond the NPC. | Plan and craft a stone pickaxe with real cobblestone and sticks; verdict `portable`; no viewer dependency. | C0 baseline; not tested. |
| CS-02 | Viewer absent | JEI/EMI not installed on the client. | NPC plans from the server recipe manager; no error; UI does not claim viewer data. | [D] EMI client-side; [P] behavior. |
| CS-03 | Viewer present but server lacks recipe | Client has JEI showing a modded recipe; server has no adapter. | NPC marks the recipe `unknown`; offers a player-run step; does not craft. | [D] viewer display; [P] behavior. |
| CS-04 | Create brass | Create installed; mixer, basin, heated blaze burner; C1 only. | Plan shows mixer + heat + stress; refuses portable; player-run card; after C2, NPC operates and verifies output. | [D] Create mechanics; [P] adapter. |
| CS-05 | Create overstress | Mixer plus insufficient SU. | Preflight blocks with "not enough stress"; asks for more capacity or a lower-impact setup; does not start. | [D] stress; [P] check. |
| CS-06 | Create fan hazard | Encased Fan bulk blasting with valuable items. | NPC warns about incineration; requires explicit confirmation; does not test with valuables. | [D] fan behavior; [P] policy. |
| CS-07 | Mekanism 3× | Raw ore, purification chamber, oxygen chain, crusher, enrichment, smelter. | Plan includes oxygen sub-chain and four machine steps; refuses portable 3×; energy checked. | [D] ore processing; [P] adapter. |
| CS-08 | Mekanism tier gate | Only Tier 1 machines; target requires Tier 2. | NPC reports missing machine/tier; does not substitute; asks. | [D] tiers; [P] check. |
| CS-09 | AE2 autocraft allowed | Pattern exists; NPC has craft permission; channels and energy sufficient. | NPC requests the craft; shows CPU/pattern; waits; verifies output. | [D] AE2 mechanics; [P] adapter. |
| CS-10 | AE2 security denied | Security terminal denies craft. | NPC reports the permission; asks the owner; does not bypass; no pattern invention. | [D] security; [P] check. |
| CS-11 | AE2 energy low | Network buffer below reserve. | NPC pauses before requesting; asks for energy; does not drain to zero. | [D] energy buffer; [P] policy. |
| CS-12 | RS processing pattern | Pattern exists; security allows autocrafting. | NPC requests iron ingot; confirms output returns to network; no pattern writing. | [D] RS patterns; [P] adapter. |
| CS-13 | RS security denied | Security Manager denies Extract. | NPC reports Insert/Extract/Autocrafting state; asks; no bypass. | [D] RS security; [P] check. |
| CS-14 | FTB claim allowed | Work zone inside owner's claim; team build allowed. | NPC builds; claim check passes; audit log records it. | [D] claims; [P] adapter. |
| CS-15 | FTB claim denied | Work zone in another player's claim. | NPC denies; names the claim owner; asks to move the zone or grant access; never tests the edit. | [D] claims; [P] check. |
| CS-16 | FTB interaction off | Team block interaction disabled. | NPC cannot open the machine; asks; offers player-run step. | [D] team settings; [P] check. |
| CS-17 | Missing ingredient | No iron ingots; simulated trip allowed. | Supply request offers raw-iron gather plus explicit smelt with real fuel (combined ETA), wait, or player-run; partial accepted; trip returns partial raw iron on recall. | [D] simulated trips in `PRODUCT.md`; [P] UI. |
| CS-18 | Portable bypass attempt | Player asks NPC to "just craft" a Create pressing recipe. | NPC refuses; explains machine step; offers inputs or player-run; never crafts. | [D] Create; [P] policy. |
| CS-19 | Reservation conflict | Two approved jobs both need 64 iron; only 64 available. | First reserves; second waits; cancelling first releases; no double-booking. | [P] ledger. |
| CS-20 | Owner logout | Owner logs out mid-job. | All execution stops, including approved and predefined menu jobs, trips, and inventory transfers; AI calls stop; reservations freeze; job resumes on login. | [D] `PRODUCT.md`; [P] behavior. |
| CS-21 | Trusted player order | Trusted player orders an AI job while owner online. | Owner consent prompt; key never leaves owner client; server validates; audit records. | [D] `PRODUCT.md`; [P] flow. |
| CS-22 | Trusted player, owner offline | Trusted player orders an AI job while owner offline. | All work pauses, including predefined menu jobs; the menu is visible but does not execute; no key use; work resumes on owner login. | [D] `PRODUCT.md`; [P] behavior. |
| CS-23 | Crash recovery | Server restarts mid-job. | Ledger reloads; reservations reconcile; stale entries pause and ask; no phantom stock. | [P] ledger. |
| CS-24 | Unknown machine | A mod machine with no adapter. | NPC marks `unknown`; does not operate; offers player-run; capability report lists it. | [P] tiers. |
| CS-25 | AE2 channel mode changed | Pack sets `/ae2 channelmode x4` or `infinite`. | NPC reads the mode and computes capacity accordingly; it does not report a false channel blocker, and does not assume default 8/32 after a reload. | [D] channel modes; [P] read. |
| CS-26 | FTB server-team claim | Work zone protected by a server team, not a player team. | NPC identifies the team, asks for access, and does not infer access from the owner's player claim. | [D] server teams; [P] check. |
| CS-27 | Generic JSON lookalike | A mod ships a recipe JSON the generic reader can parse, but the registered server type differs. | NPC does not execute; reports that the file looks like a recipe but the registered type is unconfirmed; offers a player-run step. | [P] generic reader; no execution path. |

### 14.1 Detailed scenarios

**CS-04 Create brass.** The owner asks for 4 brass ingots. The NPC reads the mixing recipe, checks copper and zinc stock, and finds the mixer and basin. It checks the Blaze Burner is heated and computes stress at the mixer's required speed. With C1 only, it shows a player-run card: "Put 4 copper and 4 zinc in the basin; the mixer needs at least 32 RPM and a heated Blaze Burner." The player runs it; the NPC verifies 4 brass ingots in the output. With C2, the NPC inserts the items, waits, and verifies. In neither case does it craft brass in its grid.

**CS-10 AE2 security denied.** The NPC wants 64 iron ingots. A pattern exists and the network has energy, but the security terminal denies `craft` for the NPC's identity. The NPC reports: "The network won't let me start crafting. The owner can grant craft permission, or I can ask you to run it." It does not attempt a workaround, does not write a pattern, and does not extract items it is not allowed to withdraw.

**CS-15 FTB claim denied.** The assigned work zone overlaps another player's claim. The NPC checks the claim before the first placement, denies the job, names the claim owner, and offers to move the zone or ask for access. It does not place a test block to "see what happens." If the owner moves the zone, the NPC re-runs the claim check.

**CS-21 Trusted player order.** Ravi asks the NPC to build a wall. The server confirms Ravi is trusted and the zone is in the owner's claim. The owner's client shows a consent prompt with scope "this job" and estimated allowance. The owner approves. The owner's client calls the AI with the owner's key; the proposal goes to the server; the server validates recipes, inventory, and permissions; the NPC builds. The audit log records Ravi's order, the owner's consent, and every permission check. Ravi never sees the key.

---

## 15. Risks and open questions

### 15.1 Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Loader/version changes break adapter APIs. | Adapters stop working; plans become wrong. | Version-range declarations; health checks; graceful degradation. |
| Pack config disables energy, channels, or claims. | Assumptions about constraints are wrong. | Read config where exposed; ask when unknown; never assume defaults. |
| Viewer data differs from server recipes. | NPC promises a recipe it cannot execute. | Server recipe manager authoritative; validate before execution. |
| Mods have no machine ACL. | NPC could use another player's machine. | NPC trust records; ask first; claims layer. |
| Claim APIs are not queryable by third parties. | NPC cannot verify access. | Ask; never test by editing; fall back to player confirmation. |
| Energy units vary by version. | Wrong energy estimates. | Read version-specific units; label estimates; check live. |
| Large packs make planning expensive. | Lag or timeouts. | Plan caps; cache; incremental planning. |
| AI hallucinates a recipe. | Wrong plan or wasted materials. | Validate against server recipe manager; refuse unverifiable recipes. |
| Localization of mod names and recipes. | UI confusion. | Use server-provided display names; fall back to IDs. |
| Griefing or misuse on public servers. | Reputation and player trust. | Claims, trust records, audit log, owner consent. |
| Key handling mistakes. | Credential exposure. | Key stays client-local; never log; never send to server. |
| Partial deliveries and stale reservations. | Jobs stall or double-book. | Reconciliation; expiry; owner notifications. |

### 15.2 Open questions

1. Which loader, Minecraft version, and pack will be targeted first? Adapter APIs depend on this.
2. Do JEI/EMI expose any stable server-side data in the target version? The design assumes not.
3. Do AE2/RS expose a public API for the exact permission and stock queries the NPC needs? The docs describe player-facing behavior, not a third-party query contract.
4. Does FTB Chunks expose a stable claim-query API? The docs describe claims and team permissions, not a public query API.
5. Do Create/Mekanism expose documented machine capabilities that are stable across versions? The docs describe behavior, not an NPC-facing API.
6. What are the target pack's energy/channel/claim config defaults?
7. How should the NPC behave when a machine is in an unloaded chunk? (Proposed: `unknown`, ask.)
8. How much planning latency is acceptable on a large pack? (Proposed: 2 seconds, then ask.)
9. Should the NPC ever operate a machine without a trust record if the owner is standing next to it? (Proposed: no; prompt once.)
10. How are modded gases represented across versions? (Proposed: adapter-specific units; no universal conversion.)

### 15.3 What would change this design

- A chosen pack/version with stable APIs would turn proposed adapters into concrete contracts.
- A public claim-query API would simplify C4.
- A public machine-automation API would simplify C2.
- Viewer server-side support would not change the authority rule; it would only add a display data source.
- A pack that disables energy/channels would remove some constraints but not the permission or reservation model.

---

## 16. Sources

All accessed 2026-11-07. "Primary" means developer wiki, official guide, or official documentation. "Community" means the official community wiki; version caveats apply. On this revision, representative sources — JEI, EMI, Create stress/mixer/press/fan/basin, AE2 channels/energy/security, Refined Storage controller/security, FTB Chunks/Teams, and Mekanism ore processing — were re-fetched and checked against the claims they support; all returned HTTP 200.

| ID | Source | Type | Used for |
|---|---|---|---|
| S1 | `PRODUCT.md` (local) | Authoritative scope | Confirmed gameplay, privacy, authority, delegated additions. |
| S2 | [JEI wiki — Recipes Overview](https://github.com/mezz/JustEnoughItems/wiki/Recipes-Overview) | Primary (developer wiki) | JEI recipe display plugin; modded recipe registration. |
| S3 | [JEI README, branch 26.3](https://github.com/mezz/JustEnoughItems/blob/26.3/README.md) | Primary (repository) | JEI is an item and recipe viewing mod; supported versions. |
| S4 | [EMI wiki — Getting Started Guide](https://github.com/emilyploszaj/emi/wiki/Getting-Started-Guide) | Primary (developer wiki) | EMI API is client-side; recipes, workstations, catalysts, recipe tree, `supportsRecipeTree`. |
| S5 | [Create wiki — Stress Units, Capacity and Impact](https://github.com/Creators-of-Create/Create/wiki/Stress-Units,-Capacity-and-Impact) | Primary (developer wiki) | Stress capacity vs. impact; speed scaling; overstress. |
| S6 | [Create wiki — Basin](https://github.com/Creators-of-Create/Create/wiki/Basin) | Primary (developer wiki) | Basin auto-runs valid recipes; item/fluid limits; heated Blaze Burner for brass. |
| S7 | [Create wiki — Mechanical Press](https://github.com/Creators-of-Create/Create/wiki/Mechanical-Press) | Primary (developer wiki) | Press compacts; constant speed; basin behavior. |
| S8 | [Create wiki — Mechanical Mixer](https://github.com/Creators-of-Create/Create/wiki/Mechanical-Mixer) | Primary (developer wiki) | Mixer recipe types; 4 SU/RPM; 32 RPM minimum; basin requirement. |
| S9 | [Create wiki — Mechanical Crafter](https://github.com/Creators-of-Create/Create/wiki/Mechanical-Crafter) | Primary (developer wiki) | Mechanical crafting; rotation; invalid recipes ejected. |
| S10 | [Create wiki — Encased Fan](https://github.com/Creators-of-Create/Create/wiki/Encased-Fan) | Primary (developer wiki) | Bulk washing/smoking/blasting; incineration risk; 2 SU/RPM. |
| S11 | [Create wiki — Deployer](https://github.com/Creators-of-Create/Create/wiki/Deployer) | Primary (developer wiki) | Deployer use/attack modes; item handling. |
| S12 | [AE2 guide — Channels](https://guide.appliedenergistics.org/1.20.1/ae2-mechanics/channels) | Primary (official guide) | 8/32 channels; ad-hoc 8; routing; channel modes. |
| S13 | [AE2 guide — Energy](https://guide.appliedenergistics.org/1.20.1/ae2-mechanics/energy) | Primary (official guide) | 2 FE = 1 AE; one-way conversion; 25 AE/device buffer; controller 8,000 AE. |
| S14 | [AE2 guide — Autocrafting](https://guide.appliedenergistics.org/1.20.1/ae2-mechanics/autocrafting) | Primary (official guide) | CPU, pattern provider, molecular assembler, processing patterns. |
| S15 | [AE2 guide — ME Security Terminal](https://guide.appliedenergistics.org/1.19.2/features/me-network/misc/me-security-terminal) | Primary (official guide) | Deposit, withdraw, craft, build, security permissions; physical security caveat. |
| S16 | [Refined Storage docs — Controller](https://refinedmods.com/refined-storage/v3.2.0/networking/controller.html) | Primary (official docs) | Controller energy; per-tick consumption; shutdown on no power. |
| S17 | [Refined Storage docs — Getting started with autocrafting](https://refinedmods.com/refined-storage/v3.2.0/guides/getting-started-with-autocrafting.html) | Primary (official docs) | Pattern per intermediate; task won't start if missing; internal task inventory. |
| S18 | [Refined Storage docs — Processing recipes](https://refinedmods.com/refined-storage/v3.2.0/guides/processing-recipes.html) | Primary (official docs) | Processing patterns; 9 inputs/9 outputs; output must return to network. |
| S19 | [Refined Storage docs — Security Manager](https://refinedmods.com/refined-storage/v3.2.0/security/security-manager.html) | Primary (official docs) | Insert, Extract, Autocrafting, Open, Build, Security; fallback card; operator bypass. |
| S20 | [FTB docs — Chunks](https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/suite/Chunks/) | Primary (official docs) | Claims; team access; allies; force-load; XMod compat. |
| S21 | [FTB docs — Teams](https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/suite/Teams/) | Primary (official docs) | Team permissions: mob griefing, block breaking, block placing, block interaction; server teams. |
| S22 | [FTB docs — FTB XMod Compat](https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/suite/Ranks/Integration/FTB%20XMod%20Compat/) | Primary (official docs) | `ftbchunks.max_claimed`, `no_wilderness`, `chunk_load_offline`. |
| S23 | [Mekanism wiki — Main page](https://wiki.aidancbrady.com/wiki/Mekanism) | Community (official wiki) | Tiers; factories 3/5/7 slots; configurable sides; energy compatibility; outdated-page notice (2024). |
| S24 | [Mekanism wiki — Ore Processing](https://wiki.aidancbrady.com/wiki/Ore_Processing) | Community (official wiki) | Tier 0–4 chains; oxygen, hydrogen chloride, sulfuric acid; multiblocks. |

### 16.1 Source caveats

- Mekanism's wiki is community-maintained and its main page carries an outdated-version notice; treat numbers as representative, not guaranteed for a chosen version.
- AE2 guide pages are version-scoped (1.19.2/1.20.1); the mechanics are representative, not a version promise.
- Refined Storage docs are version-scoped (v3.2.0); behavior may differ in other versions.
- FTB docs are version-scoped (1.21.1); claim/team behavior may differ in other versions.
- JEI and EMI wikis are developer documentation; their APIs are client-side and version-specific.

---

## 17. Out of scope and non-promises

- No implementation, mod, server, or adapter is delivered by this document.
- No mods were installed; no private configs or credentials were read.
- No loader, Minecraft version, or modpack is chosen.
- No claim that All the Mods, or any pack, works.
- No universal compatibility claim.
- No real resource dimension, mining simulation, or world generation.
- No server-side key storage, key sharing, or key logging.
- No claim that JEI/EMI can be queried by the server.
- No claim that AE2/RS/FTB expose a stable public API for the exact queries described; those are proposed adapters.

The next design step is to choose a loader/version/pack for a test server, implement the capability report and C0 planner first, and add adapters only with integration tests and a documented version range.
