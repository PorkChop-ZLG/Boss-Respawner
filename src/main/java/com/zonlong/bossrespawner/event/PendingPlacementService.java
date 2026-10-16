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
 * Drives deferred respawn-cage placements to completion once their target chunk exists.
 *
 * <p>Two mechanisms, deliberately redundant:
 *
 * <ol>
 *   <li><b>Chunk-load trigger (fast path).</b> {@link #onChunkLoad} only records <em>which</em> chunk became
 *       available; it reads no saved data and performs no world access. {@code ChunkEvent.Load} carries the
 *       same NeoForge warning as {@code EntityJoinLevelEvent} — it may fire before the chunk reaches
 *       {@code ChunkStatus#FULL}, and world interactions there cause chunk-loading deadlocks. The dangerous
 *       part is not writing to the loading chunk itself but reaching beyond it: {@code replace_existing}
 *       destroys neighbouring cages and block updates notify neighbours, and those neighbours may not be
 *       loaded. The actual placement therefore happens in {@link #onServerTick}, outside the chunk-load call
 *       stack. Lookups go through the saved data's {@code (dimension, ChunkPos)} index, so a chunk-load
 *       event costs O(1) instead of scanning the whole queue.</li>
 *   <li><b>Periodic sweep (safety net).</b> Because a queued task never expires, a single missed trigger
 *       would strand it forever. The sweep re-examines every queued task on a slow fixed interval and runs
 *       any whose chunk is loaded. It only uses {@code isLoaded} checks, so it never loads a chunk, and the
 *       queue is bounded by the number of distinct placement targets.</li>
 * </ol>
 */
public final class PendingPlacementService {

    /** Chunks seen loading since the last tick. Concurrent: chunk generation is not always on the server thread. */
    private static final Set<ChunkRef> TRIGGERED = ConcurrentHashMap.newKeySet();

    /** Safety-net sweep period, in server ticks. 100 ticks is 5 seconds. */
    private static final int SWEEP_INTERVAL_TICKS = 100;

    private static int ticksSinceSweep;

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
        MinecraftServer server = event.getServer();
        drainTriggered(server);

        if (++ticksSinceSweep >= SWEEP_INTERVAL_TICKS) {
            ticksSinceSweep = 0;
            sweep(server);
        }
    }

    private static void drainTriggered(MinecraftServer server) {
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
        BossHomeSavedData data = BossHomeSavedData.get(server);
        List<PendingPlacementTask> tasks = data.pendingForChunk(ref.dimension(), ref.chunkPos());
        if (tasks.isEmpty()) {
            return;
        }

        DebugLog.info("Chunk {} {} loaded and matched {} pending placement(s)",
                ref.dimension().location(), ref.chunkPos(), tasks.size());

        ServerLevel level = server.getLevel(ref.dimension());
        for (PendingPlacementTask task : tasks) {
            if (level != null && !level.isLoaded(task.targetBase())) {
                // Loaded and unloaded again before this tick; the sweep will pick it up later.
                DebugLog.info("Pending placement at {} is queued but its chunk is not loaded yet",
                        task.targetBase());
                continue;
            }
            finish(server, data, task);
        }
    }

    /**
     * Slow safety net. Re-examines the whole queue so a missed chunk-load trigger cannot strand a task
     * forever, and so a task whose dimension has disappeared gets failed and cleaned up.
     */
    private static void sweep(MinecraftServer server) {
        BossHomeSavedData data = BossHomeSavedData.get(server);
        if (data.pendingCount() == 0) {
            return;
        }

        List<PendingPlacementTask> tasks = data.pendingPlacements();
        int retried = 0;
        int stillWaiting = 0;
        for (PendingPlacementTask task : tasks) {
            ServerLevel level = server.getLevel(task.targetDimension());
            if (level != null && !level.isLoaded(task.targetBase())) {
                stillWaiting++;
                continue;
            }
            // level == null is handled by the placer: it fails the task and cleans up the home record.
            retried++;
            finish(server, data, task);
        }

        DebugLog.info("Deferred placement sweep: total={} retried={} stillWaiting={}",
                tasks.size(), retried, stillWaiting);
    }

    /** Runs one queued task and drops it unless it asked to stay queued. */
    private static void finish(MinecraftServer server, BossHomeSavedData data, PendingPlacementTask task) {
        // The chunk path and the sweep can both see the same task in one tick; whoever gets there first wins.
        if (data.pendingPlacement(task.key()) == null) {
            return;
        }

        RespawnCagePlacer.Outcome outcome = RespawnCagePlacer.executeDeferred(server, task);
        if (outcome == RespawnCagePlacer.Outcome.DEFERRED) {
            return;
        }
        // PLACED, or ABANDONED for a permanent reason (protected block, fallback disabled,
        // unavailable dimension): either way the task is finished with.
        data.removePendingPlacement(task.key());
        DebugLog.info("Deferred placement at {} finished with {}", task.targetBase(), outcome);
    }
}
