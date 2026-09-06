package com.zonlong.bossrespawner.init;

import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UniversalBossRespawner.MODID);

    public static final DeferredItem<BlockItem> BOSS_RESPAWNER = ITEMS.register("boss_respawner",
            () -> new BlockItem(ModBlocks.BOSS_RESPAWNER.get(),
                    new Item.Properties().fireResistant().rarity(Rarity.EPIC)));

    private ModItems() {
    }
}
