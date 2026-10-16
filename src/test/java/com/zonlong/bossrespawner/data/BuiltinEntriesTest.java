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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /** The built-in entries shipped with the mod: 18 original + 5 Bosses Rise + 9 Twilight Forest. */
    private static final int EXPECTED_ENTRY_COUNT = 32;

    /** Namespaces that went through the schema v1 -> v2 migration, used by the fidelity checks below. */
    private static final List<String> MIGRATED_NAMESPACES = List.of("legendary_monsters", "mowziesmobs");

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
    @DisplayName("all built-in entries are present and parse under schema v4")
    void allBuiltinEntriesParse() throws IOException {
        List<Loaded> loaded = loadAll();
        assertEquals(EXPECTED_ENTRY_COUNT, loaded.size(),
                "unexpected number of built-in entries; found: "
                        + loaded.stream().map(Loaded::name).toList());
    }

    @Test
    @DisplayName("every built-in entry is schema_version 4 and uses placement.mode home")
    void schemaAndModeAreUniform() throws IOException {
        for (Loaded loaded : loadAll()) {
            JsonObject json = loaded.json();
            assertEquals(4, json.get("schema_version").getAsInt(), loaded.name() + ": schema_version");
            assertEquals("home", json.getAsJsonObject("placement").get("mode").getAsString(),
                    loaded.name() + ": placement.mode");
            assertTrue(json.getAsJsonObject("placement").has("home"),
                    loaded.name() + ": placement.home must exist");
            assertEquals(RespawnEntry.PlacementMode.HOME, loaded.entry().placement().mode(),
                    loaded.name() + ": resolved mode");
        }
    }

    @Test
    @DisplayName("no built-in entry still carries the removed protected_blocks field")
    void protectedBlocksIsGoneEverywhere() throws IOException {
        // v4 deleted the field (and the global compat.protectedBlockIds list behind it), so a leftover key
        // would be dead weight that reads like a live protection floor. This is the migration's own check:
        // every entry mentions the key nowhere, in any placement mode object.
        for (Loaded loaded : loadAll()) {
            String text = Files.readString(loaded.file(), StandardCharsets.UTF_8);
            assertFalse(text.contains("protected_blocks"),
                    loaded.name() + ": protected_blocks was removed in schema v4; its block ids belong in "
                            + "avoid_blocks now");
            assertFalse(text.contains("protectedBlockIds"),
                    loaded.name() + ": the global compat.protectedBlockIds list no longer exists");
        }
    }

    @Test
    @DisplayName("the schema v1 flat placement layout is gone")
    void legacyPlacementKeysAreGone() throws IOException {
        List<String> legacyKeys = List.of("offset", "search_down", "search_up", "horizontal_radius",
                "require_ground", "avoid_fluids", "avoid_blocks");
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
                    "avoid_blocks", "place_delay_ticks")) {
                assertTrue(home.has(key), name + ": placement.home." + key + " must be explicit");
            }

            for (String key : List.of("player_kill_only", "dimensions", "biomes")) {
                assertTrue(json.getAsJsonObject("death").has(key), name + ": death." + key);
            }
            JsonObject spawn = json.getAsJsonObject("spawn");
            assertTrue(spawn.has("mode"), name + ": spawn.mode must be explicit");
            String spawnMode = spawn.get("mode").getAsString();
            String spawnModeKey = "transform".equals(spawnMode) ? "transform" : "summon";
            assertTrue(spawn.has(spawnModeKey), name + ": spawn." + spawnModeKey + " must match spawn.mode");
            assertTrue(spawn.get(spawnModeKey).isJsonObject(),
                    name + ": spawn." + spawnModeKey + " must be an object");
            JsonObject spawnFields = spawn.getAsJsonObject(spawnModeKey);
            List<String> spawnKeys = "transform".equals(spawnMode)
                    // Only what a hand-off can use: one block, an animation delay, the Peaceful gate and NBT.
                    ? List.of("transform_block", "delay_ticks", "allow_peaceful", "nbt")
                    : List.of("delay_ticks", "require_player_nearby", "player_range", "allow_peaceful",
                    "count", "spawn_offset", "nbt", "finalize_spawn", "max_attempts", "retry_interval_ticks");
            for (String key : spawnKeys) {
                assertTrue(spawnFields.has(key), name + ": spawn." + spawnModeKey + "." + key);
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
            // The old global foreignCageBlockIds avoidance was migrated into every rule explicitly, and in v4
            // avoid_blocks is the ONLY block list there is, so an empty one would silently drop the avoidance
            // of another mod's respawner block.
            assertTrue(stringArray(home, "avoid_blocks").contains("cataclysm:boss_respawner"),
                    loaded.name() + ": the migrated avoidance of cataclysm:boss_respawner must survive in "
                            + "avoid_blocks, which is now the only block list");
        }
    }

    @Test
    @DisplayName("every Twilight Forest entry avoids that mod's chest blocks")
    void twilightForestEntriesAvoidChests() throws IOException {
        // Twilight Forest delivers boss loot through chests (its Dark Tower boss chest even holds the
        // Ur-Ghast trophy) and fills boss arenas with chests, so a respawn cage must not land on one.
        //
        // In v3 these 17 ids lived in protected_blocks, which also filtered the placement SEARCH. v4 deleted
        // that field, so the ids were migrated into avoid_blocks - the one remaining list, which the first
        // search pass honours. The worst case is now "the cage ends up on top of a chest" instead of the cage
        // not appearing at all; see PlacementResolver for the two-pass search that guarantees it.
        List<String> twilightForestChests = List.of(
                "twilightforest:canopy_chest", "twilightforest:canopy_trapped_chest",
                "twilightforest:dark_chest", "twilightforest:dark_trapped_chest",
                "twilightforest:mangrove_chest", "twilightforest:mangrove_trapped_chest",
                "twilightforest:mining_chest", "twilightforest:mining_trapped_chest",
                "twilightforest:skull_chest",
                "twilightforest:sorting_chest", "twilightforest:sorting_trapped_chest",
                "twilightforest:time_chest", "twilightforest:time_trapped_chest",
                "twilightforest:transformation_chest", "twilightforest:transformation_trapped_chest",
                "twilightforest:twilight_oak_chest", "twilightforest:twilight_oak_trapped_chest");

        int checked = 0;
        for (Loaded loaded : loadAll()) {
            if (!loaded.file().toString().contains("twilightforest")) {
                continue;
            }
            checked++;
            JsonObject home = loaded.json().getAsJsonObject("placement").getAsJsonObject("home");

            List<String> missingFromAvoid = twilightForestChests.stream()
                    .filter(id -> !stringArray(home, "avoid_blocks").contains(id)).toList();
            assertTrue(missingFromAvoid.isEmpty(),
                    loaded.name() + ": avoid_blocks is missing " + missingFromAvoid);

            assertTrue(stringArray(home, "avoid_blocks").contains("cataclysm:boss_respawner"),
                    loaded.name() + ": the migrated Cataclysm avoidance entry must survive");
        }
        assertEquals(9, checked, "expected all nine Twilight Forest entries to be checked");
    }

    @Test
    @DisplayName("the migrated avoid_blocks lists are exact: 18 ids in Twilight Forest, 1 everywhere else")
    void avoidListsAreExact() throws IOException {
        // The migration merged the 17 Twilight Forest chest ids into a list that already held exactly one id
        // (cataclysm:boss_respawner), so all nine of its entries must hold 18 - no more (a duplicated merge)
        // and no fewer (a silently dropped list). Every other mod keeps the single migrated id. Pinning the
        // sizes catches both a bulk edit that flattens the lists and one that only half-applied them.
        int twilightForest = 0;
        for (Loaded loaded : loadAll()) {
            JsonObject home = loaded.json().getAsJsonObject("placement").getAsJsonObject("home");
            int size = stringArray(home, "avoid_blocks").size();
            if (loaded.file().toString().contains("twilightforest")) {
                twilightForest++;
                assertEquals(18, size,
                        loaded.name() + ": 17 Twilight Forest chest ids plus cataclysm:boss_respawner");
            } else {
                assertEquals(1, size,
                        loaded.name() + ": only the migrated cataclysm:boss_respawner avoidance is expected");
                assertEquals("cataclysm:boss_respawner", stringArray(home, "avoid_blocks").get(0),
                        loaded.name() + ": the single avoided id must be the Cataclysm respawner block");
            }
        }
        assertEquals(9, twilightForest, "expected all nine Twilight Forest entries");
    }

    /** Reads a JSON string array into a list; missing arrays yield an empty list. */
    private static List<String> stringArray(JsonObject owner, String key) {
        List<String> values = new ArrayList<>();
        if (owner.has(key) && owner.get(key).isJsonArray()) {
            for (JsonElement element : owner.getAsJsonArray(key)) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    @Test
    @DisplayName("migration preserved the per-file variations instead of flattening them")
    void migrationPreservedVariations() throws IOException {
        // Scoped to the namespaces that actually went through the v1 -> v2 migration, so adding support for
        // another mod later does not invalidate this check.
        List<Loaded> loaded = loadAll().stream()
                .filter(l -> MIGRATED_NAMESPACES.stream().anyMatch(ns -> l.file().toString().contains(ns)))
                .toList();
        assertEquals(18, loaded.size(), "expected the two originally migrated namespaces");

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
    @DisplayName("the new mod entries all use their agreed shared, pre-fight key item")
    void newModEntriesUseSharedKeys() throws IOException {
        // Design rule: a key must be obtainable BEFORE fighting the boss, so a boss drop can never be used.
        // Both new mods therefore share one scarce pre-fight item across all of their bosses.
        for (Loaded loaded : loadAll()) {
            String path = loaded.file().toString().replace('\\', '/');
            String expectedKey = null;
            if (path.contains("/data/twilightforest/")) {
                expectedKey = "twilightforest:magic_map_focus";
            } else if (path.contains("/data/block_factorys_bosses/")) {
                expectedKey = "block_factorys_bosses:ancient_trial_key";
            }
            if (expectedKey == null) {
                continue;
            }
            assertEquals(expectedKey, loaded.entry().activation().item().toString(),
                    loaded.name() + ": these entries intentionally share one pre-fight key item");
            assertTrue(loaded.entry().activation().consume(),
                    loaded.name() + ": the shared key must be consumed");
        }
    }

    @Test
    @DisplayName("every Bosses Rise and Twilight Forest boss has an entry")
    void newModCoverageIsComplete() throws IOException {
        List<Loaded> loaded = loadAll();
        assertEquals(9, loaded.stream().filter(l -> l.file().toString().contains("twilightforest")).count(),
                "8 progression bosses plus the Quest Ram");
        assertEquals(5, loaded.stream().filter(l -> l.file().toString().contains("block_factorys_bosses")).count(),
                "5 named bosses in Bosses Rise 2.1.2 (incl. Nerakyss, the Kraken)");

        List<String> expected = new java.util.ArrayList<>(List.of(
                "twilightforest:naga", "twilightforest:lich", "twilightforest:minoshroom",
                "twilightforest:hydra", "twilightforest:knight_phantom", "twilightforest:ur_ghast",
                "twilightforest:alpha_yeti", "twilightforest:snow_queen", "twilightforest:quest_ram",
                "block_factorys_bosses:infernal_dragon", "block_factorys_bosses:sandworm",
                "block_factorys_bosses:underworld_knight", "block_factorys_bosses:yeti",
                "block_factorys_bosses:kraken"));
        List<String> actual = loaded.stream()
                .map(l -> l.entry().entities().get(0).toString())
                .filter(id -> id.startsWith("twilightforest:") || id.startsWith("block_factorys_bosses:"))
                .sorted().toList();
        expected.sort(String::compareTo);
        assertEquals(expected, actual);
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
            // The rule stores parsed ResourceLocations; compare as values, not as raw strings.
            Set<ResourceLocation> expectedAvoid = new HashSet<>();
            for (String value : rawAvoid) {
                expectedAvoid.add(ResourceLocation.parse(value));
            }
            assertEquals(expectedAvoid, placement.avoidBlocks(), name + ": avoid_blocks");

            JsonObject duplicate = loaded.json().getAsJsonObject("duplicate");
            assertEquals(duplicate.get("mode").getAsString(), loaded.entry().duplicate().mode(), name);
            assertEquals(duplicate.get("search_radius").getAsInt(), loaded.entry().duplicate().searchRadius(), name);

            JsonObject spawn = loaded.json().getAsJsonObject("spawn");
            String spawnMode = spawn.get("mode").getAsString();
            RespawnEntry.SpawnRule rule = loaded.entry().spawn();
            assertEquals(RespawnEntry.SpawnMode.valueOf(spawnMode.toUpperCase()), rule.mode(), name + ": spawn.mode");

            if (rule.mode() == RespawnEntry.SpawnMode.TRANSFORM) {
                JsonObject transform = spawn.getAsJsonObject("transform");
                assertEquals(transform.get("delay_ticks").getAsInt(), rule.transform().delayTicks(), name);
                assertEquals(transform.get("allow_peaceful").getAsBoolean(),
                        rule.transform().allowPeaceful(), name);
                assertTrue(rule.summon() == null, name + ": a transform rule must not carry a summon object");
            } else {
                JsonObject summon = spawn.getAsJsonObject("summon");
                assertEquals(summon.get("delay_ticks").getAsInt(), rule.summon().delayTicks(), name);
                assertEquals(summon.get("count").getAsInt(), rule.summon().count(), name);
                assertEquals(summon.get("max_attempts").getAsInt(), rule.summon().maxAttempts(), name);
                assertEquals(summon.get("retry_interval_ticks").getAsInt(),
                        rule.summon().retryIntervalTicks(), name);
                assertTrue(rule.transform() == null, name + ": a summon rule must not carry a transform object");
            }

            JsonObject activation = loaded.json().getAsJsonObject("activation");
            assertEquals(activation.get("amount").getAsInt(), loaded.entry().activation().amount(), name);
            assertEquals(activation.get("consume").getAsBoolean(), loaded.entry().activation().consume(), name);
        }
    }
}
