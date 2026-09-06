package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.Config;
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
            return false;
        }

        Optional<BlockPos> posOpt = PlacementResolver.findPlacementPos(level, entity, entry.placement());
        if (posOpt.isEmpty()) {
            UniversalBossRespawner.LOGGER.debug("No safe placement for {} respawn cage after death of {}",
                    actualEntityId, entity);
            return false;
        }

        BlockPos pos = posOpt.get();

        if (!"allow_multiple".equals(entry.duplicate().mode())) {
            boolean hasExisting = hasExistingCage(level, pos, actualEntityId, entry.duplicate().searchRadius());
            if (hasExisting) {
                if ("keep_existing".equals(entry.duplicate().mode())) {
                    UniversalBossRespawner.LOGGER.debug("Keeping existing {} respawn cage near {}", actualEntityId, pos);
                    return false;
                }
                removeExistingCages(level, pos, actualEntityId, entry.duplicate().searchRadius());
            }
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
        }

        level.sendBlockUpdated(pos, oldState, newState, 3);

        if (Config.LOG_PLACEMENT.getAsBoolean()) {
            UniversalBossRespawner.LOGGER.info("Placed {} respawn cage at {}", actualEntityId, pos);
        }
        return true;
    }

    private static boolean hasExistingCage(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        int minChunkX = (center.getX() - r) >> 4;
        int maxChunkX = (center.getX() + r) >> 4;
        int minChunkZ = (center.getZ() - r) >> 4;
        int maxChunkZ = (center.getZ() + r) >> 4;
        long radiusSq = (long) r * r;

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
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void removeExistingCages(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        int minChunkX = (center.getX() - r) >> 4;
        int maxChunkX = (center.getX() + r) >> 4;
        int minChunkZ = (center.getZ() - r) >> 4;
        int maxChunkZ = (center.getZ() + r) >> 4;
        long radiusSq = (long) r * r;

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
                        level.destroyBlock(cage.getBlockPos(), false);
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
