package com.zonlong.bossrespawner.init;

import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UniversalBossRespawner.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BossRespawnerBlockEntity>> BOSS_RESPAWNER =
            BLOCK_ENTITY_TYPES.register("boss_respawner",
                    () -> BlockEntityType.Builder.of(BossRespawnerBlockEntity::new, ModBlocks.BOSS_RESPAWNER.get()).build(null));

    private ModBlockEntities() {
    }
}
