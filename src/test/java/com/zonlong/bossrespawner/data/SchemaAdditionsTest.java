package com.zonlong.bossrespawner.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zonlong.bossrespawner.TestPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema v3 additions that survived into v4: {@code spawn} is a mode plus a mode object, and
 * {@code placement.<mode>} gained {@code place_delay_ticks}.
 *
 * <p>The rules built here all declare {@code schema_version: 4}; v4's own change (the removal of
 * {@code protected_blocks}) is covered by {@link RespawnEntrySchemaTest}.
 */
class SchemaAdditionsTest {

    private static final ResourceLocation ENTRY = ResourceLocation.parse("test:additions");

    /**
     * Minimal valid {@code spawn} block: schema v3 requires a mode, and the mode object must be present.
     * Used by the tests whose subject is {@code placement}, so that {@code spawn} cannot be the cause of a
     * rejection.
     */
    private static final String MINIMAL_SPAWN = "{ \"mode\": \"summon\", \"summon\": {} }";

    // ------------------------------------------------------------------ spawn structure

    @Test
    @DisplayName("a summon rule parses into its own object, leaving transform empty")
    void summonRuleParses() {
        RespawnEntry entry = parseOrFail("{}", """
                { "mode": "summon", "summon": { "delay_ticks": 40, "count": 3 } }
                """);

        assertEquals(RespawnEntry.SpawnMode.SUMMON, entry.spawn().mode());
        assertNotNull(entry.spawn().summon());
        assertEquals(40, entry.spawn().summon().delayTicks());
        assertEquals(3, entry.spawn().summon().count());
        assertTrue(entry.spawn().transform() == null, "summon must not carry a transform object");
    }

    @Test
    @DisplayName("a transform rule parses into its own object with its seven fields")
    void transformRuleParses() {
        RespawnEntry entry = parseOrFail("{}", """
                {
                  "mode": "transform",
                  "transform": {
                    "transform_block": "twilightforest:knight_phantom_boss_spawner",
                    "delay_ticks": 20,
                    "allow_peaceful": true,
                    "nbt": "{Foo:1}"
                  }
                }
                """);

        assertEquals(RespawnEntry.SpawnMode.TRANSFORM, entry.spawn().mode());
        assertTrue(entry.spawn().summon() == null, "transform must not carry a summon object");
        RespawnEntry.TransformRule rule = entry.spawn().transform();
        assertEquals(ResourceLocation.parse("twilightforest:knight_phantom_boss_spawner"), rule.block());
        assertEquals(20, rule.delayTicks());
        assertTrue(rule.allowPeaceful());
        assertEquals("{Foo:1}", rule.nbt());
        assertArrayEquals(new int[]{0, 0, 0}, rule.spawnerOffset(),
                "spawner_offset defaults to zero for old rules");
        assertFalse(rule.forcePlace(), "force_place defaults to false for old rules");
        assertEquals(RespawnEntry.PlaceAt.CAGE, rule.at(), "at defaults to cage for old rules");
    }

    @Test
    @DisplayName("spawner_offset is parsed for the Lich hand-off")
    void spawnerOffsetIsParsed() {
        RespawnEntry entry = parseOrFail("{}", """
                {
                  "mode": "transform",
                  "transform": {
                    "transform_block": "twilightforest:lich_boss_spawner",
                    "at": "anchor",
                    "spawner_offset": [0, 1, 0],
                    "force_place": true
                  }
                }
                """);

        assertArrayEquals(new int[]{0, 1, 0}, entry.spawn().transform().spawnerOffset());
        assertTrue(entry.spawn().transform().forcePlace());
    }

    @Test
    @DisplayName("transform.at accepts anchor and defaults to cage")
    void transformAtIsParsed() {
        assertEquals(RespawnEntry.PlaceAt.ANCHOR, parseOrFail("{}", """
                {
                  "mode": "transform",
                  "transform": {
                    "transform_block": "twilightforest:knight_phantom_boss_spawner",
                    "at": "anchor"
                  }
                }
                """).spawn().transform().at());

        // An unknown value falls back to cage instead of silently deleting the rule.
        assertEquals(RespawnEntry.PlaceAt.CAGE, parseOrFail("{}", """
                {
                  "mode": "transform",
                  "transform": {
                    "transform_block": "twilightforest:knight_phantom_boss_spawner",
                    "at": "somewhere"
                  }
                }
                """).spawn().transform().at());
    }

