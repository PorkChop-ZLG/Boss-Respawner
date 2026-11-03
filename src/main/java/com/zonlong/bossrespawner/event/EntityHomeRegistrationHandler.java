package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.HomeRecord;
import com.zonlong.bossrespawner.data.RespawnEntry;
import com.zonlong.bossrespawner.data.RespawnRuleManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Registers home anchors for entities that match a {@code home}-mode rule.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #onEntityJoinLevel} — the normal path. Fires when an entity is added to a level, including
 *       entities loaded from disk, so a boss that existed before this mod was installed is anchored the
 *       first time it is observed. {@code loadedFromDisk()} is only ever used for logging: using it to skip
 *       registration would mean saved bosses are never anchored at all.</li>
 *   <li>{@link #onServerTick} — a one-shot back-fill after a datapack reload, for entities that were already
 *       loaded before their rule existed.</li>
 * </ul>
 *
 * <p><b>Threading:</b> {@code EntityJoinLevelEvent} can fire on a chunk-generation worker
 * ({@code ChunkStatusTasks.full} runs inside {@code CompletableFuture.supplyAsync}). {@link BossHomeSavedData}
 * is a plain {@code SavedData} backed by {@code HashMap}s and is only safe on the server thread. The join
 * handler therefore builds an immutable {@link HomeRecord} on the worker and hands it to
 * {@link MinecraftServer#execute(Runnable)}; only the server-thread task touches saved data.
 *
 * <p>{@link EntityLeaveLevelEvent} removes a home record for a non-death removal ({@code DISCARDED}).
 * Unloaded chunks and deaths keep the record: unloaded entities may return, and the death handler owns
 * the death path.
 */
public final class EntityHomeRegistrationHandler {

    /** Raised by the rule reload listener and consumed on the next server tick. */
    private static volatile boolean backfillRequested;

    private EntityHomeRegistrationHandler() {
    }

    /** Schedules a back-fill pass for already-loaded entities. Called after rules reload. */
    public static void requestBackfill() {
        backfillRequested = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Level level = event.getLevel();
        if (level.isClientSide()) {
            // The event also fires on the client; anchors are server-side state only.
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            return;
        }

        Entity entity = event.getEntity();
        RespawnEntry entry = RespawnRuleManager.INSTANCE.find(entity.getType());
        if (entry == null || entry.placement().mode() != RespawnEntry.PlacementMode.HOME) {
            return;
        }

        HomeRecord record = recordFor(serverLevel, entity);
        if (record == null) {
            return;
        }

        boolean loadedFromDisk = event.loadedFromDisk();
        MinecraftServer server = serverLevel.getServer();
        if (server.isSameThread()) {
            registerRecord(server, record, "entity-join", loadedFromDisk);
        } else {
            // Never touch BossHomeSavedData/DimensionDataStorage from a chunk worker. The record is
            // immutable and carries no Level/Entity reference, so it is safe to pass across threads.
            try {
                server.execute(() ->
                        registerRecord(server, record, "entity-join (server-thread)", loadedFromDisk));
            } catch (RuntimeException e) {
                UniversalBossRespawner.LOGGER.warn("Failed to schedule home registration for {}: {}",
                        record.entityType(), e.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
            // KILLED is owned by LivingDeathHandler; UNLOADED_* and CHANGED_DIMENSION must keep the anchor.
            return;
        }
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) {
            return;
        }
        if (BossHomeSavedData.get(server).remove(entity.getUUID())) {
            DebugLog.info("Removed home record for discarded entity: entity={} uuid={}",
                    entity.getType(), entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!backfillRequested) {
            return;
        }
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            // Keep the request pending; it will run once the master switch is enabled again.
            return;
        }
        backfillRequested = false;
        backfill(event.getServer());
    }

    @Nullable
    private static HomeRecord recordFor(ServerLevel level, Entity entity) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) {
            return null;
        }
        UUID uuid = entity.getUUID();
        BlockPos pos = entity.blockPosition();
        return new HomeRecord(uuid, typeId, level.dimension(), pos);
    }

    /**
     * Server-thread only: writes the immutable record into saved data and logs the result.
     */
    private static boolean registerRecord(MinecraftServer server, HomeRecord record, String source,
                                          boolean loadedFromDisk) {
        BossHomeSavedData data = BossHomeSavedData.get(server);
        boolean added = data.registerIfAbsent(record);
        if (added) {
            DebugLog.info("Registered home via {}: entity={} uuid={} dimension={} pos={} loadedFromDisk={}",
                    source, record.entityType(), record.entityUuid(), record.dimension().location(),
                    record.homePos(), loadedFromDisk);
        } else {
            DebugLog.info("Home already registered for entity={} uuid={}; keeping the original anchor ({})",
                    record.entityType(), record.entityUuid(), source);
        }
        return added;
    }

    /**
     * Anchors every currently loaded entity that matches a home rule but has no record yet.
     *
     * <p>Necessary because a rule added through {@code /reload} was not present when those entities joined
     * the level, so their join event could not register anything. Without this pass, such an entity would
     * die without a home record and no cage would be placed at all.
     *
     * <p>{@link ServerLevel#getAllEntities()} yields only already-loaded entities, so this never loads a
     * chunk.
     */
    private static void backfill(MinecraftServer server) {
        BossHomeSavedData data = BossHomeSavedData.get(server);
        int registered = 0;
        int alreadyKnown = 0;
        int failed = 0;

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                try {
                    RespawnEntry entry = RespawnRuleManager.INSTANCE.find(entity.getType());
                    if (entry == null || entry.placement().mode() != RespawnEntry.PlacementMode.HOME) {
                        continue;
                    }
                    HomeRecord record = recordFor(level, entity);
                    if (record == null) {
                        continue;
                    }
                    if (registerRecord(server, record, "reload-backfill", false)) {
                        registered++;
                    } else {
                        alreadyKnown++;
                    }
                } catch (Exception e) {
                    failed++;
                    UniversalBossRespawner.LOGGER.warn("Home back-fill failed for entity {}: {}",
                            entity.getType(), e.toString());
                }
            }
        }

        UniversalBossRespawner.LOGGER.info(
                "Home back-fill after rule reload: registered={} alreadyKnown={} failed={} (total anchors={})",
                registered, alreadyKnown, failed, data.homeCount());
    }
}
