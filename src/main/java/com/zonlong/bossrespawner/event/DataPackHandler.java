package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.data.RespawnRuleManager;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class DataPackHandler {
    private DataPackHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        DebugLog.info("AddReloadListenerEvent fired; registering boss respawner entry reload listener");
        event.addListener(RespawnRuleManager.INSTANCE.createReloadListener());
    }
}
