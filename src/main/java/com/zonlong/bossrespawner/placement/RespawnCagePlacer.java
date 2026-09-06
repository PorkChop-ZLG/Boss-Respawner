package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;
import com.zonlong.bossrespawner.data.RespawnEntry;
import com.zonlong.bossrespawner.init.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Optional;

public final class RespawnCagePlacer {
    private RespawnCagePlacer() {
    }

    public static boolean tryPlace(ServerLevel level, LivingEntity entity, RespawnEntry entry,
                                   String actualEntityId) {
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            DebugLog.info("Respawn cage placement skipped: mod disabled (actualEntityId={})", actualEntityId);
            return false;
        }

        Optional<BlockPos> posOpt = PlacementResolver.findPlacementPos(level, entity, entry.placement());
        if (posOpt.isEmpty()) {
            DebugLog.info("No safe placement for {} respawn cage after death of {} at {}",
                    actualEntityId, entity, entity.blockPosition());
            return false;
        }

        BlockPos pos = posOpt.get();
        DebugLog.info("Selected placement position {} for {} respawn cage", pos, actualEntityId);

        if (!"allow_multiple".equals(entry.duplicate().mode())) {
            DebugLog.info("Checking existing cages: entity={} mode={} radius={} center={}",
                    actualEntityId, entry.duplicate().mode(), entry.duplicate().searchRadius(), pos);
            boolean hasExisting = hasExistingCage(level, pos, actualEntityId, entry.duplicate().searchRadius());
            if (hasExisting) {
                if ("keep_existing".equals(entry.duplicate().mode())) {
                    DebugLog.info("Keeping existing {} respawn cage near {}", actualEntityId, pos);
                    return false;
                }
                DebugLog.info("Removing existing {} respawn cages near {} (replace_existing)", actualEntityId, pos);
                removeExistingCages(level, pos, actualEntityId, entry.duplicate().searchRadius());
            }
        } else {
            DebugLog.info("Duplicate mode is allow_multiple; skipping existing-cage check for {}", actualEntityId);
        }

        BlockState oldState = level.getBlockState(pos);
        level.setBlock(pos, ModBlocks.BOSS_RESPAWNER.get().defaultBlockState(), 2);
        BlockState newState = level.getBlockState(pos);

        if (level.getBlockEntity(pos) instanceof BossRespawnerBlockEntity be) {
            CompoundTag spawnNbt = parseNbt(entry.spawn().nbt());
            be.setSpawnerData(
                    actualEntityId,
                    entry.activation().item().toString(),
                    entry.activation().amount(),
                    entry.activation().consume(),
                    spawnNbt,
                    entry.spawn().delayTicks(),
                    entry.spawn().requirePlayerNearby(),
                    entry.spawn().playerRange(),
                    entry.spawn().allowPeaceful(),
                    entry.spawn().count(),
                    entry.spawn().spawnOffset(),
                    entry.spawn().finalizeSpawn(),
                    entry.spawn().maxAttempts(),
                    entry.spawn().retryIntervalTicks());
            DebugLog.info("Set spawner data on newly placed cage: entity={} key={} amount={} consume={} delay={} maxAttempts={}",
                    actualEntityId, entry.activation().item(), entry.activation().amount(),
                    entry.activation().consume(), entry.spawn().delayTicks(), entry.spawn().maxAttempts());
        } else {
            DebugLog.info("WARNING: newly placed cage at {} has no BossRespawnerBlockEntity!", pos);
        }

        level.sendBlockUpdated(pos, oldState, newState, 3);

        DebugLog.info("Placed {} respawn cage at {}", actualEntityId, pos);
        return true;
    }

    private static boolean hasExistingCage(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        int minChunkX = (center.getX() - r) >> 4;
        int maxChunkX = (center.getX() + r) >> 4;
        int minChunkZ = (center.getZ() - r) >> 4;
        int maxChunkZ = (center.getZ() + r) >> 4;
        long radiusSq = (long) r * r;

        DebugLog.info("Searching existing cages: entity={} center={} radius={} chunks=[{},{}]-[{},{}]",
                entityTypeId, center, r, minChunkX, minChunkZ, maxChunkX, maxChunkZ);

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
                            DebugLog.info("Found existing {} respawn cage at {}", entityTypeId, cage.getBlockPos());
                            return true;
                        }
                        DebugLog.info("Ignoring orphaned/stale {} BlockEntity at {} (actual block state={})",
                                entityTypeId, cage.getBlockPos(), level.getBlockState(cage.getBlockPos()));
                    }
                }
            }
        }
        DebugLog.info("No existing {} cage found near {}", entityTypeId, center);
        return false;
    }

    private static void removeExistingCages(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        int minChunkX = (center.getX() - r) >> 4;
        int maxChunkX = (center.getX() + r) >> 4;
        int minChunkZ = (center.getZ() - r) >> 4;
        int maxChunkZ = (center.getZ() + r) >> 4;
        long radiusSq = (long) r * r;

        DebugLog.info("Removing existing cages: entity={} center={} radius={} chunks=[{},{}]-[{},{}]",
                entityTypeId, center, r, minChunkX, minChunkZ, maxChunkX, maxChunkZ);

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
                            DebugLog.info("Destroying existing {} cage at {}", entityTypeId, cage.getBlockPos());
                            level.destroyBlock(cage.getBlockPos(), false);
                        } else {
                            DebugLog.info("Skipping orphaned/stale {} BlockEntity at {} (actual block state={})",
                                    entityTypeId, cage.getBlockPos(), level.getBlockState(cage.getBlockPos()));
                        }
                    }
                }
            }
        }
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
