package com.zonlong.bossrespawner.client;

import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.client.render.BossRespawnerBlockEntityRenderer;
import com.zonlong.bossrespawner.client.render.BossRespawnerItemRenderer;
import com.zonlong.bossrespawner.init.ModBlockEntities;
import com.zonlong.bossrespawner.init.ModItems;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = UniversalBossRespawner.MODID, value = Dist.CLIENT)
public final class ClientRegister {
    private ClientRegister() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BOSS_RESPAWNER.get(),
                BossRespawnerBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(BossRespawnerItemRenderer.ClientExtensions.INSTANCE,
                ModItems.BOSS_RESPAWNER.get());
    }
}
