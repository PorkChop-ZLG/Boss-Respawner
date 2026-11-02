package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.PendingPlacementTask;
import com.zonlong.bossrespawner.placement.RespawnCagePlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    /**
     * Marks a Twilight Forest settlement task ready as soon as its source entity is removed.
     *
     * <p>This is deliberately an event rather than only a per-tick presence check: if the death chunk is
     * unloaded before the task gets its next look, the entity is gone from the level and the presence check
     * could mistake "chunk unloaded" for "still dying" forever. The removal event carries the reason, and
     * {@code KILLED} means Twilight Forest's chest has already been deposited (it happens earlier in the
     * same {@code remove} call).
     *
     * <p>Re-enqueuing the task without the wait fields keeps the existing target key and chunk index; the
     * ordinary placement path then decides when the target chunk is ready.
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity.getRemovalReason() != Entity.RemovalReason.KILLED) {
            return;
        }
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) {
            return;
        }
        BossHomeSavedData data = BossHomeSavedData.get(server);
        if (data.pendingCount() == 0) {
            return;
        }

        // Collect first: re-enqueueing replaces the map value and would otherwise invalidate the view.
        List<PendingPlacementTask> ready = new ArrayList<>();
        for (PendingPlacementTask task : data.pendingView()) {
            if (entity.getUUID().equals(task.waitForEntityUuid())) {
                ready.add(task);
            }
        }
        for (PendingPlacementTask task : ready) {
            PendingPlacementTask settled = new PendingPlacementTask(
                    task.targetDimension(), task.targetBase(), task.lastSourceEntityUuid(),
                    task.entityType(), task.homeSnapshot(), task.ruleSnapshot(),
                    task.createdGameTime(), task.notBeforeGameTime());
            data.enqueuePendingPlacement(settled);
            DebugLog.info("Source entity {} removed; Twilight Forest placement at {} is now ready",
                    entity.getUUID(), task.targetBase());
        }
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
        // Delayed placements have a tick deadline, so they need a per-tick check: the 100-tick sweep would
        // overshoot a death animation by seconds. This is cheap because the queue is empty on almost every
        // tick, and when it is not, {@link BossHomeSavedData#pendingView()} allocates nothing.
        runDuePlacements(server);
        drainTriggered(server);

        if (++ticksSinceSweep >= SWEEP_INTERVAL_TICKS) {
            ticksSinceSweep = 0;
            sweep(server);
        }
    }

    /**
     * Runs queued tasks whose {@code place_delay_ticks} has elapsed and whose target chunk is already
     * loaded.
     *
     * <p>Tasks that are due but whose chunk is not loaded are deliberately left alone: {@link #finish} would
     * re-defer them, and doing that every tick would be a busy loop. The chunk-load trigger and the sweep
     * pick those up. A task whose dimension has vanished is likewise left to the sweep, which fails and
     * cleans it up.
     */
    private static void runDuePlacements(MinecraftServer server) {
        BossHomeSavedData data = BossHomeSavedData.get(server);
        if (data.pendingCount() == 0) {
            return;
        }

        long now = server.overworld().getGameTime();
        List<PendingPlacementTask> due = null;
        for (PendingPlacementTask task : data.pendingView()) {
            if (!task.isDue(now)) {
                continue;
            }
            if (stillWaitingForRemoval(server, task)) {
                continue;
            }
            ServerLevel level = server.getLevel(task.targetDimension());
            if (level == null || !level.isLoaded(task.targetBase())) {
                continue;
            }
            if (due == null) {
                due = new ArrayList<>();
            }
            due.add(task);
        }
        if (due == null) {
            return;
        }

        DebugLog.info("Running {} delayed placement(s) whose place_delay_ticks elapsed", due.size());
        for (PendingPlacementTask task : due) {
            finish(server, data, task);
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
        long now = server.overworld().getGameTime();
        for (PendingPlacementTask task : tasks) {
            if (!task.isDue(now)) {
                // A chunk load must not shortcut place_delay_ticks; runDuePlacements runs it when due.
                DebugLog.info("Pending placement at {} is queued but its delay has not elapsed ({} < {})",
                        task.targetBase(), now, task.notBeforeGameTime());
                continue;
            }
            if (stillWaitingForRemoval(server, task)) {
                DebugLog.info("Pending placement at {} is queued but its source entity still exists",
                        task.targetBase());
                continue;
            }
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
        long now = server.overworld().getGameTime();
        for (PendingPlacementTask task : tasks) {
            if (!task.isDue(now)) {
                // Counted as waiting, not retried: the per-tick pass owns delayed tasks.
                stillWaiting++;
                continue;
            }
            if (stillWaitingForRemoval(server, task)) {
                stillWaiting++;
                continue;
            }
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

    /**
     * Whether a Twilight Forest settlement task is still waiting for its source entity to be removed.
     *
     * <p>Twilight Forest places the loot chest inside {@code Entity#remove(KILLED)}, before the entity is
     * actually removed. Waiting for {@code !isPresent || isRemoved} therefore guarantees the chest exists.
     * An unloaded death chunk means the death animation is frozen, so the task waits instead of forcing a
     * chunk load.
     */
    private static boolean stillWaitingForRemoval(MinecraftServer server, PendingPlacementTask task) {
        UUID waitFor = task.waitForEntityUuid();
        ResourceKey<Level> dimension = task.deathDimension();
        BlockPos deathPos = task.deathPos();
        if (waitFor == null || dimension == null || deathPos == null) {
            return false;
        }
        ServerLevel deathLevel = server.getLevel(dimension);
        if (deathLevel == null) {
            // The dimension is gone; the placer will fail and clean up the task.
            return false;
        }
        if (!deathLevel.isLoaded(deathPos)) {
            return true;
        }
        Entity entity = deathLevel.getEntity(waitFor);
        return entity != null && !entity.isRemoved();
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
