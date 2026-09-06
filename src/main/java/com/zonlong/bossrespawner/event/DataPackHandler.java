package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.data.RespawnRuleManager;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class DataPackHandler {
    private DataPackHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(RespawnRuleManager.INSTANCE.createReloadListener());
    }
}
