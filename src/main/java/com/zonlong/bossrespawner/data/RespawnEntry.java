package com.zonlong.bossrespawner.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public static Optional<RespawnEntry> fromJson(ResourceLocation id, JsonObject root) {
        try {
            boolean enabled = getBoolean(root, "enabled", true);
            if (!enabled) {
                return Optional.empty();
            }

            int schemaVersion = getInt(root, "schema_version", 1);
            if (schemaVersion != 1) {
                UniversalBossRespawner.LOGGER.warn("Respawn entry {} uses unsupported schema_version {} (expected 1), skipping",
                        id, schemaVersion);
                return Optional.empty();
            }

            int priority = getInt(root, "priority", 0);
            List<ResourceLocation> entities = parseEntities(root.get("entity"));
            if (entities.isEmpty()) {
                UniversalBossRespawner.LOGGER.warn("Respawn entry {} has no valid entity", id);
                return Optional.empty();
            }

            JsonObject activationObj = getObject(root, "activation");
            JsonObject placementObj = getObject(root, "placement");
            JsonObject deathObj = getObject(root, "death");
            JsonObject spawnObj = getObject(root, "spawn");
            JsonObject duplicateObj = getObject(root, "duplicate");

            ActivationRule activation = ActivationRule.fromJson(activationObj);
            if (activation.item() == null) {
                UniversalBossRespawner.LOGGER.warn("Respawn entry {} has no activation.item", id);
                return Optional.empty();
            }

            PlacementRule placement = PlacementRule.fromJson(placementObj);
            DeathRule death = DeathRule.fromJson(deathObj);
            SpawnRule spawn = SpawnRule.fromJson(spawnObj);
            DuplicateRule duplicate = DuplicateRule.fromJson(duplicateObj);

            return Optional.of(new RespawnEntry(
                    id, true, priority, List.copyOf(entities), activation,
                    placement, death, spawn, duplicate));
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

    public record PlacementRule(int[] offset, int searchDown, int searchUp,
                                int horizontalRadius, boolean requireGround, boolean avoidFluids,
                                List<String> avoidBlocks) {
        static PlacementRule fromJson(JsonObject obj) {
            int[] offset = getIntArray(obj, "offset", new int[]{0, 0, 0});
            int searchDown = Math.max(0, getInt(obj, "search_down", 32));
            int searchUp = Math.max(0, getInt(obj, "search_up", 16));
            int horizontalRadius = Math.max(0, getInt(obj, "horizontal_radius", 4));
            boolean requireGround = getBoolean(obj, "require_ground", true);
            boolean avoidFluids = getBoolean(obj, "avoid_fluids", false);
            List<String> avoidBlocks = getStringList(obj, "avoid_blocks");
            return new PlacementRule(offset, searchDown, searchUp, horizontalRadius,
                    requireGround, avoidFluids, avoidBlocks);
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
