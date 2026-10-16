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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema v3: {@code spawn} became a mode plus a mode object, and {@code placement.<mode>} gained
 * {@code place_delay_ticks}.
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
    @DisplayName("a transform rule parses into its own object with its four fields")
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
    @DisplayName("every built-in entry is schema v3 and uses the nested spawn layout")
    void builtinsAreV3() throws IOException {
        for (Path file : builtinFiles()) {
            JsonObject json = read(file);
            String name = file.getFileName().toString();
            assertEquals(3, json.get("schema_version").getAsInt(), name);
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
    }

    @Test
    @DisplayName("Twilight Forest cages wait out the death animation before placing")
    void twilightForestRulesDelayPlacement() throws IOException {
        // Twilight Forest drops boss loot into a chest once the death animation finishes (20 ticks, 88 for
        // the Phantom Knight). A cage that arrives first can be refused because of that chest - which is
        // what stopped the Minoshroom cage from appearing at all.
        for (String boss : new String[]{"naga", "lich", "minoshroom", "hydra", "knight_phantom", "ur_ghast",
                "alpha_yeti", "snow_queen", "quest_ram"}) {
            JsonObject placement = builtin("twilightforest", boss)
                    .getAsJsonObject("placement").getAsJsonObject("home");
            assertTrue(placement.get("place_delay_ticks").getAsInt() > 0,
                    boss + " must wait for the death animation and its loot chest");
        }
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
                  "schema_version": 3,
                  "entity": "minecraft:warden",
                  "activation": { "item": "minecraft:echo_shard" },
                  "placement": { "mode": "home", "home": %s },
                  "spawn": %s
                }
                """.formatted(homeJson, spawnJson);
        return RespawnEntry.fromJson(ENTRY, JsonParser.parseString(json).getAsJsonObject());
    }
}
