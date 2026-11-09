# Implementation status: M1 foundation

* **Date:** 2026-11-09
* **Artifact:** `build/libs/bharatcode_companion-0.1.0.jar`
* **SHA-256:** `843d40c60e56714eb33cbc3146d67c4a201c91136d11a052dd70ddfae6d4488a`
* **Artifact size:** 44,807 bytes.
* **Build:** `./gradlew build` succeeded.
* **Unit tests:** 18/18 passed.
* **Runtime:** NeoForge dedicated-server GameTest run; 20/20 lifecycle tests passed.

This is the first implemented BharatCode Companion milestone. It is not a complete builder, not an ATM10-tested release, and not a client-verified feature set. The prototype and design documents were left untouched.

## 1. Environment and artifact

The host has Java 25 and a cached Temurin 17, but Gradle 8.14.2 cannot run on Java 25. A local Temurin 21.0.13+9 was installed under `~/.local/opt/jdk-21.0.13+9` and its published SHA-256 was verified before extraction. No system Java was replaced.

Verified pins:

| Component | Value |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| ModDevGradle | 2.0.91 |
| Gradle wrapper | 8.14.2 |
| Java toolchain | 21 |
| Mod version | 0.1.0 |
| Mod id | `bharatcode_companion` |

Build command:

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.13+9"
./gradlew build
```

The artifact contains the mod classes, `META-INF/neoforge.mods.toml`, the item model and texture, the language file, and the recipe. It does not contain the test sources, the GameTest harness, the GameTest structure resource, or the disposable `run/` and `run-gametest/` directories.

The hash identifies the artifact built in this workspace; a rebuild can change archive timestamps and therefore the hash.

## 2. What is implemented

### Item and recipe

`bharatcode_companion:summoning_charm` is a stack-size-one item. The shaped recipe uses four iron ingots and one redstone. The item is reusable and holds no per-instance state.

Controls:

- Right-click: summon the caller's companion, or recall it to the caller.
- Sneak + right-click: dismiss the caller's companion.
- Spectators and fake players are no-ops.

Feedback is sent as translatable chat components for success, no companion, wrong dimension, inconsistent state, and unreadable data.

### Entity

`bharatcode_companion:companion` extends `PathfinderMob` with:

- empty goals and `setNoAi(true)`;
- `setPersistenceRequired()` and no far-away removal;
- `setInvulnerable(true)`, normal damage refused;
- admin `/kill` allowed through the `minecraft:generic_kill` bypass;
- no item pickup, inventory, container access, block interaction, or world edits;
- owner UUID stored in NBT under `Owner`, read only by a legacy entity that is immediately fenced;
- default custom name `Companion`, set when the charm creates the entity.

The entity type is registered with `EntityType.Builder.noSave()`. This is the persistence boundary: `EntityType.canSerialize()` returns `false`, `Entity.getEncodeId()` returns `null`, `Entity.save(CompoundTag)` returns `false` without writing a tag, and the engine's entity storage only writes entities whose save succeeds. A companion is therefore never written to an entity region file. The SavedData record is the only durable state; the live entity is a transient projection.

The entity is stationary. It has no follow, stay, home, pathfinding, or work behaviour, and the mod makes no claim that it does. Owner logout does not add movement or work behaviour.

### Ownership authority and projections

The overworld `DimensionDataStorage` holds one `bharatcode_companion_companions` record set. Each record is keyed by the owner's UUID and contains:

- the companion UUID;
- the companion custom name;
- `ACTIVE` or `DORMANT`;
- the dimension and last known position.

The SavedData schema `DataVersion` is `2`; each record also has `RecordVersion` `2`. **A record contains no entity NBT.** The live entity is a projection of the record, not a second copy of the authority.

The lifecycle writes the record before it adds a projection. A projection is only allowed into a level if the authority says it is active, bound to the same owner, **and placed in the same dimension**. The entity's saved owner and name are overwritten from the record whenever it joins a level, so the record wins if the two ever disagree. A copy in another dimension is discarded instead of being used or re-projected.

## 3. Lifecycle and persistence boundary

### Summon with no record

1. Create the companion entity and assign the owner and default name.
2. Write and mark dirty the new authority record (ACTIVE, UUID, name, dimension, position).
3. Add the entity to the level.
4. If the first add fails, the new record becomes DORMANT; a later summon keeps the same UUID. Re-projecting an existing record instead restores that record's exact previous state and position on add failure.

### Recall or re-project with an active record

1. Scan loaded levels for the companion UUID and discard any copy the authority does not place in that dimension.
2. If the matching copy is found and owned by the caller, teleport it to the caller and update the record position.
3. If not found in a loaded level, load the recorded chunk.
4. Require `ServerLevel.areEntitiesLoaded(chunkPos)` before trusting an absence. `PersistentEntitySectionManager` loads entity sections asynchronously through its loading inbox, so an immediate UUID lookup after `getChunk` is not proof of absence.
5. If the entity sections are still loading, start one bounded pending recall for the owner: a temporary `TicketType` ticket at distance 2 (chunk ticket level 31, `ENTITY_TICKING`) holds the recorded chunk until the sections load, for at most 100 ticks. The charm reports pending feedback.
6. On every tick the request revalidates the data schema, the owner record and companion UUID, `ACTIVE` state, recorded dimension, online owner, owner dimension, and mode. The ticket is removed on completion, cancel, logout, timeout, stop, or a new action for that owner.
7. When the sections are loaded, the lookup uses the visible UUID index and the public section query (`Level.getEntities(EntityTypeTest, AABB, predicate)`). If the UUID is absent, create a new projection with the record's UUID and name at the caller. If present and owned, recall it. If present but bound to another owner, fail closed.
8. If the deadline expires, remove the ticket, report timeout feedback, and leave the authority ACTIVE at its last known position.
9. A rejected `addFreshEntity` restores the exact previous authority state and position (an existing ACTIVE record is not rewritten as DORMANT).

### Dismiss

1. Locate the live entity using the visible index and the public section query, and verify the owner.
2. If present and owned, mark the record DORMANT at the entity position, mark the data dirty, then discard the entity.
3. If no live entity is visible, mark the record DORMANT immediately and queue a hidden-projection fence: a temporary ticket on the recorded chunk plus a bounded 200-tick request that discards any companion with that UUID once the chunk's entity sections load. This fences a projection that already joined a hidden section; the join event will not fire for it again.
4. If a visible entity is bound to another owner, fail closed. The authority is not changed.
5. Dismiss is cross-dimension-safe: it can discard a located copy in another dimension and mark the authority DORMANT.

### Load fence, legacy copies, and server start

Every `CompanionEntity` that joins a server level is checked against the authority:

- an entity read from a region file (`EntityJoinLevelEvent.loadedFromDisk()`) is a legacy saved projection and is always discarded; the authority re-projects the identity if the record is ACTIVE;
- no record, a DORMANT record, an owner mismatch, or a dimension mismatch: the join is canceled and the entity is discarded;
- an ACTIVE record with the matching UUID, owner, and dimension: allowed, and its owner and custom name are reapplied from the record.

On server start, loaded companion entities are reconciled with the same dimension-aware rule. Stale projections are discarded; matching projections have their owner and name reapplied from the record. Start reconciliation also restores an ACTIVE record whose recorded chunk is already loaded **and has a nearby player**, so it never loads a chunk or projects into a hidden, player-less area.

### Restoration after restart and chunk unload

Because the entity type is transient, a chunk unload removes the projection and a restart starts with no projection. The authority keeps the identity. Restoration is bounded and never loads chunks:

- **Player login** (`PlayerEvent.PlayerLoggedInEvent`): if the owner's record is ACTIVE, the recorded chunk is near the player, and no projection exists, the companion is restored from the record. A DORMANT record only fences stray copies.
- **Chunk load** (`ChunkEvent.Load`): the loaded chunk is queued and processed after the server tick (`ServerTickEvent.Post`), never inside chunk generation. If an ACTIVE record names that chunk and a player is near enough for the entity to be tracked, the companion is projected at the recorded position. If no player is near, nothing is projected into the hidden area.
- **Server start**: reconciles loaded projections and restores only records whose chunk is already loaded with a nearby player.

A restore also waits for `areEntitiesLoaded(chunkPos)` and retries from the queue instead of spawning beside an already-joined hidden projection. The restore checks the record again at execution time, so a dismiss or recall that happened after the chunk load is not undone. The tick handler returns immediately when the queue and pending maps are empty; it never scans chunks, never loads a chunk, and never ticks entities.

### Crash windows

The record is the durable intent and the entity is never written, so the ordinary crash windows do not produce a second companion:

- **Crash after the record is written but before the entity is added:** the record is ACTIVE with no projection. The next login, chunk load near the record, or summon re-projects the same UUID.
- **Crash after the entity is added but before the record is flushed:** after restart the transient entity is gone; if the record was not flushed, the identity is lost for that crash window. No orphan entity survives because no entity was written.
- **Crash after a dismiss record is written but before the entity is discarded:** after restart the transient entity is gone. No projection survives.
- **Crash after the entity is discarded but before the dismiss record is flushed:** the record remains ACTIVE with no projection. The next login, chunk load near the record, or summon re-projects the same UUID.

### Residual risks

These are known limits, not hidden behaviour:

1. **A saved-data file that is deleted while a companion is loaded.** The transient projection is removed by the join fence or chunk unload. The companion identity is lost; no duplicate survives. The mod does not claim immunity to an administrator deleting all records.
2. **An administrator or another mod moves the companion to another dimension.** The next join/reconcile/lookup sees a dimension mismatch, discards the stale copy, and re-projects from the record if it is ACTIVE. Commands and other mods can create this state; M1 has no AI or world interaction that moves the companion.
3. **A corrupted or duplicate record blocks all owners.** A corrupt or duplicate record sets a global fail-closed flag. The raw record is preserved and re-written, but charm operations are refused for every owner until an administrator repairs the data. This is deliberately conservative.
4. **Old schema data is inert.** A `DataVersion` other than `2` is preserved verbatim and never modified. The mod does not migrate it and does not silently reset it. An administrator must remove or repair old data before the charm can be used again.
5. **Legacy saved projections are discarded.** A companion entity saved by an earlier build is fenced when it loads, because the entity type is now transient. If the authority record is ACTIVE, the identity is re-projected; if there is no record, the identity is lost. Unknown SavedData is still preserved.
6. **Automatic restoration needs a qualifying event and a nearby player.** An absent ACTIVE companion is restored on a qualifying owner login or player-near chunk-load event, or by using the charm. Merely walking into an already-loaded spawn or forced chunk does not trigger restoration; use the charm if the companion remains absent. The authority retains its identity and ACTIVE state. The mod does not load chunks for automatic restoration or project into hidden areas.
7. **`/summon` is not a supported creation path.** The join fence cancels a companion entity that has no active authority record, so an administrator `/summon` does not create a managed companion. The supported creation path is the charm. `/kill` still removes a managed companion; the next restore or charm use re-projects the same UUID.
8. **Cross-dimension recall of an ACTIVE record is unsupported.** Recall reports the limitation before any entity lookup and makes no change. Dismiss can still discard it if it can be located. A DORMANT record can be summoned in the owner's current dimension.
9. **A saved-data file that cannot be parsed at the NBT level is outside this mod's recovery logic.** The mod preserves unknown schema versions and malformed records that it can read, but a file that vanilla cannot deserialize is handled by the game before the mod's loader runs.
10. **A pending recall or dismiss fence has a bounded deadline and one ticket.** Recall waits at most 100 ticks; the dismiss fence waits at most 200 ticks. Each holds one recorded chunk loaded at entity-ticking level until it resolves, is cancelled, times out, or the server stops. A timeout leaves the authority unchanged (recall) or dormant (dismiss) and logs the unresolved fence.
11. **A hidden projection is searched only in its recorded chunk.** The public section query covers that chunk. A projection moved by an external tool to a different chunk without an authority update is not found until it becomes visible; the engine's per-level UUID uniqueness still prevents a second same-level projection.
12. **A crash before the SavedData flush can lose the latest record update.** This includes a new identity, a recall position, or a dismissal. The entity is never written, so no orphan entity survives. A global corrupt or duplicate record still blocks all owners until an administrator repairs the data; there is no automatic or in-game manual recovery command.

The implementation does not claim to be crash-proof. It is designed so the ordinary crash, restart, and unload windows either re-project the authoritative identity or leave the authority unchanged rather than silently spawning a second companion. It has not been tested with a real client, a real two-player session, a scheduled chunk unload under a client, a full process restart, crash injection, or ATM10.

## 4. Baseline correction

The earlier [implementation baseline](implementation-baseline.md) said, in section 6, to rebuild a companion from stored NBT when the record exists but the entity cannot be found. The previous M1 implementation stored a dormant entity snapshot and treated an absent active entity as an unrecoverable inconsistency.

The repaired model supersedes both:

- the SavedData record is the sole authority for identity, name, and active/dormant state;
- the entity type is transient (`noSave`), so there are no entity snapshots, dormant or otherwise, and no independently saved entity copies;
- a missing active projection is re-created from the record, not from stored entity NBT;
- an entity without a matching active record, or in a dimension the record does not name, is fenced on join, not adopted;
- an entity read from a region file is a legacy copy and is fenced regardless of the record;
- an absent entity after the loaded-chunk/entity-section fence is safe to re-project;
- an uncertain entity-section load becomes one bounded pending recall with a temporary ticket, never an immediate spawn;
- restoration is triggered by login, a nearby chunk load, or start reconciliation and never loads chunks or scans all chunks per tick;
- unknown and old schemas are inert and preserved, never reset.

This supersedes baseline section 6's rebuild rule. The rest of the baseline remains the research reference.

## 5. Evidence

### Build and unit tests

```text
BUILD SUCCESSFUL
```

Test classes and results:

| Test class | Tests | Failures |
| --- | --- | --- |
| `CompanionRecordTest` | 5 | 0 |
| `CompanionSavedDataTest` | 5 | 0 |
| `CompanionPolicyTest` | 8 | 0 |

The unit tests cover:

- record round-trip preserves identity, name, state, and position;
- records contain no `Entity` snapshot;
- unknown record versions and missing names or positions are rejected;
- unknown schema roots are inert and preserved;
- old schema roots are inert and preserved;
- a wrong-typed `Companions` list (the exact `getList` element-type trap) is inert and preserved verbatim;
- a non-list `Companions` tag is inert and preserved;
- corrupt records are quarantined and preserved;
- active + present recalls; active + absent projects; active + unknown fails closed;
- dormant + absent projects; dormant + present respawns; dormant + unknown fails closed;
- owner mismatch fails closed for summon and dismiss;
- no record creates a new companion;
- dismiss of an absent active record marks it dormant; dismiss of a dormant or missing record is a no-op;
- unknown or corrupt data always fails closed.

### Dedicated-server GameTest harness

The dev-only harness is `src/gametest/java/com/bharatcode/companion/gametest/CompanionLifecycleGameTests.java`. It drives the production `SummoningCharmItem`, `CompanionService`, production `DimensionDataStorage`, and production NeoForge events with real, non-fake `ServerPlayer`s. It is excluded from the published jar.

The players are created with the engine's `GameTestHelper.makeMockServerPlayerInLevel()`. That helper is deprecated for removal in vanilla, but it constructs a real `ServerPlayer` with an embedded connection and adds it through the normal player-list path; it is not a `FakePlayer`, and no production `FakePlayer` check was weakened. The harness is a development test hook, not a shipped command.

The run loads the production main classes from the dev source set, not the packaged jar. The jar is built from the same main classes and was checked to exclude the harness and its structure resource. A production-jar install test remains a manual step.

Command:

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.13+9"
./gradlew runGameTestServer
```

