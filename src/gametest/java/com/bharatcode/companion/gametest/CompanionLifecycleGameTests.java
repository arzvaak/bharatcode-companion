package com.bharatcode.companion.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.bharatcode.companion.BharatCodeCompanion;
import com.bharatcode.companion.data.CompanionRecord;
import com.bharatcode.companion.data.CompanionSavedData;
import com.bharatcode.companion.entity.CompanionEntity;
import com.bharatcode.companion.server.CompanionService;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import io.netty.channel.embedded.EmbeddedChannel;

/**
 * Dev-only engine harness for the production charm and service path. It uses
 * real (non-fake) ServerPlayers and is not part of the published jar.
 */
@GameTestHolder(BharatCodeCompanion.MODID)
@PrefixGameTestTemplate(false)
public final class CompanionLifecycleGameTests {
    private static final String TEMPLATE = "empty";
    private static final TicketType<UUID> TEST_TICKET =
            TicketType.create("companion_test", Comparator.comparing(UUID::toString));

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void charmLifecycleKeepsIdentity(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord first = data.get(player.getUUID());
            helper.assertTrue(first != null, "summon created an authority record");
            helper.assertTrue(first.state() == CompanionRecord.State.ACTIVE, "summoned record is active");
            helper.assertTrue("Companion".equals(first.name()), "summoned name is Companion");
            UUID id = first.companion();
            helper.assertTrue(companion(helper, id) != null, "summon created the projection");

            useCharm(helper, player, false);
            helper.assertTrue(id.equals(data.get(player.getUUID()).companion()), "recall kept the UUID");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1, "recall did not duplicate");

            useCharm(helper, player, true);
            helper.assertTrue(data.get(player.getUUID()).state() == CompanionRecord.State.DORMANT,
                    "dismiss marked the authority dormant");
            helper.assertTrue(companion(helper, id) == null, "dismiss removed the projection");

