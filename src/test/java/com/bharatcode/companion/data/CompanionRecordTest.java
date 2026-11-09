package com.bharatcode.companion.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class CompanionRecordTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void roundTripPreservesIdentityNameStateAndPosition() {
        UUID owner = UUID.randomUUID();
        UUID companion = UUID.randomUUID();

        CompanionRecord original = new CompanionRecord(owner, companion, "Bharat", CompanionRecord.State.DORMANT,
                OVERWORLD, 12.5D, 64.0D, -7.5D);
        CompanionRecord loaded = CompanionRecord.load(original.save());

        assertEquals(owner, loaded.owner());
        assertEquals(companion, loaded.companion());
        assertEquals("Bharat", loaded.name());
        assertEquals(CompanionRecord.State.DORMANT, loaded.state());
        assertEquals(OVERWORLD, loaded.dimension());
        assertEquals(12.5D, loaded.x());
        assertEquals(64.0D, loaded.y());
        assertEquals(-7.5D, loaded.z());
    }

    @Test
    void recordHasNoEntitySnapshot() {
        CompanionRecord record = new CompanionRecord(UUID.randomUUID(), UUID.randomUUID(), "Companion",
                CompanionRecord.State.DORMANT, OVERWORLD, 0.0D, 64.0D, 0.0D);

        assertEquals(false, record.save().contains("Entity"));
    }

    @Test
    void unknownRecordVersionIsRejected() {
        UUID owner = UUID.randomUUID();
        CompanionRecord record = new CompanionRecord(owner, UUID.randomUUID(), "Companion",
                CompanionRecord.State.ACTIVE, OVERWORLD, 0.0D, 64.0D, 0.0D);
        CompoundTag tag = record.save();
        tag.putInt("RecordVersion", 99);

        assertThrows(IllegalArgumentException.class, () -> CompanionRecord.load(tag));
    }

    @Test
    void missingNameIsRejected() {
        CompanionRecord record = new CompanionRecord(UUID.randomUUID(), UUID.randomUUID(), "Companion",
                CompanionRecord.State.ACTIVE, OVERWORLD, 0.0D, 64.0D, 0.0D);
        CompoundTag tag = record.save();
        tag.remove("Name");

        assertThrows(IllegalArgumentException.class, () -> CompanionRecord.load(tag));
    }

    @Test
    void recordWithoutPositionIsRejected() {
        CompanionRecord record = new CompanionRecord(UUID.randomUUID(), UUID.randomUUID(), "Companion",
                CompanionRecord.State.ACTIVE, OVERWORLD, 0.0D, 64.0D, 0.0D);
        CompoundTag tag = record.save();
        tag.remove("X");

        assertThrows(IllegalArgumentException.class, () -> CompanionRecord.load(tag));
    }
}
