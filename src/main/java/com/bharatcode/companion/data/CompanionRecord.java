package com.bharatcode.companion.data;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The durable authority for one owner's companion identity and active/dormant
 * state. It deliberately stores no entity NBT: the live entity is a projection
 * that is recreated from this record, never a second copy of the authority.
 */
public final class CompanionRecord {
    public enum State {
        ACTIVE,
        DORMANT
    }

    public static final int RECORD_VERSION = 2;

    private final UUID owner;
    private final UUID companion;
    private final String name;
    private State state;
    private ResourceLocation dimension;
    private double x;
    private double y;
    private double z;

    public CompanionRecord(UUID owner, UUID companion, String name, State state, ResourceLocation dimension,
            double x, double y, double z) {
        if (owner == null || companion == null) {
            throw new IllegalArgumentException("owner and companion UUIDs are required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a companion name is required");
        }
        if (state == null || dimension == null) {
            throw new IllegalArgumentException("state and dimension are required");
        }
        this.owner = owner;
        this.companion = companion;
        this.name = name;
        this.state = state;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID owner() {
        return owner;
    }

    public UUID companion() {
        return companion;
    }

    public String name() {
        return name;
    }

    public State state() {
        return state;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public void markActive(ResourceLocation dimension, double x, double y, double z) {
        this.state = State.ACTIVE;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void markDormant(ResourceLocation dimension, double x, double y, double z) {
        this.state = State.DORMANT;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner);
        tag.putUUID("Companion", companion);
        tag.putString("Name", name);
        tag.putString("State", state.name());
        tag.putString("Dimension", dimension.toString());
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putInt("RecordVersion", RECORD_VERSION);
        return tag;
    }

    public static CompanionRecord load(CompoundTag tag) {
        if (!tag.hasUUID("Owner") || !tag.hasUUID("Companion")) {
            throw new IllegalArgumentException("missing owner or companion UUID");
        }
        int version = tag.getInt("RecordVersion");
        if (version != RECORD_VERSION) {
            throw new IllegalArgumentException("unsupported record version " + version);
        }

        State state;
        try {
            state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown state '" + tag.getString("State") + "'");
        }

        String name = tag.getString("Name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("missing companion name");
        }

        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) {
            throw new IllegalArgumentException("invalid dimension '" + tag.getString("Dimension") + "'");
        }

        if (!tag.contains("X", Tag.TAG_DOUBLE) || !tag.contains("Y", Tag.TAG_DOUBLE)
                || !tag.contains("Z", Tag.TAG_DOUBLE)) {
            throw new IllegalArgumentException("missing position");
        }
        double x = tag.getDouble("X");
        double y = tag.getDouble("Y");
        double z = tag.getDouble("Z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("non-finite position");
        }

        return new CompanionRecord(tag.getUUID("Owner"), tag.getUUID("Companion"), name, state, dimension, x, y, z);
    }
}
