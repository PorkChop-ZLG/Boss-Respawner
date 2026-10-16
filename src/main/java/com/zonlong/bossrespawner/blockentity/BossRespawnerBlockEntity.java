package com.zonlong.bossrespawner.blockentity;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.block.BossRespawnerBlock;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.HomeRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class BossRespawnerBlockEntity extends BlockEntity {
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_KEY_ITEM_ID = "KeyItemId";
    private static final String TAG_KEY_AMOUNT = "KeyAmount";
    private static final String TAG_CONSUME_KEY_ITEM = "ConsumeKeyItem";
    private static final String TAG_SPAWN_RULE = "SpawnRule";
    private static final String TAG_LIT_TICKS = "LitTicks";
    private static final String TAG_ATTEMPTS = "Attempts";
    private static final String TAG_SPAWNED = "Spawned";
    private static final String TAG_HOME_DIMENSION = "HomeDimension";
    private static final String TAG_HOME_POS = "HomePos";
    private static final String TAG_HOME_X = "X";
    private static final String TAG_HOME_Y = "Y";
    private static final String TAG_HOME_Z = "Z";

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
    /**
     * When set, the cage replaces itself with this block once its opening animation has played, instead of
     * summoning {@code entityTypeId}.
     *
     * <p>Empty for the normal summon path. Stored as an id rather than a {@code Block} so a cage written by
     * one mod set still loads after the target mod is removed; resolution happens at activation and again
     * when the swap is due.
     */
    private String transformBlockId = "";

    /**
     * NBT written to the transformed block's block entity, with the same effect as the trailing tag of a
     * {@code /setblock} command. Empty by default, which leaves the block entity untouched.
     */
    private CompoundTag transformNbt = new CompoundTag();

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

    /**
     * Original home anchor this cage was created from, in home mode. {@code null} for death-mode cages and
     * for cages placed by older versions, which keep their previous behaviour.
     *
     * <p>Kept here (not read from the third-party entity, and not re-read from the database) so the
     * respawn chain survives independently of the death entity's record.
     */
    @Nullable
    private ResourceKey<Level> homeDimension;
    @Nullable
    private BlockPos homePos;

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
        if (be.litTicks < be.delayTicks) {
            return;
        }

        // Hand-off mode: the opening animation has played, so the cage can become the target block. The
        // difficulty gate was applied at activation and must not be re-checked here, or a difficulty change
        // mid-animation would leave the cage lit forever.
        if (!be.transformBlockId.isEmpty()) {
            be.finishTransform((ServerLevel) level, pos);
            return;
        }

        if (level.getDifficulty() == Difficulty.PEACEFUL && !be.allowPeaceful) {
            return;
        }
        if (be.requirePlayerNearby && !be.anyPlayerInRange(level)) {
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

    /**
     * Whether the stack is this cage's key item at all.
     *
     * <p>Deliberately says nothing about how many are required: {@code activation.amount} is a total across
     * the whole inventory, so one stack can never answer that on its own. See
     * {@link com.zonlong.bossrespawner.activation.KeyItemPayment}.
     */
    public boolean matchesKeyItem(ItemStack stack) {
        Item item = getCachedKeyItem();
        return item != null && stack.is(item);
    }

    public int getKeyAmount() {
        return keyAmount;
    }

    public boolean shouldConsume() {
        return consumeKeyItem;
    }

    /**
     * Starts the revival. Called on the server only, and only after the key items have been verified.
     *
     * <p>Both modes do the same thing here: light up and let the opening animation play. The difference
     * comes later, when the countdown finishes — {@link #finishTransform} instead of {@link #trySpawn}.
     *
     * @return {@code true} when the cage acted. {@code false} means nothing happened and the caller must
     *         <em>not</em> consume the key items
     */
    public boolean activate(Level level) {
        if (!level.getBlockState(worldPosition).hasProperty(BossRespawnerBlock.LIT)) {
            return false;
        }
        // Refuse before lighting up, so the player is not charged for a hand-off that can never happen.
        if (!transformBlockId.isEmpty() && !canTransform(level)) {
            return false;
        }

        DebugLog.info("Activating respawner at {}: mode={} entity={} key={} delay={} maxAttempts={}",
                worldPosition, transformBlockId.isEmpty() ? "summon" : "transform",
                entityTypeId, keyItemId, delayTicks, maxAttempts);
        level.setBlock(worldPosition, level.getBlockState(worldPosition).setValue(BossRespawnerBlock.LIT, true), 2);
        litTicks = 0;
        attempts = 0;
        setChanged();
        level.blockEvent(worldPosition, level.getBlockState(worldPosition).getBlock(), 1, 0);
        return true;
    }

    /**
     * Whether a hand-off can happen at all: the target block must be installed, and Peaceful must be
     * permitted.
     *
     * <p>A boss spawner typically refuses to fire on Peaceful, and Twilight Forest's are unbreakable
     * ({@code strength(-1.0F, ...)}), so transforming there would strand a block the player cannot remove.
     * {@code allow_peaceful} — the same opt-in the summon path uses — gates it.
     */
    private boolean canTransform(Level level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL && !allowPeaceful) {
            DebugLog.info("Respawner at {} will not transform: Peaceful difficulty and allow_peaceful is false",
                    worldPosition);
            return false;
        }
        if (resolveTransformBlock() == null) {
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} is configured to transform into block '{}', which is not installed; "
                            + "leaving the cage in place",
                    worldPosition, transformBlockId);
            return false;
        }
        return true;
    }

    /** @return the resolved target block, or {@code null} when the id is malformed or not installed */
    @Nullable
    private Block resolveTransformBlock() {
        ResourceLocation id = ResourceLocation.tryParse(transformBlockId);
        if (id == null) {
            return null;
        }
        // get() answers the registry default (air) for an unknown id, so air means "not installed".
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? null : block;
    }

    /**
     * Performs the hand-off, once the opening animation has played.
     *
     * @return {@code true} when the cage was replaced
     */
    private boolean finishTransform(ServerLevel level, BlockPos pos) {
        Block block = resolveTransformBlock();
        if (block == null) {
            // The rule changed under us, or the target mod went away. Reset rather than leave the cage lit
            // forever; the key items are not refunded, because the rule itself is what is broken.
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} could not transform: block '{}' is unavailable; resetting the cage",
                    pos, transformBlockId);
            resetToUnlit(level, pos);
            return false;
        }

        DebugLog.info("Transforming respawner at {} into {} for entity={}", pos, block, entityTypeId);
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);

        if (!transformNbt.isEmpty()) {
            BlockEntity target = level.getBlockEntity(pos);
            if (target == null) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawner at {} transformed into {}, which has no block entity; ignoring nbt",
                        pos, block);
            } else {
                // The trailing tag of a /setblock command, in effect.
                target.loadWithComponents(transformNbt, level.registryAccess());
                target.setChanged();
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        }
        return true;
    }

    /** Returns the cage to its unlit, reusable state without touching whatever else it was holding. */
    private void resetToUnlit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BossRespawnerBlock.LIT)) {
            level.setBlock(pos, state.setValue(BossRespawnerBlock.LIT, false), 2);
        }
        litTicks = 0;
        attempts = 0;
        spawned = false;
        setChanged();
    }

    /**
     * Switches this cage to hand-off mode: stay lit for {@code delayTicks} so the opening animation plays,
     * then become {@code block} and write {@code nbt} to that block's block entity.
     */
    public void setTransform(ResourceLocation block, CompoundTag nbt, int delayTicks, boolean allowPeaceful) {
        this.transformBlockId = block == null ? "" : block.toString();
        this.transformNbt = nbt == null ? new CompoundTag() : nbt.copy();
        this.delayTicks = Math.max(0, delayTicks);
        this.allowPeaceful = allowPeaceful;
        setChanged();
    }

    public String getTransformBlockId() {
        return transformBlockId;
    }

    public CompoundTag getTransformNbt() {
        return transformNbt;
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
        DebugLog.info("trySpawn: entityId={} type={} pos={} spawnPos={} count={} finalizeSpawn={} nbtEmpty={} homeAnchor={}",
                entityTypeId, type, pos, spawnPos, count, finalizeSpawn, spawnNbt.isEmpty(), hasHomeAnchor());

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
                // Order matters: Entity.load() reads the "Pos" list without a contains() guard, and
                // ListTag.getDouble() returns 0.0 for a missing element. So loading NBT that has no "Pos"
                // tag teleports the entity to (0, 0, 0). Applying the NBT first and the position second
                // keeps the cage position authoritative, which is what spawn_offset is for.
                if (!spawnNbt.isEmpty()) {
                    entity.load(spawnNbt);
                }
                entity.setPos(spawnPos);
                if (entity instanceof Mob mob && finalizeSpawn) {
                    mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
                }
                // Bind the new UUID to the original home *before* the entity joins the level. The
                // EntityJoinLevelEvent hook registers on a "first observation wins" basis, so writing the
                // anchor first means the spawn position can never be mistaken for the home.
                bindHomeAnchor(serverLevel, entity);
                created.add(entity);
                DebugLog.info("trySpawn created entity #{} of type {} for {}", i, entity.getType(), entityTypeId);
            }

            for (Entity entity : created) {
                if (!serverLevel.addFreshEntity(entity)) {
                    DebugLog.info("trySpawn failed: addFreshEntity rejected entity {} for {}", entity, entityTypeId);
                    discardAll(created);
                    // The provisional registrations must not survive a failed spawn.
                    releaseHomeAnchors(serverLevel, created);
                    return false;
                }
            }
            DebugLog.info("trySpawn added {} fresh entities for {}", created.size(), entityTypeId);
            return true;
        } catch (Exception e) {
            discardAll(created);
            releaseHomeAnchors(serverLevel, created);
            UniversalBossRespawner.LOGGER.warn("Failed to spawn {} from respawner at {}", entityTypeId, pos, e);
            DebugLog.info("trySpawn caught exception for {} at {}: {}", entityTypeId, pos, e.toString());
            return false;
        }
    }

    /** True when this cage carries a home anchor, i.e. it was created from a home-mode rule. */
    public boolean hasHomeAnchor() {
        return homeDimension != null && homePos != null;
    }

    @Nullable
    public ResourceKey<Level> getHomeDimension() {
        return homeDimension;
    }

    @Nullable
    public BlockPos getHomePos() {
        return homePos;
    }

    /**
     * Records the home anchor this cage should hand down to the entities it spawns.
     *
     * @param dimension dimension of the original observation
     * @param homePos   the raw observation position, without any JSON offset applied
     */
    public void setHomeAnchor(ResourceKey<Level> dimension, BlockPos homePos) {
        this.homeDimension = dimension;
        this.homePos = homePos.immutable();
        setChanged();
        DebugLog.info("Cage at {} now carries home anchor {} in {}", worldPosition, homePos, dimension.location());
    }

    /** Registers {@code entity}'s UUID against this cage's home anchor, when it has one. */
    private void bindHomeAnchor(ServerLevel serverLevel, Entity entity) {
        if (!hasHomeAnchor()) {
            return;
        }
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) {
            return;
        }
        BossHomeSavedData data = BossHomeSavedData.get(serverLevel.getServer());
        data.registerOrReplace(new HomeRecord(entity.getUUID(), typeId, homeDimension, homePos));
        DebugLog.info("Bound respawned entity uuid={} to home {} in {}",
                entity.getUUID(), homePos, homeDimension.location());
    }

    /** Undoes {@link #bindHomeAnchor} for a batch whose {@code addFreshEntity} failed. */
    private void releaseHomeAnchors(ServerLevel serverLevel, List<Entity> entities) {
        if (!hasHomeAnchor() || entities.isEmpty()) {
            return;
        }
        BossHomeSavedData data = BossHomeSavedData.get(serverLevel.getServer());
        for (Entity entity : entities) {
            if (entity != null && data.remove(entity.getUUID())) {
                DebugLog.info("Cleaned up provisional home record for uuid={} after a failed spawn",
                        entity.getUUID());
            }
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

    /**
     * NeoForge client hook: enables vanilla outline post-processing for this BlockEntity.
     * The actual outline geometry is submitted by {@link com.zonlong.bossrespawner.client.render.BossRespawnerBlockEntityRenderer}
     * through Minecraft's {@link net.minecraft.client.renderer.OutlineBufferSource}.
     */
    @Override
    public boolean hasCustomOutlineRendering(Player player) {
        if (!Config.HIGHLIGHT_BOSS_RESPAWNER.getAsBoolean()) {
            return false;
        }
        int range = Config.HIGHLIGHT_BOSS_RESPAWNER_RANGE.get();
        return player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= (double) range * range;
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
            this.transformBlockId = rule.getString("TransformBlock");
            this.transformNbt = rule.contains("TransformNbt", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    ? rule.getCompound("TransformNbt").copy()
                    : new CompoundTag();
        }

        this.litTicks = tag.getInt(TAG_LIT_TICKS);
        this.attempts = tag.getInt(TAG_ATTEMPTS);
        this.spawned = tag.getBoolean(TAG_SPAWNED);

        // Optional: only home-mode cages written by this version carry an anchor.
        this.homeDimension = null;
        this.homePos = null;
        if (tag.contains(TAG_HOME_DIMENSION, Tag.TAG_STRING) && tag.contains(TAG_HOME_POS, Tag.TAG_COMPOUND)) {
            try {
                CompoundTag homeTag = tag.getCompound(TAG_HOME_POS);
                this.homeDimension = ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(tag.getString(TAG_HOME_DIMENSION)));
                this.homePos = new BlockPos(
                        homeTag.getInt(TAG_HOME_X), homeTag.getInt(TAG_HOME_Y), homeTag.getInt(TAG_HOME_Z));
            } catch (Exception e) {
                this.homeDimension = null;
                this.homePos = null;
                UniversalBossRespawner.LOGGER.warn(
                        "Ignoring malformed home anchor on respawner at {}: {}", worldPosition, e.toString());
            }
        }

        invalidateCaches();
        DebugLog.info("Loaded respawner data at {}: entity={} key={} litTicks={} attempts={} spawned={} homeAnchor={}",
                worldPosition, this.entityTypeId, this.keyItemId, this.litTicks, this.attempts, this.spawned,
                hasHomeAnchor());
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
        if (!transformBlockId.isEmpty()) {
            rule.putString("TransformBlock", transformBlockId);
            if (!transformNbt.isEmpty()) {
                rule.put("TransformNbt", transformNbt.copy());
            }
        }
        tag.put(TAG_SPAWN_RULE, rule);

        tag.putInt(TAG_LIT_TICKS, litTicks);
        tag.putInt(TAG_ATTEMPTS, attempts);
        tag.putBoolean(TAG_SPAWNED, spawned);

        // Home anchor, only for home-mode cages. Absent for death-mode cages and for cages written by
        // older versions, which then keep their previous behaviour.
        if (hasHomeAnchor()) {
            tag.putString(TAG_HOME_DIMENSION, homeDimension.location().toString());
            CompoundTag homeTag = new CompoundTag();
            homeTag.putInt(TAG_HOME_X, homePos.getX());
            homeTag.putInt(TAG_HOME_Y, homePos.getY());
            homeTag.putInt(TAG_HOME_Z, homePos.getZ());
            tag.put(TAG_HOME_POS, homeTag);
        }
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
