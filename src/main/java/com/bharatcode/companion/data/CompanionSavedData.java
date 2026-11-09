package com.bharatcode.companion.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The single durable authority for companion identity and state. It stores no
 * entity snapshots. Unknown or old schemas and malformed roots are preserved
 * verbatim and made inert rather than being reset.
 */
public final class CompanionSavedData extends SavedData {
    public static final String DATA_NAME = "bharatcode_companion_companions";
    public static final int SCHEMA_VERSION = 2;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_VERSION = "DataVersion";
    private static final String TAG_COMPANIONS = "Companions";

    private final Map<UUID, CompanionRecord> records = new LinkedHashMap<>();
    private final List<CompoundTag> quarantined = new ArrayList<>();
    private boolean inert;
    private boolean corrupt;
    private CompoundTag rawRoot;

    private CompanionSavedData() {
    }

    public static CompanionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CompanionSavedData::new, CompanionSavedData::load), DATA_NAME);
    }

    public static CompanionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionSavedData data = new CompanionSavedData();
        try {
            if (!tag.contains(TAG_VERSION, Tag.TAG_INT) || tag.getInt(TAG_VERSION) != SCHEMA_VERSION) {
                data.makeInert(tag, "Companion data has an unsupported or missing schema version");
                return data;
            }

            Tag rawList = tag.get(TAG_COMPANIONS);
            if (!(rawList instanceof ListTag list)) {
                data.makeInert(tag, "Companion data is missing its record list");
                return data;
            }
            // getList(key, TAG_COMPOUND) silently returns an empty list when the raw
            // element type is different, which would drop records. Validate the raw
            // type and preserve the whole root instead.
            if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
                data.makeInert(tag, "Companion record list has element type " + list.getElementType()
                        + " instead of compound");
                return data;
            }

            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                try {
                    CompanionRecord record = CompanionRecord.load(entry);
                    if (data.records.putIfAbsent(record.owner(), record) != null) {
                        data.corrupt = true;
                        data.quarantined.add(entry.copy());
                        LOGGER.error("Duplicate companion record for owner {}; preserving the duplicate and failing closed",
                                record.owner());
                    }
                } catch (RuntimeException ex) {
                    data.corrupt = true;
                    data.quarantined.add(entry.copy());
                    LOGGER.error("Corrupt companion record at index {}; preserving it and failing closed: {}", i,
                            ex.toString());
                }
            }
        } catch (RuntimeException ex) {
            data.inert = true;
            data.rawRoot = tag.copy();
            data.records.clear();
            data.quarantined.clear();
            LOGGER.error("Could not read companion data; refusing to modify or delete it", ex);
        }
        return data;
    }

    private void makeInert(CompoundTag tag, String reason) {
        this.inert = true;
        this.rawRoot = tag.copy();
        LOGGER.error("{}; refusing to modify or delete it", reason);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (inert) {
            return rawRoot == null ? new CompoundTag() : rawRoot.copy();
        }

        CompoundTag out = new CompoundTag();
        out.putInt(TAG_VERSION, SCHEMA_VERSION);
        ListTag list = new ListTag();
        for (CompanionRecord record : records.values()) {
            list.add(record.save());
        }
        for (CompoundTag entry : quarantined) {
            list.add(entry.copy());
        }
        out.put(TAG_COMPANIONS, list);
        return out;
    }

    public boolean isUsable() {
        return !inert && !corrupt;
    }

    public boolean isInert() {
        return inert;
    }

    public boolean isCorrupt() {
        return corrupt;
    }

    public CompanionRecord get(UUID owner) {
        return records.get(owner);
    }

    public CompanionRecord byCompanion(UUID companion) {
        for (CompanionRecord record : records.values()) {
            if (record.companion().equals(companion)) {
                return record;
            }
        }
        return null;
    }

    public Collection<CompanionRecord> records() {
        return List.copyOf(records.values());
    }

    public void put(CompanionRecord record) {
        records.put(record.owner(), record);
        setDirty();
    }
}
