package com.zonlong.bossrespawner.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * One registered home anchor: the position at which this mod first observed the entity.
 *
 * <p>The stored position is the <em>raw</em> observation position; JSON offsets are applied later and
 * never written back, so the anchor cannot drift across respawn cycles.
 *
 * <p>Only registry IDs, UUIDs, dimensions and coordinates are persisted — never entity objects,
 * {@code EntityType} instances or third-party NBT.
 *
 * @param entityUuid the entity's UUID, which is also the map key
 * @param entityType registry ID of the entity type, kept for diagnostics
 * @param dimension  dimension the anchor lives in
 * @param homePos    the raw observed position
 */
public record HomeRecord(UUID entityUuid, ResourceLocation entityType,
                         ResourceKey<Level> dimension, BlockPos homePos) {

    private static final String TAG_UUID = "UUID";
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_UUID, entityUuid);
        tag.putString(TAG_ENTITY_TYPE, entityType.toString());
        BossHomeNbt.putDimension(tag, TAG_DIMENSION, dimension);
        BossHomeNbt.putBlockPos(tag, TAG_POS, homePos);
        return tag;
    }

    /**
     * @throws IllegalArgumentException when a required field is missing or malformed, so the caller can
     *                                  skip this single record and keep the rest of the save intact
     */
    public static HomeRecord fromTag(CompoundTag tag) {
        if (!tag.hasUUID(TAG_UUID)) {
            throw new IllegalArgumentException("home record is missing 'UUID'");
        }
        return new HomeRecord(
                tag.getUUID(TAG_UUID),
                BossHomeNbt.getResourceLocation(tag, TAG_ENTITY_TYPE),
                BossHomeNbt.getDimension(tag, TAG_DIMENSION),
                BossHomeNbt.getBlockPos(tag, TAG_POS));
    }
}
