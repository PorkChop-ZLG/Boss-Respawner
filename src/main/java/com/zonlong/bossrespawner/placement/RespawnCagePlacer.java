package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.block.BossRespawnerBlock;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;
import com.zonlong.bossrespawner.data.RespawnEntry;
import com.zonlong.bossrespawner.init.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

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

        if (Config.MAX_CAGES_PER_CHUNK.getAsInt() >= 0
                && countCagesInChunk(level, pos) >= Config.MAX_CAGES_PER_CHUNK.getAsInt()) {
            UniversalBossRespawner.LOGGER.debug("Skipping respawn cage at {}: chunk limit reached", pos);
            return false;
        }

        level.setBlock(pos, ModBlocks.BOSS_RESPAWNER.get().defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof BossRespawnerBlockEntity be) {
            CompoundTag spawnNbt = parseNbt(entry.spawn().nbt());
            be.setSpawnerData(
                    actualEntityId,
                    entry.activation().item().toString(),
                    entry.activation().amount(),
                    spawnNbt,
                    entry.spawn().delayTicks(),
                    entry.spawn().requirePlayerNearby(),
                    entry.spawn().playerRange(),
                    entry.spawn().allowPeaceful(),
                    entry.spawn().count(),
                    entry.spawn().spawnOffset(),
                    entry.spawn().finalizeSpawn(),
                    entry.spawn().setHomeToCage(),
                    entry.spawn().maxAttempts(),
                    entry.spawn().retryIntervalTicks());
        }

        if (Config.LOG_PLACEMENT.getAsBoolean()) {
            UniversalBossRespawner.LOGGER.info("Placed {} respawn cage at {}", actualEntityId, pos);
        }
        return true;
    }

    private static boolean hasExistingCage(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (level.getBlockState(pos).getBlock() instanceof BossRespawnerBlock
                    && level.getBlockEntity(pos) instanceof BossRespawnerBlockEntity be
                    && entityTypeId.equals(be.getEntityTypeId())) {
                return true;
            }
        }
        return false;
    }

    private static void removeExistingCages(ServerLevel level, BlockPos center, String entityTypeId, int radius) {
        int r = Math.min(radius, 32);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (level.getBlockState(pos).getBlock() instanceof BossRespawnerBlock
                    && level.getBlockEntity(pos) instanceof BossRespawnerBlockEntity be
                    && entityTypeId.equals(be.getEntityTypeId())) {
                level.destroyBlock(pos, false);
            }
        }
    }

    private static int countCagesInChunk(ServerLevel level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int count = 0;
        for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
            for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight(); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).getBlock() instanceof BossRespawnerBlock) {
                        count++;
                    }
                }
            }
        }
        return count;
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