    @Test
    @DisplayName("transform.at and spawner_offset survive the rule snapshot")
    void transformAtSurvivesSnapshot() {
        RespawnEntry entry = parseOrFail("{}", """
                {
                  "mode": "transform",
                  "transform": {
                    "transform_block": "twilightforest:lich_boss_spawner",
                    "at": "anchor",
                    "spawner_offset": [0, 1, 0],
                    "force_place": true
                  }
                }
                """);

        RespawnRuleSnapshot restored = RespawnRuleSnapshot.fromTag(RespawnRuleSnapshot.of(entry).toTag());

        assertEquals(RespawnEntry.PlaceAt.ANCHOR, restored.spawn().transform().at());
        assertArrayEquals(new int[]{0, 1, 0}, restored.spawn().transform().spawnerOffset());
        assertTrue(restored.spawn().transform().forcePlace());
    }

    @Test
    @DisplayName("spawn.mode is required, and a v2 flat layout is named as the cause")
    void missingSpawnModeIsRejected() {
        assertTrue(parse("{}", "{}").isEmpty(), "an empty spawn object has no mode");
        // A v2 file put the fields straight under "spawn"; it must not be reinterpreted as a default mode.
        assertTrue(parse("{}", "{ \"delay_ticks\": 20, \"count\": 1 }").isEmpty());
    }

    @Test
    @DisplayName("an unknown spawn.mode skips the entry instead of guessing")
    void unknownSpawnModeIsRejected() {
        assertTrue(parse("{}", "{ \"mode\": \"teleport\" }").isEmpty());
    }

    @Test
    @DisplayName("the selected mode object must exist")
    void selectedModeObjectIsRequired() {
        assertTrue(parse("{}", "{ \"mode\": \"summon\" }").isEmpty());
        assertTrue(parse("{}", "{ \"mode\": \"transform\", \"summon\": {} }").isEmpty());
    }

    @Test
    @DisplayName("transform needs a usable block id, but is not registry-checked while parsing")
    void transformBlockIsOnlyFormatChecked() {
        assertTrue(parse("{}", "{ \"mode\": \"transform\", \"transform\": {} }").isEmpty());
        assertTrue(parse("{}", """
                { "mode": "transform", "transform": { "transform_block": "###" } }
                """).isEmpty());

        // The unit-test JVM has no Twilight Forest, and a rule may name a mod that is not installed; the
        // block is resolved when the cage is activated instead.
        Optional<RespawnEntry> entry = parse("{}", """
                { "mode": "transform", "transform": { "transform_block": "some_absent_mod:its_block" } }
                """);
        assertTrue(entry.isPresent(), "a well-formed id from an absent mod must still parse");
    }

    @Test
    @DisplayName("transform delay_ticks defaults to 20, matching summon")
    void transformDelayDefaultsToTwenty() {
        RespawnEntry entry = parseOrFail("{}", """
                { "mode": "transform", "transform": { "transform_block": "minecraft:chest" } }
                """);
        assertEquals(20, entry.spawn().transform().delayTicks());
        assertFalse(entry.spawn().transform().allowPeaceful());
        assertEquals("", entry.spawn().transform().nbt());
        assertEquals(RespawnEntry.PlaceAt.CAGE, entry.spawn().transform().at());
    }

    // ------------------------------------------------------------------ place_delay_ticks

    @Test
    @DisplayName("place_delay_ticks defaults to zero, which keeps the old immediate placement")
    void placeDelayDefaultsToZero() {
        assertEquals(0, parseOrFail("{}", MINIMAL_SPAWN).placement().placeDelayTicks());
    }

    @Test
    @DisplayName("place_delay_ticks is read from the selected mode object and clamped at zero")
    void placeDelayIsReadAndClamped() {
        assertEquals(30, parseOrFail("{ \"place_delay_ticks\": 30 }", MINIMAL_SPAWN)
                .placement().placeDelayTicks());
        assertEquals(0, parseOrFail("{ \"place_delay_ticks\": -5 }", MINIMAL_SPAWN)
                .placement().placeDelayTicks());
    }

    // ------------------------------------------------------------------ pending task delay

