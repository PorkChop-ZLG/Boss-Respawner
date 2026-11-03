package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.HomeRecord;
import com.zonlong.bossrespawner.data.PendingPlacementTask;
import com.zonlong.bossrespawner.data.RespawnEntry;
import com.zonlong.bossrespawner.data.RespawnRuleSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Schedules the Twilight Forest placement mode.
 *
 * <p>Twilight Forest bosses carry their own {@code HomePos} (written by its {@code EnforcedHomePoint}).
 * Their loot chest appears when the entity is removed with {@code RemovalReason.KILLED}, at
 * {@code HomePos.below()}. The cage must therefore:
 *
 * <ol>
 *   <li>anchor to the boss's own {@code HomePos} — not to the first position this mod observed;</li>
 *   <li>wait until the entity is removed, so the chest has already been placed;</li>
 *   <li>be placed <em>beside</em> the anchor, never in the anchor cell or above the chest, because a
 *       chest directly under a solid block cannot be opened.</li>
 * </ol>
 *
 * <p>This class only reads vanilla NBT ({@code HomePos}, {@code MasterLich}) and compares entity types by
 * registry id. It never references a Twilight Forest class, so the mod keeps working when that mod is not
 * installed.
 */
public final class TwilightSettlementService {

    /** Registry id of the six-entity Phantom Knight group. */
    public static final String KNIGHT_PHANTOM = "twilightforest:knight_phantom";

    /** Registry id of the Lich, whose shadow clones are also {@code twilightforest:lich}. */
    public static final String LICH = "twilightforest:lich";

    /** Radius Twilight Forest itself uses to group the Phantom Knights. */
    public static final double KNIGHT_GROUP_RADIUS = 64.0D;

    private static final String TAG_HOME_POS = "HomePos";
    private static final String TAG_MASTER_LICH = "MasterLich";

    private TwilightSettlementService() {
    }

    /**
     * Queues the delayed placement for one Twilight Forest boss death.
     *
     * <p>Non-final Phantom Knights and Lich shadow clones are filtered out here: they are not the boss's
     * defeat, and only the final knight produces a loot chest.
     */
    public static void schedule(ServerLevel level, LivingEntity entity, RespawnEntry entry, String entityTypeId) {
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            return;
        }
        if (entry.placement().mode() != RespawnEntry.PlacementMode.TWILIGHTFOREST) {
            return;
        }

        if (isLichShadowClone(entity, entityTypeId)) {
            DebugLog.info("Skipping Twilight Forest settlement for Lich shadow clone uuid={}", entity.getUUID());
            dropHomeRecord(level, entity);
            return;
        }

        if (isNonFinalKnight(level, entity, entityTypeId)) {
            DebugLog.info("Skipping Twilight Forest settlement for non-final Phantom Knight uuid={}",
                    entity.getUUID());
            dropHomeRecord(level, entity);
            return;
        }

        Anchor anchor = resolveAnchor(entity);
        int[] offset = entry.placement().offset();
        BlockPos target = anchor.pos().offset(offset[0], offset[1], offset[2]);

        ResourceLocation typeId = ResourceLocation.tryParse(entityTypeId);
        if (typeId == null) {
            UniversalBossRespawner.LOGGER.warn(
                    "Cannot schedule Twilight Forest settlement for malformed entity id '{}'", entityTypeId);
            return;
        }

        long now = level.getGameTime();
        PendingPlacementTask task = new PendingPlacementTask(
                anchor.dimension(),
                target,
                entity.getUUID(),
                typeId,
                new HomeRecord(entity.getUUID(), typeId, anchor.dimension(), anchor.pos()),
                RespawnRuleSnapshot.of(entry),
                now,
                now,
                // Wait for this exact entity to be removed; its removal is also when Twilight Forest
                // deposits the loot chest, so the cage can never land before the chest exists.
                entity.getUUID(),
                level.dimension(),
                entity.blockPosition());

        BossHomeSavedData data = BossHomeSavedData.get(level);
        UUID displaced = data.enqueuePendingPlacement(task);
        if (displaced != null && !displaced.equals(entity.getUUID())) {
            // The merged-away death's own home record is now redundant: the surviving task carries its
            // anchor snapshot. This mirrors what the ordinary deferred placement does.
            data.remove(displaced);
        }

