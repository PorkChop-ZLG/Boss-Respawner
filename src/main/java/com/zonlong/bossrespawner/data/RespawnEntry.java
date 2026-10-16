package com.zonlong.bossrespawner.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One parsed {@code boss_respawner} JSON rule (schema v2).
 *
 * <pre>
 * data/&lt;namespace&gt;/boss_respawner/entries/&lt;any-name&gt;.json
 * </pre>
 *
 * <p>Schema v2 requires {@code schema_version}, {@code entity}, {@code activation.item},
 * {@code placement.mode} and the mode-specific object ({@code placement.death} or
 * {@code placement.home}) to be present explicitly. Everything else uses code defaults.
 *
 * <p>Schema v1 entries (flat {@code placement} without {@code mode}) are rejected rather than
 * silently reinterpreted; see {@link PlacementRule#fromJson}.
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
    public static final int SCHEMA_VERSION = 2;

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
                        "Respawn entry {} uses unsupported schema_version {} (expected {}), skipping",
                        id, schemaVersion, SCHEMA_VERSION);
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
            SpawnRule spawn = SpawnRule.fromJson(getObject(root, "spawn"));
            DuplicateRule duplicate = DuplicateRule.fromJson(getObject(root, "duplicate"));

            return Optional.of(new RespawnEntry(
                    id, true, priority, List.copyOf(entities), activation,
                    placement.get(), death, spawn, duplicate));
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
                                Set<ResourceLocation> avoidBlocks, Set<ResourceLocation> protectedBlocks) {

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
                    parseBlockIds(entryId, obj, "protected_blocks")));
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

    public record SpawnRule(int delayTicks, boolean requirePlayerNearby, double playerRange,
                            boolean allowPeaceful, int count, int[] spawnOffset, String nbt,
                            boolean finalizeSpawn, int maxAttempts, int retryIntervalTicks) {
        static SpawnRule fromJson(JsonObject obj) {
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
            return new SpawnRule(delayTicks, requirePlayerNearby, playerRange, allowPeaceful,
                    count, spawnOffset, nbt, finalizeSpawn, maxAttempts, retryIntervalTicks);
        }
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