    @Test
    @DisplayName("a task is due at its own not-before tick, and not before")
    void pendingTaskIsDueAtItsDeadline() {
        assertFalse(task(100L, 160L).isDue(159L), "one tick early is still early");
        assertTrue(task(100L, 160L).isDue(160L));
        assertTrue(task(100L, 160L).isDue(1000L));
        assertTrue(task(100L, 100L).isDue(100L), "no delay means immediately eligible");
    }

    @Test
    @DisplayName("the delay survives a save/load round trip")
    void delaySurvivesRoundTrip() {
        PendingPlacementTask restored = PendingPlacementTask.fromTag(task(100L, 160L).toTag());

        assertEquals(100L, restored.createdGameTime());
        assertEquals(160L, restored.notBeforeGameTime());
        assertFalse(restored.isDue(159L));
        assertTrue(restored.isDue(160L));
    }

    @Test
    @DisplayName("a task saved before the delay existed loads as immediately eligible")
    void legacyTaskWithoutDeadlineStaysEligible() {
        CompoundTag legacy = task(100L, 100L).toTag();
        legacy.remove("NotBeforeGameTime");

        PendingPlacementTask restored = PendingPlacementTask.fromTag(legacy);
        assertEquals(100L, restored.notBeforeGameTime(), "falls back to the creation time");
        assertTrue(restored.isDue(100L));
    }

    // ------------------------------------------------------------------ shipped rules

    @Test
    @DisplayName("every built-in entry is schema v4 and uses the nested spawn layout")
    void builtinsAreV4() throws IOException {
        for (Path file : builtinFiles()) {
            JsonObject json = read(file);
            String name = file.getFileName().toString();
            assertEquals(4, json.get("schema_version").getAsInt(), name);
            JsonObject spawn = json.getAsJsonObject("spawn");
            String mode = spawn.get("mode").getAsString();
            assertTrue(List.of("summon", "transform").contains(mode), name + ": spawn.mode " + mode);
            assertTrue(spawn.get(mode).isJsonObject(), name + ": spawn." + mode);
        }
    }

    @Test
    @DisplayName("the Phantom Knight hands over to the Twilight Forest spawner")
    void phantomKnightUsesTransformMode() throws IOException {
        JsonObject transform = builtin("twilightforest", "knight_phantom")
                .getAsJsonObject("spawn").getAsJsonObject("transform");

        assertEquals("twilightforest:knight_phantom_boss_spawner", transform.get("transform_block").getAsString(),
                "the mod's own spawner reproduces all six knights, their numbering and their loot");
        assertEquals(20, transform.get("delay_ticks").getAsInt(),
                "the cage must play its opening animation before handing over");
        assertEquals("anchor", transform.get("at").getAsString(),
                "the spawner must be placed at the boss's home, not at the cage beside the chest");
    }

