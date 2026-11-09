package com.bharatcode.companion.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.bharatcode.companion.BharatCodeCompanion;
import com.bharatcode.companion.data.CompanionRecord;
import com.bharatcode.companion.data.CompanionSavedData;
import com.bharatcode.companion.entity.CompanionEntity;
import com.bharatcode.companion.policy.CompanionPolicy;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-only owner authority and lifecycle. The SavedData record is the single
 * authority for identity, name, and active/dormant state. The entity type is
 * registered as noSave, so a live companion is a transient projection that is
 * fenced against the authority whenever it joins a level and recreated from the
 * record when its chunk loads again.
 */
public final class CompanionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_NAME = "Companion";

    /**
     * Chunks that loaded since the last server tick. Restores are deferred to
     * the end of the tick so they never run inside chunk generation. The set is
     * only touched from the server thread.
     */
    private static final Set<PendingChunk> PENDING_RESTORES = new LinkedHashSet<>();

    /**
     * A remote chunk is held with a temporary entity-ticking ticket while a
     * pending recall or hidden-projection fence waits for its entity sections.
     * Distance 2 gives the center chunk ticket level 31
     * ({@code ChunkLevel.ENTITY_TICKING_LEVEL}), so the sections become
     * accessible and load without a nearby player. The timeout is a safety net;
     * every completion path removes the ticket explicitly.
     */
    private static final int PENDING_TICKET_DISTANCE = 2;
    private static final int RECALL_DEADLINE_TICKS = 100;
    private static final int FENCE_DEADLINE_TICKS = 200;
    private static final TicketType<UUID> PENDING_TICKET =
            TicketType.create("bharatcode_companion_pending", Comparator.comparing(UUID::toString), 200);

    /**
     * One bounded remote-recall request per owner, waiting for the recorded
     * chunk's entity sections. Removed on completion, cancel, logout, timeout,
     * stop, or a new action for that owner.
     */
    private static final Map<UUID, PendingRequest> PENDING_RECALLS = new HashMap<>();

    /**
     * A dismiss fence is server-side cleanup, not a player request: it is keyed
     * by companion UUID and survives logout and a later charm action so an
     * already-joined hidden projection is still discarded. Removed on
     * completion, timeout, stop, or replacement.
     */
    private static final Map<UUID, PendingRequest> PENDING_FENCES = new HashMap<>();

    private CompanionService() {
    }

    public static void use(ServerPlayer player, boolean dismiss) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            message(player, "state_error");
            return;
        }
        cancelRecall(server, player.getUUID());

        CompanionSavedData data = CompanionSavedData.get(server);
        if (!data.isUsable()) {
            LOGGER.error("Refusing companion action for {}: data inert={}, corrupt={}", player.getUUID(),
                    data.isInert(), data.isCorrupt());
            message(player, "data_unavailable");
            return;
        }

        if (dismiss) {
            dismiss(player, server, data);
        } else {
            summon(player, server, data);
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        PENDING_RESTORES.clear();
        cancelAllPending(server);
        CompanionSavedData data = CompanionSavedData.get(server);
        if (!data.isUsable()) {
            LOGGER.error("Skipping companion reconciliation: saved data is not usable");
            return;
        }
        reconcile(server, data);
        restoreLoadedChunks(server, data);
    }

    /**
     * Load fence: a companion entity may only exist while the authority says it
     * is active, bound to the same owner, and placed in this dimension. Old or
     * corrupt data is left untouched so it can be repaired instead of being
     * silently deleted.
     */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof CompanionEntity companion)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        if (event.loadedFromDisk()) {
            // The entity type is noSave: a companion read from a region file is a
            // legacy projection, never the authority. Fence it and let the
            // authority re-project the identity from the record.
            LOGGER.warn("Discarding legacy saved companion projection {} in {}: the entity type is transient",
                    companion.getUUID(), level.dimension().location());
            companion.discard();
            event.setCanceled(true);
            return;
        }
        CompanionSavedData data = CompanionSavedData.get(server);
        if (!data.isUsable()) {
            return;
        }

        CompanionRecord record = data.byCompanion(companion.getUUID());
        if (record == null || record.state() != CompanionRecord.State.ACTIVE
                || !record.owner().equals(companion.getOwnerUUID())
                || !record.dimension().equals(level.dimension().location())) {
            LOGGER.warn("Fencing companion projection {} for owner {} in {}: no matching active authority record",
                    companion.getUUID(), companion.getOwnerUUID(), level.dimension().location());
            companion.discard();
            event.setCanceled(true);
            return;
        }

        applyAuthority(companion, record);
    }

    /**
     * A loaded chunk may hold an active companion whose transient projection
     * vanished with the previous chunk unload. The restore is queued and runs
     * after the server tick, never inside chunk generation.
     */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos = event.getChunk().getPos();
            PENDING_RESTORES.add(new PendingChunk(level.dimension(), pos.x, pos.z));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_RECALLS.isEmpty() && PENDING_FENCES.isEmpty() && PENDING_RESTORES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        CompanionSavedData data = CompanionSavedData.get(server);
        if (!data.isUsable()) {
            cancelAllPending(server);
            PENDING_RESTORES.clear();
            return;
        }

        if (!PENDING_RECALLS.isEmpty() || !PENDING_FENCES.isEmpty()) {
            tickPending(server, data);
        }
        if (!PENDING_RESTORES.isEmpty()) {
            List<PendingChunk> pending = new ArrayList<>(PENDING_RESTORES);
            PENDING_RESTORES.clear();
            for (PendingChunk chunk : pending) {
                ServerLevel level = server.getLevel(chunk.dimension());
                if (level == null || !level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
                    continue;
                }
                for (CompanionRecord record : data.records()) {
                    if (record.state() == CompanionRecord.State.ACTIVE
                            && record.dimension().equals(chunk.dimension().location())
                            && inChunk(record, chunk.x(), chunk.z())) {
                        ensureProjection(level, record, data);
                    }
                }
            }
        }
    }

    /**
     * Resolves at most one bounded request per owner. Recall revalidates the
     * owner, record, mode, dimension, and schema on every attempt; the fence
     * only needs the authority record and the ticketed chunk.
     */
    private static void tickPending(MinecraftServer server, CompanionSavedData data) {
        for (UUID owner : new ArrayList<>(PENDING_RECALLS.keySet())) {
            PendingRequest pending = PENDING_RECALLS.get(owner);
            if (pending == null) {
                continue;
            }
            if (server.getTickCount() > pending.deadlineTick()) {
                PENDING_RECALLS.remove(owner);
                removePendingTicket(server, pending);
                ServerPlayer player = server.getPlayerList().getPlayer(owner);
                if (player != null) {
                    message(player, "recall_timeout");
                }
                LOGGER.warn("Remote recall for companion {} timed out after {} ticks",
                        pending.companion(), RECALL_DEADLINE_TICKS);
                continue;
            }

            CompanionRecord record = data.get(owner);
            ServerLevel level = server.getLevel(pending.dimension());
            if (record == null || level == null || !record.companion().equals(pending.companion())) {
                PENDING_RECALLS.remove(owner);
                removePendingTicket(server, pending);
                continue;
            }
            tickRecall(server, data, owner, pending, record, level);
        }

        for (UUID companion : new ArrayList<>(PENDING_FENCES.keySet())) {
            PendingRequest pending = PENDING_FENCES.get(companion);
            if (pending == null) {
                continue;
            }
            if (server.getTickCount() > pending.deadlineTick()) {
                PENDING_FENCES.remove(companion);
                removePendingTicket(server, pending);
                LOGGER.warn("Hidden-projection fence for companion {} timed out; the authority stays dormant",
                        companion);
                continue;
            }
            CompanionRecord record = data.byCompanion(companion);
            ServerLevel level = server.getLevel(pending.dimension());
            if (record == null || level == null) {
                PENDING_FENCES.remove(companion);
                removePendingTicket(server, pending);
                continue;
            }
            tickDismissFence(server, companion, pending, record, level);
        }
    }

    private static void tickRecall(MinecraftServer server, CompanionSavedData data, UUID owner,
            PendingRequest pending, CompanionRecord record, ServerLevel level) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null || player.isSpectator()
                || record.state() != CompanionRecord.State.ACTIVE
                || !record.dimension().equals(pending.dimension().location())
                || !player.serverLevel().dimension().equals(pending.dimension())) {
            PENDING_RECALLS.remove(owner);
            removePendingTicket(server, pending);
            return;
        }
        if (!level.areEntitiesLoaded(pending.chunk().toLong())) {
            return;
        }

        Lookup lookup = find(server, record);
        if (lookup.presence() == CompanionPolicy.Presence.UNKNOWN) {
            return;
        }
        PENDING_RECALLS.remove(owner);
        removePendingTicket(server, pending);
        if (lookup.presence() == CompanionPolicy.Presence.PRESENT) {
            if (!(lookup.entity() instanceof CompanionEntity companion) || !isOwner(companion, owner)) {
                fail(player, "the located entity is not this owner's companion");
                return;
            }
            recall(player, data, record, companion);
            return;
        }
        project(player, data, record);
    }

    private static void tickDismissFence(MinecraftServer server, UUID companionId, PendingRequest pending,
            CompanionRecord record, ServerLevel level) {
        // If the authority was re-activated in the same dimension, the fenced
        // copy may now be the authoritative projection; leave it alone.
        if (record.state() == CompanionRecord.State.ACTIVE
                && record.dimension().equals(pending.dimension().location())) {
            PENDING_FENCES.remove(companionId);
            removePendingTicket(server, pending);
            return;
        }
        if (!level.areEntitiesLoaded(pending.chunk().toLong())) {
            return;
        }
        // Scope the fence to the recorded dimension/chunk, not the current
        // authority dimension: a resummon elsewhere must not strand the old
        // hidden copy.
        Entity live = findInChunk(level, pending.companion(), pending.chunk());
        if (live instanceof CompanionEntity projection) {
            LOGGER.warn("Discarding hidden dormant companion projection {} in {}", projection.getUUID(),
                    level.dimension().location());
            projection.discard();
        }
        PENDING_FENCES.remove(companionId);
        removePendingTicket(server, pending);
    }

    /**
     * Restores the owner's active projection when the recorded chunk is already
     * loaded with the owner nearby, which the chunk-load queue alone cannot
     * catch after a restart. A dormant record only fences stray copies.
     */
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CompanionSavedData data = CompanionSavedData.get(server);
        if (!data.isUsable()) {
            return;
        }
        CompanionRecord record = data.get(player.getUUID());
        if (record == null) {
            return;
        }
        if (record.state() == CompanionRecord.State.DORMANT) {
            Entity live = findLoadedProjection(server, record);
            if (live != null) {
                LOGGER.warn("Discarding dormant companion projection {} on owner login", live.getUUID());
                live.discard();
            }
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (level != null) {
            ensureProjection(level, record, data);
        }
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        cancelRecall(server, player.getUUID());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        cancelAllPending(event.getServer());
        PENDING_RESTORES.clear();
    }

    private static void reconcile(MinecraftServer server, CompanionSavedData data) {
        for (ServerLevel level : server.getAllLevels()) {
            List<CompanionEntity> loaded = new ArrayList<>();
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof CompanionEntity companion) {
                    loaded.add(companion);
                }
            }
            for (CompanionEntity companion : loaded) {
                CompanionRecord record = data.byCompanion(companion.getUUID());
                if (record == null || record.state() != CompanionRecord.State.ACTIVE
                        || !record.owner().equals(companion.getOwnerUUID())
                        || !record.dimension().equals(level.dimension().location())) {
                    LOGGER.warn("Discarding stale companion projection {} in {} after server start",
                            companion.getUUID(), level.dimension().location());
                    companion.discard();
                } else {
                    applyAuthority(companion, record);
                }
            }
        }
    }

    /**
     * Server start cannot load chunks, so it only restores active records whose
     * chunk is already loaded. Other records are restored by the chunk-load
     * queue when their chunk next loads.
     */
    private static void restoreLoadedChunks(MinecraftServer server, CompanionSavedData data) {
        for (CompanionRecord record : data.records()) {
            if (record.state() != CompanionRecord.State.ACTIVE) {
                continue;
            }
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, record.dimension()));
            if (level == null) {
                continue;
            }
            int chunkX = Mth.floor(record.x()) >> 4;
            int chunkZ = Mth.floor(record.z()) >> 4;
            if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                ensureProjection(level, record, data);
            }
        }
    }

    private static void ensureProjection(ServerLevel level, CompanionRecord record, CompanionSavedData data) {
        if (record.state() != CompanionRecord.State.ACTIVE
                || !record.dimension().equals(level.dimension().location())) {
            return;
        }
        ChunkPos chunk = chunkOf(record);
        // A chunk loaded without a nearby player keeps entities in hidden
        // sections that the visible-entity lookup cannot see. Only project where
        // a player's view distance makes the entity tracked; login and the
        // player-driven chunk loads cover the rest.
        if (!hasPlayerNear(level, chunk.x, chunk.z)) {
            return;
        }
        if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
            return;
        }
        // Entity sections load asynchronously after the chunk reaches FULL.
        // Spawning before they are loaded would miss a joined projection and be
        // rejected as a duplicate, so retry from the chunk queue next tick.
        if (!level.areEntitiesLoaded(chunk.toLong())) {
            PENDING_RESTORES.add(new PendingChunk(level.dimension(), chunk.x, chunk.z));
            return;
        }
        Entity existing = findLoadedProjection(level.getServer(), record);
        if (existing instanceof CompanionEntity companion) {
            applyAuthority(companion, record);
            return;
        }
        if (existing != null) {
            LOGGER.warn("Not restoring companion {} in {}: its UUID is used by {}",
                    record.companion(), level.dimension().location(), existing.getType());
            return;
        }
        if (spawnProjection(level, record, data, record.x(), record.y(), record.z())) {
            LOGGER.info("Restored active companion {} for owner {} in {} at {}, {}, {}",
                    record.companion(), record.owner(), level.dimension().location(), record.x(), record.y(),
                    record.z());
        }
    }

    private static void applyAuthority(CompanionEntity companion, CompanionRecord record) {
        companion.setOwnerUUID(record.owner());
        companion.setCustomName(Component.literal(record.name()));
        companion.setCustomNameVisible(true);
    }

    private static void summon(ServerPlayer player, MinecraftServer server, CompanionSavedData data) {
        CompanionRecord record = data.get(player.getUUID());
        if (record != null && record.state() == CompanionRecord.State.ACTIVE
                && !record.dimension().equals(player.serverLevel().dimension().location())) {
            message(player, "wrong_dimension");
            return;
        }
        // A dormant record is not a live authority: check only the chunks that
        // are already loaded and never force-load the stale recorded area.
        Lookup lookup;
        if (record == null) {
            lookup = new Lookup(CompanionPolicy.Presence.ABSENT, null);
        } else if (record.state() == CompanionRecord.State.DORMANT) {
            Entity loaded = findLoadedProjection(server, record);
            lookup = loaded == null
                    ? new Lookup(CompanionPolicy.Presence.ABSENT, null)
                    : new Lookup(CompanionPolicy.Presence.PRESENT, loaded);
        } else {
            lookup = find(server, record);
        }
        boolean ownerMatches = lookup.entity() == null || isOwner(lookup.entity(), player.getUUID());

        CompanionPolicy.Decision<CompanionPolicy.SummonAction> decision = CompanionPolicy.summon(
                data.isUsable(), stateOf(record), lookup.presence(), ownerMatches);

        switch (decision.action()) {
            case CREATE -> create(player, data);
            case RECALL -> recall(player, data, record, lookup.entity());
            case PROJECT -> project(player, data, record);
            case RESPAWN -> respawn(player, data, record, lookup.entity());
            case FAIL_CLOSED -> {
                if (record != null && record.state() == CompanionRecord.State.ACTIVE
                        && lookup.presence() == CompanionPolicy.Presence.UNKNOWN
                        && record.dimension().equals(player.serverLevel().dimension().location())) {
                    beginRecall(server, player.getUUID(), record, player);
                } else {
                    fail(player, decision.reason());
                }
            }
        }
    }

    private static void dismiss(ServerPlayer player, MinecraftServer server, CompanionSavedData data) {
        CompanionRecord record = data.get(player.getUUID());
        if (record != null && record.state() == CompanionRecord.State.ACTIVE) {
            Entity live = findLoadedProjection(server, record);
            if (live != null && !isOwner(live, player.getUUID())) {
                fail(player, "the located entity is not this owner's companion");
                return;
            }
            if (live instanceof CompanionEntity companion) {
                record.markDormant(companion.level().dimension().location(), companion.getX(), companion.getY(),
                        companion.getZ());
                data.put(record);
                companion.discard();
            } else {
                record.markDormant(record.dimension(), record.x(), record.y(), record.z());
                data.put(record);
                // The projection may already be joined but still in a section the
                // visible index does not contain. Hold a temporary ticket and
                // discard it once its entity sections load; a join fence alone
                // would not fire for an entity that already joined.
                beginDismissFence(server, record);
            }
            message(player, "dismissed");
            return;
        }
        Lookup lookup = record == null ? new Lookup(CompanionPolicy.Presence.ABSENT, null) : find(server, record);
        boolean ownerMatches = lookup.entity() != null && isOwner(lookup.entity(), player.getUUID());

        CompanionPolicy.Decision<CompanionPolicy.DismissAction> decision = CompanionPolicy.dismiss(
                data.isUsable(), stateOf(record), lookup.presence(), ownerMatches);

        switch (decision.action()) {
            case DISMISS -> dismissEntity(player, data, record, lookup.entity());
            case NO_OP -> message(player, "no_companion");
            case FAIL_CLOSED -> fail(player, decision.reason());
        }
    }

    private static void create(ServerPlayer player, CompanionSavedData data) {
        ServerLevel level = player.serverLevel();
        CompanionEntity companion = BharatCodeCompanion.COMPANION.get().create(level);
        if (companion == null) {
            fail(player, "entity type could not create a companion");
            return;
        }

        companion.setOwnerUUID(player.getUUID());
        companion.setCustomName(Component.literal(DEFAULT_NAME));
        companion.setCustomNameVisible(true);
        if (!placeBeside(player, companion)) {
            fail(player, "no collision-free place is available beside the owner");
            return;
        }

        CompanionRecord record = new CompanionRecord(player.getUUID(), companion.getUUID(), DEFAULT_NAME,
                CompanionRecord.State.ACTIVE, level.dimension().location(), companion.getX(), companion.getY(),
                companion.getZ());
        // The join fence consults SavedData synchronously, so publish authority
        // before addFreshEntity. Roll back to DORMANT if the engine rejects it.
        data.put(record);
        if (!level.addFreshEntity(companion)) {
            record.markDormant(level.dimension().location(), companion.getX(), companion.getY(), companion.getZ());
            data.put(record);
            fail(player, "the world rejected the new companion entity");
            return;
        }
        message(player, "summoned");
    }

    private static boolean project(ServerPlayer player, CompanionSavedData data, CompanionRecord record) {
        if (record == null) {
            fail(player, "the companion authority record disappeared before it could be projected");
            return false;
        }
        ServerLevel level = player.serverLevel();
        // Only an ACTIVE cross-dimension record is refused: the owner must
        // dismiss it first. A DORMANT record may be summoned in the owner's
        // current dimension even though it was last seen somewhere else.
        if (record.state() == CompanionRecord.State.ACTIVE
                && !record.dimension().equals(level.dimension().location())) {
            message(player, "wrong_dimension");
            return false;
        }
        Entity existing = findLoadedProjection(level.getServer(), record);
        if (existing instanceof CompanionEntity companion) {
            if (!isOwner(companion, player.getUUID())) {
                fail(player, "the located entity is not this owner's companion");
                return false;
            }
            if (!placeBeside(player, companion)) {
                message(player, "no_safe_place");
                return false;
            }
            record.markActive(level.dimension().location(), companion.getX(), companion.getY(), companion.getZ());
            data.put(record);
            message(player, "recalled");
            return true;
        }
        if (existing != null) {
            fail(player, "the companion UUID is already used by another entity");
            return false;
        }
        CompanionEntity projection = BharatCodeCompanion.COMPANION.get().create(level);
        if (projection == null) {
            fail(player, "entity type could not create a companion projection");
            return false;
        }
        projection.setUUID(record.companion());
        applyAuthority(projection, record);
        if (!placeBeside(player, projection)) {
            fail(player, "no collision-free place is available beside the owner");
            return false;
        }
        CompanionRecord.State previousState = record.state();
        ResourceLocation previousDimension = record.dimension();
        double previousX = record.x();
        double previousY = record.y();
        double previousZ = record.z();
        record.markActive(level.dimension().location(), projection.getX(), projection.getY(), projection.getZ());
        data.put(record);
        if (!level.addFreshEntity(projection)) {
            restoreAuthority(record, previousState, previousDimension, previousX, previousY, previousZ);
            data.put(record);
            fail(player, "the world rejected the restored companion projection");
            return false;
        }
        message(player, "recalled");
        return true;
    }

    private static void restoreAuthority(CompanionRecord record, CompanionRecord.State state,
            ResourceLocation dimension, double x, double y, double z) {
        if (state == CompanionRecord.State.ACTIVE) {
            record.markActive(dimension, x, y, z);
        } else {
            record.markDormant(dimension, x, y, z);
        }
    }

    private static boolean spawnProjection(ServerLevel level, CompanionRecord record, CompanionSavedData data,
            double x, double y, double z) {
        CompanionEntity companion = BharatCodeCompanion.COMPANION.get().create(level);
        if (companion == null) {
            LOGGER.error("Entity type could not create a companion projection for {}", record.companion());
            return false;
        }

        companion.setUUID(record.companion());
        companion.setOwnerUUID(record.owner());
        companion.setCustomName(Component.literal(record.name()));
        companion.setCustomNameVisible(true);
        companion.setPos(x, y, z);

        if (!level.addFreshEntity(companion)) {
            LOGGER.error("The world rejected the companion projection {}", record.companion());
            return false;
        }
        record.markActive(level.dimension().location(), companion.getX(), companion.getY(), companion.getZ());
        data.put(record);
        return true;
    }

    private static void respawn(ServerPlayer player, CompanionSavedData data, CompanionRecord record, Entity stale) {
        if (!isOwner(stale, player.getUUID())) {
            fail(player, "the located entity is not this owner's companion");
            return;
        }
        stale.discard();
        project(player, data, record);
    }

    private static boolean recall(ServerPlayer player, CompanionSavedData data, CompanionRecord record, Entity entity) {
        if (!(entity instanceof CompanionEntity companion) || !isOwner(companion, player.getUUID())) {
            fail(player, "the located entity is not this owner's companion");
            return false;
        }
        if (!companion.level().dimension().equals(player.serverLevel().dimension())) {
            message(player, "wrong_dimension");
            return false;
        }

        if (!placeBeside(player, companion)) {
            message(player, "no_safe_place");
            return false;
        }
        record.markActive(player.serverLevel().dimension().location(), companion.getX(), companion.getY(),
                companion.getZ());
        data.put(record);
        message(player, "recalled");
        return true;
    }

    private static void dismissEntity(ServerPlayer player, CompanionSavedData data, CompanionRecord record,
            Entity entity) {
        if (entity == null) {
            record.markDormant(record.dimension(), record.x(), record.y(), record.z());
            data.put(record);
            message(player, "dismissed");
            return;
        }
        if (!(entity instanceof CompanionEntity companion) || !isOwner(companion, player.getUUID())) {
            fail(player, "the located entity is not this owner's companion");
            return;
        }

        record.markDormant(companion.level().dimension().location(), companion.getX(), companion.getY(),
                companion.getZ());
        data.put(record);
        companion.discard();
        message(player, "dismissed");
    }

    private static Lookup find(MinecraftServer server, CompanionRecord record) {
        Entity match = findLoadedProjection(server, record);
        if (match != null) {
            return new Lookup(CompanionPolicy.Presence.PRESENT, match);
        }

        ResourceLocation dimension = record.dimension();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            return new Lookup(CompanionPolicy.Presence.UNKNOWN, null);
        }

        ChunkPos chunk = chunkOf(record);
        try {
            level.getChunk(chunk.x, chunk.z);
        } catch (RuntimeException ex) {
            LOGGER.warn("Could not load companion chunk at {}, {} in {}", chunk.x, chunk.z, dimension, ex);
            return new Lookup(CompanionPolicy.Presence.UNKNOWN, null);
        }

        // The entity sections are loaded from disk asynchronously through the
        // loading inbox. Looking up the UUID before that completes would report a
        // live companion as absent and invite a duplicate projection.
        if (!level.areEntitiesLoaded(chunk.toLong())) {
            LOGGER.warn("Entity sections for companion {} at {}, {} in {} are not loaded yet; presence is unknown",
                    record.companion(), chunk.x, chunk.z, dimension);
            return new Lookup(CompanionPolicy.Presence.UNKNOWN, null);
        }

        Entity entity = findInChunk(level, record);
        return entity == null
                ? new Lookup(CompanionPolicy.Presence.ABSENT, null)
                : new Lookup(CompanionPolicy.Presence.PRESENT, entity);
    }

    /**
     * Finds the live projection in the dimension the authority names. A copy in
     * any other dimension is stale and is discarded instead of being returned.
     */
    private static Entity findLoadedProjection(MinecraftServer server, CompanionRecord record) {
        UUID id = record.companion();
        ResourceLocation dimension = record.dimension();
        Entity match = null;
        for (ServerLevel level : server.getAllLevels()) {
            boolean named = level.dimension().location().equals(dimension);
            Entity entity = named ? findInChunk(level, record) : level.getEntities().get(id);
            if (entity == null) {
                continue;
            }
            if (!named) {
                LOGGER.warn("Discarding stale companion projection {} in {}: the authority places it in {}",
                        id, level.dimension().location(), dimension);
                entity.discard();
                continue;
            }
            match = entity;
        }
        return match;
    }

    /**
     * The visible-entity lookup only returns projections in the tracking index.
     * A projection that already joined a loaded chunk can sit in an entity
     * section the index has not exposed yet. The public section query covers
     * both cases; it uses only {@link Level#getEntities} and no engine internals.
     */
    private static Entity findInChunk(ServerLevel level, CompanionRecord record) {
        return findInChunk(level, record.companion(), chunkOf(record));
    }

    private static Entity findInChunk(ServerLevel level, UUID id, ChunkPos chunk) {
        Entity visible = level.getEntities().get(id);
        if (visible != null) {
            return visible;
        }
        AABB box = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
        List<CompanionEntity> hidden = level.getEntities(EntityTypeTest.forClass(CompanionEntity.class), box,
                companion -> id.equals(companion.getUUID()));
        return hidden.isEmpty() ? null : hidden.get(0);
    }

    private static ChunkPos chunkOf(CompanionRecord record) {
        return new ChunkPos(Mth.floor(record.x()) >> 4, Mth.floor(record.z()) >> 4);
    }

    private static void beginRecall(MinecraftServer server, UUID owner, CompanionRecord record,
            ServerPlayer player) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (level == null) {
            fail(player, "the recorded dimension is not loaded");
            return;
        }
        cancelRecall(server, owner);
        ChunkPos chunk = chunkOf(record);
        level.getChunkSource().addRegionTicket(PENDING_TICKET, chunk, PENDING_TICKET_DISTANCE, record.companion());
        PENDING_RECALLS.put(owner, new PendingRequest(record.companion(), level.dimension(), chunk,
                server.getTickCount() + RECALL_DEADLINE_TICKS));
        LOGGER.info("Queued remote recall for companion {} at {}, {} in {}", record.companion(), chunk.x, chunk.z,
                record.dimension());
        message(player, "recall_pending");
    }

    private static void beginDismissFence(MinecraftServer server, CompanionRecord record) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (level == null) {
            return;
        }
        UUID companion = record.companion();
        PendingRequest previous = PENDING_FENCES.remove(companion);
        if (previous != null) {
            removePendingTicket(server, previous);
        }
        ChunkPos chunk = chunkOf(record);
        level.getChunkSource().addRegionTicket(PENDING_TICKET, chunk, PENDING_TICKET_DISTANCE, companion);
        PENDING_FENCES.put(companion, new PendingRequest(companion, level.dimension(), chunk,
                server.getTickCount() + FENCE_DEADLINE_TICKS));
        LOGGER.info("Queued hidden-projection fence for companion {} at {}, {} in {}", companion, chunk.x, chunk.z,
                record.dimension());
    }

    private static void cancelRecall(MinecraftServer server, UUID owner) {
        PendingRequest pending = PENDING_RECALLS.remove(owner);
        if (pending == null) {
            return;
        }
        removePendingTicket(server, pending);
    }

    private static void cancelAllPending(MinecraftServer server) {
        for (PendingRequest pending : new ArrayList<>(PENDING_RECALLS.values())) {
            removePendingTicket(server, pending);
        }
        for (PendingRequest pending : new ArrayList<>(PENDING_FENCES.values())) {
            removePendingTicket(server, pending);
        }
        PENDING_RECALLS.clear();
        PENDING_FENCES.clear();
    }

    private static void removePendingTicket(MinecraftServer server, PendingRequest pending) {
        ServerLevel level = server.getLevel(pending.dimension());
        if (level != null) {
            level.getChunkSource().removeRegionTicket(PENDING_TICKET, pending.chunk(), PENDING_TICKET_DISTANCE,
                    pending.companion());
        }
    }

    private static boolean inChunk(CompanionRecord record, int chunkX, int chunkZ) {
        return (Mth.floor(record.x()) >> 4) == chunkX && (Mth.floor(record.z()) >> 4) == chunkZ;
    }

    private static boolean hasPlayerNear(ServerLevel level, int chunkX, int chunkZ) {
        for (ServerPlayer player : level.players()) {
            int distance = Math.max(Math.abs(player.chunkPosition().x - chunkX),
                    Math.abs(player.chunkPosition().z - chunkZ));
            if (distance <= Math.max(player.requestedViewDistance(), 2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOwner(Entity entity, UUID owner) {
        return entity instanceof CompanionEntity companion && owner.equals(companion.getOwnerUUID());
    }

    private static boolean placeBeside(ServerPlayer player, CompanionEntity companion) {
        ServerLevel level = player.serverLevel();
        double oldX = companion.getX();
        double oldY = companion.getY();
        double oldZ = companion.getZ();
        BlockPos origin = player.blockPosition();
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy >= -1; dy--) {
                        double x = origin.getX() + dx + 0.5D;
                        double y = origin.getY() + dy;
                        double z = origin.getZ() + dz + 0.5D;
                        companion.setPos(x, y, z);
                        // Players do not report as collidable to Level.noCollision,
                        // so an owner at a block corner can share a candidate box
                        // with the companion. Reject that overlap explicitly.
                        if (companion.getBoundingBox().intersects(player.getBoundingBox())) {
                            continue;
                        }
                        if (level.noCollision(companion, companion.getBoundingBox())) {
                            return true;
                        }
                    }
                }
            }
        }
        companion.setPos(oldX, oldY, oldZ);
        return false;
    }

    private static CompanionPolicy.State stateOf(CompanionRecord record) {
        if (record == null) {
            return CompanionPolicy.State.NONE;
        }
        return record.state() == CompanionRecord.State.ACTIVE
                ? CompanionPolicy.State.ACTIVE
                : CompanionPolicy.State.DORMANT;
    }

    private static void fail(ServerPlayer player, String reason) {
        LOGGER.warn("Companion action refused for {}: {}", player.getUUID(), reason);
        message(player, "state_error");
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.bharatcode_companion." + key), false);
    }

    private record Lookup(CompanionPolicy.Presence presence, Entity entity) {
    }

    private record PendingChunk(ResourceKey<Level> dimension, int x, int z) {
    }

    private record PendingRequest(UUID companion, ResourceKey<Level> dimension, ChunkPos chunk, int deadlineTick) {
    }
}
