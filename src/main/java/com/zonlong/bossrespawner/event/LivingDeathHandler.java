package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.UniversalBossRespawner;
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
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        RespawnEntry entry = RespawnRuleManager.INSTANCE.find(entity.getType());
        if (entry == null) {
            return;
        }

        if (!matchesDeathCondition(entity, event.getSource(), entry.death())) {
            return;
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null) {
            return;
        }

        try {
            RespawnCagePlacer.tryPlace(serverLevel, entity, entry, entityId.toString());
        } catch (Exception e) {
            UniversalBossRespawner.LOGGER.warn("Failed to place {} respawn cage", entityId, e);
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
