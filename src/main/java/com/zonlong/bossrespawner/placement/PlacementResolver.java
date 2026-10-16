package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.data.RespawnEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a safe position for a respawn cage by searching around a caller-supplied base position.
 *
 * <p>The base is chosen by {@link RespawnCagePlacer}: the death position in {@code death} mode, the
 * registered home in {@code home} mode. Search order is unchanged from earlier versions: downward,
 * then upward, then a horizontal ring.
 *
 * <p>Only {@code avoid_blocks} participates here (it is a search <em>preference</em>). The
 * {@code protected_blocks} floor is enforced on the write path instead, because a forced placement
 * bypasses this search entirely.
 *
 * <p>Candidates in unloaded chunks are rejected without loading them: {@link ServerLevel#isLoaded} is a
 * non-loading query, and a loading one here would stall the server thread during a death event.
 */
public final class PlacementResolver {

    private PlacementResolver() {
    }

    public static Optional<BlockPos> findPlacementPos(ServerLevel level, BlockPos base,
                                                      RespawnEntry.PlacementRule placement,
                                                      Set<ResourceLocation> protectedBlocks) {
        DebugLog.info("Placement search started: base={} searchDown={} searchUp={} horizontalRadius={} requireGround={} avoidFluids={}",
                base, placement.searchDown(), placement.searchUp(),
                placement.horizontalRadius(), placement.requireGround(), placement.avoidFluids());
        DebugLog.info("Placement block lists: avoid={} protected={}", placement.avoidBlocks(), protectedBlocks);

        boolean hasBlockLists = !placement.avoidBlocks().isEmpty() || !protectedBlocks.isEmpty();

        // Downward search first, matching Cataclysm's general behaviour.
        for (int dy = 0; dy <= placement.searchDown(); dy++) {
            BlockPos candidate = base.below(dy);
            if (isLoadedAndValid(level, candidate, placement, protectedBlocks, hasBlockLists)) {
                DebugLog.info("Placement found via downward search: {}", candidate);
                return Optional.of(candidate);
            }
        }

        // Upward search.
        for (int dy = 1; dy <= placement.searchUp(); dy++) {
            BlockPos candidate = base.above(dy);
            if (isLoadedAndValid(level, candidate, placement, protectedBlocks, hasBlockLists)) {
                DebugLog.info("Placement found via upward search: {}", candidate);
                return Optional.of(candidate);
            }
        }

        // Horizontal rings around the base Y level.
        int radius = placement.horizontalRadius();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos candidate = base.offset(dx, 0, dz);
                    if (isLoadedAndValid(level, candidate, placement, protectedBlocks, hasBlockLists)) {
                        DebugLog.info("Placement found via horizontal search: {}", candidate);
                        return Optional.of(candidate);
                    }
                }
            }
        }

        DebugLog.info("No safe placement found near base={}", base);
        return Optional.empty();
    }

    /**
     * Validates one search candidate.
     *
     * <p>Both block lists reject a candidate here: {@code avoid_blocks} because the search should prefer
     * elsewhere, and {@code protected_blocks} because the search must never propose a position that the
     * write path would refuse. Without the latter, a candidate sitting directly on top of a protected block
     * would be selected and then abort the whole placement, leaving the player with no cage at all instead
     * of a relocated one.
     *
     * <p>The block below is fetched once and reused for the ground, fluid and block-list checks.
     */
    private static boolean isLoadedAndValid(ServerLevel level, BlockPos pos,
                                            RespawnEntry.PlacementRule placement,
                                            Set<ResourceLocation> protectedBlocks, boolean hasBlockLists) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Placement candidate rejected (chunk not loaded): {}", pos);
            }
            return false;
        }

        BlockState state = level.getBlockState(pos);
        // Air or a replaceable block only. This also excludes an existing respawn cage of this mod:
        // its block is neither air nor replaceable, so the search keeps looking for another candidate.
        if (!state.isAir() && !state.canBeReplaced()) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Placement candidate rejected (not air/replaceable): {} state={}", pos, state);
            }
            return false;
        }

        BlockState below = level.getBlockState(pos.below());

        if (placement.avoidFluids() && (!state.getFluidState().isEmpty() || !below.getFluidState().isEmpty())) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Placement candidate rejected (fluid): {}", pos);
            }
            return false;
        }

        if (placement.requireGround() && !below.isFaceSturdy(level, pos.below(), Direction.UP)) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Placement candidate rejected (no sturdy ground): {} below={}", pos, below);
            }
            return false;
        }

        if (hasBlockLists && isRejectedByBlockLists(state, below,
                placement.avoidBlocks(), protectedBlocks)) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Placement candidate rejected (avoid/protected block): {}", pos);
            }
            return false;
        }
        return true;
    }

    /** Checks both lists against the candidate block and the block under it. */
    private static boolean isRejectedByBlockLists(BlockState state, BlockState below,
                                                  Set<ResourceLocation> avoidBlocks,
                                                  Set<ResourceLocation> protectedBlocks) {
        ResourceLocation stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ResourceLocation belowId = BuiltInRegistries.BLOCK.getKey(below.getBlock());
        return isListed(stateId, avoidBlocks)
                || isListed(belowId, avoidBlocks)
                || isListed(stateId, protectedBlocks)
                || isListed(belowId, protectedBlocks);
    }

    /**
     * Membership test against a pre-parsed id set.
     *
     * <p>Compares {@link ResourceLocation} values, so there is no {@code toString()} allocation and the
     * lookup is O(1) regardless of how many ids the list holds.
     */
    static boolean isListed(ResourceLocation blockId, Set<ResourceLocation> ids) {
        return blockId != null && !ids.isEmpty() && ids.contains(blockId);
    }
}
