package com.zonlong.bossrespawner.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zonlong.bossrespawner.TestPaths;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Static-resource verification for the shipped data pack entries.
 *
 * <p>This is not a string check: every file is fed through the real {@link RespawnEntry} parser, so a
 * migration that merely looked correct would still fail here.
 */
class BuiltinEntriesTest {

    private static final Path ENTRIES_ROOT = TestPaths.dataPackRoot();

    /** The 18 entries shipped with the mod. */
    private static final int EXPECTED_ENTRY_COUNT = 18;

    private record Loaded(Path file, JsonObject json, RespawnEntry entry) {
        String name() {
            return file.getFileName().toString();
        }
    }

    private static List<Path> entryFiles() throws IOException {
        assertTrue(Files.isDirectory(ENTRIES_ROOT),
                "expected the data pack root at " + ENTRIES_ROOT.toAbsolutePath()
                        + " - the test working directory must be the project directory");
        try (Stream<Path> walk = Files.walk(ENTRIES_ROOT)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> p.getParent() != null && "entries".equals(p.getParent().getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    private static List<Loaded> loadAll() throws IOException {
        List<Loaded> loaded = new ArrayList<>();
        for (Path file : entryFiles()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", file.getFileName().toString());
            Optional<RespawnEntry> parsed = RespawnEntry.fromJson(id, json);
            if (parsed.isEmpty()) {
                fail("built-in entry " + file + " was rejected by RespawnEntry.fromJson");
            }
            loaded.add(new Loaded(file, json, parsed.get()));
        }
        return loaded;
    }

    @Test
    @DisplayName("all built-in entries are present and parse under schema v2")
    void allBuiltinEntriesParse() throws IOException {
        List<Loaded> loaded = loadAll();
        assertEquals(EXPECTED_ENTRY_COUNT, loaded.size(),
                "unexpected number of built-in entries; found: "
                        + loaded.stream().map(Loaded::name).toList());
    }

    @Test
    @DisplayName("every built-in entry is schema_version 2 and uses placement.mode home")
    void schemaAndModeAreUniform() throws IOException {
        for (Loaded loaded : loadAll()) {
            JsonObject json = loaded.json();
            assertEquals(2, json.get("schema_version").getAsInt(), loaded.name() + ": schema_version");
            assertEquals("home", json.getAsJsonObject("placement").get("mode").getAsString(),
                    loaded.name() + ": placement.mode");
            assertTrue(json.getAsJsonObject("placement").has("home"),
                    loaded.name() + ": placement.home must exist");
            assertEquals(RespawnEntry.PlacementMode.HOME, loaded.entry().placement().mode(),
                    loaded.name() + ": resolved mode");
        }
    }

    @Test
    @DisplayName("the schema v1 flat placement layout is gone")
    void legacyPlacementKeysAreGone() throws IOException {
        List<String> legacyKeys = List.of("offset", "search_down", "search_up", "horizontal_radius",
                "require_ground", "avoid_fluids", "avoid_blocks", "protected_blocks");
        for (Loaded loaded : loadAll()) {
            JsonObject placement = loaded.json().getAsJsonObject("placement");
            for (String key : legacyKeys) {
                assertFalse(placement.has(key),
                        loaded.name() + ": placement." + key + " is a schema v1 key and must live under placement.home");
            }
        }
    }

    @Test
    @DisplayName("built-in entries spell out every optional field, so they work as templates")
    void optionalFieldsAreExplicit() throws IOException {
        for (Loaded loaded : loadAll()) {
            JsonObject json = loaded.json();
            String name = loaded.name();

            for (String key : List.of("enabled", "priority", "entity", "activation", "placement",
                    "death", "spawn", "duplicate")) {
                assertTrue(json.has(key), name + ": top-level '" + key + "' must be explicit");
            }
            for (String key : List.of("item", "amount", "consume")) {
                assertTrue(json.getAsJsonObject("activation").has(key), name + ": activation." + key);
            }

            JsonObject home = json.getAsJsonObject("placement").getAsJsonObject("home");
            for (String key : List.of("offset", "force_place", "search_failure_fallback", "search_down",
                    "search_up", "horizontal_radius", "require_ground", "avoid_fluids",
                    "avoid_blocks", "protected_blocks")) {
                assertTrue(home.has(key), name + ": placement.home." + key + " must be explicit");
            }

            for (String key : List.of("player_kill_only", "dimensions", "biomes")) {
                assertTrue(json.getAsJsonObject("death").has(key), name + ": death." + key);
            }
            for (String key : List.of("delay_ticks", "require_player_nearby", "player_range", "allow_peaceful",
                    "count", "spawn_offset", "nbt", "finalize_spawn", "max_attempts", "retry_interval_ticks")) {
                assertTrue(json.getAsJsonObject("spawn").has(key), name + ": spawn." + key);
            }
            for (String key : List.of("mode", "search_radius")) {
                assertTrue(json.getAsJsonObject("duplicate").has(key), name + ": duplicate." + key);
            }
        }
    }

    @Test
    @DisplayName("the new placement fields carry the documented defaults")
    void newPlacementFieldsUseDefaults() throws IOException {
        for (Loaded loaded : loadAll()) {
            JsonObject home = loaded.json().getAsJsonObject("placement").getAsJsonObject("home");
            assertFalse(home.get("force_place").getAsBoolean(), loaded.name() + ": force_place");
            assertTrue(home.get("search_failure_fallback").getAsBoolean(),
                    loaded.name() + ": search_failure_fallback");
            assertTrue(home.getAsJsonArray("protected_blocks").isEmpty(),
                    loaded.name() + ": protected_blocks is provided globally for Cataclysm");
            assertTrue(home.getAsJsonArray("avoid_blocks").size() > 0,
                    loaded.name() + ": the previous global avoidance list must be migrated explicitly");
        }
    }

    @Test
    @DisplayName("migration preserved the per-file variations instead of flattening them")
    void migrationPreservedVariations() throws IOException {
        List<Loaded> loaded = loadAll();

        List<String> notRequiringGround = loaded.stream()
                .filter(l -> !l.entry().placement().requireGround())
                .map(Loaded::name).sorted().toList();
        assertEquals(List.of("umvuthi.json"), notRequiringGround,
                "the one entry that opted out of require_ground must keep doing so");

        long keepExisting = loaded.stream()
                .filter(l -> "keep_existing".equals(l.entry().duplicate().mode())).count();
        long allowMultiple = loaded.stream()
                .filter(l -> "allow_multiple".equals(l.entry().duplicate().mode())).count();
        assertEquals(6, keepExisting, "entries using keep_existing");
        assertEquals(12, allowMultiple, "entries using allow_multiple");
    }

    @Test
    @DisplayName("parser values match the raw JSON, field by field")
    void parsedValuesMatchRawJson() throws IOException {
        for (Loaded loaded : loadAll()) {
            JsonObject home = loaded.json().getAsJsonObject("placement").getAsJsonObject("home");
            RespawnEntry.PlacementRule placement = loaded.entry().placement();
            String name = loaded.name();

            assertEquals(home.get("search_down").getAsInt(), placement.searchDown(), name);
            assertEquals(home.get("search_up").getAsInt(), placement.searchUp(), name);
            assertEquals(home.get("horizontal_radius").getAsInt(), placement.horizontalRadius(), name);
            assertEquals(home.get("require_ground").getAsBoolean(), placement.requireGround(), name);
            assertEquals(home.get("avoid_fluids").getAsBoolean(), placement.avoidFluids(), name);
            assertEquals(home.get("force_place").getAsBoolean(), placement.forcePlace(), name);
            assertEquals(home.get("search_failure_fallback").getAsBoolean(), placement.searchFailureFallback(), name);

            List<String> rawAvoid = new ArrayList<>();
            for (JsonElement element : home.getAsJsonArray("avoid_blocks")) {
                rawAvoid.add(element.getAsString());
            }
            assertEquals(rawAvoid, placement.avoidBlocks(), name + ": avoid_blocks");

            JsonObject duplicate = loaded.json().getAsJsonObject("duplicate");
            assertEquals(duplicate.get("mode").getAsString(), loaded.entry().duplicate().mode(), name);
            assertEquals(duplicate.get("search_radius").getAsInt(), loaded.entry().duplicate().searchRadius(), name);

            JsonObject spawn = loaded.json().getAsJsonObject("spawn");
            assertEquals(spawn.get("delay_ticks").getAsInt(), loaded.entry().spawn().delayTicks(), name);
            assertEquals(spawn.get("count").getAsInt(), loaded.entry().spawn().count(), name);
            assertEquals(spawn.get("max_attempts").getAsInt(), loaded.entry().spawn().maxAttempts(), name);
            assertEquals(spawn.get("retry_interval_ticks").getAsInt(), loaded.entry().spawn().retryIntervalTicks(), name);

            JsonObject activation = loaded.json().getAsJsonObject("activation");
            assertEquals(activation.get("amount").getAsInt(), loaded.entry().activation().amount(), name);
            assertEquals(activation.get("consume").getAsBoolean(), loaded.entry().activation().consume(), name);
        }
    }
}
