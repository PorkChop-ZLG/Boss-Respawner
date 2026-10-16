package com.zonlong.bossrespawner.compat.jade;

import com.zonlong.bossrespawner.block.BossRespawnerBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade plugin entry point for the Boss Respawner block.
 *
 * <p>Jade finds this class on its own: during {@code FMLLoadCompleteEvent} it scans every mod's annotation
 * data for {@link WailaPlugin} and instantiates the hits reflectively. No service file, no explicit
 * registration, and no entry in our own {@code @Mod} class is involved.
 *
 * <h2>Optional-dependency contract (do not break)</h2>
 *
 * <p>Jade is an <em>optional</em> dependency. This class and {@link BossRespawnerJadeProvider} reference
 * Jade types, so they must never be referenced from code that always loads — the {@code @Mod} class, event
 * handlers, config, registries, and so on. That single rule is what makes the integration safe:
 *
 * <ul>
 *   <li>Without Jade, nothing in this mod ever mentions these two classes, so they are never class-loaded
 *       and a {@code NoClassDefFoundError} is impossible. Jade's scanning code lives in Jade's own event
 *       handler, so it does not run either.</li>
 *   <li>With Jade present, Jade loads them itself.</li>
 * </ul>
 *
 * <p>{@code registerClient} is only invoked on a physical client, so nothing here runs on a dedicated
 * server.
 */
@WailaPlugin
public class BossRespawnerJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BossRespawnerJadeProvider.INSTANCE, BossRespawnerBlock.class);
    }
}
