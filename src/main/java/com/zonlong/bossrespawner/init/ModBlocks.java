package com.zonlong.bossrespawner.init;

import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.block.BossRespawnerBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UniversalBossRespawner.MODID);

    public static final DeferredBlock<BossRespawnerBlock> BOSS_RESPAWNER = BLOCKS.register("boss_respawner",
            () -> new BossRespawnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .sound(SoundType.STONE)));

    private ModBlocks() {
    }
}
