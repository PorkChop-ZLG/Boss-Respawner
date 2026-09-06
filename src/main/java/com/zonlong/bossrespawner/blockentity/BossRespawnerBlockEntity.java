package com.zonlong.bossrespawner.blockentity;

import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.zonlong.bossrespawner.block.BossRespawnerBlock;

public class BossRespawnerBlockEntity extends BlockEntity {
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_KEY_ITEM_ID = "KeyItemId";
    private static final String TAG_KEY_AMOUNT = "KeyAmount";
    private static final String TAG_SPAWN_RULE = "SpawnRule";
    private static final String TAG_LIT_TICKS = "LitTicks";
    private static final String TAG_ATTEMPTS = "Attempts";
    private static final String TAG_SPAWNED = "Spawned";
    private static final String TAG_STOPPED = "Stopped";

    private String entityTypeId = "";
    private String keyItemId = "";
    private int keyAmount = 1;
    private CompoundTag spawnNbt = new CompoundTag();
    private int delayTicks = 60;
    private boolean requirePlayerNearby = true;
    private double playerRange = 16.0D;
    private boolean allowPeaceful = false;
    private int count = 1;
    private int[] spawnOffset = new int[]{0, 1, 0};
    private boolean finalizeSpawn = true;
    private boolean setHomeToCage = false;
    private int maxAttempts = -1;
    private int retryIntervalTicks = 20;

    public int tickCount;
    public final AnimationState openingAnimationState = new AnimationState();
    private Entity displayEntity;

    private int litTicks = 0;
    private int attempts = 0;
    private boolean spawned = false;
    private boolean stopped = false;

    public BossRespawnerBlockEntity(BlockPos pos, BlockState state) {
        super(com.zonlong.bossrespawner.init.ModBlockEntities.BOSS_RESPAWNER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BossRespawnerBlockEntity be) {
        be.tickCount++;
        if (be.spawned || be.stopped) {
            return;
        }

        if (!state.getValue(BossRespawnerBlock.LIT)) {
            be.litTicks = 0;
            return;
        }

        if (level.isClientSide) {
            return;
        }

        be.litTicks++;
        if (level.getDifficulty() == Difficulty.PEACEFUL && !be.allowPeaceful) {
            return;
        }
        if (be.requirePlayerNearby && !be.anyPlayerInRange(level)) {
            return;
        }
        if (be.litTicks < be.delayTicks) {
            return;
        }

        be.litTicks = 0;
        if (be.trySpawn((ServerLevel) level, pos)) {
            be.spawned = true;
            level.destroyBlock(pos, false);
        } else {
            be.attempts++;
            if (be.maxAttempts >= 0 && be.attempts >= be.maxAttempts) {
                be.stopped = true;
            }
            // Reuse litTicks as a countdown so the next attempt happens after retryIntervalTicks.
            be.litTicks = Math.max(0, be.delayTicks - be.retryIntervalTicks);
            be.setChanged();
        }
    }

    public boolean matchesKeyItem(ItemStack stack) {
        if (keyItemId.isEmpty()) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(keyItemId));
        return item != null && stack.is(item) && stack.getCount() >= keyAmount;
    }

    public int getKeyAmount() {
        return keyAmount;
    }

    public void activate(Level level) {
        if (level.getBlockState(worldPosition).hasProperty(BossRespawnerBlock.LIT)) {
            level.setBlock(worldPosition, level.getBlockState(worldPosition).setValue(BossRespawnerBlock.LIT, true), 2);
            litTicks = 0;
            setChanged();
            level.blockEvent(worldPosition, level.getBlockState(worldPosition).getBlock(), 1, 0);
        }
    }

    private boolean anyPlayerInRange(Level level) {
        return level.hasNearbyAlivePlayer(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D,
                playerRange);
    }

    private boolean trySpawn(ServerLevel serverLevel, BlockPos pos) {
        if (entityTypeId.isEmpty()) {
            return false;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityTypeId));
        if (type == null) {
            UniversalBossRespawner.LOGGER.warn("Cannot find entity type {} for respawner at {}", entityTypeId, pos);
            return false;
        }

        Vec3 spawnPos = Vec3.atLowerCornerWithOffset(
                pos.offset(spawnOffset[0], spawnOffset[1], spawnOffset[2]),
                0.5D, 0.0D, 0.5D);

