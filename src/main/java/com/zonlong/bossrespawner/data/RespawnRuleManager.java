package com.zonlong.bossrespawner.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class RespawnRuleManager {
    public static final RespawnRuleManager INSTANCE = new RespawnRuleManager();

    private Map<ResourceLocation, RespawnEntry> byEntity = Collections.emptyMap();

    private RespawnRuleManager() {
    }

    public RespawnRuleReloadListener createReloadListener() {
        return new RespawnRuleReloadListener(this);
    }

    public RespawnEntry find(EntityType<?> entityType) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return key == null ? null : byEntity.get(key);
    }

    public boolean isEmpty() {
        return byEntity.isEmpty();
    }

    public Map<ResourceLocation, RespawnEntry> getByEntity() {
        return Collections.unmodifiableMap(byEntity);
    }

    void replaceAll(Map<ResourceLocation, RespawnEntry> byEntity) {
        this.byEntity = Collections.unmodifiableMap(new HashMap<>(byEntity));
    }
}
