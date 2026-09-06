package com.zonlong.bossrespawner.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RespawnRuleReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "boss_respawner/entries";

    private final RespawnRuleManager manager;

    public RespawnRuleReloadListener(RespawnRuleManager manager) {
        super(GSON, DIRECTORY);
        this.manager = manager;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager,
                         ProfilerFiller profilerFiller) {
        Map<ResourceLocation, RespawnEntry> byId = new HashMap<>();
        Map<ResourceLocation, RespawnEntry> byEntity = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();
            if (!element.isJsonObject()) {
                UniversalBossRespawner.LOGGER.warn("Skipping non-object respawn entry {}", id);
                continue;
            }

            Optional<RespawnEntry> parsed = RespawnEntry.fromJson(id, element.getAsJsonObject());
            if (parsed.isEmpty()) {
                continue;
            }

            RespawnEntry respawnEntry = parsed.get();
            boolean missingEntity = false;
            for (ResourceLocation entityId : respawnEntry.entities()) {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                    UniversalBossRespawner.LOGGER.warn(
                            "Skipping respawn entry {} because entity {} is not registered",
                            id, entityId);
                    missingEntity = true;
                    break;
                }
            }
            if (missingEntity) {
                continue;
            }

            if (respawnEntry.activation().item() == null
                    || !BuiltInRegistries.ITEM.containsKey(respawnEntry.activation().item())) {
                UniversalBossRespawner.LOGGER.warn(
                        "Skipping respawn entry {} because activation item {} is not registered",
                        id, respawnEntry.activation().item());
                continue;
            }

            String nbt = respawnEntry.spawn().nbt();
            if (nbt != null && !nbt.isBlank()) {
                try {
                    TagParser.parseTag(nbt);
                } catch (Exception e) {
                    UniversalBossRespawner.LOGGER.warn(
                            "Skipping respawn entry {} because spawn NBT is invalid: {}",
                            id, e.toString());
                    continue;
                }
            }

            byId.put(respawnEntry.id(), respawnEntry);
            for (ResourceLocation entityId : respawnEntry.entities()) {
                RespawnEntry previous = byEntity.get(entityId);
                if (previous == null || shouldReplace(previous, respawnEntry)) {
                    byEntity.put(entityId, respawnEntry);
                }
            }
        }

        manager.replaceAll(byEntity, byId);
        UniversalBossRespawner.LOGGER.info("Loaded {} boss respawn entries ({} effective entity mappings)",
                byId.size(), byEntity.size());
    }

    private boolean shouldReplace(RespawnEntry previous, RespawnEntry candidate) {
        if (candidate.priority() != previous.priority()) {
            return candidate.priority() > previous.priority();
        }
        return candidate.id().compareTo(previous.id()) < 0;
    }
}
