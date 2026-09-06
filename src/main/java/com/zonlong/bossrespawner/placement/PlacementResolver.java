package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.data.RespawnEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PlacementResolver {
    private PlacementResolver() {
    }

    public static Optional<BlockPos> findPlacementPos(ServerLevel level, LivingEntity entity,
                                                      RespawnEntry.PlacementRule placement) {
        // Design decision: this mod only places cages at/near the entity's death position.
        BlockPos base = entity.blockPosition()
                .offset(placement.offset()[0], placement.offset()[1], placement.offset()[2]);

        DebugLog.info("Placement search started: base={} offset={} searchDown={} searchUp={} horizontalRadius={} requireGround={} avoidFluids={}",
                base, placement.offset(), placement.searchDown(), placement.searchUp(),
                placement.horizontalRadius(), placement.requireGround(), placement.avoidFluids());

        List<String> avoidBlocks = new ArrayList<>(placement.avoidBlocks());
        for (String foreign : Config.FOREIGN_CAGE_BLOCK_IDS.get()) {
            if (!avoidBlocks.contains(foreign)) {
                avoidBlocks.add(foreign);
            }
        }
        DebugLog.info("Placement avoid blocks: {}", avoidBlocks);

        // Downward search first, matching Cataclysm's general behaviour.
        for (int dy = 0; dy <= placement.searchDown(); dy++) {
            BlockPos candidate = base.below(dy);
            if (isLoadedAndValid(level, candidate, placement, avoidBlocks)) {
                DebugLog.info("Placement found via downward search: {}", candidate);
                return Optional.of(candidate);
            }
        }

        // Upward search.
        for (int dy = 1; dy <= placement.searchUp(); dy++) {
            BlockPos candidate = base.above(dy);
            if (isLoadedAndValid(level, candidate, placement, avoidBlocks)) {
                DebugLog.info("Placement found via upward search: {}", candidate);
                return Optional.of(candidate);
            }
        }

        // Horizontal spiral around the original Y level.
        int radius = placement.horizontalRadius();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos candidate = base.offset(dx, 0, dz);
                    if (isLoadedAndValid(level, candidate, placement, avoidBlocks)) {
                        DebugLog.info("Placement found via horizontal search: {}", candidate);
                        return Optional.of(candidate);
                    }
                }
            }
        }

        DebugLog.info("No safe placement found near base={}", base);
        return Optional.empty();
    }

    private static boolean isLoadedAndValid(ServerLevel level, BlockPos pos,
                                            RespawnEntry.PlacementRule placement, List<String> avoidBlocks) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
            DebugLog.info("Placement candidate rejected (chunk not loaded): {}", pos);
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) {
            DebugLog.info("Placement candidate rejected (not air/replaceable): {} state={}", pos, state);
            return false;
        }
        if (placement.avoidFluids() && (!state.getFluidState().isEmpty() || !level.getFluidState(pos.below()).isEmpty())) {
            DebugLog.info("Placement candidate rejected (fluid): {}", pos);
            return false;
        }

        if (placement.requireGround()) {
            BlockState below = level.getBlockState(pos.below());
            if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) {
                DebugLog.info("Placement candidate rejected (no sturdy ground): {} below={}", pos, below);
                return false;
            }
        }

        if (isAvoided(state, avoidBlocks) || isAvoided(level.getBlockState(pos.below()), avoidBlocks)) {
            DebugLog.info("Placement candidate rejected (avoided block): {}", pos);
            return false;
        }
        return true;
    }

    private static boolean isAvoided(BlockState state, List<String> avoidBlocks) {
        if (avoidBlocks == null || avoidBlocks.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && avoidBlocks.contains(key.toString());
    }
}
