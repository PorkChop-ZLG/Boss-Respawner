package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.HomeRecord;
import com.zonlong.bossrespawner.data.PendingPlacementTask;
import com.zonlong.bossrespawner.data.RespawnEntry;
import com.zonlong.bossrespawner.data.RespawnRuleSnapshot;
import com.zonlong.bossrespawner.init.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Anchors and writes respawn cages, and owns the deferred-placement pipeline.
 *
 * <p>Three outcomes are possible for a death:
 * <ul>
 *   <li>{@link Outcome#PLACED} — the cage was written;</li>
 *   <li>{@link Outcome#DEFERRED} — the target chunk was not loaded, so a persistent task was queued and
 *       the world was left untouched;</li>
 *   <li>{@link Outcome#ABANDONED} — a permanent condition prevents placement (unknown home, unavailable
 *       dimension, or {@code search_failure_fallback} disabled while the search found nothing), so no task
 *       is kept. Note that a block occupying the target is <em>not</em> such a condition: the force paths
 *       overwrite it by design.</li>
 * </ul>
 *
 * <p>The target <em>dimension</em> is resolved before the chunk check, because an unavailable dimension
 * and an unloaded chunk need opposite handling: the first must fail immediately (otherwise the task could
 * never complete and, having no expiry, would leak forever), the second must be deferred.
 */
public final class RespawnCagePlacer {

    /** Result of a placement attempt. */
    public enum Outcome {
        /** The cage exists. */
        PLACED,
        /** Nothing was written; a persistent task will retry once the target chunk is loaded. */
        DEFERRED,
        /** Nothing was written and nothing will be retried. */
        ABANDONED
    }

    private RespawnCagePlacer() {
    }

    /**
     * Death entry point: resolves the anchor mode, then runs the placement pipeline.
     *
     * @param deathLevel   the level the entity died in (not necessarily the target level)
     * @param entity       the dying entity
     * @param entry        the matched rule
     * @param entityTypeId registry ID of the entity type, as stored on the cage
     */
    public static Outcome tryPlace(ServerLevel deathLevel, LivingEntity entity, RespawnEntry entry,
                                   String entityTypeId) {
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            DebugLog.info("Respawn cage placement skipped: mod disabled (entity={})", entityTypeId);
            return Outcome.ABANDONED;
        }

        UUID sourceUuid = entity.getUUID();
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityType == null) {
            UniversalBossRespawner.LOGGER.warn("Cannot resolve entity type for {}; no respawn cage placed",
                    entityTypeId);
            return Outcome.ABANDONED;
        }

        RespawnEntry.PlacementRule placement = entry.placement();

        ResourceKey<Level> targetDimension;
        BlockPos origin;
        HomeRecord homeSnapshot = null;

        if (placement.mode() == RespawnEntry.PlacementMode.HOME) {
            BossHomeSavedData data = BossHomeSavedData.get(deathLevel.getServer());
            HomeRecord record = data.get(sourceUuid);
            if (record == null) {
                // Design rule: home mode never falls back to the death position.
                UniversalBossRespawner.LOGGER.warn(
                        "No home record for {} (uuid={}, death dimension={}, death position={}); "
                                + "no respawn cage placed. The entity was never observed alive by this mod, "
                                + "or the matching home rule was added after it loaded - check the rule load "
                                + "order or run /reload.",
                        entityTypeId, sourceUuid, deathLevel.dimension().location(), entity.blockPosition());
                return Outcome.ABANDONED;
            }
            targetDimension = record.dimension();
            origin = record.homePos();
            homeSnapshot = record;
        } else {
            targetDimension = deathLevel.dimension();
            origin = entity.blockPosition();
        }

        // The mode offset is applied exactly once, here, to the raw anchor.
        BlockPos base = origin.offset(placement.offset()[0], placement.offset()[1], placement.offset()[2]);

        Request request = new Request(
                deathLevel.getServer(),
                targetDimension,
                base,
                sourceUuid,
                entityType,
                entityTypeId,
                homeSnapshot,
                RespawnRuleSnapshot.of(entry),
                deathLevel.getGameTime());

        if (placement.placeDelayTicks() > 0) {
            // Queue instead of running the pipeline now. The boss's death animation may still be playing and
            // its loot chest may not exist yet; a chest landing where the cage wants to go is what stopped
            // the Minoshroom cage from being placed at all.
            DebugLog.info("Delaying respawn cage for {} at {} by {} ticks (place_delay_ticks)",
                    entityTypeId, base, placement.placeDelayTicks());
            return defer(deathLevel, request);
        }

        return execute(request);
    }

    /**
     * Re-runs the pipeline for a task that was deferred. Uses the stored rule snapshot, so a later
     * {@code /reload} cannot change how the original death is handled.
     *
     * <p>Takes the {@link MinecraftServer} rather than a {@link ServerLevel} on purpose: a task whose
     * target dimension has disappeared must still be able to run, so that it can be failed and cleaned up
     * instead of waiting forever for a chunk load that can never happen.
     */
    public static Outcome executeDeferred(MinecraftServer server, PendingPlacementTask task) {
        return execute(new Request(
                server,
                task.targetDimension(),
                task.targetBase(),
                task.lastSourceEntityUuid(),
                task.entityType(),
                task.entityType().toString(),
                task.homeSnapshot(),
                task.ruleSnapshot(),
                task.createdGameTime()));
    }

    // ------------------------------------------------------------------ pipeline

    private record Request(MinecraftServer server, ResourceKey<Level> targetDimension, BlockPos base,
                           UUID sourceEntityUuid, ResourceLocation entityType, String entityTypeId,
                           HomeRecord homeSnapshot, RespawnRuleSnapshot rules, long gameTime) {
    }

    private static Outcome execute(Request request) {
        ServerLevel level = request.server().getLevel(request.targetDimension());
        if (level == null) {
            // A dimension that does not exist can never load, so a deferred task would never complete.
            // Fail now, and drop the home record so it cannot leak either.
            UniversalBossRespawner.LOGGER.error(
                    "Target dimension {} is unavailable for the respawn cage of {} (uuid={}, base={}); "
                            + "no cage placed and no deferred task created.",
                    request.targetDimension().location(), request.entityTypeId(),
                    request.sourceEntityUuid(), request.base());
            dropHomeRecord(request);
            return Outcome.ABANDONED;
        }

        if (!level.isLoaded(request.base())) {
            // Never write here: this path runs from a death event or a chunk-load follow-up, and a write
            // would synchronously generate the chunk.
            return defer(level, request);
        }

        RespawnEntry.PlacementRule placement = request.rules().placement();

        BlockPos chosen;
        if (placement.forcePlace()) {
            chosen = request.base();
        } else {
            Optional<BlockPos> found = PlacementResolver.findPlacementPos(level, request.base(), placement);
            if (found.isPresent()) {
                chosen = found.get();
            } else if (placement.searchFailureFallback()) {
                DebugLog.info("No safe placement found; forcing respawn cage at {} for {}",
                        request.base(), request.entityTypeId());
                chosen = request.base();
            } else {
                UniversalBossRespawner.LOGGER.warn(
                        "No safe placement position for {} near {} and search_failure_fallback is false; "
                                + "no respawn cage placed",
                        request.entityTypeId(), request.base());
                return Outcome.ABANDONED;
            }
        }

        // Deliberately no protection check here. force_place and the fallback mean "place the cage no matter
        // what", so they overwrite whatever occupies the position - a loot chest, another mod's boss spawner,
        // even bedrock - and the replaced block's contents are gone rather than dropped. avoid_blocks is the
        // only way to steer the search away from a block, and both of these paths skip the search.
        if (placement.forcePlace() || chosen.equals(request.base())) {
            DebugLog.info("Writing respawn cage for {} at {} without protection checks", request.entityTypeId(),
                    chosen);
        }

        if (!hasExistingCageConflict(level, chosen, request)) {
            return Outcome.ABANDONED;
        }

        if (!writeCage(level, chosen, request)) {
            return Outcome.ABANDONED;
        }

        // The cage now carries the home anchor itself, so the death record has served its purpose.
        dropHomeRecord(request);
        DebugLog.info("Placed {} respawn cage at {}", request.entityTypeId(), chosen);
        return Outcome.PLACED;
    }

    /** Queues (or refreshes) the persistent deferred task for this placement target. */
    private static Outcome defer(ServerLevel level, Request request) {
        BossHomeSavedData data = BossHomeSavedData.get(request.server());
        // Recomputed from the death time, not from "now", so re-deferring an unloaded chunk cannot push the
        // deadline further out on every attempt: a task queued with a delay stays due at the same tick.
        long notBefore = request.gameTime() + Math.max(0, request.rules().placement().placeDelayTicks());
        PendingPlacementTask task = new PendingPlacementTask(
                request.targetDimension(),
                request.base(),
                request.sourceEntityUuid(),
                request.entityType(),
                request.homeSnapshot(),
                request.rules(),
                request.gameTime(),
                notBefore);

        UUID displaced = data.enqueuePendingPlacement(task);
        if (displaced != null && !displaced.equals(request.sourceEntityUuid())) {
            // This target already had a task from an earlier death; that death's home record is now
            // redundant because the merged task carries its own home snapshot.
            if (data.remove(displaced)) {
                DebugLog.info("Merged deferred placement at {}: dropped redundant home record for {}",
                        request.base(), displaced);
            }
        }

        DebugLog.info("Deferred respawn cage for {} at {} (not before tick {}); queue size={}",
                request.entityTypeId(), request.base(), notBefore, data.pendingCount());
        return Outcome.DEFERRED;
    }

    /** Drops the source entity's home record, if this placement came from a home-mode death. */
    private static void dropHomeRecord(Request request) {
        if (request.homeSnapshot() == null) {
            return;
        }
        BossHomeSavedData data = BossHomeSavedData.get(request.server());
        if (data.remove(request.sourceEntityUuid())) {
            DebugLog.info("Removed home record for {} after successful placement", request.sourceEntityUuid());
        }
    }

    /**
     * Applies {@code duplicate.mode} at the chosen position.
     *
     * @return {@code false} when {@code keep_existing} found a cage and the placement should stop
     */
    private static boolean hasExistingCageConflict(ServerLevel level, BlockPos pos, Request request) {
        String mode = request.rules().duplicate().mode();
        if ("allow_multiple".equals(mode)) {
            DebugLog.info("Duplicate mode is allow_multiple; skipping existing-cage check for {}",
                    request.entityTypeId());
            return true;
        }

        int radius = request.rules().duplicate().searchRadius();
        DebugLog.info("Checking existing cages: entity={} mode={} radius={} center={}",
                request.entityTypeId(), mode, radius, pos);
        if (!hasExistingCage(level, pos, request.entityTypeId(), radius)) {
            return true;
        }
        if ("keep_existing".equals(mode)) {
            DebugLog.info("Keeping existing {} respawn cage near {}", request.entityTypeId(), pos);
            return false;
        }
        DebugLog.info("Removing existing {} respawn cages near {} (replace_existing)", request.entityTypeId(), pos);
        removeExistingCages(level, pos, request.entityTypeId(), radius);
        return true;
    }

    private static boolean writeCage(ServerLevel level, BlockPos pos, Request request) {
        BlockState oldState = level.getBlockState(pos);
        if (!level.setBlock(pos, ModBlocks.BOSS_RESPAWNER.get().defaultBlockState(), 2)) {
            UniversalBossRespawner.LOGGER.warn("Failed to set Boss respawner block at {}", pos);
            return false;
        }
        BlockState newState = level.getBlockState(pos);
        if (!newState.is(ModBlocks.BOSS_RESPAWNER.get())) {
            UniversalBossRespawner.LOGGER.warn("Boss respawner block was not set at {}", pos);
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof BossRespawnerBlockEntity be)) {
            UniversalBossRespawner.LOGGER.warn("Boss respawner block at {} has no BossRespawnerBlockEntity", pos);
            return false;
        }

        RespawnEntry.ActivationRule activation = request.rules().activation();
        RespawnEntry.SpawnRule spawn = request.rules().spawn();

        // Identity first, and for both modes: the boss it revives and the key it costs. The transform path
        // needs these as much as the summon path does - without them Jade has nothing to show and
        // matchesKeyItem can never succeed, so the cage would be inert.
        be.setIdentity(request.entityTypeId(), activation.item().toString(),
                activation.amount(), activation.consume());

        if (spawn.mode() == RespawnEntry.SpawnMode.TRANSFORM && spawn.transform() != null) {
            // Hand-off mode: the cage becomes another block once its opening animation has played, instead
            // of summoning the entity. Its delay and allow_peaceful live in the transform object.
            RespawnEntry.TransformRule transform = spawn.transform();
            be.setTransform(transform.block(), parseNbt(transform.nbt()),
                    transform.delayTicks(), transform.allowPeaceful());
        } else if (spawn.summon() != null) {
            RespawnEntry.SummonRule summon = spawn.summon();
            be.setSpawnerData(
                    request.entityTypeId(),
                    activation.item().toString(),
                    activation.amount(),
                    activation.consume(),
                    parseNbt(summon.nbt()),
                    summon.delayTicks(),
                    summon.requirePlayerNearby(),
                    summon.playerRange(),
                    summon.allowPeaceful(),
                    summon.count(),
                    summon.spawnOffset(),
                    summon.finalizeSpawn(),
                    summon.maxAttempts(),
                    summon.retryIntervalTicks());
        } else {
            UniversalBossRespawner.LOGGER.warn(
                    "Respawn rule for {} has spawn.mode {} but no matching object; cage left unconfigured",
                    request.entityTypeId(), spawn.mode());
        }

        // Home-mode cages remember the original anchor so the respawned entity inherits it instead of
        // registering wherever it happens to appear.
        if (request.homeSnapshot() != null) {
            be.setHomeAnchor(request.homeSnapshot().dimension(), request.homeSnapshot().homePos());
        }

        level.sendBlockUpdated(pos, oldState, newState, 3);
        return true;
    }

    // ------------------------------------------------------------------ existing cage scan

    private static boolean hasExistingCage(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        long radiusSq = (long) r * r;

        for (BlockPos cagePos : existingCagePositions(level, center, entityTypeId, r, radiusSq)) {
            DebugLog.info("Found existing {} respawn cage at {}", entityTypeId, cagePos);
            return true;
        }
        DebugLog.info("No existing {} cage found near {}", entityTypeId, center);
        return false;
    }

    private static void removeExistingCages(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        long radiusSq = (long) r * r;

        // Collect first, then destroy: destroying while iterating the chunk's BlockEntity map would
        // throw ConcurrentModificationException.
        List<BlockPos> toDestroy = existingCagePositions(level, center, entityTypeId, r, radiusSq);
        for (BlockPos pos : toDestroy) {
            DebugLog.info("Destroying existing {} cage at {}", entityTypeId, pos);
            level.destroyBlock(pos, false);
        }
    }

    private static List<BlockPos> existingCagePositions(ServerLevel level, BlockPos center, String entityTypeId,
                                                        int r, long radiusSq) {
        List<BlockPos> found = new ArrayList<>();
        int minChunkX = (center.getX() - r) >> 4;
        int maxChunkX = (center.getX() + r) >> 4;
        int minChunkZ = (center.getZ() - r) >> 4;
        int maxChunkZ = (center.getZ() + r) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof BossRespawnerBlockEntity cage
                            && entityTypeId.equals(cage.getEntityTypeId())
                            && cage.getBlockPos().distSqr(center) <= radiusSq) {
                        if (level.getBlockState(cage.getBlockPos()).is(ModBlocks.BOSS_RESPAWNER.get())) {
                            found.add(cage.getBlockPos());
                        } else {
                            DebugLog.info("Ignoring orphaned/stale {} BlockEntity at {} (actual block state={})",
                                    entityTypeId, cage.getBlockPos(), level.getBlockState(cage.getBlockPos()));
                        }
                    }
                }
            }
        }
        return found;
    }

    private static CompoundTag parseNbt(String nbt) {
        if (nbt == null || nbt.isBlank()) {
            return new CompoundTag();
        }
        try {
            return TagParser.parseTag(nbt);
        } catch (Exception e) {
            UniversalBossRespawner.LOGGER.warn("Failed to parse spawn NBT '{}': {}", nbt, e.toString());
            return new CompoundTag();
        }
    }
}
