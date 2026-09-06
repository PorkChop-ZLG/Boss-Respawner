package com.zonlong.bossrespawner;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only helper. Loaded from the common mod entry only when running on the client.
 */
public final class UniversalBossRespawnerClient {
    private UniversalBossRespawnerClient() {
    }

    public static void init(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