            useCharm(helper, player, false);
            CompanionRecord resummoned = data.get(player.getUUID());
            helper.assertTrue(id.equals(resummoned.companion()), "resummon kept the UUID");
            helper.assertTrue("Companion".equals(resummoned.name()), "resummon kept the name");
            helper.assertTrue(companion(helper, id) != null, "resummon restored the projection");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1, "resummon did not duplicate");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void secondOwnerIsSeparate(GameTestHelper helper) {
        ServerPlayer first = joinPlayer(helper);
        ServerPlayer second = joinPlayer(helper);
        try {
            useCharm(helper, first, false);
            useCharm(helper, second, false);
            CompanionSavedData data = data(helper);
            CompanionRecord firstRecord = data.get(first.getUUID());
            CompanionRecord secondRecord = data.get(second.getUUID());
            helper.assertTrue(firstRecord != null && secondRecord != null, "both owners have records");
            helper.assertTrue(!firstRecord.companion().equals(secondRecord.companion()),
                    "owners have distinct companion UUIDs");
            helper.assertTrue(ownedCount(helper, first.getUUID()) == 1, "first owner has exactly one projection");
            helper.assertTrue(ownedCount(helper, second.getUUID()) == 1, "second owner has exactly one projection");

            useCharm(helper, first, true);
            helper.assertTrue(data.get(first.getUUID()).state() == CompanionRecord.State.DORMANT,
                    "first owner dismissed");
            helper.assertTrue(data.get(second.getUUID()).state() == CompanionRecord.State.ACTIVE,
                    "second owner stayed active");
            helper.assertTrue(ownedCount(helper, second.getUUID()) == 1, "second projection was not touched");
            helper.succeed();
        } finally {
            leavePlayer(helper, first);
            leavePlayer(helper, second);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void authorityRoundTripsAndFencesStaleProjection(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord record = data.get(player.getUUID());
            UUID id = record.companion();

            CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            CompanionSavedData reloaded = CompanionSavedData.load(saved, helper.getLevel().registryAccess());
            helper.assertTrue(reloaded.isUsable(), "serialized authority is usable");
            CompanionRecord loaded = reloaded.get(player.getUUID());
            helper.assertTrue(loaded != null && id.equals(loaded.companion())
                    && "Companion".equals(loaded.name())
                    && loaded.state() == CompanionRecord.State.ACTIVE,
                    "authority round-trip preserved identity, name, and state");
            ListTag savedRecords = saved.getList("Companions", Tag.TAG_COMPOUND);
            boolean found = false;
            for (int i = 0; i < savedRecords.size(); i++) {
                CompoundTag entry = savedRecords.getCompound(i);
                if (entry.hasUUID("Companion") && id.equals(entry.getUUID("Companion"))) {
                    found = true;
                    helper.assertFalse(entry.contains("Entity", Tag.TAG_COMPOUND),
                            "authority record has no entity snapshot");
                }
            }
            helper.assertTrue(found, "serialized authority contains this record");

            CompanionService.onServerStarted(new ServerStartedEvent(helper.getLevel().getServer()));
            helper.assertTrue(companion(helper, id) != null, "server-start reconciliation kept the matching projection");

            useCharm(helper, player, true);
            helper.assertTrue(data.get(player.getUUID()).state() == CompanionRecord.State.DORMANT,
                    "record is dormant before the stale fence");

            CompanionEntity stale = BharatCodeCompanion.COMPANION.get().create(helper.getLevel());
            helper.assertTrue(stale != null, "stale entity created");
            stale.setUUID(id);
            stale.setOwnerUUID(player.getUUID());
            stale.setCustomName(Component.literal("Stale"));
            stale.setPos(player.getX() + 2.0D, player.getY(), player.getZ());
            helper.assertTrue(!helper.getLevel().addFreshEntity(stale),
                    "join fence refused a dormant projection");
            helper.assertTrue(companion(helper, id) == null, "stale projection was not added");

            useCharm(helper, player, false);
            helper.assertTrue(companion(helper, id) != null, "authority re-projected the companion");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1, "no duplicate after re-projection");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void missingProjectionIsReprojectedFromAuthority(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord record = data.get(player.getUUID());
            UUID id = record.companion();
            CompanionEntity live = companion(helper, id);
            helper.assertTrue(live != null, "live projection exists");

            live.discard();
            helper.assertTrue(companion(helper, id) == null, "projection loss simulated");
            helper.assertTrue(data.get(player.getUUID()).state() == CompanionRecord.State.ACTIVE,
                    "authority remains active after projection loss");

            useCharm(helper, player, false);
            helper.assertTrue(id.equals(data.get(player.getUUID()).companion()), "same UUID re-projected");
            helper.assertTrue(companion(helper, id) != null, "projection restored from authority");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1, "no duplicate after re-projection");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "remote-recall")
    public static void remoteRecallFindsHiddenJoinedProjection(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            CompanionSavedData data = data(helper);
            UUID id = UUID.randomUUID();
            BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(5000, 0, 5000);
            double x = far.getX() + 0.5D;
            double y = far.getY();
            double z = far.getZ() + 0.5D;
            CompanionRecord record = new CompanionRecord(player.getUUID(), id, "Companion",
                    CompanionRecord.State.ACTIVE, helper.getLevel().dimension().location(), x, y, z);
            data.put(record);

            CompanionEntity hidden = BharatCodeCompanion.COMPANION.get().create(helper.getLevel());
            helper.assertTrue(hidden != null, "hidden projection created");
            hidden.setUUID(id);
            hidden.setOwnerUUID(player.getUUID());
            hidden.setCustomName(Component.literal("Companion"));
            hidden.setPos(x, y, z);
            helper.assertTrue(helper.getLevel().addFreshEntity(hidden),
                    "the active authority lets the hidden projection join");
            helper.assertTrue(companion(helper, id) == null,
                    "the hidden projection is not in the visible UUID index");
            helper.assertTrue(findCompanionInChunk(helper.getLevel(), id, chunkOf(record)) == null,
                    "the hidden section is not visible before the chunk is loaded");

            long startTick = helper.getLevel().getServer().getTickCount();
            double ownerX = player.getX();
            double ownerY = player.getY();
            double ownerZ = player.getZ();
            useCharm(helper, player, false);
            awaitHiddenRecall(helper, player, hidden, id, chunkOf(record), startTick, ownerX, ownerY, ownerZ, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitHiddenRecall(GameTestHelper helper, ServerPlayer player, CompanionEntity hidden,
            UUID id, ChunkPos oldChunk, long startTick, double ownerX, double ownerY, double ownerZ, int attempt) {
        CompanionEntity recalled = companion(helper, id);
        if (recalled == null || recalled.distanceToSqr(player) >= 25.0D) {
            if (attempt >= 100) {
                try {
                    helper.fail("the original remote recall did not complete within the pending deadline");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitHiddenRecall(helper, player, hidden, id, oldChunk, startTick, ownerX,
                    ownerY, ownerZ, attempt + 1));
            return;
        }
        try {
            long elapsed = helper.getLevel().getServer().getTickCount() - startTick;
            helper.assertTrue(elapsed <= 100L,
                    "the original remote recall completed within the pending deadline");
            helper.assertTrue(Math.abs(player.getX() - ownerX) < 0.001D
                            && Math.abs(player.getY() - ownerY) < 0.001D
                            && Math.abs(player.getZ() - ownerZ) < 0.001D,
                    "the owner stayed remote while the recall completed");
            helper.assertTrue(recalled == hidden, "the recall reused the hidden joined projection");
            helper.assertTrue(recalled.distanceToSqr(player) < 25.0D,
                    "the recall moved the hidden projection beside the owner");
            helper.assertTrue(id.equals(recalled.getUUID()), "the recalled projection kept the authority UUID");
            helper.assertTrue(player.getUUID().equals(recalled.getOwnerUUID()),
                    "the recalled projection kept the owner");
            helper.assertFalse(recalled.getBoundingBox().intersects(player.getBoundingBox()),
                    "the recalled projection does not overlap the owner");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1,
                    "the recall left exactly one owner projection");
            helper.getLevel().getChunk(oldChunk.x, oldChunk.z);
            awaitOldChunkHasNoDuplicate(helper, player, id, oldChunk, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = "remote-recall")
    public static void remoteRecallAfterEngineUnloadKeepsIdentity(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos far = base.offset(4000, 0, 4000);
            double farX = far.getX() + 0.5D;
            double farY = 200.0D;
            double farZ = far.getZ() + 0.5D;
            player.absMoveTo(farX, farY, farZ, player.getYRot(), player.getXRot());
            helper.getLevel().getChunkSource().move(player);
            awaitFarChunkForSummon(helper, player, farX, farY, farZ, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitFarChunkForSummon(GameTestHelper helper, ServerPlayer player, double x, double y,
            double z, int attempt) {
        ChunkPos chunk = chunkOfAt(x, z);
        if (!helper.getLevel().getChunkSource().hasChunk(chunk.x, chunk.z)
                || !helper.getLevel().areEntitiesLoaded(chunk.toLong())) {
            if (attempt >= 100) {
                try {
                    helper.fail("the far chunk did not load for the summon");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitFarChunkForSummon(helper, player, x, y, z, attempt + 1));
            return;
        }
        try {
            player.absMoveTo(x, y, z, player.getYRot(), player.getXRot());
            helper.getLevel().getChunkSource().move(player);
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord record = data.get(player.getUUID());
            helper.assertTrue(record != null && record.state() == CompanionRecord.State.ACTIVE,
                    "the far summon created an active authority");
            UUID id = record.companion();
            awaitFarProjection(helper, player, id, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitFarProjection(GameTestHelper helper, ServerPlayer player, UUID id, int attempt) {
        CompanionEntity live = companion(helper, id);
        if (live == null) {
            if (attempt >= 20) {
                try {
                    helper.fail("the far summon created a live projection");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitFarProjection(helper, player, id, attempt + 1));
            return;
        }
        try {
            CompanionRecord record = data(helper).get(player.getUUID());
            ChunkPos oldChunk = chunkOf(record);
            double oldX = record.x();
            double oldY = record.y();
            double oldZ = record.z();

            BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
            player.absMoveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, player.getYRot(),
                    player.getXRot());
            helper.getLevel().getChunkSource().move(player);
            awaitEngineUnload(helper, player, live, id, oldChunk, oldX, oldY, oldZ, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitEngineUnload(GameTestHelper helper, ServerPlayer player, CompanionEntity live, UUID id,
            ChunkPos oldChunk, double oldX, double oldY, double oldZ, int attempt) {
        boolean unloaded = live.isRemoved()
                && live.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK
                && !helper.getLevel().getChunkSource().hasChunk(oldChunk.x, oldChunk.z);
        if (!unloaded) {
            if (attempt >= 200) {
                try {
                    helper.fail("the engine did not unload the formerly live projection");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitEngineUnload(helper, player, live, id, oldChunk, oldX, oldY, oldZ,
                    attempt + 1));
            return;
        }
        try {
            CompanionRecord record = data(helper).get(player.getUUID());
            helper.assertTrue(record.state() == CompanionRecord.State.ACTIVE,
                    "engine unload leaves the authority active");
            helper.assertTrue(record.dimension().equals(helper.getLevel().dimension().location()),
                    "engine unload keeps the recorded dimension");
            helper.assertTrue(Math.abs(record.x() - oldX) < 0.001D
                            && Math.abs(record.y() - oldY) < 0.001D
                            && Math.abs(record.z() - oldZ) < 0.001D,
                    "engine unload keeps the recorded position");

            long startTick = helper.getLevel().getServer().getTickCount();
            double ownerX = player.getX();
            double ownerY = player.getY();
            double ownerZ = player.getZ();
            useCharm(helper, player, false);
            awaitRecallAfterUnload(helper, player, id, oldChunk, startTick, ownerX, ownerY, ownerZ, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitRecallAfterUnload(GameTestHelper helper, ServerPlayer player, UUID id,
            ChunkPos oldChunk, long startTick, double ownerX, double ownerY, double ownerZ, int attempt) {
        CompanionEntity recalled = companion(helper, id);
        if (recalled == null || recalled.distanceToSqr(player) >= 25.0D) {
            if (attempt >= 100) {
                try {
                    helper.fail("the original remote recall did not complete within the pending deadline");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitRecallAfterUnload(helper, player, id, oldChunk, startTick, ownerX,
                    ownerY, ownerZ, attempt + 1));
            return;
        }
        try {
            long elapsed = helper.getLevel().getServer().getTickCount() - startTick;
            helper.assertTrue(elapsed <= 100L,
                    "the original remote recall completed within the pending deadline");
            helper.assertTrue(Math.abs(player.getX() - ownerX) < 0.001D
                            && Math.abs(player.getY() - ownerY) < 0.001D
                            && Math.abs(player.getZ() - ownerZ) < 0.001D,
                    "the owner stayed remote while the recall completed");
            helper.assertTrue(id.equals(recalled.getUUID()), "the recalled projection kept the authority UUID");
            helper.assertTrue(player.getUUID().equals(recalled.getOwnerUUID()),
                    "the recalled projection kept the owner");
            helper.assertTrue(recalled.distanceToSqr(player) < 25.0D,
                    "the recall placed the projection beside the owner");
            helper.assertFalse(recalled.getBoundingBox().intersects(player.getBoundingBox()),
                    "the recalled projection does not overlap the owner");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1,
                    "the recall left exactly one owner projection");
            helper.getLevel().getChunk(oldChunk.x, oldChunk.z);
            awaitOldChunkHasNoDuplicate(helper, player, id, oldChunk, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitOldChunkHasNoDuplicate(GameTestHelper helper, ServerPlayer player, UUID id,
            ChunkPos oldChunk, int attempt) {
        ServerLevel level = helper.getLevel();
        if (!level.areEntitiesLoaded(oldChunk.toLong())) {
            if (attempt >= 100) {
                try {
                    helper.fail("the old area's entity sections did not load");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitOldChunkHasNoDuplicate(helper, player, id, oldChunk, attempt + 1));
            return;
        }
        try {
            helper.assertTrue(findCompanionInChunk(level, id, oldChunk) == null,
                    "reloading the old area did not restore a duplicate projection");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1,
                    "reloading the old area left exactly one projection");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "spectator-recall")
    public static void spectatorModeCancelsPendingRecall(GameTestHelper helper) {
        ModePlayer player = joinModePlayer(helper);
        try {
            CompanionSavedData data = data(helper);
            UUID id = UUID.randomUUID();
            BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(5000, 0, 5000);
            double x = far.getX() + 0.5D;
            double y = far.getY();
            double z = far.getZ() + 0.5D;
            awaitSpectatorChunkUnloaded(helper, player, data, id, x, y, z, chunkOfAt(x, z), 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitSpectatorChunkUnloaded(GameTestHelper helper, ModePlayer player,
            CompanionSavedData data, UUID id, double x, double y, double z, ChunkPos chunk, int attempt) {
        if (helper.getLevel().getChunkSource().hasChunk(chunk.x, chunk.z)) {
            if (attempt >= 200) {
                try {
                    helper.fail("the far chunk did not unload before the pending recall");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitSpectatorChunkUnloaded(helper, player, data, id, x, y, z, chunk,
                    attempt + 1));
            return;
        }
        try {
            CompanionRecord record = new CompanionRecord(player.getUUID(), id, "Companion",
                    CompanionRecord.State.ACTIVE, helper.getLevel().dimension().location(), x, y, z);
            data.put(record);
            CompanionEntity hidden = BharatCodeCompanion.COMPANION.get().create(helper.getLevel());
            helper.assertTrue(hidden != null, "hidden projection created");
            hidden.setUUID(id);
            hidden.setOwnerUUID(player.getUUID());
            hidden.setCustomName(Component.literal("Companion"));
            hidden.setPos(x, y, z);
            helper.assertTrue(helper.getLevel().addFreshEntity(hidden),
                    "the active authority lets the hidden projection join");

            useCharm(helper, player, false);
            helper.assertTrue(companion(helper, id) == null,
                    "the remote recall is pending before the mode change");
            player.enterSpectator();
            awaitSpectatorRecallCancelled(helper, player, id, hidden, x, y, z, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitSpectatorRecallCancelled(GameTestHelper helper, ServerPlayer player, UUID id,
            CompanionEntity hidden, double x, double y, double z, int attempt) {
        CompanionEntity visible = companion(helper, id);
        if (visible != null && visible.distanceToSqr(player) < 25.0D) {
            try {
                helper.fail("a spectator owner's pending recall moved a companion beside the owner");
            } finally {
                leavePlayer(helper, player);
            }
            return;
        }
        if (attempt >= 120) {
            try {
                CompanionRecord record = data(helper).get(player.getUUID());
                helper.assertTrue(record != null && record.state() == CompanionRecord.State.ACTIVE,
                        "spectator mode leaves the authority active");
                helper.assertTrue(Math.abs(record.x() - x) < 0.001D
                                && Math.abs(record.y() - y) < 0.001D
                                && Math.abs(record.z() - z) < 0.001D,
                        "spectator mode leaves the authority position unchanged");
                if (hidden.isRemoved()) {
                    helper.assertTrue(hidden.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                            "spectator mode only lets the old chunk unload the hidden projection");
                } else {
                    helper.assertTrue(Math.abs(hidden.getX() - x) < 0.001D
                                    && Math.abs(hidden.getY() - y) < 0.001D
                                    && Math.abs(hidden.getZ() - z) < 0.001D,
                            "spectator mode does not teleport the hidden joined projection");
                }
                helper.succeed();
            } finally {
                leavePlayer(helper, player);
            }
            return;
        }
        helper.runAfterDelay(1, () -> awaitSpectatorRecallCancelled(helper, player, id, hidden, x, y, z,
                attempt + 1));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void remoteDismissWorksAfterRecordedChunkLoadsWithoutOwner(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            CompanionSavedData data = data(helper);
            UUID id = UUID.randomUUID();
            BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(1000, 0, 1000);
            data.put(new CompanionRecord(player.getUUID(), id, "Companion", CompanionRecord.State.ACTIVE,
                    helper.getLevel().dimension().location(), far.getX(), far.getY(), far.getZ()));

            useCharm(helper, player, true);

            helper.assertTrue(data.get(player.getUUID()).state() == CompanionRecord.State.DORMANT,
                    "remote dismiss updates authority despite asynchronous/untracked remote entity sections");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 2000, batch = "hidden-dismiss")
    public static void immediateCrossDimensionResummonSurvivesOutstandingFence(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the nether level exists");
        BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(7000, 0, 7000);
        ChunkPos chunk = new ChunkPos(far);
        // A real old companion area has already generated its surrounding chunks.
        // Prepare that terrain before starting the production 200-tick deadline.
        nether.getChunkSource().addRegionTicket(TEST_TICKET, chunk, 2, player.getUUID());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                nether.getChunk(chunk.x + dx, chunk.z + dz);
            }
        }
        awaitNetherPrepared(helper, player, nether, chunk, false, 0);
    }

    private static void awaitNetherPrepared(GameTestHelper helper, ServerPlayer player, ServerLevel nether,
            ChunkPos chunk, boolean unloading, int attempt) {
        boolean ready = unloading ? !nether.getChunkSource().hasChunk(chunk.x, chunk.z)
                && !nether.areEntitiesLoaded(chunk.toLong()) : nether.areEntitiesLoaded(chunk.toLong());
        if (!ready) {
            if (attempt >= 1500) {
                nether.getChunkSource().removeRegionTicket(TEST_TICKET, chunk, 2, player.getUUID());
                leavePlayer(helper, player);
                helper.fail("the old nether area did not finish preparation/unload");
                return;
            }
            helper.runAfterDelay(1, () -> awaitNetherPrepared(helper, player, nether, chunk, unloading, attempt + 1));
            return;
        }
        if (!unloading) {
            nether.getChunkSource().removeRegionTicket(TEST_TICKET, chunk, 2, player.getUUID());
            awaitNetherPrepared(helper, player, nether, chunk, true, 0);
            return;
        }
        dismissAndImmediatelyResummon(helper, player, nether, chunk);
    }

    private static void dismissAndImmediatelyResummon(GameTestHelper helper, ServerPlayer player,
            ServerLevel nether, ChunkPos chunk) {
        try {
            CompanionSavedData data = data(helper);
            UUID id = UUID.randomUUID();
            double oldX = chunk.getMinBlockX() + 8.5D;
            double oldY = 64.0D;
            double oldZ = chunk.getMinBlockZ() + 8.5D;
            CompanionRecord record = new CompanionRecord(player.getUUID(), id, "Companion",
                    CompanionRecord.State.ACTIVE, nether.dimension().location(), oldX, oldY, oldZ);
            data.put(record);

            // Join a real nether projection while its chunk is still unloaded. It
            // lives in a hidden section, so neither the visible UUID lookup nor
            // the public section query sees it yet.
            CompanionEntity hidden = BharatCodeCompanion.COMPANION.get().create(nether);
            helper.assertTrue(hidden != null, "hidden nether projection created");
            hidden.setUUID(id);
            hidden.setOwnerUUID(player.getUUID());
            hidden.setCustomName(Component.literal("Companion"));
            hidden.setPos(oldX, oldY, oldZ);
            helper.assertTrue(nether.addFreshEntity(hidden),
                    "the active authority lets the hidden nether projection join");
            helper.assertTrue(nether.getEntities().get(id) == null,
                    "the hidden nether projection is not in the visible UUID index");
            helper.assertTrue(findCompanionInChunk(nether, id, chunkOf(record)) == null,
                    "the hidden nether section is not visible before the chunk is loaded");

            useCharm(helper, player, true);
            helper.assertTrue(data.get(player.getUUID()).state() == CompanionRecord.State.DORMANT,
                    "remote dismiss marked the authority dormant");
            helper.assertTrue(!hidden.isRemoved(),
                    "the dismiss fence is still outstanding when the resummon starts");

            // Resummon immediately, in the owner's dimension, while the nether
            // fence is still waiting for the old chunk. The different dimension
            // lets the new projection join before the fence discards the old one.
            useCharm(helper, player, false);
            CompanionRecord active = data.get(player.getUUID());
            helper.assertTrue(active.state() == CompanionRecord.State.ACTIVE,
                    "immediate resummon activated the authority");
            helper.assertTrue(active.dimension().equals(helper.getLevel().dimension().location()),
                    "immediate resummon projected in the owner's dimension");
            CompanionEntity summoned = companion(helper, id);
            helper.assertTrue(summoned != null,
                    "immediate resummon created the owner-dimension projection");
            helper.assertTrue(summoned != hidden,
                    "the owner-dimension projection is not the old hidden nether copy");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1,
                    "immediate resummon left exactly one owner-dimension projection");

            // The old area returns while the dismiss fence is still outstanding.
            nether.getChunk(chunkOfAt(oldX, oldZ).x, chunkOfAt(oldX, oldZ).z);
            awaitOldNetherFence(helper, player, hidden, record, nether, oldX, oldY, oldZ, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitOldNetherFence(GameTestHelper helper, ServerPlayer player, CompanionEntity hidden,
            CompanionRecord record, ServerLevel nether, double oldX, double oldY, double oldZ, int attempt) {
        if (!hidden.isRemoved()) {
            if (attempt >= 200) {
                try {
                    helper.fail("the outstanding dismiss fence did not discard the old hidden projection");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitOldNetherFence(helper, player, hidden, record, nether, oldX, oldY,
                    oldZ, attempt + 1));
            return;
        }
        try {
            helper.assertTrue(hidden.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                    "the outstanding fence discarded the old hidden nether projection");
            helper.assertTrue(findCompanionInChunk(nether, record.companion(), chunkOfAt(oldX, oldZ)) == null,
                    "the old nether chunk holds no hidden projection after the fence");
            nether.getChunk(chunkOfAt(oldX, oldZ).x, chunkOfAt(oldX, oldZ).z);
            awaitOldNetherChunkHasNoDuplicate(helper, player, record, nether, oldX, oldZ, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitOldNetherChunkHasNoDuplicate(GameTestHelper helper, ServerPlayer player,
            CompanionRecord record, ServerLevel nether, double oldX, double oldZ, int attempt) {
        ChunkPos oldChunk = chunkOfAt(oldX, oldZ);
        if (!nether.areEntitiesLoaded(oldChunk.toLong())) {
            if (attempt >= 100) {
                try {
                    helper.fail("the old nether area's entity sections did not load");
                } finally {
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitOldNetherChunkHasNoDuplicate(helper, player, record, nether,
                    oldX, oldZ, attempt + 1));
            return;
        }
        try {
            helper.assertTrue(findCompanionInChunk(nether, record.companion(), oldChunk) == null,
                    "reloading the old nether area did not restore a duplicate projection");
            helper.assertTrue(nether.getEntities().get(record.companion()) == null,
                    "the old nether area has no visible projection after the reload");
            helper.assertTrue(companion(helper, record.companion()) != null,
                    "the owner-dimension projection survived the old-area reload");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1,
                    "the owner-dimension projection remains the only projection");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void dormantCrossDimensionSummonProjectsInOwnerDimension(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            CompanionSavedData data = data(helper);
            UUID id = UUID.randomUUID();
            BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(1000, 0, 1000);
            data.put(new CompanionRecord(player.getUUID(), id, "Companion", CompanionRecord.State.DORMANT,
                    Level.NETHER.location(), far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D));

            useCharm(helper, player, false);

            CompanionRecord record = data.get(player.getUUID());
            helper.assertTrue(record.state() == CompanionRecord.State.ACTIVE,
                    "dormant cross-dimension summon activated the authority");
            helper.assertTrue(record.dimension().equals(helper.getLevel().dimension().location()),
                    "the projection was placed in the owner's dimension, not the stale recorded one");
            helper.assertTrue(companion(helper, id) != null, "the dormant companion was projected beside the owner");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1, "exactly one projection was created");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "rejected-spawn")
    public static void rejectedSpawnAttemptsProductionAddAndRestoresAuthority(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            CompanionSavedData data = data(helper);
            UUID id = UUID.randomUUID();
            BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(1000, 0, 1000);
            BlockPos decoyPos = helper.absolutePos(new BlockPos(2, 2, 2)).offset(2000, 0, 2000);
            double expectedX = far.getX() + 0.5D;
            double expectedY = far.getY();
            double expectedZ = far.getZ() + 0.5D;
            CompanionRecord record = new CompanionRecord(player.getUUID(), id, "Companion",
                    CompanionRecord.State.ACTIVE, helper.getLevel().dimension().location(),
                    expectedX, expectedY, expectedZ);
            data.put(record);

            CompanionEntity decoy = BharatCodeCompanion.COMPANION.get().create(helper.getLevel());
            helper.assertTrue(decoy != null, "decoy created");
            decoy.setUUID(id);
            decoy.setOwnerUUID(player.getUUID());
            decoy.setPos(decoyPos.getX() + 0.5D, decoyPos.getY(), decoyPos.getZ() + 0.5D);
            helper.assertTrue(helper.getLevel().addFreshEntity(decoy),
                    "the decoy joined in a hidden section with the authority UUID");

            ChunkPos recorded = new ChunkPos(Mth.floor(expectedX) >> 4, Mth.floor(expectedZ) >> 4);
            helper.getLevel().getChunkSource().addRegionTicket(TEST_TICKET, recorded, 2, player.getUUID());
            helper.getLevel().getChunk(recorded.x, recorded.z);
            JoinAttemptListener listener = new JoinAttemptListener(id);
            awaitRecordedChunkReady(helper, player, record, decoy, listener, recorded, expectedX, expectedY,
                    expectedZ, 0);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        }
    }

    private static void awaitRecordedChunkReady(GameTestHelper helper, ServerPlayer player, CompanionRecord record,
            CompanionEntity decoy, JoinAttemptListener listener, ChunkPos recorded, double expectedX,
            double expectedY, double expectedZ, int attempt) {
        if (!helper.getLevel().areEntitiesLoaded(recorded.toLong())) {
            if (attempt >= 100) {
                try {
                    helper.fail("the recorded chunk's entity sections did not load");
                } finally {
                    helper.getLevel().getChunkSource().removeRegionTicket(TEST_TICKET, recorded, 2, player.getUUID());
                    leavePlayer(helper, player);
                }
                return;
            }
            helper.runAfterDelay(1, () -> awaitRecordedChunkReady(helper, player, record, decoy, listener, recorded,
                    expectedX, expectedY, expectedZ, attempt + 1));
            return;
        }
        try {
            NeoForge.EVENT_BUS.register(listener);
            useCharm(helper, player, false);
            assertRejectedProductionAdd(helper, player, decoy, listener, expectedX, expectedY, expectedZ);
        } catch (RuntimeException ex) {
            leavePlayer(helper, player);
            throw ex;
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            helper.getLevel().getChunkSource().removeRegionTicket(TEST_TICKET, recorded, 2, player.getUUID());
        }
    }

    private static void assertRejectedProductionAdd(GameTestHelper helper, ServerPlayer player,
            CompanionEntity decoy, JoinAttemptListener listener, double expectedX, double expectedY,
            double expectedZ) {
        try {
            helper.assertTrue(listener.attempts() == 1 && listener.cancellations == 1,
                    "the test event rejected exactly one actual production addFreshEntity attempt");
            CompanionRecord after = data(helper).get(player.getUUID());
            helper.assertTrue(after.state() == CompanionRecord.State.ACTIVE,
                    "a rejected spawn leaves the existing authority ACTIVE");
            helper.assertTrue(after.dimension().equals(helper.getLevel().dimension().location()),
                    "a rejected spawn keeps the recorded dimension");
            helper.assertTrue(Math.abs(after.x() - expectedX) < 0.001D
                            && Math.abs(after.y() - expectedY) < 0.001D
                            && Math.abs(after.z() - expectedZ) < 0.001D,
                    "a rejected spawn restores the previous authority position");
            helper.assertTrue(!decoy.isRemoved(),
                    "the original entity holding the UUID survives the rejected spawn");
            helper.assertTrue(decoy.level() != null,
                    "the original entity remains attached to its level");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void activeCrossDimensionRecallRefusesBeforeProjectionLookup(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            CompanionSavedData data = data(helper);
            UUID id = UUID.randomUUID();
            BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(1000, 0, 1000);
            data.put(new CompanionRecord(player.getUUID(), id, "Companion", CompanionRecord.State.ACTIVE,
                    Level.NETHER.location(), far.getX(), far.getY(), far.getZ()));

            useCharm(helper, player, false);

            helper.assertTrue(data.get(player.getUUID()).state() == CompanionRecord.State.ACTIVE,
                    "cross-dimension refusal leaves the authority active");
            helper.assertTrue(companion(helper, id) == null,
                    "cross-dimension refusal does not project from a hidden/unloaded lookup");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void summonPlacesCompanionBesideOwner(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionEntity companion = companion(helper, data(helper).get(player.getUUID()).companion());
            helper.assertTrue(companion != null, "summon created a projection");
            helper.assertFalse(companion.getBoundingBox().intersects(player.getBoundingBox()),
                    "summoned companion does not overlap the owner's bounding box");
            helper.assertTrue(helper.getLevel().noCollision(companion, companion.getBoundingBox()),
                    "summoned companion occupies a collision-free position");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void summonAtBlockCornerDoesNotOverlapOwner(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            player.absMoveTo(pos.getX() + 0.01D, pos.getY(), pos.getZ() + 0.01D, player.getYRot(),
                    player.getXRot());
            helper.getLevel().getChunkSource().move(player);

            useCharm(helper, player, false);
            CompanionRecord record = data(helper).get(player.getUUID());
            helper.assertTrue(record != null, "the corner summon created an authority record");
            CompanionEntity companion = companion(helper, record.companion());
            helper.assertTrue(companion != null, "the corner summon created a projection");
            helper.assertFalse(companion.getBoundingBox().intersects(player.getBoundingBox()),
                    "the corner summon does not overlap the owner's bounding box");
            helper.assertTrue(helper.getLevel().noCollision(companion, companion.getBoundingBox()),
                    "the corner summon remains collision-free");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void blockedPlacementPreservesPositionAndAuthority(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        List<BlockPos> placed = new ArrayList<>();
        try {
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord record = data.get(player.getUUID());
            UUID id = record.companion();
            CompanionEntity companion = companion(helper, id);
            helper.assertTrue(companion != null, "the initial summon created the projection");

            double oldX = player.getX() + 2.0D;
            double oldY = player.getY();
            double oldZ = player.getZ() + 2.0D;
            companion.absMoveTo(oldX, oldY, oldZ, companion.getYRot(), companion.getXRot());
            record.markActive(helper.getLevel().dimension().location(), oldX, oldY, oldZ);
            data.put(record);

            placed.addAll(blockPlacementCandidates(helper.getLevel(), player.blockPosition()));

            useCharm(helper, player, false);

            CompanionRecord after = data.get(player.getUUID());
            helper.assertTrue(after.state() == CompanionRecord.State.ACTIVE,
                    "a failed placement keeps the authority active");
            helper.assertTrue(after.dimension().equals(helper.getLevel().dimension().location()),
                    "a failed placement keeps the authority dimension");
            helper.assertTrue(Math.abs(after.x() - oldX) < 0.001D
                            && Math.abs(after.y() - oldY) < 0.001D
                            && Math.abs(after.z() - oldZ) < 0.001D,
                    "a failed placement keeps the authority position");
            helper.assertTrue(Math.abs(companion.getX() - oldX) < 0.001D
                            && Math.abs(companion.getY() - oldY) < 0.001D
                            && Math.abs(companion.getZ() - oldZ) < 0.001D,
                    "a failed placement restores the live entity position");
            helper.assertTrue(!companion.isRemoved(), "a failed placement keeps the live projection");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1,
                    "a failed placement leaves exactly one projection");
            helper.succeed();
        } finally {
            for (BlockPos pos : placed) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void companionEntityIsTransient(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionRecord record = data(helper).get(player.getUUID());
            helper.assertTrue(record != null, "the charm created an authority record");
            CompanionEntity live = companion(helper, record.companion());
            helper.assertTrue(live != null, "the registered companion entity is live");

            helper.assertFalse(BharatCodeCompanion.COMPANION.get().canSerialize(),
                    "the registered companion entity type is transient");
            helper.assertTrue(live.getEncodeId() == null, "a companion has no serializable entity id");

            CompoundTag tag = new CompoundTag();
            helper.assertFalse(live.save(tag), "a normal entity save refuses a companion");
            helper.assertTrue(tag.isEmpty(), "a refused save writes no entity data");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reloadedAuthorityRestoresActiveAndNotDormant(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord record = data.get(player.getUUID());
            UUID id = record.companion();
            CompanionEntity live = companion(helper, id);
            helper.assertTrue(live != null, "the projection exists before the simulated restart");
            live.discard();
            helper.assertTrue(companion(helper, id) == null, "the transient projection is gone after restart");

            CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            CompanionSavedData reloaded = CompanionSavedData.load(saved, helper.getLevel().registryAccess());
            helper.assertTrue(reloaded.isUsable(), "the serialized authority reloads as usable");
            install(helper, reloaded);

            CompanionService.onServerStarted(new ServerStartedEvent(helper.getLevel().getServer()));
            CompanionEntity restored = companion(helper, id);
            helper.assertTrue(restored != null, "the active companion is restored from the reloaded authority");
            helper.assertTrue(player.getUUID().equals(restored.getOwnerUUID()),
                    "the restored companion kept the authority owner");
            helper.assertTrue("Companion".equals(restored.getCustomName().getString()),
                    "the restored companion kept the authority name");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1, "the restore did not duplicate");

            useCharm(helper, player, true);
            helper.assertTrue(CompanionSavedData.get(helper.getLevel().getServer()).get(player.getUUID())
                    .state() == CompanionRecord.State.DORMANT, "dismiss marked the reloaded authority dormant");

            CompoundTag dormantSaved = CompanionSavedData.get(helper.getLevel().getServer())
                    .save(new CompoundTag(), helper.getLevel().registryAccess());
            CompanionSavedData dormantReloaded = CompanionSavedData.load(dormantSaved,
                    helper.getLevel().registryAccess());
            install(helper, dormantReloaded);
            CompanionService.onServerStarted(new ServerStartedEvent(helper.getLevel().getServer()));
            helper.assertTrue(companion(helper, id) == null, "a dormant authority does not restore a projection");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void staleCrossDimensionProjectionIsRejected(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord record = data.get(player.getUUID());
            UUID id = record.companion();
            CompanionEntity original = companion(helper, id);
            helper.assertTrue(original != null, "the projection exists before the authority moves");
            ServerLevel overworld = helper.getLevel();
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "the nether level exists");
            BlockPos farNether = nether.getSharedSpawnPos().offset(3000, 0, 3000);

            CompanionEntity wrongDimension = BharatCodeCompanion.COMPANION.get().create(nether);
            helper.assertTrue(wrongDimension != null, "nether copy created");
            wrongDimension.setUUID(id);
            wrongDimension.setOwnerUUID(player.getUUID());
            wrongDimension.setCustomName(Component.literal("Stale"));
            wrongDimension.setPos(farNether.getX() + 0.5D, farNether.getY(), farNether.getZ() + 0.5D);
            helper.assertFalse(nether.addFreshEntity(wrongDimension),
                    "a copy in a dimension the authority does not name is rejected on join");
            helper.assertTrue(wrongDimension.isRemoved(), "the rejected cross-dimension copy is discarded");

            record.markActive(nether.dimension().location(), farNether.getX() + 0.5D, farNether.getY(),
                    farNether.getZ() + 0.5D);
            data.put(record);

            CompanionService.onServerStarted(new ServerStartedEvent(helper.getLevel().getServer()));
            helper.assertTrue(original.isRemoved(),
                    "start reconciliation discards a copy the authority no longer places in that dimension");
            helper.assertTrue(companion(helper, id) == null, "no live copy remains in the old dimension");

            BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
            record.markActive(overworld.dimension().location(), home.getX(), home.getY(), home.getZ());
            data.put(record);
            CompanionService.onServerStarted(new ServerStartedEvent(helper.getLevel().getServer()));
            helper.assertTrue(companion(helper, id) != null, "the authority re-projects in its own dimension");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1, "no duplicate survived reconciliation");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void chunkLoadRestoresActiveProjection(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        CompanionSavedData data = data(helper);
        UUID id = UUID.randomUUID();
        BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(3000, 0, 3000);
        double farY = 200.0D;
        CompanionRecord record = new CompanionRecord(player.getUUID(), id, "Companion",
                CompanionRecord.State.ACTIVE, helper.getLevel().dimension().location(),
                far.getX() + 0.5D, farY, far.getZ() + 0.5D);
        data.put(record);

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        CompanionSavedData reloaded = CompanionSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(reloaded.isUsable(), "the reloaded authority is usable");
        install(helper, reloaded);

        // Walking to the recorded area loads its chunk with the player nearby:
        // the production chunk-load queue restores the active projection.
        player.absMoveTo(far.getX() + 0.5D, farY, far.getZ() + 0.5D, player.getYRot(), player.getXRot());
        helper.getLevel().getChunkSource().move(player);
        awaitChunkLoadRestore(helper, player, id, 0);
    }

    private static void awaitChunkLoadRestore(GameTestHelper helper, ServerPlayer player, UUID id, int attempt) {
        if (companion(helper, id) == null && attempt < 100) {
            helper.runAfterDelay(1, () -> awaitChunkLoadRestore(helper, player, id, attempt + 1));
            return;
        }
        try {
            CompanionEntity restored = companion(helper, id);
            helper.assertTrue(restored != null, "the chunk load restored the active companion");
            helper.assertTrue(player.getUUID().equals(restored.getOwnerUUID()),
                    "the chunk-load restore kept the authority owner");
            helper.assertTrue("Companion".equals(restored.getCustomName().getString()),
                    "the chunk-load restore kept the authority name");
            helper.assertTrue(ownedCount(helper, player.getUUID()) == 1,
                    "the chunk-load restore did not duplicate");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void legacySavedProjectionIsFenced(GameTestHelper helper) {
        ServerPlayer player = joinPlayer(helper);
        try {
            useCharm(helper, player, false);
            CompanionSavedData data = data(helper);
            CompanionRecord record = data.get(player.getUUID());
            UUID id = record.companion();
            CompanionEntity live = companion(helper, id);
            helper.assertTrue(live != null, "live projection exists");
            live.discard();

            CompanionEntity legacy = BharatCodeCompanion.COMPANION.get().create(helper.getLevel());
            helper.assertTrue(legacy != null, "legacy copy created");
            legacy.setUUID(id);
            legacy.setOwnerUUID(player.getUUID());
            legacy.setCustomName(Component.literal("Companion"));
            legacy.setPos(player.getX() + 2.0D, player.getY(), player.getZ());

            EntityJoinLevelEvent event = new EntityJoinLevelEvent(legacy, helper.getLevel(), true);
            NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled(),
                    "a disk-loaded companion is fenced even when the authority record matches");
            helper.assertTrue(legacy.isRemoved(), "the legacy saved projection is discarded");
            helper.assertTrue(companion(helper, id) == null, "the legacy copy was not adopted");
            helper.succeed();
        } finally {
            leavePlayer(helper, player);
        }
    }

    @SuppressWarnings("deprecation")
    private static ServerPlayer joinPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return player;
    }

    private static ModePlayer joinModePlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mode-player"), false);
        ModePlayer player = new ModePlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.absMoveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.getChunkSource().move(player);
        return player;
    }

    private static final class ModePlayer extends ServerPlayer {
        private boolean spectator;

        private ModePlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                ClientInformation information) {
            super(server, level, profile, information);
        }

        @Override
        public boolean isSpectator() {
            return spectator;
        }

        @Override
        public boolean isCreative() {
            return true;
        }

        private void enterSpectator() {
            spectator = true;
        }
    }

    private static final class JoinAttemptListener {
        private final UUID id;
        private int attempts;
        private int cancellations;

        private JoinAttemptListener(UUID id) {
            this.id = id;
        }

        @SubscribeEvent
        public void onJoin(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof CompanionEntity companion && id.equals(companion.getUUID())) {
                attempts++;
                event.setCanceled(true);
                cancellations++;
            }
        }

        private int attempts() {
            return attempts;
        }
    }

    private static void leavePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void useCharm(GameTestHelper helper, ServerPlayer player, boolean dismiss) {
        player.setShiftKeyDown(dismiss);
        ItemStack stack = new ItemStack(BharatCodeCompanion.SUMMONING_CHARM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BharatCodeCompanion.SUMMONING_CHARM.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
    }

    private static CompanionSavedData data(GameTestHelper helper) {
        return CompanionSavedData.get(helper.getLevel().getServer());
    }

    private static void install(GameTestHelper helper, CompanionSavedData data) {
        helper.getLevel().getServer().overworld().getDataStorage()
                .set(CompanionSavedData.DATA_NAME, data);
    }

    private static CompanionEntity companion(GameTestHelper helper, UUID id) {
        return helper.getLevel().getEntities().get(id) instanceof CompanionEntity companion ? companion : null;
    }

    private static CompanionEntity hiddenInRecordedChunk(GameTestHelper helper, CompanionRecord record) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = chunkOf(record);
        AABB box = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
        List<CompanionEntity> found = level.getEntities(EntityTypeTest.forClass(CompanionEntity.class), box,
                companion -> record.companion().equals(companion.getUUID()));
        return found.isEmpty() ? null : found.get(0);
    }

    private static ChunkPos chunkOf(CompanionRecord record) {
        return chunkOfAt(record.x(), record.z());
    }

    private static ChunkPos chunkOfAt(double x, double z) {
        return new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    private static CompanionEntity findCompanionInChunk(ServerLevel level, UUID id, ChunkPos chunk) {
        Entity visible = level.getEntities().get(id);
        if (visible instanceof CompanionEntity companion && chunk.equals(companion.chunkPosition())) {
            return companion;
        }
        AABB box = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
        List<CompanionEntity> found = level.getEntities(EntityTypeTest.forClass(CompanionEntity.class), box,
                companion -> id.equals(companion.getUUID()));
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<BlockPos> blockPlacementCandidates(ServerLevel level, BlockPos origin) {
        List<BlockPos> placed = new ArrayList<>();
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                            placed.add(pos);
                        }
                    }
                }
            }
        }
        return placed;
    }

    private static int ownedCount(GameTestHelper helper, UUID owner) {
        int count = 0;
        for (Entity entity : helper.getLevel().getEntities().getAll()) {
            if (entity instanceof CompanionEntity companion && owner.equals(companion.getOwnerUUID())) {
                count++;
            }
        }
        return count;
    }
}
