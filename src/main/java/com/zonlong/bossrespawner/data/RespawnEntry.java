package com.zonlong.bossrespawner.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One parsed {@code boss_respawner} JSON rule (schema v3).
 *
 * <pre>
 * data/&lt;namespace&gt;/boss_respawner/entries/&lt;any-name&gt;.json
 * </pre>
 *
 * <p>Schema v3 requires {@code schema_version}, {@code entity}, {@code activation.item},
 * {@code placement.mode} with its mode object ({@code placement.death} or {@code placement.home}), and
 * {@code spawn.mode} with its mode object ({@code spawn.summon} or {@code spawn.transform}). Everything
 * else uses code defaults.
 *
 * <p>Older layouts are rejected rather than silently reinterpreted, and the message names the real cause:
 * schema v1 put the placement fields directly under {@code placement}, and schema v2 put the spawn fields
 * directly under {@code spawn}.
 */
public record RespawnEntry(
        ResourceLocation id,
        boolean enabled,
        int priority,
        List<ResourceLocation> entities,
        ActivationRule activation,
        PlacementRule placement,
        DeathRule death,
        SpawnRule spawn,
        DuplicateRule duplicate) {

    /** The only supported {@code schema_version}. Any other value makes the entry be skipped. */
    public static final int SCHEMA_VERSION = 3;

    /**
     * Explains a schema version mismatch in terms of the layout that was actually used, so the fix is
     * obvious instead of looking like a typo.
     */
    private static String layoutHint(JsonObject root, int version) {
        if (version == 2) {
            return " - schema v2 put the spawn fields directly under \"spawn\"; v3 requires \"spawn.mode\" "
                    + "and nests them under \"spawn.summon\" or \"spawn.transform\". See README for the "
                    + "v2 -> v3 mapping";
        }
        if (version == 1) {
            return " - schema v1 put the placement fields directly under \"placement\"; see README for the "
                    + "v1 -> v3 mapping";
        }
        return "";
    }

    /** Selects which position the respawn cage is anchored to. */
    public enum PlacementMode {
        /** Anchor to the position where the entity died. */
        DEATH,
        /** Anchor to the registered home position stored in {@code BossHomeSavedData}. */
        HOME
    }

    public static Optional<RespawnEntry> fromJson(ResourceLocation id, JsonObject root) {
        try {
            boolean enabled = getBoolean(root, "enabled", true);
            if (!enabled) {
                return Optional.empty();
            }

            // schema_version must be present and numeric; a missing value is an error, not a default.
            JsonElement schemaElement = root.get("schema_version");
            if (schemaElement == null || !schemaElement.isJsonPrimitive()
                    || !schemaElement.getAsJsonPrimitive().isNumber()) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawn entry {} is missing a numeric schema_version (expected {}), skipping",
                        id, SCHEMA_VERSION);
                return Optional.empty();
            }
            int schemaVersion = schemaElement.getAsInt();
            if (schemaVersion != SCHEMA_VERSION) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawn entry {} uses unsupported schema_version {} (expected {}), skipping{}",
                        id, schemaVersion, SCHEMA_VERSION, layoutHint(root, schemaVersion));
                return Optional.empty();
            }

            int priority = getInt(root, "priority", 0);

            List<ResourceLocation> entities = parseEntities(root.get("entity"));
            if (entities.isEmpty()) {
                UniversalBossRespawner.LOGGER.warn("Respawn entry {} has no valid entity", id);
                return Optional.empty();
            }

            ActivationRule activation = ActivationRule.fromJson(getObject(root, "activation"));
            if (activation.item() == null) {
                UniversalBossRespawner.LOGGER.warn("Respawn entry {} has no activation.item", id);
                return Optional.empty();
            }

            Optional<PlacementRule> placement = PlacementRule.fromJson(id, getObject(root, "placement"));
            if (placement.isEmpty()) {
                return Optional.empty();
            }

            DeathRule death = DeathRule.fromJson(getObject(root, "death"));
            Optional<SpawnRule> spawn = SpawnRule.fromJson(id, getObject(root, "spawn"));
            if (spawn.isEmpty()) {
                return Optional.empty();
            }
            DuplicateRule duplicate = DuplicateRule.fromJson(getObject(root, "duplicate"));

            return Optional.of(new RespawnEntry(
                    id, true, priority, List.copyOf(entities), activation,
                    placement.get(), death, spawn.get(), duplicate));
        } catch (Exception e) {
            UniversalBossRespawner.LOGGER.warn("Failed to parse respawn entry {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    private static List<ResourceLocation> parseEntities(JsonElement element) {
        List<ResourceLocation> result = new ArrayList<>();
        if (element == null) {
            return result;
        }
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                parseEntity(e, result);
            }
        } else {
            parseEntity(element, result);
        }
        return result;
    }

    private static void parseEntity(JsonElement element, List<ResourceLocation> out) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return;
        }
        ResourceLocation loc = parseLocation(element.getAsString());
        if (loc != null && !out.contains(loc)) {
            out.add(loc);
        }
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
            return null;
        }
    }

    static JsonObject getObject(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : defaultValue;
    }

    static int getInt(JsonObject obj, String key, int defaultValue) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : defaultValue;
    }

    static double getDouble(JsonObject obj, String key, double defaultValue) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : defaultValue;
    }

    static String getString(JsonObject obj, String key, String defaultValue) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : defaultValue;
    }

    static List<String> getStringList(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        JsonElement element = obj.get(key);
        if (element == null) {
            return result;
        }
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                    result.add(e.getAsString());
                }
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            result.add(element.getAsString());
        }
        return result;
    }

    static int[] getIntArray(JsonObject obj, String key, int[] defaultValue) {
        JsonElement element = obj.get(key);
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() == 3) {
                int[] result = new int[3];
                for (int i = 0; i < 3; i++) {
                    JsonElement e = array.get(i);
                    if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                        result[i] = e.getAsInt();
                    } else {
                        return defaultValue;
                    }
                }
                return result;
            }
        }
        return defaultValue;
    }

    public record ActivationRule(ResourceLocation item, int amount, boolean consume) {
        static ActivationRule fromJson(JsonObject obj) {
            String itemId = getString(obj, "item", "");
            ResourceLocation item = itemId.isBlank() ? null : parseLocation(itemId);
            return new ActivationRule(item, Math.max(1, getInt(obj, "amount", 1)), getBoolean(obj, "consume", true));
        }
    }

    /**
     * Resolved placement settings for the selected {@link PlacementMode}.
     *
     * <p>The nested JSON layout ({@code placement.death} / {@code placement.home}) is flattened here:
     * the selected mode's object supplies every field below. Both mode objects share the same field set
     * and the same defaults.
     */
    public record PlacementRule(PlacementMode mode, int[] offset, boolean forcePlace,
                                boolean searchFailureFallback, int searchDown, int searchUp,
                                int horizontalRadius, boolean requireGround, boolean avoidFluids,
                                Set<ResourceLocation> avoidBlocks, Set<ResourceLocation> protectedBlocks,
                                int placeDelayTicks) {

        /**
         * Parses {@code placement} and resolves the mode-specific object.
         *
         * @return empty (with a {@code warn}) when {@code placement.mode} is missing, unknown, or the
         *         corresponding mode object is absent. A schema v1 flat layout is reported distinctly so
         *         that the cause is obvious rather than being mistaken for a typo.
         */
        static Optional<PlacementRule> fromJson(ResourceLocation entryId, JsonObject placementObj) {
            String modeName = getString(placementObj, "mode", "");
            if (modeName.isBlank()) {
                if (looksLikeSchemaV1Placement(placementObj)) {
                    UniversalBossRespawner.LOGGER.warn(
                            "Respawn entry {} uses the schema v1 flat placement layout. Schema v1 is no "
                                    + "longer supported; add \"mode\" and move the placement fields into "
                                    + "\"death\" or \"home\". See README for the v1 -> v2 mapping.",
                            entryId);
                } else {
                    UniversalBossRespawner.LOGGER.warn(
                            "Respawn entry {} is missing the required placement.mode "
                                    + "(expected \"death\" or \"home\"), skipping",
                            entryId);
                }
                return Optional.empty();
            }

            PlacementMode mode = switch (modeName) {
                case "death" -> PlacementMode.DEATH;
                case "home" -> PlacementMode.HOME;
                default -> null;
            };
            if (mode == null) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawn entry {} has invalid placement.mode \"{}\" "
                                + "(expected \"death\" or \"home\"), skipping",
                        entryId, modeName);
                return Optional.empty();
            }

            String modeKey = mode == PlacementMode.DEATH ? "death" : "home";
            JsonElement modeElement = placementObj.get(modeKey);
            if (modeElement == null || !modeElement.isJsonObject()) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawn entry {} selects placement.mode \"{}\" but has no placement.{} object, skipping",
                        entryId, modeName, modeKey);
                return Optional.empty();
            }

            JsonObject obj = modeElement.getAsJsonObject();
            return Optional.of(new PlacementRule(
                    mode,
                    getIntArray(obj, "offset", new int[]{0, 0, 0}),
                    getBoolean(obj, "force_place", false),
                    getBoolean(obj, "search_failure_fallback", true),
                    Math.max(0, getInt(obj, "search_down", 32)),
                    Math.max(0, getInt(obj, "search_up", 16)),
                    Math.max(0, getInt(obj, "horizontal_radius", 4)),
                    getBoolean(obj, "require_ground", true),
                    getBoolean(obj, "avoid_fluids", false),
                    parseBlockIds(entryId, obj, "avoid_blocks"),
                    parseBlockIds(entryId, obj, "protected_blocks"),
                    Math.max(0, getInt(obj, "place_delay_ticks", 0))));
        }

        /**
         * Parses a block-ID list into an immutable {@link Set} once, at rule load time.
         *
         * <p>Runtime block checks then compare {@link ResourceLocation} values directly: O(1) lookup, no
         * per-candidate {@code toString()} allocation, and no linear scan. (The old code did all three on
         * every candidate of every placement search.)
         *
         * <p>Well-formed IDs for blocks that are not installed are kept — they simply never match, which is
         * what compatibility lists need. Only malformed IDs are dropped, with a warning.
         */
        private static Set<ResourceLocation> parseBlockIds(ResourceLocation entryId, JsonObject obj, String key) {
            List<String> raw = getStringList(obj, key);
            if (raw.isEmpty()) {
                return Set.of();
            }
            Set<ResourceLocation> ids = new HashSet<>(raw.size());
            for (String value : raw) {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id == null) {
                    UniversalBossRespawner.LOGGER.warn(
                            "Respawn entry {} has a malformed block id '{}' in {}; ignoring that entry",
                            entryId, value, key);
                } else {
                    ids.add(id);
                }
            }
            return Set.copyOf(ids);
        }

        /**
         * Detects the schema v1 flat placement layout so the rejection message can name the real cause.
         * Schema v1 put these keys directly under {@code placement} and had no {@code mode}.
         */
        private static boolean looksLikeSchemaV1Placement(JsonObject placementObj) {
            for (String legacyKey : new String[]{
                    "offset", "search_down", "search_up", "horizontal_radius",
                    "require_ground", "avoid_fluids", "avoid_blocks"}) {
                if (placementObj.has(legacyKey)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record DeathRule(boolean playerKillOnly, List<String> dimensions, List<String> biomes) {
        static DeathRule fromJson(JsonObject obj) {
            boolean playerKillOnly = getBoolean(obj, "player_kill_only", false);
            List<String> dimensions = getStringList(obj, "dimensions");
            List<String> biomes = getStringList(obj, "biomes");
            return new DeathRule(playerKillOnly, dimensions, biomes);
        }
    }

    /**
     * How the boss is produced once the cage is activated.
     */
    public enum SpawnMode {
        /** Create {@code entity} directly. The original behaviour. */
        SUMMON,
        /**
         * Replace the cage with {@code transform_block} and let that block produce the boss.
         *
         * <p>This exists because some bosses are not one entity: Twilight Forest's Phantom Knight is six
         * entities tied together by proximity, and a lone one would take the "last knight standing" branch —
         * handing out the whole boss reward and marking the Knight Stronghold conquered after a single kill.
         * The mod's own boss spawner blocks reproduce the fight exactly (correct count, numbering, home
         * point and loot), so the cage hands over to them instead.
         */
        TRANSFORM
    }

    /**
     * Resolved {@code spawn}: a mode plus the one object that belongs to it.
     *
     * <p>Unlike {@code placement}, the two mode objects do <em>not</em> share a field set — summoning an
     * entity and handing over to a block have almost nothing in common — so they are separate records
     * rather than one flattened set. Only the selected object needs to be present.
     */
    public record SpawnRule(SpawnMode mode, @Nullable SummonRule summon, @Nullable TransformRule transform) {

        /**
         * The NBT text of whichever mode applies — an entity tag for summon, a block entity tag for
         * transform. Both are optional and default to empty.
         */
        public String nbt() {
            if (transform != null) {
                return transform.nbt();
            }
            return summon != null ? summon.nbt() : "";
        }

        /**
         * @return empty (with a {@code warn}) when {@code spawn.mode} is missing or unknown, when the
         *         selected mode object is absent, or when {@code transform} names no usable block
         */
        static Optional<SpawnRule> fromJson(ResourceLocation entryId, JsonObject spawnObj) {
            String modeName = getString(spawnObj, "mode", "");
            if (modeName.isBlank()) {
                if (looksLikeFlatSpawn(spawnObj)) {
                    UniversalBossRespawner.LOGGER.warn(
                            "Respawn entry {} uses the schema v2 flat spawn layout. Schema v2 is no longer "
                                    + "supported; add \"mode\" and move the spawn fields into \"summon\". "
                                    + "See README for the v2 -> v3 mapping.",
                            entryId);
                } else {
                    UniversalBossRespawner.LOGGER.warn(
                            "Respawn entry {} is missing the required spawn.mode "
                                    + "(expected \"summon\" or \"transform\"), skipping",
                            entryId);
                }
                return Optional.empty();
            }

            SpawnMode mode = switch (modeName) {
                case "summon" -> SpawnMode.SUMMON;
                case "transform" -> SpawnMode.TRANSFORM;
                default -> null;
            };
            if (mode == null) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawn entry {} has invalid spawn.mode \"{}\" (expected \"summon\" or "
                                + "\"transform\"), skipping",
                        entryId, modeName);
                return Optional.empty();
            }

            String modeKey = mode == SpawnMode.SUMMON ? "summon" : "transform";
            JsonElement modeElement = spawnObj.get(modeKey);
            if (modeElement == null || !modeElement.isJsonObject()) {
                UniversalBossRespawner.LOGGER.warn(
                        "Respawn entry {} selects spawn.mode \"{}\" but has no spawn.{} object, skipping",
                        entryId, modeName, modeKey);
                return Optional.empty();
            }
            JsonObject obj = modeElement.getAsJsonObject();

            if (mode == SpawnMode.TRANSFORM) {
                String raw = getString(obj, "transform_block", "");
                ResourceLocation block = raw.isBlank() ? null : ResourceLocation.tryParse(raw);
                if (block == null) {
                    UniversalBossRespawner.LOGGER.warn(
                            "Respawn entry {} uses spawn.mode \"transform\" but has no valid "
                                    + "spawn.transform.transform_block, skipping",
                            entryId);
                    return Optional.empty();
                }
                // No registry lookup on purpose, matching how entity ids are handled: a rule may name
                // content from a mod that is not installed, and it simply never fires. Resolving the block
                // at parse time would also break the parser's independence from registry state.
                return Optional.of(new SpawnRule(mode, null, new TransformRule(
                        block,
                        Math.max(0, getInt(obj, "delay_ticks", 20)),
                        getBoolean(obj, "allow_peaceful", false),
                        getString(obj, "nbt", ""))));
            }

            return Optional.of(new SpawnRule(mode, SummonRule.fromJson(obj), null));
        }

        /** Detects the schema v2 flat layout so the rejection message can name the real cause. */
        private static boolean looksLikeFlatSpawn(JsonObject spawnObj) {
            for (String legacyKey : new String[]{"delay_ticks", "require_player_nearby", "player_range",
                    "allow_peaceful", "count", "spawn_offset", "nbt", "finalize_spawn", "max_attempts",
                    "retry_interval_ticks", "transform_block"}) {
                if (spawnObj.has(legacyKey)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** {@code spawn.summon}: create the entity directly. */
    public record SummonRule(int delayTicks, boolean requirePlayerNearby, double playerRange,
                             boolean allowPeaceful, int count, int[] spawnOffset, String nbt,
                             boolean finalizeSpawn, int maxAttempts, int retryIntervalTicks) {

        static SummonRule fromJson(JsonObject obj) {
            int delayTicks = Math.max(0, getInt(obj, "delay_ticks", 20));
            boolean requirePlayerNearby = getBoolean(obj, "require_player_nearby", true);
            double playerRange = Math.max(1.0D, getDouble(obj, "player_range", 9.0D));
            boolean allowPeaceful = getBoolean(obj, "allow_peaceful", false);
            int count = Math.max(1, getInt(obj, "count", 1));
            int[] spawnOffset = getIntArray(obj, "spawn_offset", new int[]{0, 0, 0});
            String nbt = getString(obj, "nbt", "");
            boolean finalizeSpawn = getBoolean(obj, "finalize_spawn", true);
            int maxAttempts = getInt(obj, "max_attempts", 20);
            if (maxAttempts < 0) {
                maxAttempts = -1;
            } else if (maxAttempts == 0) {
                maxAttempts = 20;
            }
            int retryIntervalTicks = Math.max(1, getInt(obj, "retry_interval_ticks", 4));
            return new SummonRule(delayTicks, requirePlayerNearby, playerRange, allowPeaceful,
                    count, spawnOffset, nbt, finalizeSpawn, maxAttempts, retryIntervalTicks);
        }
    }

    /**
     * {@code spawn.transform}: keep the cage lit for {@code delayTicks} so its opening animation plays, then
     * replace the cage with {@code block} and let that block produce the boss.
     *
     * <p>Only four fields, because most of the summon path has no meaning here: there is one block rather
     * than a count of entities, it lands on the cage's own position rather than at an offset, and
     * {@code setBlock} on that position cannot fail, so the attempt limits have nothing to govern.
     *
     * @param block         the block to become; resolved again when the animation ends
     * @param delayTicks    how long the cage stays lit before the swap
     * @param allowPeaceful whether the swap may happen on Peaceful. Boss spawner blocks generally refuse to
     *                      fire there — and Twilight Forest's are unbreakable — so refusing by default
     *                      avoids stranding a block the player cannot remove
     * @param nbt           NBT written to the new block's block entity, exactly as the trailing tag of a
     *                      {@code /setblock} command would be. Empty leaves the block entity untouched
     */
    public record TransformRule(ResourceLocation block, int delayTicks, boolean allowPeaceful, String nbt) {
    }

    public record DuplicateRule(String mode, int searchRadius) {
        static DuplicateRule fromJson(JsonObject obj) {
            // Default matches Cataclysm: no duplicate check, allow multiple respawn cages.
            String mode = getString(obj, "mode", "allow_multiple");
            if (!List.of("allow_multiple", "keep_existing", "replace_existing").contains(mode)) {
                UniversalBossRespawner.LOGGER.warn("Invalid duplicate.mode '{}', falling back to allow_multiple", mode);
                mode = "allow_multiple";
            }
            int searchRadius = Math.max(0, getInt(obj, "search_radius", 16));
            return new DuplicateRule(mode, searchRadius);
        }
    }
}
