package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.data.RespawnEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public final class PlacementResolver {
    private PlacementResolver() {
    }

    public static Optional<BlockPos> findPlacementPos(ServerLevel level, LivingEntity entity,
                                                      RespawnEntry.PlacementRule placement) {
        Optional<BlockPos> origin = resolveOrigin(entity, placement.mode());
        if (origin.isEmpty()) {
            return Optional.empty();
        }

        BlockPos base = origin.get().offset(placement.offset()[0], placement.offset()[1], placement.offset()[2]);

        // Downward search first, matching Cataclysm's general behaviour.
        for (int dy = 0; dy <= placement.searchDown(); dy++) {
            BlockPos candidate = base.below(dy);
            if (isLoadedAndValid(level, candidate, placement)) {
                return Optional.of(candidate);
            }
        }

        // Upward search.
        for (int dy = 1; dy <= placement.searchUp(); dy++) {
            BlockPos candidate = base.above(dy);
            if (isLoadedAndValid(level, candidate, placement)) {
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
                    if (isLoadedAndValid(level, candidate, placement)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> resolveOrigin(LivingEntity entity, String mode) {
        return switch (mode == null ? "death_or_home" : mode) {
            case "death" -> Optional.of(entity.blockPosition());
            case "home" -> HomePosLocator.findHomePos(entity);
            default -> {
                Optional<BlockPos> home = HomePosLocator.findHomePos(entity);
                yield home.isPresent() ? home : Optional.of(entity.blockPosition());
            }
        };
    }

    private static boolean isLoadedAndValid(ServerLevel level, BlockPos pos,
                                            RespawnEntry.PlacementRule placement) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }
        if (placement.avoidFluids() && (!state.getFluidState().isEmpty() || !level.getFluidState(pos.below()).isEmpty())) {
            return false;
        }

        if (placement.requireGround()) {
            BlockState below = level.getBlockState(pos.below());
            if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) {
                return false;
            }
        }

        if (isAvoided(state, placement.avoidBlocks()) || isAvoided(level.getBlockState(pos.below()), placement.avoidBlocks())) {
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
