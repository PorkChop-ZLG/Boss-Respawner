package com.zonlong.bossrespawner.blockentity;

import com.zonlong.bossrespawner.Config;
import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.UniversalBossRespawner;
import com.zonlong.bossrespawner.block.BossRespawnerBlock;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.HomeRecord;
import com.zonlong.bossrespawner.data.RespawnEntry;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

public class BossRespawnerBlockEntity extends BlockEntity {
    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_KEY_ITEM_ID = "KeyItemId";
    private static final String TAG_KEY_AMOUNT = "KeyAmount";
    private static final String TAG_CONSUME_KEY_ITEM = "ConsumeKeyItem";
    private static final String TAG_SPAWN_RULE = "SpawnRule";
    private static final String TAG_LIT_TICKS = "LitTicks";
    private static final String TAG_ATTEMPTS = "Attempts";
    private static final String TAG_RETRY_COOLDOWN = "RetryCooldown";
    private static final String TAG_SPAWNED = "Spawned";
    private static final String TAG_HOME_DIMENSION = "HomeDimension";
    private static final String TAG_HOME_POS = "HomePos";
    private static final String TAG_HOME_X = "X";
    private static final String TAG_HOME_Y = "Y";
    private static final String TAG_HOME_Z = "Z";
    private static final String TAG_PAYMENT_PLAYER = "PaymentPlayer";
    private static final String TAG_PAYMENT_ITEM = "PaymentItem";
    private static final String TAG_PAYMENT_AMOUNT = "PaymentAmount";

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
     * When true, the target block is placed at this cage's stored home anchor instead of at the cage's own
     * position, and the cage itself is removed.
     *
     * <p>Twilight Forest needs this: the cage sits beside the boss's home so the death chest keeps its open
     * top, while the native boss spawner must occupy the home itself. Placing the spawner at the cage would
     * move the boss's home one block per respawn cycle.
     */
    private boolean transformAtAnchor = false;

    /**
     * Offset of the target block from the home anchor, applied only when {@link #transformAtAnchor} is set.
     *
     * <p>Default zero keeps the old behaviour. Twilight Forest's Lich spawner needs {@code [0, 1, 0]}:
     * that spawner descends to the lowest air block and then unconditionally moves up one, so placing it
     * directly on the Lich's home would move the home up by one block every respawn cycle.
     */
    private int[] spawnerOffset = new int[]{0, 0, 0};

    /**
     * When true, a chest at the target (or anchor) cell is replaced even if it still has loot. Only
     * Twilight Forest's Lich and Minoshroom use this: their spawner must occupy the reward-chest cell,
     * and the chest is intentionally destroyed to keep the boss's home from drifting.
     */
    private boolean transformForcePlace = false;

    /** Why the most recent {@link #activate(Level)} refused, so the block can give feedback. */
    public enum TransformFailure {
        NONE,
        PEACEFUL,
        BLOCK_MISSING,
        NO_ANCHOR,
        ANCHOR_NOT_LOADED,
        ANCHOR_OCCUPIED,
        TARGET_NOT_LOADED,
        TARGET_OCCUPIED,
        MISSING_ENTITY,
        MISSING_KEY,
        SET_BLOCK_FAILED,
        WRONG_DIMENSION
    }

    private TransformFailure lastTransformFailure = TransformFailure.NONE;

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
    private int retryCooldown = 0;
    private boolean spawned = false;

    /**
     * The key items removed when this cage was activated, so a later failure can refund them instead of
     * silently keeping the player's payment. Persisted because the cage can be unloaded or the server can
     * restart between activation and retry exhaustion.
     */
    @Nullable
    private UUID paymentPlayer;
    private String paymentItemId = "";
    private int paymentAmount;

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

