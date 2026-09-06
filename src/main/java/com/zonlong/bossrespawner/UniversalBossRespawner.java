package com.zonlong.bossrespawner;

import com.mojang.logging.LogUtils;
import com.zonlong.bossrespawner.event.DataPackHandler;
import com.zonlong.bossrespawner.event.LivingDeathHandler;
import com.zonlong.bossrespawner.init.ModBlockEntities;
import com.zonlong.bossrespawner.init.ModBlocks;
import com.zonlong.bossrespawner.init.ModItems;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(UniversalBossRespawner.MODID)
public class UniversalBossRespawner {
    public static final String MODID = "boss_respawner";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UniversalBossRespawner(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        NeoForge.EVENT_BUS.register(LivingDeathHandler.class);
        NeoForge.EVENT_BUS.addListener(DataPackHandler::onAddReloadListeners);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            UniversalBossRespawnerClient.init(modContainer);
        }
    }

    @SubscribeEvent
    public void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.BOSS_RESPAWNER);
        }
    }
}
