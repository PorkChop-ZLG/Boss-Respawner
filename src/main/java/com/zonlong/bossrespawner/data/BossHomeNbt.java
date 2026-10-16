package com.zonlong.bossrespawner.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared NBT helpers for the on-disk format of {@link BossHomeSavedData}.
 *
 * <p>All readers validate the stored type and throw {@link IllegalArgumentException} when a required
 * field is missing or malformed. Callers catch that per record so a single corrupt entry can be
 * skipped without failing the whole world load.
 */
final class BossHomeNbt {

    private BossHomeNbt() {
    }

    // ------------------------------------------------------------------ dimensions

    static void putDimension(CompoundTag tag, String key, ResourceKey<Level> dimension) {
        tag.putString(key, dimension.location().toString());
    }

    static ResourceKey<Level> getDimension(CompoundTag tag, String key) {
        String raw = requireString(tag, key);
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(raw));
    }

    // ------------------------------------------------------------------ block positions

    static void putBlockPos(CompoundTag tag, String key, BlockPos pos) {
        CompoundTag inner = new CompoundTag();
        inner.putInt("X", pos.getX());
        inner.putInt("Y", pos.getY());
        inner.putInt("Z", pos.getZ());
        tag.put(key, inner);
    }

    static BlockPos getBlockPos(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("missing block position compound '" + key + "'");
        }
        CompoundTag inner = tag.getCompound(key);
        return new BlockPos(
                requireInt(inner, "X"),
                requireInt(inner, "Y"),
                requireInt(inner, "Z"));
    }

    // ------------------------------------------------------------------ primitives

    static String requireString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing string '" + key + "'");
        }
        String value = tag.getString(key);
        if (value.isBlank()) {
            throw new IllegalArgumentException("blank string '" + key + "'");
        }
        return value;
    }

    static int requireInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("missing int '" + key + "'");
        }
        return tag.getInt(key);
    }

    static ResourceLocation getResourceLocation(CompoundTag tag, String key) {
        return ResourceLocation.parse(requireString(tag, key));
    }

    // ------------------------------------------------------------------ string lists

    static void putStringList(CompoundTag tag, String key, List<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        tag.put(key, list);
    }

    static List<String> getStringList(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        List<String> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return List.copyOf(result);
    }

    /** Reads an {@code int[3]}; rejects arrays of any other length. */
    static int[] getIntArray3(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("missing int[3] '" + key + "'");
        }
        int[] value = tag.getIntArray(key);
        if (value.length != 3) {
            throw new IllegalArgumentException("int array '" + key + "' must have length 3 but was " + value.length);
        }
        return value;
    }
}
