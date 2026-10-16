package com.zonlong.bossrespawner.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema v4 parsing rules for {@link RespawnEntry}.
 *
 * <p>Covers the behaviours required by the implementation handoff: nested {@code placement.death} /
 * {@code placement.home} objects, no implicit {@code mode} default, mode-object presence checks,
 * documented field defaults, and rejection of the schema v1 flat layout.
 *
 * <p>Schema v3 also requires {@code spawn.mode} plus its mode object; a schema v2 flat {@code spawn} is
 * covered by {@link SchemaAdditionsTest}, so every rule built here carries a minimal valid {@code spawn}.
 *
 * <p>Schema v4 removed {@code protected_blocks} entirely: {@code avoid_blocks} is the only block list, and
 * no field of {@link RespawnEntry.PlacementRule} can express an unoverwritable block any more. The tests
 * below therefore pin the new contract - a leftover v3 {@code protected_blocks} key is parsed but has no
 * effect at all, so a half-migrated rule cannot silently keep a protection floor that no longer exists.
 */
class RespawnEntrySchemaTest {

    private static final ResourceLocation ID = ResourceLocation.parse("test:entry");

    private static Optional<RespawnEntry> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return RespawnEntry.fromJson(ID, root);
    }

    /** Minimal valid rule; the caller supplies the {@code placement} object body. */
    private static String entryWith(String placementBody) {
        return """
                {
                  "schema_version": 4,
                  "entity": "minecraft:warden",
                  "activation": { "item": "minecraft:echo_shard" },
                  "placement": %s,
                  "spawn": { "mode": "summon", "summon": {} }
                }
                """.formatted(placementBody);
    }

    /**
     * Builds a complete rule whose {@code schema_version} line is supplied by the caller, so a version
     * gate can be tested in isolation.
     *
     * @param schemaVersionLine the full line including its comma, or an empty string to omit the field
     */
    private static Optional<RespawnEntry> parseVersioned(String schemaVersionLine, String placementBody,
                                                         String spawnBody) {
        return parse("""
                {
                  %s
                  "entity": "minecraft:warden",
                  "activation": { "item": "minecraft:echo_shard" },
                  "placement": %s,
                  "spawn": %s
                }
                """.formatted(schemaVersionLine, placementBody, spawnBody));
    }

    private static RespawnEntry parseOrFail(String json) {
        Optional<RespawnEntry> parsed = parse(json);
        assertTrue(parsed.isPresent(), "expected the entry to parse, but it was rejected");
        return parsed.get();
    }

    // ---------------------------------------------------------------- happy paths

    @Test
    @DisplayName("nested home placement parses and resolves mode HOME")
    void homePlacementParses() {
        RespawnEntry entry = parseOrFail(entryWith("""
                {
                  "mode": "home",
                  "home": {
                    "offset": [0, -1, 0],
                    "force_place": true,
                    "search_failure_fallback": false,
                    "search_down": 8,
                    "search_up": 4,
                    "horizontal_radius": 2,
                    "require_ground": false,
                    "avoid_fluids": true,
                    "avoid_blocks": ["minecraft:bedrock", "minecraft:chest"]
                  }
                }
                """));

        RespawnEntry.PlacementRule placement = entry.placement();
        assertEquals(RespawnEntry.PlacementMode.HOME, placement.mode());
        assertArrayEquals(new int[]{0, -1, 0}, placement.offset());
        assertTrue(placement.forcePlace());
        assertFalse(placement.searchFailureFallback());
        assertEquals(8, placement.searchDown());
        assertEquals(4, placement.searchUp());
        assertEquals(2, placement.horizontalRadius());
        assertFalse(placement.requireGround());
        assertTrue(placement.avoidFluids());
        assertEquals(java.util.Set.of(
                        ResourceLocation.parse("minecraft:bedrock"),
                        ResourceLocation.parse("minecraft:chest")),
                placement.avoidBlocks(),
                "avoid_blocks is the only block list in v4, so every entry must survive here");
    }

    @Test
    @DisplayName("nested death placement parses and resolves mode DEATH")
    void deathPlacementParses() {
        RespawnEntry entry = parseOrFail(entryWith("""
                {
                  "mode": "death",
                  "death": { "offset": [1, 2, 3] }
                }
                """));

        assertEquals(RespawnEntry.PlacementMode.DEATH, entry.placement().mode());
        assertArrayEquals(new int[]{1, 2, 3}, entry.placement().offset());
    }

    @Test
    @DisplayName("an empty mode object is valid and yields the documented defaults")
    void emptyModeObjectUsesDocumentedDefaults() {
        RespawnEntry home = parseOrFail(entryWith("""
                { "mode": "home", "home": {} }
                """));
        RespawnEntry death = parseOrFail(entryWith("""
                { "mode": "death", "death": {} }
                """));

        for (RespawnEntry.PlacementRule placement : java.util.List.of(home.placement(), death.placement())) {
            assertArrayEquals(new int[]{0, 0, 0}, placement.offset());
            assertFalse(placement.forcePlace(), "force_place must default to false");
            assertTrue(placement.searchFailureFallback(), "search_failure_fallback must default to true");
            assertEquals(32, placement.searchDown());
            assertEquals(16, placement.searchUp());
            assertEquals(4, placement.horizontalRadius());
            assertTrue(placement.requireGround());
            assertFalse(placement.avoidFluids());
            assertTrue(placement.avoidBlocks().isEmpty());
        }
        assertEquals(RespawnEntry.PlacementMode.HOME, home.placement().mode());
        assertEquals(RespawnEntry.PlacementMode.DEATH, death.placement().mode());
    }

    @Test
    @DisplayName("avoid_blocks parses on its own, and a leftover v3 protected_blocks key has no effect")
    void avoidBlocksParseIndependently() {
        // The v3 shape is kept in the file on purpose: a rule that only bumps schema_version and forgets to
        // move its protected ids must not keep any protection. In v4 the key is simply never read.
        RespawnEntry entry = parseOrFail(entryWith("""
                {
                  "mode": "home",
                  "home": {
                    "avoid_blocks": ["cataclysm:boss_respawner", "minecraft:spawner"],
                    "protected_blocks": ["minecraft:chest"]
                  }
                }
                """));

        assertEquals(java.util.Set.of(
                        ResourceLocation.parse("cataclysm:boss_respawner"),
                        ResourceLocation.parse("minecraft:spawner")),
                entry.placement().avoidBlocks(),
                "avoid_blocks must be parsed exactly, independently of the other mode fields");
        assertFalse(entry.placement().avoidBlocks().contains(ResourceLocation.parse("minecraft:chest")),
                "a leftover v3 protected_blocks key must not leak into avoid_blocks either");
    }

    @Test
    @DisplayName("PlacementRule cannot express an unoverwritable block any more")
    void protectedBlocksAccessorIsGone() {
        // Guards the deletion itself rather than a behaviour: if protectedBlocks() ever comes back, the
        // search/write paths could start refusing placements again (the ABANDON failure mode that motivated
        // v4), and this test must fail loudly before that ships.
        java.util.List<String> accessors = java.util.Arrays.stream(RespawnEntry.PlacementRule.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("protected"))
                .sorted()
                .toList();
        assertTrue(accessors.isEmpty(),
                "PlacementRule still exposes protected-block accessors: " + accessors);
    }

    @Test
    @DisplayName("the avoid_blocks list is parsed eagerly, dropping malformed ids but keeping unregistered ones")
    void blockIdListsAreParsedDefensively() {
        RespawnEntry entry = parseOrFail(entryWith("""
                {
                  "mode": "home",
                  "home": {
                    "avoid_blocks": ["minecraft:bedrock", "###", "othermod:not_installed"]
                  }
                }
                """));

        // "###" is not a valid resource location and is dropped; "othermod:not_installed" is well formed
        // and is kept even though that block is not installed - compatibility lists rely on that.
        assertEquals(java.util.Set.of(
                        ResourceLocation.parse("minecraft:bedrock"),
                        ResourceLocation.parse("othermod:not_installed")),
                entry.placement().avoidBlocks(),
                "malformed ids must be dropped, unregistered-but-valid ids must survive");
    }

    // ---------------------------------------------------------------- rejections

    @Test
    @DisplayName("a missing placement.mode is rejected (no implicit home default)")
    void missingModeIsRejected() {
        assertTrue(parse(entryWith("""
                { "home": { "offset": [0, 0, 0] } }
                """)).isEmpty());
    }

    @Test
    @DisplayName("an unknown placement.mode is rejected")
    void invalidModeIsRejected() {
        assertTrue(parse(entryWith("""
                { "mode": "hom", "home": {} }
                """)).isEmpty());
    }

    @Test
    @DisplayName("selecting a mode without its object is rejected")
    void missingSelectedModeObjectIsRejected() {
        // mode: home, but only placement.death is present
        assertTrue(parse(entryWith("""
                { "mode": "home", "death": {} }
                """)).isEmpty());

        // mode: death, but only placement.home is present
        assertTrue(parse(entryWith("""
                { "mode": "death", "home": {} }
                """)).isEmpty());

        // mode present but neither object
        assertTrue(parse(entryWith("""
                { "mode": "home" }
                """)).isEmpty());
    }

    @Test
    @DisplayName("a non-object mode value is rejected")
    void nonObjectModeValueIsRejected() {
        assertTrue(parse(entryWith("""
                { "mode": "home", "home": 42 }
                """)).isEmpty());
    }

    @Test
    @DisplayName("the schema v1 flat placement layout is rejected, not reinterpreted")
    void schemaV1FlatPlacementIsRejected() {
        assertTrue(parse(entryWith("""
                { "offset": [0, 0, 0], "search_down": 32, "avoid_blocks": ["minecraft:bedrock"] }
                """)).isEmpty());
    }

    @Test
    @DisplayName("unsupported, missdeclared or missing schema_version is rejected")
    void schemaVersionIsValidated() {
        String placement = """
                { "mode": "home", "home": {} }
                """;
        String spawn = """
                { "mode": "summon", "summon": {} }
                """;

        // 4 is the only supported version: with the rest of the rule valid, it must parse.
        assertTrue(parseVersioned("\"schema_version\": 4,", placement, spawn).isPresent(),
                "schema_version 4 is the supported version and must parse");

        // v1 is no longer accepted.
        assertTrue(parseVersioned("\"schema_version\": 1,", placement, spawn).isEmpty(),
                "schema v1 must be rejected");

        // v2 is the previous version and is rejected as well; its flat spawn layout is separate from this
        // gate (see SchemaAdditionsTest), so this case declares v2 with an otherwise valid v3/v4 body.
        assertTrue(parseVersioned("\"schema_version\": 2,", placement, spawn).isEmpty(),
                "schema v2 is no longer supported and must be rejected");

        // v3 became unsupported in v4: its protected_blocks field is gone, so a v3 rule must be migrated
        // rather than silently accepted with the protection silently dropped.
        assertTrue(parseVersioned("\"schema_version\": 3,", placement, spawn).isEmpty(),
                "schema v3 must be rejected now that v4 removed protected_blocks");

        // missing schema_version is an error, not a default
        assertTrue(parseVersioned("", placement, spawn).isEmpty(),
                "a missing schema_version must be rejected");

        // a future version is rejected too
        assertTrue(parseVersioned("\"schema_version\": 5,", placement, spawn).isEmpty(),
                "a future schema_version must be rejected");
    }

    @Test
    @DisplayName("a missing placement object is rejected")
    void missingPlacementObjectIsRejected() {
        assertTrue(parse("""
                {
                  "schema_version": 4,
                  "entity": "minecraft:warden",
                  "activation": { "item": "minecraft:echo_shard" },
                  "spawn": { "mode": "summon", "summon": {} }
                }
                """).isEmpty());
    }

    @Test
    @DisplayName("entity and activation.item stay mandatory")
    void mandatoryTopLevelFieldsAreEnforced() {
        assertTrue(parse("""
                {
                  "schema_version": 4,
                  "activation": { "item": "minecraft:echo_shard" },
                  "placement": { "mode": "home", "home": {} },
                  "spawn": { "mode": "summon", "summon": {} }
                }
                """).isEmpty(), "missing entity must be rejected");

        assertTrue(parse("""
                {
                  "schema_version": 4,
                  "entity": "minecraft:warden",
                  "placement": { "mode": "home", "home": {} },
                  "spawn": { "mode": "summon", "summon": {} }
                }
                """).isEmpty(), "missing activation.item must be rejected");
    }

    @Test
    @DisplayName("enabled:false is ignored, and entity may be an array")
    void enabledAndEntityArrayBehaveAsBefore() {
        assertTrue(parse("""
                {
                  "schema_version": 4,
                  "enabled": false,
                  "entity": "minecraft:warden",
                  "activation": { "item": "minecraft:echo_shard" },
                  "placement": { "mode": "home", "home": {} },
                  "spawn": { "mode": "summon", "summon": {} }
                }
                """).isEmpty());

        RespawnEntry entry = parseOrFail("""
                {
                  "schema_version": 4,
                  "entity": ["minecraft:warden", "minecraft:ravager"],
                  "activation": { "item": "minecraft:echo_shard" },
                  "placement": { "mode": "home", "home": {} },
                  "spawn": { "mode": "summon", "summon": {} }
                }
                """);
        assertEquals(2, entry.entities().size());
    }
}