    @Test
    @DisplayName("Twilight Forest bosses use the twilightforest mode; the Quest Ram keeps home + summon")
    void twilightForestRulesUseTwilightforestMode() throws IOException {
        // Twilight Forest drops boss loot into a chest when the boss is removed. The dedicated mode waits
        // for that removal and places the cage beside the boss's own HomePos, so the chest keeps its open
        // top and the boss's home does not move between cycles.
        for (String boss : new String[]{"naga", "lich", "minoshroom", "hydra", "knight_phantom", "ur_ghast",
                "alpha_yeti", "snow_queen"}) {
            JsonObject json = builtin("twilightforest", boss);
            JsonObject placement = json.getAsJsonObject("placement");
            assertEquals("twilightforest", placement.get("mode").getAsString(),
                    boss + ": placement.mode");
            JsonObject modeObject = placement.getAsJsonObject("twilightforest");
            assertTrue(modeObject.has("offset"), boss + ": placement.twilightforest.offset");
            assertEquals(0, modeObject.getAsJsonArray("offset").get(0).getAsInt(), boss + ": offset.x");
            assertEquals(-1, modeObject.getAsJsonArray("offset").get(1).getAsInt(), boss + ": offset.y");
            assertEquals(-1, modeObject.getAsJsonArray("offset").get(2).getAsInt(), boss + ": offset.z");
            assertEquals(0, modeObject.get("place_delay_ticks").getAsInt(),
                    boss + ": the removal wait replaces the old per-boss delay guess");
            assertFalse(modeObject.get("search_failure_fallback").getAsBoolean(),
                    boss + ": a failed search must not force the cage over the chest");
            assertTrue(modeObject.get("force_place").getAsBoolean(),
                    boss + ": the eight TF bosses use placement.force_place");
            assertFalse(modeObject.get("require_ground").getAsBoolean(),
                    boss + ": TF cages must be allowed to float (Ur-Ghast, broken arenas)");

            JsonObject spawn = json.getAsJsonObject("spawn");
            assertEquals("transform", spawn.get("mode").getAsString(), boss + ": spawn.mode");
            JsonObject transform = spawn.getAsJsonObject("transform");
            assertEquals(boss + "_boss_spawner",
                    transform.get("transform_block").getAsString().replace("twilightforest:", ""),
                    boss + ": must use its native boss spawner");
            assertEquals("anchor", transform.get("at").getAsString(),
                    boss + ": the spawner belongs at the home anchor");
            boolean forced = "lich".equals(boss) || "minoshroom".equals(boss);
            int expectedY = forced ? -1 : 0;
            assertEquals(expectedY, transform.getAsJsonArray("spawner_offset").get(1).getAsInt(),
                    boss + ": only the Lich/Minoshroom put the spawner on the reward-chest cell");
            assertEquals(forced, transform.get("force_place").getAsBoolean(),
                    boss + ": only the Lich/Minoshroom replace the reward chest");
        }

        // The Quest Ram is a normal Animal (no BaseTFBoss, no loot chest): keep the pre-change behaviour.
        JsonObject quest = builtin("twilightforest", "quest_ram");
        JsonObject placement = quest.getAsJsonObject("placement");
        assertEquals("home", placement.get("mode").getAsString(), "quest_ram: placement.mode");
        JsonObject home = placement.getAsJsonObject("home");
        assertEquals(30, home.get("place_delay_ticks").getAsInt(), "quest_ram: old home delay");
        assertFalse(home.get("force_place").getAsBoolean(), "quest_ram: old non-forcing home");
        assertTrue(home.get("search_failure_fallback").getAsBoolean(), "quest_ram: old fallback");
        assertTrue(home.get("require_ground").getAsBoolean(), "quest_ram: old ground rule");
        assertEquals("summon", quest.getAsJsonObject("spawn").get("mode").getAsString(),
                "quest_ram: summon");
    }

    @Test
    @DisplayName("mods whose placement was already reliable keep the immediate default")
    void otherModsKeepZeroDelay() throws IOException {
        JsonObject placement = builtin("legendary_monsters", "cloud_golem")
                .getAsJsonObject("placement").getAsJsonObject("home");
        assertEquals(0, placement.get("place_delay_ticks").getAsInt());
    }

    // ------------------------------------------------------------------ helpers

    private static List<Path> builtinFiles() throws IOException {
        Path root = TestPaths.dataPackRoot();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/boss_respawner/entries/"))
                    .sorted()
                    .toList();
        }
    }

    private static JsonObject builtin(String namespace, String name) throws IOException {
        Path file = TestPaths.dataPackRoot()
                .resolve(namespace + "/boss_respawner/entries/" + name + ".json");
        assertTrue(Files.isRegularFile(file), "missing built-in entry " + file);
        return read(file);
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static PendingPlacementTask task(long created, long notBefore) {
        return new PendingPlacementTask(
                Level.OVERWORLD, BlockPos.ZERO, UUID.randomUUID(),
                ResourceLocation.parse("minecraft:warden"), null, snapshot(), created, notBefore);
    }

    private static RespawnRuleSnapshot snapshot() {
        return RespawnRuleSnapshot.of(parseOrFail("{}", MINIMAL_SPAWN));
    }

    private static RespawnEntry parseOrFail(String homeJson, String spawnJson) {
        Optional<RespawnEntry> entry = parse(homeJson, spawnJson);
        assertTrue(entry.isPresent(), "expected the entry to parse");
        return entry.get();
    }

    private static Optional<RespawnEntry> parse(String homeJson, String spawnJson) {
        String json = """
                {
                  "schema_version": 4,
                  "entity": "minecraft:warden",
                  "activation": { "item": "minecraft:echo_shard" },
                  "placement": { "mode": "home", "home": %s },
                  "spawn": %s
                }
                """.formatted(homeJson, spawnJson);
        return RespawnEntry.fromJson(ENTRY, JsonParser.parseString(json).getAsJsonObject());
    }
}
