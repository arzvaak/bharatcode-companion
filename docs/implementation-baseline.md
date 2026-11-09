# Implementation baseline: loadable companion milestone (M1)

* **Status:** research baseline only. No mod code, Gradle project, build, or gameplay has been executed. Every version below was verified against the source named next to it; nothing here is a tested claim.
* **Date:** 2026-11-08
* **Scope:** first loadable NeoForge mod for ATM10: craftable reusable summon item, one owner-bound persistent companion, summon/dismiss with retained identity, safe minimal controls. No AI, world edits, gathering, storage, or protection integration.

This file resolves the "ATM10 release, manifest and loader build remain unverified" caveat in [the Goldfish disposition](goldfish-review-disposition.md) and the provisional-8.5 note in [the brief](../concepts_brief.md#L58-L62). It does not change any other design decision.

## 1. Verified target baseline

| Fact | Value | Source and how it was checked |
| --- | --- | --- |
| Pack | All the Mods 10, version **8.5** | Official changelog index lists 8.5 dated 2026-11-04: <https://raw.githubusercontent.com/AllTheMods/ATM-10/main/CHANGELOG.md>. 8.5 changes: <https://raw.githubusercontent.com/AllTheMods/ATM-10/main/changelogs/CHANGELOG-ATM10-8.4-8.5.md>. |
| Release status | **release**, file id 9431424, uploaded 2026-11-04T07:16Z | CurseForge file page (<https://www.curseforge.com/minecraft/modpacks/all-the-mods-10/files/9431424>) returns HTTP 403 to scripted fetches, so release type was read from the CFWidget mirror (<https://api.cfwidget.com/925200>), which reports `"type": "release"` for that file. Treat the mirror as secondary evidence; the pack's own manifest is primary. |
| Pack archive | `All-the-Mods-10-8.5.zip`, 205,881,742 bytes | CurseForge download API redirects to `https://edge.forgecdn.net/files/9431/424/All-the-Mods-10-8.5.zip`, which supports HTTP range requests (`accept-ranges: bytes`). |
| Minecraft | **1.21.1**, release, released 2024-08-08, `javaVersion.majorVersion = 21` | Mojang version manifest <https://piston-meta.mojang.com/mc/game/version_manifest_v2.json> → <https://piston-meta.mojang.com/v1/packages/82ba6501da174a665c1787775f946487422f2432/1.21.1.json>. |
| NeoForge | **21.1.265** (`neoforge-21.1.265`, primary loader) | Read from the pack's own `manifest.json` inside the 8.5 zip (method in Appendix A). Artifact confirmed present: <https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-userdev.jar> (HTTP 200, 3,997,110 bytes). `21.1.265` is also the newest build in the 21.1 line of <https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml>. |
| Pack contents | 498 required mods, `recommendedRam: 8196`, `overrides/` present | Same extracted `manifest.json`. |
| Extracted manifest hash | `sha256 d95b3d7a994a6ec19759b82837d3f37845ee8373906d39be27f52df4e27790d6` | `sha256sum` of the extracted 54,336-byte `manifest.json`. |

Notes:

* The pack's `manifest.json` is the authoritative statement of the loader build; the CurseForge page itself does not expose it. Re-extract it whenever the pack version changes instead of trusting this snapshot.
* ATM10 is a moving pack. 8.5 is the pin for this milestone; 8.4 (2026-10-29) and earlier are not supported targets.
* The brief's earlier statement that 8.5 was "provisional" is now superseded for release status and manifest. It remains true that no ATM10 gameplay compatibility has been tested.

## 2. Milestone M1: in scope and out of scope

**In scope**

1. A NeoForge 1.21.1 mod that loads on a dedicated server and on a client connected to it.
2. One item, `bharatcode_companion:summoning_charm`, craftable from a vanilla recipe, reusable and not consumed.
3. One entity type, `bharatcode_companion:companion`: player-shaped, invulnerable, persistent, non-despawning, stationary.
4. Server-authoritative owner binding: one companion per owner, identified by the owner's UUID.
5. Summon and dismiss that preserve identity (entity UUID, custom name, owner) across dismiss/summon, world restart, and server restart.
6. Safe minimal controls: right-click summons or recalls; sneak + right-click dismisses. Chat feedback for success and failure.
7. Build, load, and behaviour checks listed in section 7.

**Out of scope (explicit)**

AI or BharatCode calls, addressed chat, pathfinding or follow goals, block placement/breaking, any world edit, inventory or carrying, gathering trips, crafting/smelting automation, protection/claim integration (FTB Chunks or anything else), settlements, previews, GUI screens, config files, trusted-player delegation, combat, spawn eggs, loot tables, advancements, localisation beyond `en_US`, data migration.

Because M1 makes no world edits, the brief's fail-closed protection rule ([concepts_brief.md](../concepts_brief.md#L35)) is not exercised here. It becomes a hard gate on the first milestone that changes blocks; do not treat M1 as evidence that protection handling works.

## 3. Chosen identifiers

| Kind | Identifier | Notes |
| --- | --- | --- |
| Mod id / namespace | `bharatcode_companion` | Fits NeoForge's `[a-z][a-z0-9_]{1,63}` rule. No registry, Modrinth, or CurseForge collision check has been run. |
| Group / base package | `com.bharatcode.companion` | Provisional; cheap to change before any artifact is published. |
| Item | `bharatcode_companion:summoning_charm` | Lang key `item.bharatcode_companion.summoning_charm`. |
| Entity | `bharatcode_companion:companion` | Lang key `entity.bharatcode_companion.companion`. |
| Saved data id | `bharatcode_companion_companions` | Key in the overworld `DimensionDataStorage`. |
| Recipe | `bharatcode_companion:summoning_charm` at `data/bharatcode_companion/recipe/summoning_charm.json` | 1.21.1 uses the singular `recipe/` directory (verified against vanilla 1.21.1 data, Appendix A). |
| Creative tab | `CreativeModeTabs.TOOLS_AND_UTILITIES` | Added via `BuildCreativeModeTabContentsEvent`; no custom tab in M1. |
| Default companion name | `Companion` | Stored as `CustomName` in NBT; renaming is deferred. |
| Mod version | `0.1.0` | `mod_version` in `gradle.properties`; jar name `bharatcode_companion-0.1.0.jar`. |
| Message keys | `message.bharatcode_companion.summoned`, `.recalled`, `.dismissed`, `.no_companion`, `.error` | Server sends translatable components. |

## 4. Recommended pins

| Component | Pin | Rationale / source |
| --- | --- | --- |
| Minecraft | `1.21.1` | Pack manifest; Mojang metadata. |
| NeoForge | `21.1.265` | The pack's exact loader. The official MDK 1.21.1 template still pins `21.1.176`; override it so the dev runtime matches the pack. |
| ModDevGradle | `2.0.91` | Pin used by the official 1.21.1 MDK template (`build.gradle` on branch `archive/1.21-mdg`). Newer 2.0.x exists (latest `2.0.148` in <https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml>) but is not the template-tested pair for 1.21.1. |
| Gradle wrapper | `8.14.2` | MDK 1.21.1 wrapper (`gradle/wrapper/gradle-wrapper.properties`). MDG itself requires Gradle 8.8+ and Java 17+ to run. |
| Java toolchain | `21` | MC 1.21.1 ships `java-runtime-delta` (major 21); NeoForge 1.21.1 docs require JDK 21: <https://docs.neoforged.net/docs/1.21.1/gettingstarted/>. Set `java.toolchain.languageVersion = JavaLanguageVersion.of(21)`. |
| Parchment | `1.21.1` / `2024.11.17` | MDK default; optional for M1 but harmless. |
| foojay resolver | `1.0.0` | MDK `settings.gradle`; enables toolchain auto-provisioning. |
| Version ranges | MC `[1.21.1,1.22)`, NeoForge `[21.1.0,)`, loader `[4,)` | MDK `gradle.properties`; keep in `neoforge.mods.toml` and `gradle.properties`. |

## 5. Toolchain constraints and current host state

Observed on this machine (read-only inspection):

* JDK **25.0.4.1** is installed at `/usr/lib/jvm/java-25-openjdk`; `JAVA_HOME` is unset. There is no JDK 21 in `/usr/lib/jvm`.
* No `gradle` and no `mvn` on `PATH`.
* A Gradle user home exists at `/home/sushi/.gradle` with cached distributions `gradle-9.3.1-all` and `gradle-9.4.1-bin`, and one auto-provisioned **Temurin 17.0.21** JDK at `~/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2`.
* `/mnt/kingston` has 323 GB free; 60 GB RAM (36 GB available). `recommendedRam` for the pack is 8 GB.
* `/mnt/kingston/MCBC` is **not a git repository**. There is no version control to diff against; initialise one before writing code if reviewable diffs matter.
* Network endpoints reachable: `services.gradle.org` (redirects to GitHub), `maven.neoforged.net`, `repo1.maven.org`, `piston-meta.mojang.com`, `edge.forgecdn.net`, `github.com`.

The binding constraint: **Gradle 8.14.2 cannot run on Java 25.** Per the Gradle compatibility table (<https://docs.gradle.org/current/userguide/compatibility.html>), Java 25 requires Gradle 9.1.0+ to run; Java 21 needs Gradle 8.5+. Two viable paths, in order of preference:

**Path A (recommended, matches the template).** Install Temurin 21 and run the 8.14.2 wrapper with it.

```bash
mkdir -p ~/.local/opt
curl -L -o /tmp/jdk21.tar.gz 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz'
tar xzf /tmp/jdk21.tar.gz -C ~/.local/opt   # -> ~/.local/opt/jdk-21.0.13+9
export JAVA_HOME=~/.local/opt/jdk-21.0.13+9
cd /mnt/kingston/MCBC && ./gradlew build
```

The Adoptium asset above was resolved from <https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse> on 2026-11-08 (semver `21.0.13+9.0.LTS`). Alternatively set `org.gradle.java.home` in `gradle.properties` (do not commit a machine-specific path).

**Path B (no install).** Run the 8.14.2 wrapper on the cached Temurin 17 and let the foojay resolver download the Java 21 toolchain:

```bash
JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ./gradlew build
```

This relies on toolchain auto-provisioning and on network access during the first build. It is acceptable for local iteration but Path A is the reproducible choice for CI.

**Path C (do not use without a spike).** Cached Gradle 9.4.1 on Java 25 with MDG 2.0.148. MDG's minimum is Gradle 8.8, but Gradle 9 support for this plugin/NeoForge pair is unverified; a throwaway spike in `/tmp` is required before adopting it.

First build cost: Gradle downloads Minecraft and NeoForge, decompiles sources, and prepares runs. Expect a long first run and several GB in `~/.gradle`.

## 6. Minimal design contract

The point of M1 is to prove the persistence and ownership model, not to build the companion. Keep every part below as small as it can be while still being testable.

**Registries.** `DeferredRegister` for items and entity types; one `@Mod` entry class; one client entry point or a `Dist.CLIENT`-annotated class for the renderer. Entity attributes via `EntityAttributeCreationEvent` (health 20, movement speed 0.3, follow range 32).

**Entity.** `CompanionEntity extends PathfinderMob`; `registerGoals()` empty; `MobCategory.CREATURE`; size 0.6 × 1.8; `setPersistenceRequired()`; `removeWhenFarAway` false; `hurt(...)` returns false and `isInvulnerableTo(...)` returns true so no damage source kills it; `setInvulnerable(true)` as belt and braces. `/kill` and entity removal still work — that is intended admin behaviour, not a bug. Owner UUID persisted in NBT under `Owner`. No block interaction, no item pickup, no movement goals.

**Item.** `SummoningCharmItem extends Item`; `use(Level, Player, InteractionHand)` handles both actions on the server and returns `InteractionResult.sidedSuccess(level.isClientSide)`. Spectators and fake players are no-ops. The item is never consumed and holds no per-instance state; ownership lives in the saved data, not on the stack.

**Saved data.** `CompanionSavedData extends SavedData` in the overworld `DimensionDataStorage`, id `bharatcode_companion_companions`. One record per owner UUID: companion UUID, dimension, last position, `dormant` flag, and the entity's NBT (`saveWithoutId` plus an explicit UUID entry) including `CustomName`. Write on summon, recall, and dismiss; `setDirty()` after every mutation.

**Summon / recall.** If the caller already has a registered companion, never spawn a second one: locate the existing entity (loading the chunk at its stored position when it is not loaded, as part of this explicit action), then teleport it to the caller and report "recalled". If the record exists but the entity cannot be found (killed, removed, or corrupt), rebuild from the stored NBT with the same UUID and log a warning. Only when no record exists is a new companion created.

**Dismiss.** Save the entity's NBT into the record, mark it dormant, discard the entity, report "dismissed". If no companion is registered or the record is already dormant, report `no_companion` and do nothing.

**Ownership.** Only the owner's own item use can dismiss that owner's companion. Summon always targets the caller's own record. Another player holding the same item gets their own companion; two players therefore produce two companions, one per owner. No delegation in M1.

**Fail-closed.** Inconsistent state must produce a message and no spawn, never a duplicate entity and never deletion of another owner's record. Corrupt NBT drops that record with a logged error rather than crashing the server.

**Client.** A placeholder renderer only: `HumanoidMobRenderer` with `PlayerModel` and the vanilla Steve texture, so the companion is visible and player-shaped. All client classes stay in a `client` package so the dedicated server never loads them.

**Resources.** Hand-written JSON, no datagen:

* `assets/bharatcode_companion/lang/en_us.json` — item, entity, and message keys.
* `assets/bharatcode_companion/models/item/summoning_charm.json` — `item/generated`, one texture.
* `assets/bharatcode_companion/textures/item/summoning_charm.png` — 16×16 placeholder.
* `data/bharatcode_companion/recipe/summoning_charm.json` — shaped, 4 iron ingots + 1 redstone:

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": [" I ", "IRI", " I "],
  "key": {
    "I": { "item": "minecraft:iron_ingot" },
    "R": { "item": "minecraft:redstone" }
  },
  "result": { "id": "bharatcode_companion:summoning_charm", "count": 1 }
}
```

`result.id` and the singular `recipe/` directory are the 1.21.1 format (verified against vanilla 1.21.1 data; the older `result.item` and `recipes/` forms do not apply here).

## 7. Acceptance checks

No check below has been run. Each needs captured evidence (log excerpt, command output, screenshot, or jar hash) before it counts as passed.

**Build and load**

1. `./gradlew --version` reports Gradle 8.14.2 and a Java 21 JVM.
2. `./gradlew build` ends `BUILD SUCCESSFUL`; `build/libs/bharatcode_companion-0.1.0.jar` exists; record its SHA-256.
3. `./gradlew runClient` reaches the main menu; the mod appears in the mod list; the log has no missing-registry, missing-model, or missing-renderer errors.
4. `./gradlew runServer` starts a dedicated server (accept `eula=true` in the run directory); a client connects; the mod is listed on both sides; no `NoClassDefFoundError` for client classes on the server.

**Behaviour (single-player or local server, recorded)**

5. Craft the charm from 4 iron ingots and 1 redstone in a 3×3 grid; the recipe appears in the recipe book.
6. Right-click with the charm: exactly one `bharatcode_companion:companion` exists, at the player. Count it with `/execute as @e[type=bharatcode_companion:companion] run data get entity @s UUID`.
7. Right-click again: still exactly one entity; it has moved to the player (recall), not duplicated.
8. Sneak + right-click: zero companions; message says dismissed.
9. Summon again: the UUID and custom name are identical to step 6. This is the identity-retention test.
10. Quit to title and rejoin; summon: same UUID again.
11. Stop and restart the server. If the companion was summoned, it is still present where it was left; summon and confirm the same UUID. Then dismiss, restart again, and confirm it stays dismissed until summoned, after which the same UUID returns.
12. Invulnerability: `/damage @e[type=bharatcode_companion:companion,limit=1] 100 minecraft:generic` leaves health unchanged and produces no death. Repeat with a hostile mob's melee attack.
13. Persistence: leave the area for more than five minutes, return; the companion still exists (no despawn).
14. Ownership: a second player cannot dismiss the first player's companion; their own first summon creates a second, separately owned companion.
15. Distance: teleport the owner ~1000 blocks away; sneak + right-click dismisses without a duplicate and without a crash; summon returns the same UUID at the owner.
16. Spectator mode: item use does nothing.
17. No world edits: compare screenshots before and after the whole run; no block changes.

**Robustness**

18. Delete or corrupt the saved-data entry, then summon: the server logs the error and creates a fresh companion without crashing and without a duplicate.
19. Kill the companion with `/kill`, then summon: it is rebuilt with the same UUID (or reported as rebuilt) and there is still only one.

**Static**

20. `grep -R "net.minecraft.client" src/main/java` matches only the `client` package.

Pass criteria: 1–4 must pass for "loadable"; 5–11 and 12–15 must pass for "summon/dismiss retains identity" and "owner-bound"; 17–20 guard against the failure modes most likely to be missed.

## 8. Implemented limits and next steps

Limits that will be true of M1 and must be stated in any report of it:

* No AI, no chat, no pathfinding, no follow/stay, no world edits, no inventory, no gathering, no crafting automation, no protection integration, no config, no GUI, no trusted-player delegation.
* The companion is stationary apart from explicit recall teleports. It is invulnerable by design; administrators can still remove it with `/kill`.
* Persistence stores raw entity NBT without a schema version. That is acceptable for one entity with no inventory; add a `dataVersion` field before storing player-visible items.
* Protection behaviour is untested because nothing in M1 touches the world. The first world-editing milestone must verify protection and stay disabled when it cannot, per the brief.

Next steps, in order:

1. Implement M1, run section 7, and record the evidence. Do not report "loadable" before checks 1–4 pass.
2. M2: movement and follow with a bounded recall, still no world edits.
3. M3: companion inventory/custody with crash-safe accounting.
4. M4: protection adapter with fail-closed default.
5. M5: addressed chat and the client-side AI route, with server-side validation.

Known risks: ATM10 releases move and will change the loader pin (re-extract the manifest); MDG/NeoForge API drift between 21.1.176 and 21.1.265; accidental client-class loading on the dedicated server; saved-data schema drift once items are stored; and the mod id has had no collision check.

## Appendix A: reproduction

Pack manifest extraction (no 205 MB download; the CDN supports ranges):

1. `HEAD` the CurseForge download URL and follow the redirects to `edge.forgecdn.net`; read `content-length` and `accept-ranges`.
2. Range-fetch the last 64 KB, find the ZIP end-of-central-directory record (`PK\x05\x06`), read the central-directory size and offset.
3. Range-fetch the central directory, locate the entry named `manifest.json`, read its local-header offset and compressed size.
4. Range-fetch that entry and inflate it (`zlib.decompress(raw, -15)`).
5. Verify `sha256sum` equals `d95b3d7a994a6ec19759b82837d3f37845ee8373906d39be27f52df4e27790d6`.

Vanilla 1.21.1 recipe shape was checked against <https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/recipe/crafting_table.json> (`result.id`, `key`/`pattern`, singular `recipe/`).

## Appendix B: sources

| Source | Used for |
| --- | --- |
| <https://github.com/AllTheMods/ATM-10> | Official pack repository; README states ATM10 is 1.21.1. |
| <https://raw.githubusercontent.com/AllTheMods/ATM-10/main/CHANGELOG.md> | 8.5 dated 2026-11-04. |
| <https://raw.githubusercontent.com/AllTheMods/ATM-10/main/changelogs/CHANGELOG-ATM10-8.4-8.5.md> | 8.5 change list. |
| <https://www.curseforge.com/minecraft/modpacks/all-the-mods-10/files/9431424> | Release file page (403 to scripted fetch). |
| <https://api.cfwidget.com/925200> | Mirror reporting the 8.5 file as `release`. |
| `https://edge.forgecdn.net/files/9431/424/All-the-Mods-10-8.5.zip` | Pack archive; source of `manifest.json`. |
| <https://piston-meta.mojang.com/mc/game/version_manifest_v2.json> and the 1.21.1 package JSON | MC version, release date, Java 21 requirement. |
| <https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml> | NeoForge 21.1.265 availability. |
| <https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml> | MDG versions up to 2.0.148. |
| <https://github.com/neoforged/MDK/tree/archive/1.21-mdg> | 1.21.1 template: MDG 2.0.91, Gradle 8.14.2, NeoForge 21.1.176, Parchment 2024.11.17, foojay 1.0.0. |
| <https://docs.neoforged.net/docs/1.21.1/gettingstarted/> | JDK 21 requirement for 1.21.1. |
| <https://docs.gradle.org/current/userguide/compatibility.html> | Java 25 needs Gradle 9.1.0+ to run. |
| <https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse> | Temurin 21.0.13+9 Linux x64 asset. |
| <https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/recipe/crafting_table.json> | 1.21.1 recipe JSON shape and `recipe/` path. |
