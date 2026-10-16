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
        tag.putInt("PlaceDelayTicks", rule.placeDelayTicks());
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
                parseIds(BossHomeNbt.getStringList(tag, "ProtectedBlocks")),
                // Absent in snapshots written before schema v3, where no delay existed.
                tag.contains("PlaceDelayTicks", Tag.TAG_INT) ? tag.getInt("PlaceDelayTicks") : 0);
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
        tag.putString("Mode", rule.mode().name());
        if (rule.summon() != null) {
            RespawnEntry.SummonRule summon = rule.summon();
            CompoundTag s = new CompoundTag();
            s.putInt("DelayTicks", summon.delayTicks());
            s.putBoolean("RequirePlayerNearby", summon.requirePlayerNearby());
            s.putDouble("PlayerRange", summon.playerRange());
            s.putBoolean("AllowPeaceful", summon.allowPeaceful());
            s.putInt("Count", summon.count());
            s.putIntArray("SpawnOffset", summon.spawnOffset());
            s.putString("Nbt", summon.nbt());
            s.putBoolean("FinalizeSpawn", summon.finalizeSpawn());
            s.putInt("MaxAttempts", summon.maxAttempts());
            s.putInt("RetryIntervalTicks", summon.retryIntervalTicks());
            tag.put("Summon", s);
        }
        if (rule.transform() != null) {
            RespawnEntry.TransformRule transform = rule.transform();
            CompoundTag t = new CompoundTag();
            t.putString("Block", transform.block().toString());
            t.putInt("DelayTicks", transform.delayTicks());
            t.putBoolean("AllowPeaceful", transform.allowPeaceful());
            t.putString("Nbt", transform.nbt());
            tag.put("Transform", t);
        }
        return tag;
    }

    private static RespawnEntry.SpawnRule spawnFromTag(CompoundTag tag) {
        RespawnEntry.SpawnMode mode;
        try {
            mode = RespawnEntry.SpawnMode.valueOf(BossHomeNbt.requireString(tag, "Mode"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown spawn mode in rule snapshot: " + e.getMessage(), e);
        }

        if (mode == RespawnEntry.SpawnMode.TRANSFORM) {
            CompoundTag t = requireCompound(tag, "Transform");
            return new RespawnEntry.SpawnRule(mode, null, new RespawnEntry.TransformRule(
                    BossHomeNbt.getResourceLocation(t, "Block"),
                    t.getInt("DelayTicks"),
                    t.getBoolean("AllowPeaceful"),
                    t.getString("Nbt")));
        }

        CompoundTag s = requireCompound(tag, "Summon");
        return new RespawnEntry.SpawnRule(mode, new RespawnEntry.SummonRule(
                BossHomeNbt.requireInt(s, "DelayTicks"),
                s.getBoolean("RequirePlayerNearby"),
                s.getDouble("PlayerRange"),
                s.getBoolean("AllowPeaceful"),
                BossHomeNbt.requireInt(s, "Count"),
                BossHomeNbt.getIntArray3(s, "SpawnOffset"),
                s.getString("Nbt"),
                s.getBoolean("FinalizeSpawn"),
                BossHomeNbt.requireInt(s, "MaxAttempts"),
                BossHomeNbt.requireInt(s, "RetryIntervalTicks")), null);
    }

    /** A snapshot with a missing mode object is unusable, so the whole task is reported as malformed. */
    private static CompoundTag requireCompound(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("spawn snapshot is missing '" + key + "'");
        }
        return tag.getCompound(key);
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
