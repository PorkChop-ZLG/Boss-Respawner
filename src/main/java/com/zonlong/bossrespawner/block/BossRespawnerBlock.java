package com.zonlong.bossrespawner.block;

import com.mojang.serialization.MapCodec;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity;
import com.zonlong.bossrespawner.init.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class BossRespawnerBlock extends BaseEntityBlock {
    public static final MapCodec<BossRespawnerBlock> CODEC = simpleCodec(BossRespawnerBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BossRespawnerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public MapCodec<BossRespawnerBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BossRespawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.BOSS_RESPAWNER.get(), BossRespawnerBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(LIT)) {
            DebugLog.info("Right-click ignored: respawner is already lit at {}", pos);
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(level.getBlockEntity(pos) instanceof BossRespawnerBlockEntity be)) {
            DebugLog.info("Right-click ignored: no BossRespawnerBlockEntity at {}", pos);
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!be.matchesKeyItem(stack)) {
            DebugLog.info("Right-click ignored: key item mismatch at {} (stack={}, required={})",
                    pos, stack, be.getKeyItemId());
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        DebugLog.info("Right-click accepted: activating respawner at {} with stack={}", pos, stack);
        if (!level.isClientSide) {
            if (!player.getAbilities().instabuild && be.shouldConsume()) {
                stack.shrink(be.getKeyAmount());
            }
            be.activate(level);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, level.getBlockState(pos)));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}
