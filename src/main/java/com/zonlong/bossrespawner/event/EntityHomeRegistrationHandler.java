package com.zonlong.bossrespawner.event;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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
 * <p>Both paths are strictly read-only with respect to the world. {@code EntityJoinLevelEvent} can be fired
 * before the chunk is promoted to {@code ChunkStatus#FULL}, and NeoForge documents that any world
 * interaction there risks a chunk-loading deadlock. Registering an anchor only touches the saved data.
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

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            // The event also fires on the client; anchors are server-side state only.
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Entity entity = event.getEntity();
        RespawnEntry entry = RespawnRuleManager.INSTANCE.find(entity.getType());
        if (entry == null || entry.placement().mode() != RespawnEntry.PlacementMode.HOME) {
            return;
        }

        registerHome(serverLevel, entity, "entity-join", event.loadedFromDisk());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!backfillRequested) {
            return;
        }
        backfillRequested = false;
        backfill(event.getServer());
    }

    /**
     * Anchors {@code entity} at its current position unless it already has a record.
     *
     * @param source         where this registration came from, for logging only
     * @param loadedFromDisk relevant to the join path only; never used to skip registration
     * @return {@code true} when a new anchor was stored
     */
    private static boolean registerHome(ServerLevel level, Entity entity, String source, boolean loadedFromDisk) {
        UUID uuid = entity.getUUID();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) {
            return false;
        }

        BlockPos pos = entity.blockPosition();
        BossHomeSavedData data = BossHomeSavedData.get(level);
        boolean added = data.registerIfAbsent(new HomeRecord(uuid, typeId, level.dimension(), pos));

        if (added) {
            DebugLog.info("Registered home via {}: entity={} uuid={} dimension={} pos={} loadedFromDisk={}",
                    source, typeId, uuid, level.dimension().location(), pos, loadedFromDisk);
        } else {
            DebugLog.info("Home already registered for entity={} uuid={}; keeping the original anchor ({})",
                    typeId, uuid, source);
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
                    if (registerHome(level, entity, "reload-backfill", false)) {
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