    /**
     * A cage's NBT controls which entity it revives, its transform target and its spawn NBT. Vanilla applies
     * an item's {@code block_entity_data} component for non-ops unless the block entity opts out; command
     * blocks and spawners opt out, and this cage must too.
     */
    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
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
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            // The master switch must stop existing lit cages too, not just new deaths. The litTicks state is
            // kept so re-enabling resumes exactly where the cage was.
            return;
        }

        be.litTicks++;
        if (be.retryCooldown > 0) {
            be.retryCooldown--;
            be.setChanged();
            return;
        }
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
            // The activation pre-check should have caught this, but the difficulty can change during the
            // countdown. Reset (and refund) rather than leaving the cage lit forever with the player's keys.
            be.resetToUnlit((ServerLevel) level, pos);
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
            be.clearPayment();
            DebugLog.info("Spawn succeeded from respawner at {}: entity={}; destroying cage", pos, be.entityTypeId);
            level.destroyBlock(pos, false);
        } else {
            be.attempts++;
            DebugLog.info("Spawn failed from respawner at {}: entity={} attempts={} maxAttempts={}",
                    pos, be.entityTypeId, be.attempts, be.maxAttempts);
            if (be.maxAttempts > 0 && be.attempts >= be.maxAttempts) {
                be.resetAfterMaxAttempts((ServerLevel) level);
            } else {
                // Keep the delay gate satisfied and wait an explicit cooldown. The old
                // `litTicks = delayTicks - retryIntervalTicks` formula collapsed to "retry every tick" when
                // retryIntervalTicks >= delayTicks (including the legal delay_ticks: 0).
                be.litTicks = be.delayTicks;
                be.retryCooldown = Math.max(1, be.retryIntervalTicks);
                be.setChanged();
                DebugLog.info("Scheduled retry for respawner at {}: next attempt in {} ticks",
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
     * Records what was just removed from the player's inventory. The record survives a reset/retry and is
     * refunded if the cage can no longer produce the boss.
     */
    public void recordPayment(Player player, Item item, int amount) {
        if (player == null || item == null || item == Items.AIR || amount <= 0) {
            clearPayment();
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            clearPayment();
            return;
        }
        this.paymentPlayer = player.getUUID();
        this.paymentItemId = itemId.toString();
        this.paymentAmount = Math.min(RespawnEntry.MAX_ACTIVATION_AMOUNT, amount);
        setChanged();
        DebugLog.info("Recorded payment for respawner at {}: player={} item={} amount={}",
                worldPosition, paymentPlayer, paymentItemId, paymentAmount);
    }

    public void clearPayment() {
        if (paymentPlayer == null && paymentAmount == 0 && paymentItemId.isEmpty()) {
            return;
        }
        paymentPlayer = null;
        paymentItemId = "";
        paymentAmount = 0;
        setChanged();
    }

    /**
     * Refunds a recorded payment when this cage is removed by {@code replace_existing}, before the new cage
     * is written. Safe to call when there is no payment.
     */
    public void refundPaymentOnRemoval(ServerLevel level) {
        if (paymentAmount > 0) {
            refundPayment(level, worldPosition);
        }
    }

    private void refundPayment(ServerLevel level, BlockPos pos) {
        UUID playerId = paymentPlayer;
        String itemId = paymentItemId;
        int amount = paymentAmount;
        clearPayment();
        if (playerId == null || itemId.isEmpty() || amount <= 0) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) {
            UniversalBossRespawner.LOGGER.warn(
                    "Cannot refund {} x {} at {}: item is no longer registered", amount, itemId, pos);
            return;
        }
        ItemStack refund = new ItemStack(item, amount);
        ServerPlayer player = level.getServer() == null
                ? null
                : level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            if (!player.getInventory().add(refund)) {
                player.drop(refund, false);
            }
            player.displayClientMessage(
                    Component.translatable("boss_respawner.message.key_refunded", amount, item.getDescription()), false);
            DebugLog.info("Refunded {} x {} to player {} at {}", amount, itemId, playerId, pos);
            return;
        }
        Block.popResource(level, pos, refund);
        DebugLog.info("Dropped refund of {} x {} at {} (player {} offline)",
                amount, itemId, pos, playerId);
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
        lastTransformFailure = TransformFailure.NONE;
        if (!Config.ENABLE_MOD.getAsBoolean()) {
            return false;
        }
        if (!level.getBlockState(worldPosition).hasProperty(BossRespawnerBlock.LIT)) {
            return false;
        }
        // Refuse before lighting up, so the player is not charged for a hand-off that can never happen.
        if (!transformBlockId.isEmpty() && !canTransform(level)) {
            return false;
        }
        if (transformBlockId.isEmpty()) {
            if (level.getDifficulty() == Difficulty.PEACEFUL && !allowPeaceful) {
                lastTransformFailure = TransformFailure.PEACEFUL;
                DebugLog.info("Respawner at {} will not summon: Peaceful difficulty and allow_peaceful is false",
                        worldPosition);
                return false;
            }
            if (getCachedEntityType() == null) {
                lastTransformFailure = TransformFailure.MISSING_ENTITY;
                UniversalBossRespawner.LOGGER.warn(
                        "Respawner at {} refers to unregistered entity '{}'; refusing activation",
                        worldPosition, entityTypeId);
                return false;
            }
            if (getCachedKeyItem() == null) {
                lastTransformFailure = TransformFailure.MISSING_KEY;
                UniversalBossRespawner.LOGGER.warn(
                        "Respawner at {} refers to unregistered key item '{}'; refusing activation",
                        worldPosition, keyItemId);
                return false;
            }
        }

        DebugLog.info("Activating respawner at {}: mode={} entity={} key={} delay={} maxAttempts={}",
                worldPosition, transformBlockId.isEmpty() ? "summon" : "transform",
                entityTypeId, keyItemId, delayTicks, maxAttempts);
        if (!level.setBlock(worldPosition,
                level.getBlockState(worldPosition).setValue(BossRespawnerBlock.LIT, true), 2)) {
            lastTransformFailure = TransformFailure.SET_BLOCK_FAILED;
            return false;
        }
        litTicks = 0;
        attempts = 0;
        retryCooldown = 0;
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
            lastTransformFailure = TransformFailure.PEACEFUL;
            DebugLog.info("Respawner at {} will not transform: Peaceful difficulty and allow_peaceful is false",
                    worldPosition);
            return false;
        }
        if (resolveTransformBlock() == null) {
            lastTransformFailure = TransformFailure.BLOCK_MISSING;
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} is configured to transform into block '{}', which is not installed; "
                            + "leaving the cage in place",
                    worldPosition, transformBlockId);
            return false;
        }
        if (!transformAtAnchor) {
            return true;
        }

        if (!hasHomeAnchor()) {
            lastTransformFailure = TransformFailure.NO_ANCHOR;
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} is configured to place its target block at the home anchor, but "
                            + "carries no anchor; leaving the cage in place",
                    worldPosition);
            return false;
        }
        if (!homeDimension.equals(level.dimension())) {
            lastTransformFailure = TransformFailure.WRONG_DIMENSION;
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} carries a home anchor in {} but is in {}; refusing to touch the wrong "
                            + "dimension's blocks",
                    worldPosition, homeDimension.location(), level.dimension().location());
            return false;
        }

        BlockPos target = transformTargetPos();
        if (!homePos.equals(worldPosition)) {
            if (!level.isLoaded(homePos)) {
                lastTransformFailure = TransformFailure.ANCHOR_NOT_LOADED;
                DebugLog.info("Respawner at {} will not transform yet: home anchor {} is not loaded",
                        worldPosition, homePos);
                return false;
            }
            if (!isReplaceableOrAllowedChest(level, homePos)) {
                lastTransformFailure = TransformFailure.ANCHOR_OCCUPIED;
                UniversalBossRespawner.LOGGER.warn(
                        "Respawner at {} cannot use the home anchor {} because that cell is occupied by "
                                + "{}; leave the cage in place and clear/loot it first",
                        worldPosition, homePos, level.getBlockState(homePos));
                return false;
            }
        }

        // The cage itself may be the target (a rule with zero/net offsets); it will be replaced in place.
        if (target.equals(worldPosition)) {
            return true;
        }

        if (!target.equals(homePos)) {
            if (!level.isLoaded(target)) {
                lastTransformFailure = TransformFailure.TARGET_NOT_LOADED;
                DebugLog.info("Respawner at {} will not transform yet: target {} is not loaded",
                        worldPosition, target);
                return false;
            }
            if (!isReplaceableOrAllowedChest(level, target)) {
                lastTransformFailure = TransformFailure.TARGET_OCCUPIED;
                UniversalBossRespawner.LOGGER.warn(
                        "Respawner at {} cannot place its target block at {} because that cell is occupied "
                                + "by {}; leave the cage in place and clear it first",
                        worldPosition, target, level.getBlockState(target));
                return false;
            }
        }
        return true;
    }

    /** Air, replaceable, or an empty chest (a Twilight Forest death chest that can be cleared). */
    private static boolean isReplaceableOrEmptyChest(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return true;
        }
        return state.getBlock() instanceof ChestBlock
                && level.getBlockEntity(pos) instanceof Container container
                && container.isEmpty();
    }

    /**
     * Like {@link #isReplaceableOrEmptyChest}, but a force-place transform may also replace a non-empty
     * chest. Non-chest blocks still block; the deliberate chest destruction is limited to the Lich and
     * Minoshroom spawner cells.
     */
    private boolean isReplaceableOrAllowedChest(Level level, BlockPos pos) {
        return isReplaceableOrEmptyChest(level, pos)
                || (transformForcePlace && level.getBlockState(pos).getBlock() instanceof ChestBlock);
    }

    /** The cell the transform target will occupy: the anchor plus {@link #spawnerOffset}. */
    private BlockPos transformTargetPos() {
        if (!transformAtAnchor || homePos == null) {
            return worldPosition;
        }
        return homePos.offset(spawnerOffset[0], spawnerOffset[1], spawnerOffset[2]);
    }

    public TransformFailure getLastTransformFailure() {
        return lastTransformFailure;
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
        if (transformAtAnchor && hasHomeAnchor() && !homeDimension.equals(level.dimension())) {
            lastTransformFailure = TransformFailure.WRONG_DIMENSION;
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} carries a home anchor in {} but is in {}; resetting without touching "
                            + "blocks in the wrong dimension",
                    pos, homeDimension.location(), level.dimension().location());
            resetToUnlit(level, pos);
            return false;
        }
        Block block = resolveTransformBlock();
        if (block == null) {
            // The rule changed under us, or the target mod went away. Reset rather than leave the cage lit
            // forever; resetToUnlit refunds the recorded key payment.
            lastTransformFailure = TransformFailure.BLOCK_MISSING;
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} could not transform: block '{}' is unavailable; resetting the cage",
                    pos, transformBlockId);
            resetToUnlit(level, pos);
            return false;
        }

        BlockPos targetPos = transformTargetPos();
        if (!targetPos.equals(pos)) {
            // The home cell must be clear even when the target is above it: Twilight Forest's Lich spawner
            // descends through the home cell and stops only on a non-air block below it.
            if (transformAtAnchor && hasHomeAnchor()
                    && !homePos.equals(pos) && !homePos.equals(targetPos)) {
                if (!level.isLoaded(homePos) || !isReplaceableOrAllowedChest(level, homePos)) {
                    lastTransformFailure = level.isLoaded(homePos)
                            ? TransformFailure.ANCHOR_OCCUPIED
                            : TransformFailure.ANCHOR_NOT_LOADED;
                    UniversalBossRespawner.LOGGER.warn(
                            "Respawner at {} could not transform: home anchor {} is not available; "
                                    + "resetting the cage", pos, homePos);
                    resetToUnlit(level, pos);
                    return false;
                }
                if (level.getBlockState(homePos).getBlock() instanceof ChestBlock) {
                    // The loot was already taken; clear the empty chest so the spawner can descend through.
                    level.setBlock(homePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            if (!level.isLoaded(targetPos) || !isReplaceableOrAllowedChest(level, targetPos)) {
                lastTransformFailure = level.isLoaded(targetPos)
                        ? TransformFailure.TARGET_OCCUPIED
                        : TransformFailure.TARGET_NOT_LOADED;
                UniversalBossRespawner.LOGGER.warn(
                        "Respawner at {} could not transform: target {} is not available; resetting the cage",
                        pos, targetPos);
                resetToUnlit(level, pos);
                return false;
            }
            if (level.getBlockState(targetPos).getBlock() instanceof ChestBlock) {
                // The loot was already taken; clear the empty chest so the spawner can take its cell.
                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            // The cage is a marker beside the anchor, not the spawner itself.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        DebugLog.info("Transforming respawner at {} into {} at {} for entity={}",
                pos, block, targetPos, entityTypeId);
        if (!level.setBlock(targetPos, block.defaultBlockState(), Block.UPDATE_ALL)) {
            lastTransformFailure = TransformFailure.SET_BLOCK_FAILED;
            UniversalBossRespawner.LOGGER.warn(
                    "Respawner at {} could not transform: setBlock({}) was rejected; resetting the cage",
                    pos, targetPos);
            resetToUnlit(level, pos);
            return false;
        }

        if (!transformNbt.isEmpty()) {
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawner at {} transformed into {}, which has no block entity; ignoring nbt",
                        targetPos, block);
            } else {
                try {
                    // The trailing tag of a /setblock command, in effect.
                    target.loadWithComponents(transformNbt, level.registryAccess());
                    target.setChanged();
                    BlockState state = level.getBlockState(targetPos);
                    level.sendBlockUpdated(targetPos, state, state, Block.UPDATE_ALL);
                } catch (Exception e) {
                    // The block is already in place; a malformed tag must not escape from the block-entity tick.
                    UniversalBossRespawner.LOGGER.warn(
                            "Failed to apply transform nbt at {}: {}", targetPos, e.toString());
                }
            }
        }

        clearPayment();
        return true;
    }

    /** Returns the cage to its unlit, reusable state without touching whatever else it was holding. */
    private void resetToUnlit(ServerLevel level, BlockPos pos) {
        refundPayment(level, pos);
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BossRespawnerBlock.LIT)) {
            level.setBlock(pos, state.setValue(BossRespawnerBlock.LIT, false), 2);
        }
        litTicks = 0;
        attempts = 0;
        retryCooldown = 0;
        spawned = false;
        setChanged();
    }

    /**
     * Records <em>what</em> this cage revives and what it costs, independently of how it revives it.
     *
     * <p>Both spawn modes need this. Without it the cage has no identity: the Jade tooltip has no boss name
     * and no key item to show, and {@code matchesKeyItem} can never match, so right-clicking does nothing
     * at all. {@link #setSpawnerData} sets the same fields for the summon path, so calling this first is
     * simply the shared half of the configuration.
     */
    public void setIdentity(String entityTypeId, String keyItemId, int keyAmount, boolean consumeKeyItem) {
        this.entityTypeId = entityTypeId == null ? "" : entityTypeId;
        this.keyItemId = keyItemId == null ? "" : keyItemId;
        this.keyAmount = Math.min(RespawnEntry.MAX_ACTIVATION_AMOUNT, Math.max(1, keyAmount));
        this.consumeKeyItem = consumeKeyItem;
        invalidateCaches();
        setChanged();
    }

    /**
     * Switches this cage to hand-off mode: stay lit for {@code delayTicks} so the opening animation plays,
     * then hand the boss over to {@code block}.
     *
     * @param atAnchor when true, the block is placed at this cage's stored home anchor and the cage itself
     *                 is removed; when false the cage is replaced in place
     */
    public void setTransform(ResourceLocation block, CompoundTag nbt, int delayTicks, boolean allowPeaceful,
                             int[] spawnerOffset, boolean forcePlace, boolean atAnchor) {
        this.transformBlockId = block == null ? "" : block.toString();
        this.transformNbt = nbt == null ? new CompoundTag() : nbt.copy();
        this.delayTicks = Math.min(RespawnEntry.MAX_DELAY_TICKS, Math.max(0, delayTicks));
        this.allowPeaceful = allowPeaceful;
        this.spawnerOffset = normalizeOffset(spawnerOffset);
        this.transformForcePlace = forcePlace;
        this.transformAtAnchor = atAnchor;
        setChanged();
    }

    public boolean isTransformAtAnchor() {
        return transformAtAnchor;
    }

    public boolean isTransformForcePlace() {
        return transformForcePlace;
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
                    // A new boss must get a fresh UUID. Copying a UUID from /data get entity would make
                    // bindHomeAnchor overwrite the real boss's home and addFreshEntity reject the duplicate.
                    CompoundTag toLoad = spawnNbt.copy();
                    toLoad.remove("UUID");
                    entity.load(toLoad);
                }
                entity.setPos(spawnPos);
                if (entity instanceof Mob mob) {
                    if (finalizeSpawn) {
                        // Fire FinalizeSpawnEvent instead of calling Mob.finalizeSpawn directly. Mods use
                        // this event for attributes, equipment, boss bars and spawn cancellation.
                        EventHooks.finalizeMobSpawn(mob, serverLevel,
                                serverLevel.getCurrentDifficultyAt(entity.blockPosition()),
                                MobSpawnType.SPAWNER, null);
                    }
                    // A respawned boss must not despawn while the player is away.
                    mob.setPersistenceRequired();
                }
                if (entity instanceof Mob mob && mob.isSpawnCancelled()) {
                    DebugLog.info("trySpawn aborted: {} cancelled by FinalizeSpawnEvent", entityTypeId);
                    discardAll(created);
                    releaseHomeAnchors(serverLevel, created);
                    return false;
                }
                // Bind the new UUID to the original home *before* the entity joins the level. The
                // EntityJoinLevelEvent hook registers on a "first observation wins" basis, so writing the
                // anchor first means the spawn position can never be mistaken for the home.
                bindHomeAnchor(serverLevel, entity);
                created.add(entity);
                DebugLog.info("trySpawn created entity #{} of type {} for {}", i, entity.getType(), entityTypeId);
            }

            for (Entity entity : created) {
                if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
                    DebugLog.info("trySpawn failed: tryAddFreshEntityWithPassengers rejected entity {} for {}",
                            entity, entityTypeId);
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
        if (!homeDimension.equals(serverLevel.dimension())) {
            UniversalBossRespawner.LOGGER.warn(
                    "Not binding spawned entity {} to home anchor {} in {}: cage is in {}",
                    entity.getUUID(), homePos, homeDimension.location(), serverLevel.dimension().location());
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
        refundPayment(level, worldPosition);
        attempts = 0;
        litTicks = 0;
        retryCooldown = 0;

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
        this.keyAmount = Math.min(RespawnEntry.MAX_ACTIVATION_AMOUNT, Math.max(1, keyAmount));
        this.consumeKeyItem = consumeKeyItem;
        this.spawnNbt = spawnNbt == null ? new CompoundTag() : spawnNbt.copy();
        this.delayTicks = Math.min(RespawnEntry.MAX_DELAY_TICKS, Math.max(0, delayTicks));
        this.requirePlayerNearby = requirePlayerNearby;
        this.playerRange = Math.max(1.0D, Math.min(RespawnEntry.MAX_PLAYER_RANGE, playerRange));
        this.allowPeaceful = allowPeaceful;
        this.count = Math.min(RespawnEntry.MAX_COUNT, Math.max(1, count));
        this.spawnOffset = normalizeOffset(spawnOffset);
        this.finalizeSpawn = finalizeSpawn;
        this.maxAttempts = normalizeMaxAttempts(maxAttempts);
        this.retryIntervalTicks = Math.min(RespawnEntry.MAX_RETRY_INTERVAL_TICKS,
                Math.max(1, retryIntervalTicks));
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
                // DefaultedRegistry#get returns PIG for unknown ids; a boss cage must not silently spawn a pig.
                cachedEntityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityLocation).orElse(null);
            }
        }
        return cachedEntityType;
    }

    public Item getCachedKeyItem() {
        if (cachedItem == null && !keyItemId.isEmpty()) {
            itemLocation = parseLocation(keyItemId);
            if (itemLocation != null) {
                // Same trap as the entity registry: get() would return AIR for an unknown item and
                // matchesKeyItem(EMPTY) would then be true.
                cachedItem = BuiltInRegistries.ITEM.getOptional(itemLocation).orElse(null);
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
        this.keyAmount = Math.min(RespawnEntry.MAX_ACTIVATION_AMOUNT,
                Math.max(1, tag.getInt(TAG_KEY_AMOUNT)));
        if (this.keyAmount <= 0) {
            this.keyAmount = 1;
        }
        this.consumeKeyItem = !tag.contains(TAG_CONSUME_KEY_ITEM) || tag.getBoolean(TAG_CONSUME_KEY_ITEM);

        if (tag.contains(TAG_SPAWN_RULE, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag rule = tag.getCompound(TAG_SPAWN_RULE);
            this.delayTicks = Math.min(RespawnEntry.MAX_DELAY_TICKS, Math.max(0, rule.getInt("DelayTicks")));
            this.requirePlayerNearby = rule.getBoolean("RequirePlayerNearby");
            this.playerRange = Math.max(1.0D,
                    Math.min(RespawnEntry.MAX_PLAYER_RANGE, rule.getDouble("PlayerRange")));
            this.allowPeaceful = rule.getBoolean("AllowPeaceful");
            this.count = Math.min(RespawnEntry.MAX_COUNT, Math.max(1, rule.getInt("Count")));
            if (rule.contains("SpawnOffset", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                this.spawnOffset = normalizeOffset(rule.getIntArray("SpawnOffset"));
            }
            this.finalizeSpawn = rule.getBoolean("FinalizeSpawn");
            this.maxAttempts = normalizeMaxAttempts(rule.getInt("MaxAttempts"));
            this.retryIntervalTicks = Math.min(RespawnEntry.MAX_RETRY_INTERVAL_TICKS,
                    Math.max(1, rule.getInt("RetryIntervalTicks")));
            if (rule.contains("SpawnNbt", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                this.spawnNbt = rule.getCompound("SpawnNbt").copy();
            }
            this.transformBlockId = rule.getString("TransformBlock");
            this.transformNbt = rule.contains("TransformNbt", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    ? rule.getCompound("TransformNbt").copy()
                    : new CompoundTag();
            this.transformAtAnchor = rule.getBoolean("AtAnchor");
            this.spawnerOffset = rule.contains("SpawnerOffset", net.minecraft.nbt.Tag.TAG_INT_ARRAY)
                    ? normalizeOffset(rule.getIntArray("SpawnerOffset"))
                    : new int[]{0, 0, 0};
            this.transformForcePlace = rule.getBoolean("ForcePlace");
        }

        this.litTicks = Math.max(0, tag.getInt(TAG_LIT_TICKS));
        this.attempts = Math.max(0, tag.getInt(TAG_ATTEMPTS));
        this.retryCooldown = Math.min(RespawnEntry.MAX_RETRY_INTERVAL_TICKS,
                Math.max(0, tag.getInt(TAG_RETRY_COOLDOWN)));
        this.spawned = tag.getBoolean(TAG_SPAWNED);

        this.paymentPlayer = tag.hasUUID(TAG_PAYMENT_PLAYER) ? tag.getUUID(TAG_PAYMENT_PLAYER) : null;
        this.paymentItemId = tag.getString(TAG_PAYMENT_ITEM);
        this.paymentAmount = Math.min(RespawnEntry.MAX_ACTIVATION_AMOUNT,
                Math.max(0, tag.getInt(TAG_PAYMENT_AMOUNT)));
        if (paymentPlayer == null || paymentItemId.isEmpty() || paymentAmount <= 0) {
            paymentPlayer = null;
            paymentItemId = "";
            paymentAmount = 0;
        }

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
        if (level instanceof ServerLevel serverLevel) {
            ResourceLocation typeId = ResourceLocation.tryParse(entityTypeId);
            if (typeId != null) {
                // Existing cages in old saves become visible to the cross-chunk duplicate index as soon as
                // their chunk loads.
                BossHomeSavedData.get(serverLevel).addCage(
                        serverLevel.dimension().location(), typeId, worldPosition);
            }
        }
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
            rule.putBoolean("AtAnchor", transformAtAnchor);
            rule.putIntArray("SpawnerOffset", spawnerOffset);
            rule.putBoolean("ForcePlace", transformForcePlace);
            if (!transformNbt.isEmpty()) {
                rule.put("TransformNbt", transformNbt.copy());
            }
        }
        tag.put(TAG_SPAWN_RULE, rule);

        tag.putInt(TAG_LIT_TICKS, litTicks);
        tag.putInt(TAG_ATTEMPTS, attempts);
        tag.putInt(TAG_RETRY_COOLDOWN, retryCooldown);
        tag.putBoolean(TAG_SPAWNED, spawned);

        if (paymentPlayer != null && paymentAmount > 0 && !paymentItemId.isEmpty()) {
            tag.putUUID(TAG_PAYMENT_PLAYER, paymentPlayer);
            tag.putString(TAG_PAYMENT_ITEM, paymentItemId);
            tag.putInt(TAG_PAYMENT_AMOUNT, paymentAmount);
        }

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
        int limit = RespawnEntry.MAX_SPAWN_OFFSET;
        return new int[]{
                Math.max(-limit, Math.min(limit, offset[0])),
                Math.max(-limit, Math.min(limit, offset[1])),
                Math.max(-limit, Math.min(limit, offset[2]))
        };
    }

    private static int normalizeMaxAttempts(int value) {
        if (value < 0) {
            return -1;
        }
        if (value == 0) {
            return 20;
        }
        return Math.min(RespawnEntry.MAX_ATTEMPTS, value);
    }
}
