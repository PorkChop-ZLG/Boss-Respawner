package com.zonlong.bossrespawner.blockentity;

import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.block.BossRespawnerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class BossRespawnerBlockEntity extends BlockEntity {
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_KEY_ITEM_ID = "KeyItemId";
    private static final String TAG_KEY_AMOUNT = "KeyAmount";
    private static final String TAG_CONSUME_KEY_ITEM = "ConsumeKeyItem";
    private static final String TAG_SPAWN_RULE = "SpawnRule";
    private static final String TAG_LIT_TICKS = "LitTicks";
    private static final String TAG_ATTEMPTS = "Attempts";
    private static final String TAG_SPAWNED = "Spawned";

    private String entityTypeId = "";
    private String keyItemId = "";
    private int keyAmount = 1;
    private boolean consumeKeyItem = true;
    private CompoundTag spawnNbt = new CompoundTag();
    private int delayTicks = 20;
    private boolean requirePlayerNearby = true;
    private double playerRange = 9.0D;
    private boolean allowPeaceful = false;
    private int count = 1;
    private int[] spawnOffset = new int[]{0, 0, 0};
    private boolean finalizeSpawn = true;
    private int maxAttempts = 20;
    private int retryIntervalTicks = 4;

    private ResourceLocation entityLocation;
    private EntityType<?> cachedEntityType;
    private ResourceLocation itemLocation;
    private Item cachedItem;

    public int tickCount;
    public final AnimationState openingAnimationState = new AnimationState();
    private Entity displayEntity;

    private int litTicks = 0;
    private int attempts = 0;
    private boolean spawned = false;

    public BossRespawnerBlockEntity(BlockPos pos, BlockState state) {
        super(com.zonlong.bossrespawner.init.ModBlockEntities.BOSS_RESPAWNER.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BossRespawnerBlockEntity be) {
        // Only tick while lit. Unlit cages do no gameplay work.
        if (!state.getValue(BossRespawnerBlock.LIT)) {
            return;
        }

        be.tickCount++;
        if (be.spawned || level.isClientSide) {
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
        DebugLog.info("Attempting spawn from lit respawner at {}: entity={} attempts={} maxAttempts={}",
                pos, be.entityTypeId, be.attempts, be.maxAttempts);
        if (be.trySpawn((ServerLevel) level, pos)) {
            be.spawned = true;
            DebugLog.info("Spawn succeeded from respawner at {}: entity={}; destroying cage", pos, be.entityTypeId);
            level.destroyBlock(pos, false);
        } else {
            be.attempts++;
            DebugLog.info("Spawn failed from respawner at {}: entity={} attempts={} maxAttempts={}",
                    pos, be.entityTypeId, be.attempts, be.maxAttempts);
            if (be.maxAttempts > 0 && be.attempts >= be.maxAttempts) {
                be.resetAfterMaxAttempts((ServerLevel) level);
            } else {
                // Reuse litTicks as a countdown for the next retry.
                be.litTicks = Math.max(0, be.delayTicks - be.retryIntervalTicks);
                be.setChanged();
                DebugLog.info("Scheduled retry for respawner at {}: next attempts in {} ticks",
                        pos, Math.max(1, be.retryIntervalTicks));
            }
        }
    }

    public boolean matchesKeyItem(ItemStack stack) {
        Item item = getCachedKeyItem();
        return item != null && stack.is(item) && stack.getCount() >= keyAmount;
    }

    public int getKeyAmount() {
        return keyAmount;
    }

    public boolean shouldConsume() {
        return consumeKeyItem;
    }

    public void activate(Level level) {
        if (level.getBlockState(worldPosition).hasProperty(BossRespawnerBlock.LIT)) {
            DebugLog.info("Activating respawner at {}: entity={} key={} delay={} maxAttempts={}",
                    worldPosition, entityTypeId, keyItemId, delayTicks, maxAttempts);
            level.setBlock(worldPosition, level.getBlockState(worldPosition).setValue(BossRespawnerBlock.LIT, true), 2);
            litTicks = 0;
            attempts = 0;
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
        EntityType<?> type = getCachedEntityType();
        if (type == null) {
            UniversalBossRespawner.LOGGER.warn("Cannot find entity type {} for respawner at {}", entityTypeId, pos);
            DebugLog.info("trySpawn failed: cached entity type is null for entityId={} at {}", entityTypeId, pos);
            return false;
        }

        Vec3 spawnPos = Vec3.atLowerCornerWithOffset(
                pos.offset(spawnOffset[0], spawnOffset[1], spawnOffset[2]),
                0.5D, 0.0D, 0.5D);
        DebugLog.info("trySpawn: entityId={} type={} pos={} spawnPos={} count={} finalizeSpawn={} nbtEmpty={}",
                entityTypeId, type, pos, spawnPos, count, finalizeSpawn, spawnNbt.isEmpty());

        // Create all entities first so a late creation failure doesn't leave partial spawns.
        List<Entity> created = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                Entity entity = type.create(serverLevel);
                if (entity == null) {
                    DebugLog.info("trySpawn failed: type.create returned null for {} at {}", entityTypeId, pos);
                    discardAll(created);
                    return false;
                }
                entity.setPos(spawnPos);
                if (!spawnNbt.isEmpty()) {
                    entity.load(spawnNbt);
                }
                if (entity instanceof Mob mob && finalizeSpawn) {
                    mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
                }
                created.add(entity);
                DebugLog.info("trySpawn created entity #{} of type {} for {}", i, entity.getType(), entityTypeId);
            }

            for (Entity entity : created) {
                if (!serverLevel.addFreshEntity(entity)) {
                    DebugLog.info("trySpawn failed: addFreshEntity rejected entity {} for {}", entity, entityTypeId);
                    discardAll(created);
                    return false;
                }
            }
            DebugLog.info("trySpawn added {} fresh entities for {}", created.size(), entityTypeId);
            return true;
        } catch (Exception e) {
            discardAll(created);
            UniversalBossRespawner.LOGGER.warn("Failed to spawn {} from respawner at {}", entityTypeId, pos, e);
            DebugLog.info("trySpawn caught exception for {} at {}: {}", entityTypeId, pos, e.toString());
            return false;
        }
    }

    private static void discardAll(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private void resetAfterMaxAttempts(ServerLevel level) {
        DebugLog.info("Resetting respawner after max attempts at {}: entity={} attempts={} maxAttempts={}",
                worldPosition, entityTypeId, attempts, maxAttempts);
        attempts = 0;
        litTicks = 0;

        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(BossRespawnerBlock.LIT)) {
            BlockState litState = state;
            BlockState unlitState = state.setValue(BossRespawnerBlock.LIT, false);
            level.setBlock(worldPosition, unlitState, 2);
            level.sendBlockUpdated(worldPosition, litState, unlitState, 3);
        }

        Component message = Component.translatable("boss_respawner.message.respawn_failed", entityTypeId);
        for (Player player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 256.0D) {
                player.displayClientMessage(message, false);
            }
        }
        UniversalBossRespawner.LOGGER.warn("Respawner at {} failed {} times and reset for entity {}",
                worldPosition, maxAttempts, entityTypeId);
        setChanged();
    }

    public void setSpawnerData(String entityTypeId, String keyItemId, int keyAmount, boolean consumeKeyItem,
                               CompoundTag spawnNbt, int delayTicks, boolean requirePlayerNearby,
                               double playerRange, boolean allowPeaceful, int count, int[] spawnOffset,
                               boolean finalizeSpawn, int maxAttempts, int retryIntervalTicks) {
        this.entityTypeId = entityTypeId == null ? "" : entityTypeId;
        this.keyItemId = keyItemId == null ? "" : keyItemId;
        this.keyAmount = Math.max(1, keyAmount);
        this.consumeKeyItem = consumeKeyItem;
        this.spawnNbt = spawnNbt == null ? new CompoundTag() : spawnNbt.copy();
        this.delayTicks = Math.max(0, delayTicks);
        this.requirePlayerNearby = requirePlayerNearby;
        this.playerRange = Math.max(1.0D, playerRange);
        this.allowPeaceful = allowPeaceful;
        this.count = Math.max(1, count);
        this.spawnOffset = normalizeOffset(spawnOffset);
        this.finalizeSpawn = finalizeSpawn;
        this.maxAttempts = normalizeMaxAttempts(maxAttempts);
        this.retryIntervalTicks = Math.max(1, retryIntervalTicks);
        this.litTicks = 0;
        this.attempts = 0;
        this.spawned = false;
        this.displayEntity = null;
        invalidateCaches();
        DebugLog.info("setSpawnerData at {}: entity={} key={} amount={} consume={} delay={} maxAttempts={}",
                worldPosition, this.entityTypeId, this.keyItemId, this.keyAmount,
                this.consumeKeyItem, this.delayTicks, this.maxAttempts);
        setChanged();
    }

    public String getEntityTypeId() {
        return entityTypeId;
    }

    public String getKeyItemId() {
        return keyItemId;
    }

    public EntityType<?> getCachedEntityType() {
        if (cachedEntityType == null && !entityTypeId.isEmpty()) {
            entityLocation = parseLocation(entityTypeId);
            if (entityLocation != null) {
                cachedEntityType = BuiltInRegistries.ENTITY_TYPE.get(entityLocation);
            }
        }
        return cachedEntityType;
    }

    public Item getCachedKeyItem() {
        if (cachedItem == null && !keyItemId.isEmpty()) {
            itemLocation = parseLocation(keyItemId);
            if (itemLocation != null) {
                cachedItem = BuiltInRegistries.ITEM.get(itemLocation);
            }
        }
        return cachedItem;
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
        EntityType<?> type = getCachedEntityType();
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
        this.consumeKeyItem = !tag.contains(TAG_CONSUME_KEY_ITEM) || tag.getBoolean(TAG_CONSUME_KEY_ITEM);

        if (tag.contains(TAG_SPAWN_RULE, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag rule = tag.getCompound(TAG_SPAWN_RULE);
            this.delayTicks = Math.max(0, rule.getInt("DelayTicks"));
            this.requirePlayerNearby = rule.getBoolean("RequirePlayerNearby");
            this.playerRange = Math.max(1.0D, rule.getDouble("PlayerRange"));
            this.allowPeaceful = rule.getBoolean("AllowPeaceful");
            this.count = Math.max(1, rule.getInt("Count"));
            if (rule.contains("SpawnOffset", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                this.spawnOffset = normalizeOffset(rule.getIntArray("SpawnOffset"));
            }
            this.finalizeSpawn = rule.getBoolean("FinalizeSpawn");
            this.maxAttempts = normalizeMaxAttempts(rule.getInt("MaxAttempts"));
            this.retryIntervalTicks = Math.max(1, rule.getInt("RetryIntervalTicks"));
            if (rule.contains("SpawnNbt", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                this.spawnNbt = rule.getCompound("SpawnNbt").copy();
            }
        }

        this.litTicks = tag.getInt(TAG_LIT_TICKS);
        this.attempts = tag.getInt(TAG_ATTEMPTS);
        this.spawned = tag.getBoolean(TAG_SPAWNED);
        invalidateCaches();
        DebugLog.info("Loaded respawner data at {}: entity={} key={} litTicks={} attempts={} spawned={}",
                worldPosition, this.entityTypeId, this.keyItemId, this.litTicks, this.attempts, this.spawned);
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
            tag.putBoolean(TAG_CONSUME_KEY_ITEM, consumeKeyItem);
        }

        CompoundTag rule = new CompoundTag();
        rule.putInt("DelayTicks", delayTicks);
        rule.putBoolean("RequirePlayerNearby", requirePlayerNearby);
        rule.putDouble("PlayerRange", playerRange);
        rule.putBoolean("AllowPeaceful", allowPeaceful);
        rule.putInt("Count", count);
        rule.putIntArray("SpawnOffset", spawnOffset);
        rule.putBoolean("FinalizeSpawn", finalizeSpawn);
        rule.putInt("MaxAttempts", maxAttempts);
        rule.putInt("RetryIntervalTicks", retryIntervalTicks);
        if (!spawnNbt.isEmpty()) {
            rule.put("SpawnNbt", spawnNbt.copy());
        }
        tag.put(TAG_SPAWN_RULE, rule);

        tag.putInt(TAG_LIT_TICKS, litTicks);
        tag.putInt(TAG_ATTEMPTS, attempts);
        tag.putBoolean(TAG_SPAWNED, spawned);
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

    private void invalidateCaches() {
        this.entityLocation = null;
        this.cachedEntityType = null;
        this.itemLocation = null;
        this.cachedItem = null;
    }

    private static ResourceLocation parseLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.contains(":")) {
                return ResourceLocation.parse(value);
            }
            return ResourceLocation.fromNamespaceAndPath("minecraft", value);
        } catch (Exception e) {
            UniversalBossRespawner.LOGGER.warn("Invalid resource location '{}' in respawner NBT", value);
            return null;
        }
    }

    private static int[] normalizeOffset(int[] offset) {
        if (offset == null || offset.length != 3) {
            return new int[]{0, 0, 0};
        }
        return offset.clone();
    }

    private static int normalizeMaxAttempts(int value) {
        if (value < 0) {
            return -1;
        }
        if (value == 0) {
            return 20;
        }
        return value;
    }
}
