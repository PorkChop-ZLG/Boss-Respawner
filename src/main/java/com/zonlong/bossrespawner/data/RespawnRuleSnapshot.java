package com.zonlong.bossrespawner.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of the rule fields a deferred placement needs in order to re-run the full
 * placement pipeline later.
 *
 * <p>A snapshot — rather than the entry id — is stored because datapacks can change via {@code /reload}
 * or a server restart before the deferred task runs. The task must behave exactly as it would have at
 * the moment of death.
 *
 * <p>{@link RespawnEntry.PlacementRule#mode()} is deliberately part of the snapshot: re-running the
 * pipeline must keep using the same anchor mode.
 */
public record RespawnRuleSnapshot(
        RespawnEntry.ActivationRule activation,
        RespawnEntry.PlacementRule placement,
        RespawnEntry.SpawnRule spawn,
        RespawnEntry.DuplicateRule duplicate) {

    private static final String TAG_ACTIVATION = "Activation";
    private static final String TAG_PLACEMENT = "Placement";
    private static final String TAG_SPAWN = "Spawn";
    private static final String TAG_DUPLICATE = "Duplicate";

    /** Captures the fields of {@code entry} that placement and cage setup depend on. */
    public static RespawnRuleSnapshot of(RespawnEntry entry) {
        return new RespawnRuleSnapshot(
                entry.activation(), entry.placement(), entry.spawn(), entry.duplicate());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_ACTIVATION, activationToTag(activation));
        tag.put(TAG_PLACEMENT, placementToTag(placement));
        tag.put(TAG_SPAWN, spawnToTag(spawn));
        tag.put(TAG_DUPLICATE, duplicateToTag(duplicate));
        return tag;
    }

    public static RespawnRuleSnapshot fromTag(CompoundTag tag) {
        if (!tag.contains(TAG_ACTIVATION, Tag.TAG_COMPOUND)
                || !tag.contains(TAG_PLACEMENT, Tag.TAG_COMPOUND)
                || !tag.contains(TAG_SPAWN, Tag.TAG_COMPOUND)
                || !tag.contains(TAG_DUPLICATE, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("rule snapshot is missing one of its four sub-objects");
        }
        return new RespawnRuleSnapshot(
                activationFromTag(tag.getCompound(TAG_ACTIVATION)),
                placementFromTag(tag.getCompound(TAG_PLACEMENT)),
                spawnFromTag(tag.getCompound(TAG_SPAWN)),
                duplicateFromTag(tag.getCompound(TAG_DUPLICATE)));
    }

    // ------------------------------------------------------------------ activation

    private static CompoundTag activationToTag(RespawnEntry.ActivationRule rule) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Item", rule.item().toString());
        tag.putInt("Amount", rule.amount());
        tag.putBoolean("Consume", rule.consume());
        return tag;
    }

    private static RespawnEntry.ActivationRule activationFromTag(CompoundTag tag) {
        return new RespawnEntry.ActivationRule(
                BossHomeNbt.getResourceLocation(tag, "Item"),
                Math.max(1, BossHomeNbt.requireInt(tag, "Amount")),
                tag.getBoolean("Consume"));
    }

    // ------------------------------------------------------------------ placement

    private static CompoundTag placementToTag(RespawnEntry.PlacementRule rule) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", rule.mode().name());
        tag.putIntArray("Offset", rule.offset());
        tag.putBoolean("ForcePlace", rule.forcePlace());
        tag.putBoolean("SearchFailureFallback", rule.searchFailureFallback());
        tag.putInt("SearchDown", rule.searchDown());
        tag.putInt("SearchUp", rule.searchUp());
        tag.putInt("HorizontalRadius", rule.horizontalRadius());
        tag.putBoolean("RequireGround", rule.requireGround());
        tag.putBoolean("AvoidFluids", rule.avoidFluids());
        BossHomeNbt.putStringList(tag, "AvoidBlocks", toIdStrings(rule.avoidBlocks()));
        BossHomeNbt.putStringList(tag, "ProtectedBlocks", toIdStrings(rule.protectedBlocks()));
        return tag;
    }

    private static RespawnEntry.PlacementRule placementFromTag(CompoundTag tag) {
        RespawnEntry.PlacementMode mode;
        try {
            mode = RespawnEntry.PlacementMode.valueOf(BossHomeNbt.requireString(tag, "Mode"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown placement mode in rule snapshot: " + e.getMessage(), e);
        }
        return new RespawnEntry.PlacementRule(
                mode,
                BossHomeNbt.getIntArray3(tag, "Offset"),
                tag.getBoolean("ForcePlace"),
                tag.getBoolean("SearchFailureFallback"),
                BossHomeNbt.requireInt(tag, "SearchDown"),
                BossHomeNbt.requireInt(tag, "SearchUp"),
                BossHomeNbt.requireInt(tag, "HorizontalRadius"),
                tag.getBoolean("RequireGround"),
                tag.getBoolean("AvoidFluids"),
                parseIds(BossHomeNbt.getStringList(tag, "AvoidBlocks")),
                parseIds(BossHomeNbt.getStringList(tag, "ProtectedBlocks")));
    }

    /** Serialises a parsed block-id set back to the plain string list used on disk. */
    private static List<String> toIdStrings(Set<ResourceLocation> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            out.add(id.toString());
        }
        return out;
    }

    /** Re-parses stored block ids into the same {@link Set} form the rules use at runtime. */
    private static Set<ResourceLocation> parseIds(List<String> raw) {
        if (raw.isEmpty()) {
            return Set.of();
        }
        Set<ResourceLocation> ids = new HashSet<>(raw.size());
        for (String value : raw) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) {
                ids.add(id);
            }
        }
        return Set.copyOf(ids);
    }

    // ------------------------------------------------------------------ spawn

    private static CompoundTag spawnToTag(RespawnEntry.SpawnRule rule) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DelayTicks", rule.delayTicks());
        tag.putBoolean("RequirePlayerNearby", rule.requirePlayerNearby());
        tag.putDouble("PlayerRange", rule.playerRange());
        tag.putBoolean("AllowPeaceful", rule.allowPeaceful());
        tag.putInt("Count", rule.count());
        tag.putIntArray("SpawnOffset", rule.spawnOffset());
        tag.putString("Nbt", rule.nbt());
        tag.putBoolean("FinalizeSpawn", rule.finalizeSpawn());
        tag.putInt("MaxAttempts", rule.maxAttempts());
        tag.putInt("RetryIntervalTicks", rule.retryIntervalTicks());
        return tag;
    }

    private static RespawnEntry.SpawnRule spawnFromTag(CompoundTag tag) {
        return new RespawnEntry.SpawnRule(
                BossHomeNbt.requireInt(tag, "DelayTicks"),
                tag.getBoolean("RequirePlayerNearby"),
                tag.getDouble("PlayerRange"),
                tag.getBoolean("AllowPeaceful"),
                BossHomeNbt.requireInt(tag, "Count"),
                BossHomeNbt.getIntArray3(tag, "SpawnOffset"),
                tag.getString("Nbt"),
                tag.getBoolean("FinalizeSpawn"),
                BossHomeNbt.requireInt(tag, "MaxAttempts"),
                BossHomeNbt.requireInt(tag, "RetryIntervalTicks"));
    }

    // ------------------------------------------------------------------ duplicate

    private static CompoundTag duplicateToTag(RespawnEntry.DuplicateRule rule) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", rule.mode());
        tag.putInt("SearchRadius", rule.searchRadius());
        return tag;
    }

    private static RespawnEntry.DuplicateRule duplicateFromTag(CompoundTag tag) {
        return new RespawnEntry.DuplicateRule(
                BossHomeNbt.requireString(tag, "Mode"),
                BossHomeNbt.requireInt(tag, "SearchRadius"));
    }
}
