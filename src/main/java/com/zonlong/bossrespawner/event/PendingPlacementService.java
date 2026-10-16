package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.PendingPlacementTask;
import com.zonlong.bossrespawner.placement.RespawnCagePlacer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retries deferred respawn-cage placements once their target chunk exists.
 *
 * <p>Split deliberately in two:
 * <ol>
 *   <li>{@link #onChunkLoad} only records <em>which</em> chunk became available. It reads no saved data and
 *       performs no world access. {@code ChunkEvent.Load} carries the same NeoForge warning as
 *       {@code EntityJoinLevelEvent} — it may fire before the chunk reaches {@code ChunkStatus#FULL}, and
 *       world interactions there cause chunk-loading deadlocks. The dangerous part is not writing to the
 *       loading chunk itself, but reaching beyond it: {@code replace_existing} destroys neighbouring cages
 *       and block updates notify neighbours, and those neighbours may not be loaded.</li>
 *   <li>{@link #onServerTick} drains that set on the server thread, outside the chunk-load call stack, and
 *       only then runs the placement pipeline.</li>
 * </ol>
 *
 * <p>Tasks are looked up through the saved data's {@code (dimension, ChunkPos)} index, so a chunk-load
 * event costs O(1) rather than scanning the whole queue. {@code ChunkEvent.Load} fires thousands of times
 * per minute while a player moves, and the queue has no expiry, so a linear scan would degrade as a world
 * grows.
 */
public final class PendingPlacementService {

    /** Chunks seen loading since the last tick. Concurrent: chunk generation is not always on the server thread. */
    private static final Set<ChunkRef> TRIGGERED = ConcurrentHashMap.newKeySet();

    private record ChunkRef(ResourceKey<Level> dimension, long chunkPos) {
    }

    private PendingPlacementService() {
    }

    /** Trigger only: no saved-data access and no world interaction. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        TRIGGERED.add(new ChunkRef(serverLevel.dimension(), event.getChunk().getPos().toLong()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TRIGGERED.isEmpty()) {
            return;
        }
        // Remove-as-we-drain: whichever thread removes an entry owns it, so a chunk loaded concurrently is
        // never dropped between building the batch and clearing the set.
        List<ChunkRef> batch = new ArrayList<>();
        for (ChunkRef ref : TRIGGERED) {
            if (TRIGGERED.remove(ref)) {
                batch.add(ref);
            }
        }

        MinecraftServer server = event.getServer();
        for (ChunkRef ref : batch) {
            try {
                runTasksForChunk(server, ref);
            } catch (Exception e) {
                DebugLog.info("Deferred placement retry failed for {} chunk {}: {}",
                        ref.dimension().location(), ref.chunkPos(), e.toString());
            }
        }
    }

    private static void runTasksForChunk(MinecraftServer server, ChunkRef ref) {
        ServerLevel level = server.getLevel(ref.dimension());
        if (level == null) {
            return;
        }

        BossHomeSavedData data = BossHomeSavedData.get(server);
        // pendingForChunk returns a snapshot list, so removing tasks while iterating is safe.
        for (PendingPlacementTask task : data.pendingForChunk(ref.dimension(), ref.chunkPos())) {
            if (!level.isLoaded(task.targetBase())) {
                // Loaded and unloaded again before this tick; keep it queued for the next time.
                continue;
            }

            RespawnCagePlacer.Outcome outcome = RespawnCagePlacer.executeDeferred(level, task);
            if (outcome == RespawnCagePlacer.Outcome.DEFERRED) {
                continue;
            }
            // PLACED, or ABANDONED for a permanent reason (protected block, fallback disabled,
            // unavailable dimension): either way the task is finished with.
            data.removePendingPlacement(task.key());
            DebugLog.info("Deferred placement at {} finished with {}", task.targetBase(), outcome);
        }
    }
}
