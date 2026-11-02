package com.zonlong.bossrespawner.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A one-shot instruction for the entity-join hook: when the native Twilight Forest spawner creates a
 * boss, restore its {@code HomePos} to the cage's anchor instead of the spawner cell.
 *
 * <p>Only used by {@code spawn.transform.force_place} rules (currently the Lich and Minoshroom). Their
 * spawners replace the reward chest, one block below the original home; without this override the
 * spawned boss would inherit the chest cell as its home and the next reward chest/cage cycle would
 * drift.
 *
 * @param spawnerDimension dimension of the placed spawner
 * @param spawnerPos       position of the placed spawner (the reward-chest cell)
 * @param anchorDimension  dimension of the original home anchor
 * @param anchorPos        original home anchor to restore
 * @param createdAt        game time when the override was recorded, for expiry
 */
public record PendingHomeOverride(ResourceKey<Level> spawnerDimension, BlockPos spawnerPos,
                                  ResourceKey<Level> anchorDimension, BlockPos anchorPos, long createdAt) {

    /** Overrides older than this are ignored; the native spawner may wait a long time for a player. */
    public static final long TTL_TICKS = 20L * 60L * 60L;

    private static final String TAG_SPAWNER_DIMENSION = "SpawnerDimension";
    private static final String TAG_SPAWNER_POS = "SpawnerPos";
    private static final String TAG_ANCHOR_DIMENSION = "AnchorDimension";
    private static final String TAG_ANCHOR_POS = "AnchorPos";
    private static final String TAG_CREATED_AT = "CreatedAt";

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        BossHomeNbt.putDimension(tag, TAG_SPAWNER_DIMENSION, spawnerDimension);
        BossHomeNbt.putBlockPos(tag, TAG_SPAWNER_POS, spawnerPos);
        BossHomeNbt.putDimension(tag, TAG_ANCHOR_DIMENSION, anchorDimension);
        BossHomeNbt.putBlockPos(tag, TAG_ANCHOR_POS, anchorPos);
        tag.putLong(TAG_CREATED_AT, createdAt);
        return tag;
    }

    public static PendingHomeOverride fromTag(CompoundTag tag) {
        return new PendingHomeOverride(
                BossHomeNbt.getDimension(tag, TAG_SPAWNER_DIMENSION),
                BossHomeNbt.getBlockPos(tag, TAG_SPAWNER_POS),
                BossHomeNbt.getDimension(tag, TAG_ANCHOR_DIMENSION),
                BossHomeNbt.getBlockPos(tag, TAG_ANCHOR_POS),
                tag.getLong(TAG_CREATED_AT));
    }
}
