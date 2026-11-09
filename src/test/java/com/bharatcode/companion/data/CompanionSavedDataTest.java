package com.bharatcode.companion.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

class CompanionSavedDataTest {
    @Test
    void unknownSchemaIsInertAndPreserved() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 999);
        tag.putString("FutureField", "must-survive");

        CompanionSavedData data = CompanionSavedData.load(tag, null);

        assertTrue(data.isInert());
        assertFalse(data.isUsable());
        CompoundTag saved = data.save(new CompoundTag(), null);
        assertEquals(999, saved.getInt("DataVersion"));
        assertEquals("must-survive", saved.getString("FutureField"));
    }

    @Test
    void oldSchemaIsInertAndPreserved() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 1);
        ListTag records = new ListTag();
        CompoundTag old = new CompoundTag();
        old.putInt("RecordVersion", 1);
        old.putString("Note", "old-schema-must-survive");
        records.add(old);
        tag.put("Companions", records);

        CompanionSavedData data = CompanionSavedData.load(tag, null);

        assertTrue(data.isInert());
        assertFalse(data.isUsable());
        CompoundTag saved = data.save(new CompoundTag(), null);
        assertEquals(1, saved.getInt("DataVersion"));
        assertEquals(1, saved.getList("Companions", Tag.TAG_COMPOUND).size());
        assertEquals("old-schema-must-survive",
                saved.getList("Companions", Tag.TAG_COMPOUND).getCompound(0).getString("Note"));
    }

    @Test
    void wrongListElementTypeIsInertAndPreserved() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", CompanionSavedData.SCHEMA_VERSION);
        ListTag wrongType = new ListTag();
        wrongType.add(StringTag.valueOf("not-a-companion-record"));
        tag.put("Companions", wrongType);
        tag.putString("FutureField", "must-survive");

        CompanionSavedData data = CompanionSavedData.load(tag, null);

        assertTrue(data.isInert());
        assertFalse(data.isUsable());
        CompoundTag saved = data.save(new CompoundTag(), null);
        assertEquals(CompanionSavedData.SCHEMA_VERSION, saved.getInt("DataVersion"));
        assertEquals("must-survive", saved.getString("FutureField"));
        ListTag savedList = (ListTag) saved.get("Companions");
        assertEquals(Tag.TAG_STRING, savedList.getElementType());
        assertEquals(1, savedList.size());
        assertEquals("not-a-companion-record", savedList.getString(0));
    }

    @Test
    void nonListRecordsTagIsInertAndPreserved() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", CompanionSavedData.SCHEMA_VERSION);
        tag.putString("Companions", "not-a-list");

        CompanionSavedData data = CompanionSavedData.load(tag, null);

        assertTrue(data.isInert());
        CompoundTag saved = data.save(new CompoundTag(), null);
        assertEquals("not-a-list", saved.getString("Companions"));
    }

    @Test
    void corruptRecordIsQuarantinedAndPreserved() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", CompanionSavedData.SCHEMA_VERSION);
        ListTag records = new ListTag();
        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("State", "ACTIVE");
        corrupt.putString("Note", "must-survive");
        records.add(corrupt);
        tag.put("Companions", records);

        CompanionSavedData data = CompanionSavedData.load(tag, null);

        assertTrue(data.isCorrupt());
        assertFalse(data.isUsable());
        CompoundTag saved = data.save(new CompoundTag(), null);
        ListTag savedRecords = saved.getList("Companions", Tag.TAG_COMPOUND);
        assertEquals(1, savedRecords.size());
        assertEquals("must-survive", savedRecords.getCompound(0).getString("Note"));
    }
}
