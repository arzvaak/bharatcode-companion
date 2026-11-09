# BharatCode Companion: M1 foundation

A NeoForge 1.21.1 mod that adds one reusable summoning charm and one owner-bound, invulnerable humanoid companion whose identity persists across sessions through a SavedData record. This is the smallest buildable/loadable foundation, not the full BharatCode builder.

## Status

- Build: green.
- Unit tests: 18/18 passing.
- Dedicated-server lifecycle harness: 20/20 GameTests passing on the NeoForge `gameTestServer` run. The harness drives the production charm item, `CompanionService`, production `DimensionDataStorage`, and production NeoForge events with real (non-fake) `ServerPlayer`s.
- Persistence boundary: one versioned SavedData record is the sole authority for companion identity, name, and active/dormant state. The entity type is registered with `noSave`, so a live companion is a transient projection that is never written to an entity region file. There are no entity snapshots.
- Restoration: an ACTIVE projection is re-created from the record on player login, on a nearby chunk load, and during start reconciliation. DORMANT records restore nothing. A legacy entity loaded from a region file is fenced. Join, reconciliation, and lookup reject a copy whose dimension does not match the record. A remote recall whose recorded chunk's entity sections are still loading is queued for at most 100 ticks with a temporary entity-ticking ticket, then resolved or reported as timed out. A dismiss marks the authority dormant immediately and fences a hidden, already-joined projection with a temporary ticket for at most 200 ticks. Cross-dimension recall of an ACTIVE record is refused from the record before any entity lookup; a DORMANT record can be summoned in the owner's current dimension.
- Not executed: client rendering, two real client players, a scheduled chunk unload under a client, a full JVM restart inside the harness, crash injection, or an ATM10 instance. See [implementation status](docs/implementation-status.md).
- No universal pack compatibility claim. The target is the ATM10 8.5 manifest (Minecraft 1.21.1, NeoForge 21.1.265), but the mod has not been run inside ATM10.

## Target

| Component | Pin |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| ModDevGradle | 2.0.91 |
| Gradle wrapper | 8.14.2 |
| Java toolchain | 21 |

## Build

