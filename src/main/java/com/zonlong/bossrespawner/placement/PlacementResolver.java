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
 * <p>{@code avoid_blocks} is the only block list, and it is a search <em>preference</em>: the first pass
 * honours it, and if that finds nothing the second pass repeats the search without it, so avoidance can
 * never cost the player a cage. The write paths that skip this search entirely ({@code force_place} and the
 * fallback) perform no block checks at all — they overwrite whatever is there.
 *
 * <p>Candidates in unloaded chunks are rejected without loading them: {@link ServerLevel#isLoaded} is a
 * non-loading query, and a loading one here would stall the server thread during a death event.
 */
public final class PlacementResolver {

    private PlacementResolver() {
    }

    /**
     * Finds a position for the cage, in two passes.
     *
     * <p>The first pass honours {@code avoid_blocks}. If it finds nothing, the second pass repeats the same
     * search with that preference dropped, so a cage is never <em>lost</em> merely because the only free
     * spots sit next to an avoided block. Dropping the filter cannot destroy anything: a candidate must be
     * air or replaceable, so an avoided block such as a chest can never be the target itself — the worst
     * outcome is the cage sitting on top of it.
     *
     * <p>Only the write paths that skip the search entirely ({@code force_place} and the fallback in
     * {@link RespawnCagePlacer}) can overwrite a solid block, and they do so deliberately.
     */
    public static Optional<BlockPos> findPlacementPos(ServerLevel level, BlockPos base,
                                                      RespawnEntry.PlacementRule placement) {
        DebugLog.info("Placement search started: base={} searchDown={} searchUp={} horizontalRadius={} requireGround={} avoidFluids={} avoid={}",
                base, placement.searchDown(), placement.searchUp(),
                placement.horizontalRadius(), placement.requireGround(), placement.avoidFluids(),
                placement.avoidBlocks());

        Optional<BlockPos> found = search(level, base, placement, true);
        if (found.isPresent() || placement.avoidBlocks().isEmpty()) {
            return found;
        }

        DebugLog.info("No placement found near {} while avoiding {}; retrying with the avoid filter relaxed",
                base, placement.avoidBlocks());
        Optional<BlockPos> relaxed = search(level, base, placement, false);
        DebugLog.info("Relaxed search near {} {}", base, relaxed.isPresent() ? "succeeded" : "found nothing either");
        return relaxed;
    }

    /**
     * One full sweep: downward, then upward, then horizontal rings.
     *
     * @param honourAvoid whether {@code avoid_blocks} rejects candidates
     */
    private static Optional<BlockPos> search(ServerLevel level, BlockPos base,
                                             RespawnEntry.PlacementRule placement, boolean honourAvoid) {
        boolean checkAvoid = honourAvoid && !placement.avoidBlocks().isEmpty();

        // Downward search first, matching Cataclysm's general behaviour.
        for (int dy = 0; dy <= placement.searchDown(); dy++) {
            BlockPos candidate = base.below(dy);
            if (isLoadedAndValid(level, candidate, placement, checkAvoid)) {
                DebugLog.info("Placement found via downward search: {}", candidate);
                return Optional.of(candidate);
            }
        }

        // Upward search.
        for (int dy = 1; dy <= placement.searchUp(); dy++) {
            BlockPos candidate = base.above(dy);
            if (isLoadedAndValid(level, candidate, placement, checkAvoid)) {
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
                    if (isLoadedAndValid(level, candidate, placement, checkAvoid)) {
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
     * <p>The air, fluid and ground requirements are hard filters in both passes — they decide whether a
     * position is usable at all. {@code avoid_blocks} is the only preference, and only the first pass
     * applies it.
     *
     * <p>The block below is fetched once and reused for the ground, fluid and avoid checks.
     */
    private static boolean isLoadedAndValid(ServerLevel level, BlockPos pos,
                                            RespawnEntry.PlacementRule placement, boolean checkAvoid) {
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

        if (checkAvoid && isAvoided(state, below, placement.avoidBlocks())) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Placement candidate rejected (avoided block): {}", pos);
            }
            return false;
        }
        return true;
    }

    /** Checks {@code avoid_blocks} against the candidate block and the block under it. */
    private static boolean isAvoided(BlockState state, BlockState below, Set<ResourceLocation> avoidBlocks) {
        ResourceLocation stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ResourceLocation belowId = BuiltInRegistries.BLOCK.getKey(below.getBlock());
        return isListed(stateId, avoidBlocks) || isListed(belowId, avoidBlocks);
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
