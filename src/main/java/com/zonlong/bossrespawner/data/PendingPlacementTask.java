package com.zonlong.bossrespawner.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A placement that could not be performed synchronously because the target chunk was not loaded.
 *
 * <p>Tasks are keyed on the <em>placement target</em> ({@link Key}), not on the entity that died. Keying
 * on the source entity would be no de-duplication at all — every death is a new UUID — and the queue
 * would grow with the total number of deaths. Keying on the target bounds the queue by the number of
 * distinct placement targets that ever existed, which is what makes the "never expires" rule safe.
 *
 * <p>{@link #lastSourceEntityUuid} is informational only: it names the most recent death that fed this
 * target, for logging and diagnostics.
 *
 * <p>A full {@link RespawnRuleSnapshot} is carried so the task re-runs the original placement pipeline
 * with the rules as they were at death time, unaffected by later {@code /reload}s.
 */
public record PendingPlacementTask(
        ResourceKey<Level> targetDimension,
        BlockPos targetBase,
        UUID lastSourceEntityUuid,
        ResourceLocation entityType,
        @Nullable HomeRecord homeSnapshot,
        RespawnRuleSnapshot ruleSnapshot,
        long createdGameTime) {    private static final String TAG_DIMENSION = "TargetDimension";
    private static final String TAG_BASE = "TargetBase";
    private static final String TAG_SOURCE_UUID = "LastSourceEntityUuid";
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_HOME = "HomeSnapshot";
    private static final String TAG_RULES = "RuleSnapshot";
    private static final String TAG_CREATED = "CreatedGameTime";

    /** De-duplication key: one pending task per placement target. */
    public record Key(ResourceKey<Level> dimension, long basePos) {
        public static Key of(ResourceKey<Level> dimension, BlockPos basePos) {
            return new Key(dimension, basePos.asLong());
        }
    }

    public Key key() {
        return Key.of(targetDimension, targetBase);
    }

    /** Chunk that must be loaded before this task can run. */
    public long chunkPos() {
        return ChunkPos.asLong(targetBase.getX() >> 4, targetBase.getZ() >> 4);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        BossHomeNbt.putDimension(tag, TAG_DIMENSION, targetDimension);
        BossHomeNbt.putBlockPos(tag, TAG_BASE, targetBase);
        tag.putUUID(TAG_SOURCE_UUID, lastSourceEntityUuid);
        tag.putString(TAG_ENTITY_TYPE, entityType.toString());
        if (homeSnapshot != null) {
            tag.put(TAG_HOME, homeSnapshot.toTag());
        }
        tag.put(TAG_RULES, ruleSnapshot.toTag());
        tag.putLong(TAG_CREATED, createdGameTime);
        return tag;
    }

    /**
     * @throws IllegalArgumentException when a required field is missing or malformed, so the caller can
     *                                  skip this single task and keep the rest of the queue intact
     */
    public static PendingPlacementTask fromTag(CompoundTag tag) {
        if (!tag.hasUUID(TAG_SOURCE_UUID)) {
            throw new IllegalArgumentException("pending task is missing 'LastSourceEntityUuid'");
        }
        if (!tag.contains(TAG_RULES, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("pending task is missing 'RuleSnapshot'");
        }
        // A home snapshot is absent for DEATH-mode tasks, which anchor to the death position instead.
        HomeRecord home = tag.contains(TAG_HOME, Tag.TAG_COMPOUND)
                ? HomeRecord.fromTag(tag.getCompound(TAG_HOME))
                : null;
        return new PendingPlacementTask(
                BossHomeNbt.getDimension(tag, TAG_DIMENSION),
                BossHomeNbt.getBlockPos(tag, TAG_BASE),
                tag.getUUID(TAG_SOURCE_UUID),
                BossHomeNbt.getResourceLocation(tag, TAG_ENTITY_TYPE),
                home,
                RespawnRuleSnapshot.fromTag(tag.getCompound(TAG_RULES)),
                tag.getLong(TAG_CREATED));
    }
}