        DebugLog.info(
                "Scheduled Twilight Forest cage for {} (uuid={}) at {} beside home {} in {}; waiting for "
                        + "entity removal",
                entityTypeId, entity.getUUID(), target, anchor.pos(), anchor.dimension().location());
    }

    /** Removes a skipped entity's home record, if the generic home tracker ever created one. */
    private static void dropHomeRecord(ServerLevel level, LivingEntity entity) {
        BossHomeSavedData.get(level).remove(entity.getUUID());
    }

    /**
     * Reads Twilight Forest's {@code HomePos} from the entity's own save data.
     *
     * <p>The compound is a {@code GlobalPos}: {@code {dimension: "twilightforest:twilight_forest",
     * pos: [I;x,y,z]}}. A home in another dimension is ignored, because Twilight Forest itself falls back
     * to the death position in that case.
     */
    private static BlockPos readHomePos(LivingEntity entity) {
        CompoundTag tag;
        try {
            tag = entity.saveWithoutId(new CompoundTag());
        } catch (Exception e) {
            UniversalBossRespawner.LOGGER.warn(
                    "Could not read HomePos from {} (uuid={}): {}",
                    entity.getType(), entity.getUUID(), e.toString());
            return null;
        }
        return parseHomePos(tag, entity.level().dimension());
    }

    /**
     * Parses Twilight Forest's {@code HomePos} compound from an entity save tag.
     *
     * <p>Package-visible so the NBT contract can be unit-tested without a running level.
     *
     * @return the anchor, or {@code null} when the tag is absent/malformed or the home is in another
     *         dimension (Twilight Forest itself falls back to the death position in that case)
     */
    static BlockPos parseHomePos(CompoundTag tag, ResourceKey<Level> currentDimension) {
        if (!tag.contains(TAG_HOME_POS, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag home = tag.getCompound(TAG_HOME_POS);
        String dimension = home.getString("dimension");
        int[] pos = home.getIntArray("pos");
        if (dimension.isBlank() || pos.length != 3) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        if (dimensionId == null) {
            return null;
        }
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (!dimensionKey.equals(currentDimension)) {
            return null;
        }
        return new BlockPos(pos[0], pos[1], pos[2]);
    }

    /**
     * Resolves the anchor: the boss's own home, or the death position when it has none (a summoned boss,
     * or a boss whose HomePos is in another dimension).
     */
    private static Anchor resolveAnchor(LivingEntity entity) {
        BlockPos home = readHomePos(entity);
        if (home != null) {
            return new Anchor(entity.level().dimension(), home);
        }
        UniversalBossRespawner.LOGGER.warn(
                "No usable HomePos for {} (uuid={}, dimension={}, position={}); using the death position as "
                        + "the Twilight Forest anchor",
                entity.getType(), entity.getUUID(), entity.level().dimension().location(), entity.blockPosition());
        return new Anchor(entity.level().dimension(), entity.blockPosition());
    }

    /** A Twilight Forest boss's home anchor. */
    private record Anchor(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** True only for a Lich shadow clone; the real Lich has no {@code MasterLich} tag. */
    private static boolean isLichShadowClone(LivingEntity entity, String entityTypeId) {
        if (!LICH.equals(entityTypeId)) {
            return false;
        }
        try {
            return hasMasterLich(entity.saveWithoutId(new CompoundTag()));
        } catch (Exception e) {
            // Be conservative: if the data cannot be read, treat it as the real Lich rather than silently
            // losing the boss's renewal.
            return false;
        }
    }

    /** Package-visible NBT predicate so the clone contract can be unit-tested. */
    static boolean hasMasterLich(CompoundTag tag) {
        return tag.contains(TAG_MASTER_LICH);
    }

    /**
     * True when another living Phantom Knight is within the group radius, meaning this death is not the
     * group's defeat and must not produce a cage.
     *
     * <p>This mirrors {@code KnightPhantom#getNearbyKnights()}, which uses {@code isAlive()} (health &gt; 0)
     * and a 64 block radius. Dead knights are excluded, so the last one to fall is the only final death.
     */
    private static boolean isNonFinalKnight(ServerLevel level, LivingEntity entity, String entityTypeId) {
        if (!KNIGHT_PHANTOM.equals(entityTypeId)) {
            return false;
        }
        List<Entity> nearby = level.getEntitiesOfClass(
                Entity.class,
                entity.getBoundingBox().inflate(KNIGHT_GROUP_RADIUS),
                other -> other != entity && other.isAlive() && other.getType() == entity.getType());
        return !nearby.isEmpty();
    }
}