Gradle 8.14.2 cannot run on Java 25. Point `JAVA_HOME` at a JDK 21. This workspace used a locally installed Temurin 21.0.13+9; no system Java was replaced.

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.13+9"   # any JDK 21
./gradlew --version
./gradlew build
./gradlew test
./gradlew runGameTestServer   # dev-only lifecycle harness
# optional clean rebuild
./gradlew clean build
```

`./gradlew --version` must report Gradle 8.14.2 and a Java 21 JVM. The first build downloads Minecraft, NeoForge, and their dependencies.

`runGameTestServer` uses the dev-only `gametest` source set and the disposable `run-gametest/` game directory. The harness is not part of the published jar.

The produced artifact is:

```text
build/libs/bharatcode_companion-0.1.0.jar
```

Current SHA-256 (44,807 bytes):

```text
843d40c60e56714eb33cbc3146d67c4a201c91136d11a052dd70ddfae6d4488a
```

This hash identifies the artifact built in this workspace. A rebuild can change archive timestamps and therefore the hash.

Recompute it after any rebuild:

```bash
sha256sum build/libs/bharatcode_companion-0.1.0.jar
```

## Install

Copy the jar into the `mods/` directory of a Minecraft 1.21.1 / NeoForge 21.1.265 instance:

```bash
cp build/libs/bharatcode_companion-0.1.0.jar /path/to/instance/mods/
```

This was not performed against ATM10. Do not treat the build as evidence of ATM10 compatibility.

## Use

1. Craft the charm from four iron ingots and one redstone:

   ```text
    I
   IRI
    I
   ```

2. Right-click with the charm to summon the companion. Right-click again to recall it to the owner.
3. Sneak + right-click to dismiss it. The item is reusable and is not consumed.
4. Each player gets their own companion. The owner is the player who used the charm.
5. The companion is stationary and invulnerable; its identity persists across sessions, while the live entity itself is transient and restored from the authority record. Normal damage is refused; administrators can still remove it with `/kill`.
6. Cross-dimension recall is not supported. The charm reports that limitation instead of spawning a duplicate.

Chat feedback is provided for summoned, recalled, dismissed, no-companion, wrong-dimension, inconsistent-state, and unreadable-data cases.

## Implemented

- One item: `bharatcode_companion:summoning_charm`, craftable and reusable.
- One entity: `bharatcode_companion:companion`, humanoid, invulnerable, no-despawn, owner-bound, no AI, no goals, no item pickup, no inventory, no world interaction.
- The entity type is transient (`EntityType.Builder.noSave()`): a normal `Entity.save` returns false, no companion is written to an entity region file, and the SavedData record is the only durable state.
- Server-only mutation. The client only reports the interaction result.
- One versioned SavedData record per player, holding the companion UUID, custom name, `ACTIVE`/`DORMANT` state, dimension, and last known position. This record is the sole authority; it contains no entity NBT.
- The live entity is a transient projection of that record. The record is updated in memory before the entity is added. Once the authority has been flushed to disk, it can restore the projection after a crash; an unflushed update may be lost, including a newly created identity or a recent dismissal.
- A join fence rejects a companion entity that has no matching active authority record, or whose owner, state, or dimension does not match. A dormant, orphan, or cross-dimension projection is discarded instead of being allowed to coexist with a re-projection.
- An entity read from a region file is treated as a legacy saved projection and is always discarded; an ACTIVE record re-projects the identity.
- Remote recall is a bounded pending request (one per owner, 100-tick deadline) backed by a temporary entity-ticking ticket on the recorded chunk. Each retry revalidates the owner, record, mode, dimension, and schema; the ticket is removed on completion, cancel, logout, timeout, stop, or a new action. An absent projection after the entity sections load is re-projected at the owner. The charm reports pending and timeout feedback.
- Dismiss marks the authority dormant immediately. A visible projection is discarded; if the projection may be joined but hidden, a temporary ticket and a 200-tick fence discard it once its entity sections load, so the old area keeps no dormant copy.
- Restoration is bounded and never loads chunks: player login and start reconciliation restore an ACTIVE record whose recorded chunk is loaded with a nearby player; a loaded chunk is queued and processed after the server tick, never inside chunk generation, and nothing is projected into a player-less hidden area. A restore waits for the chunk's entity sections and retries from the queue instead of spawning beside an already-joined projection.
- Cross-dimension recall of an ACTIVE record is refused from the record before lookup. A DORMANT record may be summoned in the owner's current dimension.
- Join, reconciliation, and lookup discard a copy whose dimension does not match the record.
- Unknown schemas, old schemas, malformed roots, and wrong-typed record lists are preserved verbatim and made inert. Corrupt records are quarantined and preserved. Nothing is silently reset.
- Built-in item model, texture, language file, recipe, and a Steve-textured humanoid renderer.
- Client classes isolated in the `client` package.

## Not implemented

No AI or BharatCode calls, chat commands, pathfinding, follow/stay/home, world edits, block placement or breaking, inventory or storage, item intake, gathering trips, crafting automation, protection or claim integration, settlements, GUI, config, trusted-player delegation, combat, spawn eggs, loot tables, advancements, additional localisation, or data migration.

The companion is not a builder yet. It is a stationary proof of ownership and persistence.

## Checks that were run

- `./gradlew build` completed successfully.
- `./gradlew test` ran 18 tests with no failures.
- `./gradlew test runGameTestServer build` succeeded on Java 21. The final run passed all 20 lifecycle GameTests (`All 20 required tests passed`). See [implementation status](docs/implementation-status.md) for the scenarios and limits.
- The harness asserts that the actual registered companion's normal entity save returns false and writes no data, that a serialized/reloaded authority installed in the production `DimensionDataStorage` restores ACTIVE but not DORMANT, that a stale cross-dimension copy is rejected on join and reconciliation, that a real chunk load restores the active projection, and that a disk-loaded legacy copy is fenced.
- Remote recall completes from exactly one charm action within 100 ticks while the owner stays remote, both for a hidden joined projection and after a formerly live projection is removed by a real engine chunk unload. Both preserve the UUID and check the reloaded old area for duplicates.
- Additional regressions cover spectator cancellation of a pending recall, block-corner owner exclusion, blocked-placement rollback, an event-cancelled production spawn attempt, and immediate cross-dimension resummon while the old hidden-copy fence remains outstanding.
- The harness log shows the pending recall ticket and resolution, the hidden-projection dismiss fence, the legacy fence, the cross-dimension join/reconciliation fences, and the restore log.
- The disposable world's pre-fix entity region file still contains four legacy companions; none of the entity region files written by the repaired runs contains a companion.
- Static check: `net.minecraft.client` imports exist only under `src/main/java/com/bharatcode/companion/client/`.
- The published jar contains no gametest classes or structure resources; all 19 packaged main class files match the compiled main output byte-for-byte.
- No `eula.txt` was created in `run-gametest/`; the development runtime auto-agrees through its IDE/gametest hook, not through a file accepted by this workspace.

## Checks that were not run

See the detailed list in [implementation status](docs/implementation-status.md). In short: no client launch, no real two-player client test, no scheduled chunk unload under a client, no full JVM restart inside the harness, no crash injection, and no ATM10 instance test. The harness reloads the authority and exercises the production restore hooks; it does not restart the process.

## Next steps

1. Run the client and the two-player manual checklist in [implementation status](docs/implementation-status.md).
2. Add a real scheduled chunk unload under a client and a full process restart to the manual evidence.
3. Test the jar in an actual ATM10 8.5 instance, including restart and unloaded-chunk recall.
4. Only after those checks pass, add follow/home movement and then world-editing work with protection verification.

## Documentation

- [Project specification](docs/project-spec.md) and [project task list](docs/project-tasks.md) cover the M1 acceptance gate through the builder, production, and engineering releases.
- [Implementation baseline](docs/implementation-baseline.md): verified ATM10 8.5 / NeoForge / Minecraft facts.
- [Implementation status](docs/implementation-status.md): lifecycle design, evidence, remaining risks, and manual checks.
- [Concepts brief](concepts_brief.md) and [product scope](PRODUCT.md): product direction.