Observed:

```text
20 GAME TESTS COMPLETE IN 7.132 s
All 20 required tests passed :)
```

The final `./gradlew test runGameTestServer build` run succeeded in 28 seconds on Java 21. Its full output is `/tmp/sol-finish-final.log`. The immediately preceding run also passed all 20 GameTests. This replaces the earlier 16-test evidence for the current artifact.

### Playtest review fixes (R1 through R5)

Preserved work from the cancelled worker included the explicit owner bounding-box exclusion and expanded engine tests. The remaining production change cancels a pending recall when its owner becomes a spectator. No persistence or recovery redesign was introduced.

The first preserved suite passed despite the missing spectator guard: synchronous tests invoked server-start reconciliation and cleared other tests' pending requests. Separating pending scenarios into batches exposed the bug. `/tmp/sol-finish-red.log` records `a spectator owner's pending recall moved a companion beside the owner`; the final run passes with the guard in place. Batch isolation remains in the harness so reconciliation cannot hide these failures.

The outstanding Nether-fence scenario prepares and unloads terrain before the action deadline begins. The test still joins the old copy in a hidden section, dismisses, and resummons immediately before cleanup. It requires the old copy's removal reason to be `DISCARDED`, waits for the old entity sections on reload, and verifies the new Overworld projection survives. Generating a never-visited Nether area within the 200-tick production deadline is not established by this test.

The rejected-spawn listener now cancels exactly one real production `addFreshEntity` join event and asserts that attempt/cancellation count before checking rollback and survival of the original UUID holder. It unregisters in a `finally` block. The recorded test chunk is held loaded while its entity sections become ready. The unrelated chunk-load restoration check now polls up to 100 ticks instead of assuming asynchronous loading always finishes in 20 ticks.

The twenty tests are:

| Test | What it exercises |
| --- | --- |
| `charmLifecycleKeepsIdentity` | summon creates a record and projection; recall keeps the UUID and does not duplicate; dismiss marks the record dormant and removes the projection; resummon keeps the UUID and name and creates exactly one projection |
| `secondOwnerIsSeparate` | two owners get distinct companion UUIDs; dismissing the first does not touch the second |
| `authorityRoundTripsAndFencesStaleProjection` | the authority serializes and reloads with identity, name, and state; the serialized record has no entity snapshot; server-start reconciliation keeps a matching projection; a dormant projection is rejected by the join fence; a later summon re-projects the same UUID without a duplicate |
| `missingProjectionIsReprojectedFromAuthority` | removing the live projection without changing the record leaves the authority ACTIVE; the next summon re-projects the same UUID and does not duplicate |
| `remoteRecallFindsHiddenJoinedProjection` | exactly one recall action reuses the actual hidden joined object within 100 ticks while the owner stays remote; UUID, owner exclusion, and no duplicate on old-area reload are asserted |
| `remoteRecallAfterEngineUnloadKeepsIdentity` | summons a real projection in a remote player-loaded chunk, moves the owner away, waits for `UNLOADED_TO_CHUNK` and actual chunk unload (never discards it), then recalls from exactly one action within 100 ticks; UUID and old-area uniqueness survive |
| `spectatorModeCancelsPendingRecall` | switches a non-fake test player to spectator after queueing recall; no teleport/reprojection or authority-position update occurs through 120 ticks |
| `remoteDismissWorksAfterRecordedChunkLoadsWithoutOwner` | a distant ACTIVE record whose chunk has no owner nearby is dismissed: the authority becomes DORMANT without a visible projection |
| `immediateCrossDimensionResummonSurvivesOutstandingFence` | dismisses a hidden joined Nether copy, immediately resummons in the Overworld while the fence is pending, then requires the old copy to be discarded and the new one to survive old-area reload |
| `dormantCrossDimensionSummonProjectsInOwnerDimension` | a DORMANT record last seen in the Nether is summoned by an owner in the Overworld; the projection and authority move to the owner's dimension |
| `activeCrossDimensionRecallRefusesBeforeProjectionLookup` | an ACTIVE record in another dimension is refused before entity lookup and no projection is created |
| `companionEntityIsTransient` | the actual registered companion entity type has `canSerialize()` false, `getEncodeId()` null, and a normal `Entity.save(CompoundTag)` returns false and writes an empty tag |
| `reloadedAuthorityRestoresActiveAndNotDormant` | the authority is serialized, reloaded, installed as the actual production `DimensionDataStorage` entry, and the production server-start path restores the ACTIVE companion with the same UUID, owner, and name and no duplicate; a DORMANT record restores nothing |
| `staleCrossDimensionProjectionIsRejected` | a copy in a dimension the authority does not name is rejected on join; start reconciliation discards a live copy after the authority moves to another dimension; the authority re-projects in its own dimension without a duplicate |
| `chunkLoadRestoresActiveProjection` | the authority is reloaded and installed, the player walks to the recorded area, the chunk loads with the player nearby, and the production chunk-load queue restores the ACTIVE companion with the same UUID, owner, and name and no duplicate |
| `legacySavedProjectionIsFenced` | a production `EntityJoinLevelEvent` with `loadedFromDisk() == true` is canceled and the legacy entity is discarded even when the active record matches |
| `summonPlacesCompanionBesideOwner` | the summoned projection is placed without intersecting the owner or world collision |
| `rejectedSpawnAttemptsProductionAddAndRestoresAuthority` | test-only join cancellation proves one production add was attempted/rejected, then checks exact prior ACTIVE state/dimension/position and that the original hidden UUID holder survives |
| `summonAtBlockCornerDoesNotOverlapOwner` | owner local x/z are 0.01; the overlapping first candidate is rejected explicitly |
| `blockedPlacementPreservesPositionAndAuthority` | blocks all placement candidates; failed recall keeps the live position, UUID, ACTIVE state, dimension, and authority position unchanged |

The transient-entity boundary produced this evidence:

```text
Restored active companion 7a9e5eaa-9b4e-456a-93c6-7562f8feadef for owner ... in minecraft:overworld at -7057978.5, 200.0, 5309528.5
Discarding legacy saved companion projection 65aeb66c-d5ea-4f9a-b0dc-5b624c502230 in minecraft:overworld: the entity type is transient
Fencing companion projection 7620507e-e056-440e-8f88-804308b0eea5 for owner ... in minecraft:the_nether: no matching active authority record
Discarding stale companion projection 7620507e-e056-440e-8f88-804308b0eea5 in minecraft:overworld after server start
```

The pending recall and hidden-projection fence produced this runtime evidence:

```text
[Server thread/INFO] [com.bharatcode.companion.server.CompanionService/]: Queued remote recall for companion 85275f8a-ee49-471d-98ce-a14bd1e1bb33 at 855934, 757297 in minecraft:overworld
[Server thread/INFO] [com.bharatcode.companion.server.CompanionService/]: Queued hidden-projection fence for companion e7fd28d9-a836-42b3-8f14-3a560924de8e at 855935, 757298 in minecraft:overworld
[Server thread/WARN] [com.bharatcode.companion.server.CompanionService/]: Discarding hidden dormant companion projection 8b59bf38-c5b3-4065-894e-1ac7c5ba6f00 in minecraft:overworld
```

### Entity region inspection

Before the repair, the disposable `run-gametest/` world contained a pre-fix entity region file (`r.28683.28094.mca`) with four `bharatcode_companion:companion` entities in two chunks. That is the independently saved entity copy the review found.

After the repair and several GameTest runs, a scan of all `run-gametest/world/entities/*.mca` files finds companion entities only in that same pre-fix file (four entries). None of the entity region files written by the repaired runs contains a companion. The pre-fix file was not loaded by these tests, so its entries are untouched legacy data; the `legacySavedProjectionIsFenced` test exercises the production fence for a disk-loaded copy, and the join fence discards it when the chunk does load.

### Published-jar isolation

```bash
unzip -l build/libs/bharatcode_companion-0.1.0.jar | grep -iE 'gametest|structure|Lifecycle'
```

No match. The published jar does not contain the GameTest class or the GameTest structure resource. The dev harness is on the `gametest` source set only.

The final artifact was also opened independently with Python's `zipfile`: every one of its 19 main class entries matched `build/classes/java/main` byte-for-byte, and no gametest/structure entry was present. SHA-256 and size were computed from the jar bytes, not inferred from a build report.

### Static isolation

```bash
grep -R "net.minecraft.client" -n src/main/java
```

Only `src/main/java/com/bharatcode/companion/client/` matched. The dedicated-server log contained no `NoClassDefFoundError` for client classes.

### Workspace state

- No git repository was initialized and no commit was made.
- The design documents, prototype, and baseline were not modified.
- The disposable `run-gametest/` world and its temporary `server.properties` are not part of the artifact.
- The pre-repair `run/` world was left untouched.
- No `eula.txt` was created in `run-gametest/`. The development runtime's IDE/GameTest hook auto-agrees; this workspace did not accept or write an EULA file.
- The user's Minecraft installation and worlds were not touched.

## 6. Manual checks not executed

These are required before any broader "works in ATM10" or "gameplay verified" claim:

1. Craft the charm from four iron ingots and one redstone in a 3×3 grid and confirm it appears in the recipe book.
2. Right-click the charm and confirm exactly one companion appears.
3. Right-click again and confirm the same UUID moves to the player instead of duplicating.
4. Sneak + right-click and confirm zero companions and the dismissed message.
5. Summon again and compare UUID and custom name with the first summon.
6. Quit and rejoin; summon and compare UUID.
7. Stop and restart the server while the companion is summoned; confirm the transient projection is restored on login or when the recorded chunk loads with the player nearby, and compare the UUID.
8. Dismiss, restart, and confirm it stays dismissed until summoned, then returns with the same UUID.
9. Leave the area until the chunk unloads, return, and confirm the projection is restored from the record with no duplicate and no loss.
10. Use a second player: confirm they cannot dismiss the first player's companion and their own first summon creates a separate companion.
11. Teleport the owner about 1,000 blocks away from the companion; recall and confirm the pending feedback, the eventual recall beside the owner, and no duplicate. Then dismiss and resummon, return to the old area, and confirm no duplicate remains there.
12. Confirm spectator use does nothing.
13. Compare world screenshots before and after a session; no block changes.
14. Corrupt or delete a saved-data record in a disposable world and confirm the server logs the problem, preserves the raw data where readable, and does not spawn a duplicate.
15. Launch a client and confirm the companion renders with the Steve texture, has no missing model or renderer error, and shows the custom name.
16. Install the jar in an ATM10 8.5 instance and repeat the relevant checks. This is the only way to support an ATM10 compatibility claim.

Checks 1-16 have not been repeated against this replacement jar. The user's earlier client playtests of the prior jar reported remote UNKNOWN responses, visibility-dependent dimension refusal, and owner overlap; those reports motivated these fixes and do not certify the replacement. The GameTest harness is runtime evidence for the server-side lifecycle paths it covers, including a real engine chunk unload, bounded remote recall, and the hidden-projection dismiss fence. It is not a fresh client, scheduled-chunk-unload-under-a-client, full-process-restart, crash-injection, or ATM10 test. The authority serialize/load/install and production restore hooks are exercised, but the JVM and client are not restarted within a scenario.

## 7. Next steps

1. Run the client and the two-player manual checklist above and capture UUIDs, logs, and screenshots.
2. Add a real scheduled chunk unload under a client, a 1,000-block pending recall, and a full process restart to the manual evidence; the current harness reloads the authority into the production storage and exercises the production restore hooks, but does not restart the JVM.
3. Test in an actual ATM10 8.5 instance. Until then, describe the mod as targeting that manifest, not as ATM10-compatible.
4. Add an explicit administrator recovery path for a fail-closed record, if the manual tests show that `/kill` or a data repair is too blunt.
5. Only after M1 checks pass, add follow/home movement (M2), then world-editing work with protection verification (M4). Do not treat this milestone as evidence that protection or world-edit safety works.
