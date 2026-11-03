package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.RespawnEntry;
import com.zonlong.bossrespawner.data.RespawnRuleManager;
import com.zonlong.bossrespawner.placement.RespawnCagePlacer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.Optional;

public final class LivingDeathHandler {
    private LivingDeathHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            // The master switch disables placement, not the cleanup of a dead entity's now-useless record.
            BossHomeSavedData.get(serverLevel).remove(entity.getUUID());
            return;
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (DebugLog.isEnabled()) {
            DebugLog.info("LivingDeathEvent: entity={} type={} entityId={} position={} dimension={}",
                    entity, entity.getType(), entityId, entity.blockPosition(), serverLevel.dimension().location());
        }

        RespawnEntry entry = RespawnRuleManager.INSTANCE.find(entity.getType());
        if (entry == null) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("No respawn rule found for entityId={}; dropping its home record (if any)",
                        entityId);
            }
            BossHomeSavedData.get(serverLevel).remove(entity.getUUID());
            return;
        }

        if (DebugLog.isEnabled()) {
            DebugLog.info("Found respawn rule id={} for entityId={}", entry.id(), entityId);
        }

        if (!matchesDeathCondition(entity, event.getSource(), entry.death())) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Death conditions not met for entityId={} rule={} (playerKillOnly={}, dimensions={}, biomes={})",
                        entityId, entry.id(), entry.death().playerKillOnly(), entry.death().dimensions(), entry.death().biomes());
            }
            // The entity is dead and this death produces no cage. A home record can never be reused by this
            // UUID, so keeping it would only grow boss_respawner_homes.dat forever.
            BossHomeSavedData.get(serverLevel).remove(entity.getUUID());
            return;
        }

        if (DebugLog.isEnabled()) {
            DebugLog.info("Death conditions met; attempting to place respawn cage for entityId={} rule={}", entityId, entry.id());
        }

        if (entry.placement().mode() == RespawnEntry.PlacementMode.TWILIGHTFOREST) {
            // Twilight Forest bosses get their loot chest only when the entity is removed, so the cage is
            // scheduled behind a wait-for-removal task instead of being placed from this death event.
            TwilightSettlementService.schedule(serverLevel, entity, entry, entityId.toString());
            return;
        }

        try {
            RespawnCagePlacer.Outcome outcome =
                    RespawnCagePlacer.tryPlace(serverLevel, entity, entry, entityId.toString());
            if (DebugLog.isEnabled()) {
                DebugLog.info("Placement attempt finished: outcome={} entityId={} rule={}",
                        outcome, entityId, entry.id());
            }
        } catch (Exception e) {
            UniversalBossRespawner.LOGGER.warn("Failed to place {} respawn cage", entityId, e);
            // The placement pipeline threw before it could consume the record. The entity is dead; keeping
            // the home would leak it, and there is no cage to hand it to.
            BossHomeSavedData.get(serverLevel).remove(entity.getUUID());
            if (DebugLog.isEnabled()) {
                DebugLog.info("Exception while placing respawn cage for entityId={}: {}", entityId, e.toString());
            }
        }
    }

    private static boolean matchesDeathCondition(LivingEntity entity, DamageSource source,
                                                 RespawnEntry.DeathRule deathRule) {
        if (deathRule.playerKillOnly()) {
            boolean byPlayer = source.getEntity() instanceof Player
                    || source.getDirectEntity() instanceof Player;
            if (!byPlayer) {
                return false;
            }
        }

        List<String> dimensions = deathRule.dimensions();
        if (!dimensions.isEmpty()) {
            String dimension = entity.level().dimension().location().toString();
            if (!dimensions.contains(dimension)) {
                return false;
            }
        }

        List<String> biomes = deathRule.biomes();
        if (!biomes.isEmpty()) {
            Optional<String> biome = entity.level().getBiome(entity.blockPosition())
                    .unwrapKey()
                    .map(key -> key.location().toString());
            if (biome.isEmpty() || !biomes.contains(biome.get())) {
                return false;
            }
        }

        return true;
    }
}