        for (int i = 0; i < count; i++) {
            try {
                Entity entity = type.create(serverLevel);
                if (entity == null) {
                    return false;
                }
                entity.setPos(spawnPos);
                if (!spawnNbt.isEmpty()) {
                    entity.load(spawnNbt);
                }
                if (entity instanceof Mob mob && finalizeSpawn) {
                    mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
                }
                if (setHomeToCage) {
                    trySetHome(entity, serverLevel, pos);
                }
                if (!serverLevel.addFreshEntity(entity)) {
                    return false;
                }
            } catch (Exception e) {
                UniversalBossRespawner.LOGGER.warn("Failed to spawn {} from respawner at {}", entityTypeId, pos, e);
                return false;
            }
        }
        return true;
    }

    private static void trySetHome(Entity entity, ServerLevel serverLevel, BlockPos pos) {
        try {
            Class<?> clazz = Class.forName("com.github.L_Ender.cataclysm.entity.etc.IHomeEntity");
            if (clazz.isInstance(entity)) {
                clazz.getMethod("setHomePos", GlobalPos.class)
                        .invoke(entity, GlobalPos.of(serverLevel.dimension(), pos));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Optional Cataclysm integration; ignore when absent.
        }
    }

    public void setSpawnerData(String entityTypeId, String keyItemId, int keyAmount,
                               CompoundTag spawnNbt, int delayTicks, boolean requirePlayerNearby,
                               double playerRange, boolean allowPeaceful, int count, int[] spawnOffset,
                               boolean finalizeSpawn, boolean setHomeToCage, int maxAttempts,
                               int retryIntervalTicks) {
        this.entityTypeId = entityTypeId;
        this.keyItemId = keyItemId;
        this.keyAmount = keyAmount;
        this.spawnNbt = spawnNbt == null ? new CompoundTag() : spawnNbt.copy();
        this.delayTicks = delayTicks;
        this.requirePlayerNearby = requirePlayerNearby;
        this.playerRange = playerRange;
        this.allowPeaceful = allowPeaceful;
        this.count = count;
        this.spawnOffset = spawnOffset == null ? new int[]{0, 1, 0} : spawnOffset.clone();
        this.finalizeSpawn = finalizeSpawn;
        this.setHomeToCage = setHomeToCage;
        this.maxAttempts = maxAttempts;
        this.retryIntervalTicks = retryIntervalTicks;
        this.litTicks = 0;
        this.attempts = 0;
        this.spawned = false;
        this.stopped = false;
        setChanged();
    }

    public String getEntityTypeId() {
        return entityTypeId;
    }

    public String getKeyItemId() {
        return keyItemId;
    }

    public CompoundTag getSpawnNbt() {
        return spawnNbt;
    }

    public int getDelayTicks() {
        return delayTicks;
    }

    public int getRetryIntervalTicks() {
        return retryIntervalTicks;
    }

    public boolean isSetHomeToCage() {
        return setHomeToCage;
    }

    public int getLitTicks() {
        return litTicks;
    }

    public AnimationState getAnimationState(String input) {
        if ("opening".equals(input)) {
            return openingAnimationState;
        }
        return new AnimationState();
    }

    public Entity getDisplayEntity(Level level) {
        if (entityTypeId.isEmpty()) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityTypeId));
        if (type == null) {
            return null;
        }
        if (displayEntity == null || displayEntity.getType() != type) {
            displayEntity = type.create(level);
        }
        return displayEntity;
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.entityTypeId = tag.getString(TAG_ENTITY_TYPE);
        this.keyItemId = tag.getString(TAG_KEY_ITEM_ID);
        this.keyAmount = tag.getInt(TAG_KEY_AMOUNT);
        if (this.keyAmount <= 0) {
            this.keyAmount = 1;
        }

        if (tag.contains(TAG_SPAWN_RULE, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag rule = tag.getCompound(TAG_SPAWN_RULE);
            this.delayTicks = rule.getInt("DelayTicks");
            this.requirePlayerNearby = rule.getBoolean("RequirePlayerNearby");
            this.playerRange = rule.getDouble("PlayerRange");
            this.allowPeaceful = rule.getBoolean("AllowPeaceful");
            this.count = rule.getInt("Count");
            if (rule.contains("SpawnOffset", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                this.spawnOffset = rule.getIntArray("SpawnOffset");
            }
            this.finalizeSpawn = rule.getBoolean("FinalizeSpawn");
            this.setHomeToCage = rule.getBoolean("SetHomeToCage");
            this.maxAttempts = rule.getInt("MaxAttempts");
            this.retryIntervalTicks = rule.getInt("RetryIntervalTicks");
            if (rule.contains("SpawnNbt", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                this.spawnNbt = rule.getCompound("SpawnNbt").copy();
            }
        }

        this.litTicks = tag.getInt(TAG_LIT_TICKS);
        this.attempts = tag.getInt(TAG_ATTEMPTS);
        this.spawned = tag.getBoolean(TAG_SPAWNED);
        this.stopped = tag.getBoolean(TAG_STOPPED);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!entityTypeId.isEmpty()) {
            tag.putString(TAG_ENTITY_TYPE, entityTypeId);
        }
        if (!keyItemId.isEmpty()) {
            tag.putString(TAG_KEY_ITEM_ID, keyItemId);
            tag.putInt(TAG_KEY_AMOUNT, keyAmount);
        }

        CompoundTag rule = new CompoundTag();
        rule.putInt("DelayTicks", delayTicks);
        rule.putBoolean("RequirePlayerNearby", requirePlayerNearby);
        rule.putDouble("PlayerRange", playerRange);
        rule.putBoolean("AllowPeaceful", allowPeaceful);
        rule.putInt("Count", count);
        rule.putIntArray("SpawnOffset", spawnOffset);
        rule.putBoolean("FinalizeSpawn", finalizeSpawn);
        rule.putBoolean("SetHomeToCage", setHomeToCage);
        rule.putInt("MaxAttempts", maxAttempts);
        rule.putInt("RetryIntervalTicks", retryIntervalTicks);
        if (!spawnNbt.isEmpty()) {
            rule.put("SpawnNbt", spawnNbt.copy());
        }
        tag.put(TAG_SPAWN_RULE, rule);

        tag.putInt(TAG_LIT_TICKS, litTicks);
        tag.putInt(TAG_ATTEMPTS, attempts);
        tag.putBoolean(TAG_SPAWNED, spawned);
        tag.putBoolean(TAG_STOPPED, stopped);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public boolean triggerEvent(int type, int data) {
        if (type == 1) {
            this.litTicks = 0;
            if (this.level != null && this.level.isClientSide) {
                this.openingAnimationState.start(this.tickCount);
            }
            return true;
        }
        return super.triggerEvent(type, data);
    }
}
