package com.zonlong.bossrespawner.data;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the manual acceptance data pack honest.
 *
 * <p>{@code docs/examples/acceptance-datapack} is what a human loads in game to walk through
 * {@code docs/manual-acceptance-2026-10-16.md}. If one of its supposedly-valid entries stopped parsing, or
 * one of the deliberately-broken ones started parsing, the checklist would silently become wrong. This test
 * pins both halves.
 */
class AcceptanceDatapackTest {

    private static final Path DATAPACK = TestPaths.projectDir()
            .resolve("docs/examples/acceptance-datapack");

    private static final Path ENTRIES = DATAPACK.resolve("data/acceptance/boss_respawner/entries");

    /** Entries that must load, each exercising a different placement behaviour. */
    private static final List<String> VALID = List.of(
            "01_home_ravager.json",
            "02_death_zombie.json",
            "03_force_place_over_avoid.json",
            "04_fallback_overwrites.json",
            "05_no_fallback.json",
            "06_duplicate_keep_existing.json");

    /**
     * Entries that must be rejected: the v1 structure, the v1 flat placement layout, a missing
     * {@code placement.mode} and the v3 layout that v4 superseded. 08 and 09 still declare
     * {@code schema_version: 2} and 10 declares {@code 3}, so the version gate refuses all three outright -
     * the flattened placement, the missing mode and the removed {@code protected_blocks} are pinned against
     * a valid v4 body in {@code RespawnEntrySchemaTest} instead.
     */
    private static final List<String> INVALID = List.of(
            "07_rejected_v1_schema.json",
            "08_rejected_flat_placement.json",
            "09_rejected_missing_mode.json",
            "10_rejected_v3_schema.json");

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Optional<RespawnEntry> parse(Path file) throws IOException {
        return RespawnEntry.fromJson(
                ResourceLocation.fromNamespaceAndPath("acceptance", file.getFileName().toString()),
                read(file));
    }

    private static List<Path> entryFiles() throws IOException {
        assertTrue(Files.isDirectory(ENTRIES),
                "acceptance data pack entries not found at " + ENTRIES.toAbsolutePath());
        try (Stream<Path> list = Files.list(ENTRIES)) {
            return list.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    @DisplayName("the acceptance data pack ships every entry the checklist refers to")
    void allExpectedEntriesExist() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : entryFiles()) {
            found.add(file.getFileName().toString());
        }
        List<String> expected = new ArrayList<>(VALID);
        expected.addAll(INVALID);
        expected.sort(String::compareTo);
        assertEquals(expected, found);
    }

    @Test
    @DisplayName("the six positive entries parse and carry the behaviour they advertise")
    void positiveEntriesParseWithIntendedShape() throws IOException {
        for (String name : VALID) {
            Path file = ENTRIES.resolve(name);
            Optional<RespawnEntry> parsed = parse(file);
            assertTrue(parsed.isPresent(), name + " must parse; the checklist depends on it");
            assertFalse(parsed.get().activation().consume(),
                    name + ": acceptance keys must not be consumed, so a tester never runs out");

            // The pack is schema v4: nested spawn, an explicit place_delay_ticks in the selected placement
            // mode object, and no protected_blocks anywhere. A tester copies this pack in game, so it must not
            // fall back to defaults or rely on a superseded layout.
            JsonObject json = read(file);
            assertEquals(4, json.get("schema_version").getAsInt(), name + ": schema_version");
            JsonObject spawn = json.getAsJsonObject("spawn");
            assertEquals("summon", spawn.get("mode").getAsString(), name + ": spawn.mode");
            assertTrue(spawn.getAsJsonObject("summon").has("delay_ticks"), name + ": spawn.summon");
            JsonObject modeObject = json.getAsJsonObject("placement")
                    .getAsJsonObject(parsed.get().placement().mode() == RespawnEntry.PlacementMode.HOME
                            ? "home" : "death");
            assertTrue(modeObject.has("place_delay_ticks"), name + ": placement.<mode>.place_delay_ticks");
            assertFalse(modeObject.has("protected_blocks"),
                    name + ": protected_blocks was removed in schema v4 and must not linger in the pack");

            RespawnEntry.PlacementRule placement = parsed.get().placement();
            assertTrue(placement.avoidBlocks().isEmpty()
                            || placement.avoidBlocks().equals(java.util.Set.of(ResourceLocation.parse("minecraft:chest"))),
                    name + ": the acceptance pack avoids chests or nothing at all, got " + placement.avoidBlocks());
        }

        // Home vs death anchors.
        assertEquals(RespawnEntry.PlacementMode.HOME,
                parse(ENTRIES.resolve("01_home_ravager.json")).orElseThrow().placement().mode());
        assertEquals(RespawnEntry.PlacementMode.DEATH,
                parse(ENTRIES.resolve("02_death_zombie.json")).orElseThrow().placement().mode());

        // 03 vs 04: the two write paths that skip the search result. Both target the same block (same offset)
        // and both avoid chests, so together they cover every path on which avoid_blocks cannot save a block.
        RespawnEntry forcePlaceEntry = parse(ENTRIES.resolve("03_force_place_over_avoid.json")).orElseThrow();
        RespawnEntry fallbackEntry = parse(ENTRIES.resolve("04_fallback_overwrites.json")).orElseThrow();

        assertTrue(forcePlaceEntry.placement().forcePlace(),
                "03 must force place, which skips the search and therefore avoid_blocks entirely");
        assertFalse(fallbackEntry.placement().forcePlace(),
                "04 must not force place, so it is the search-then-fallback path and not a copy of 03");
        assertTrue(fallbackEntry.placement().searchFailureFallback(),
                "04 must keep the fallback enabled, otherwise the search failure would place nothing");

        assertEquals(java.util.Set.of(ResourceLocation.parse("minecraft:chest")),
                forcePlaceEntry.placement().avoidBlocks(),
                "03 must avoid chests, otherwise it proves nothing about force_place ignoring them");
        assertEquals(forcePlaceEntry.placement().avoidBlocks(), fallbackEntry.placement().avoidBlocks(),
                "both must avoid chests, otherwise 04 does not prove the fallback overwrites an avoided block");
        assertEquals(forcePlaceEntry.placement().offset()[1], fallbackEntry.placement().offset()[1],
                "both must use the same vertical offset so they target the same block");

        // 04's search cannot possibly succeed: every radius is zero, so base is the only candidate, and with a
        // chest occupying base that candidate is rejected as neither air nor replaceable - in both passes,
        // because the avoid filter is not what rejects it. require_ground stays on so 04 and 05 share one
        // search shape. That is what makes the fallback - rather than the search - responsible for the cage the
        // checklist expects to see.
        assertEquals(0, fallbackEntry.placement().searchDown());
        assertEquals(0, fallbackEntry.placement().searchUp());
        assertEquals(0, fallbackEntry.placement().horizontalRadius());
        assertTrue(fallbackEntry.placement().requireGround(),
                "04 must require ground so an occupied base has no valid candidate at all");

        // 05: the no-fallback entry must be unable to find any candidate at all.
        RespawnEntry noFallback = parse(ENTRIES.resolve("05_no_fallback.json")).orElseThrow();
        assertFalse(noFallback.placement().searchFailureFallback(),
                "05 must disable the fallback so the checklist can prove nothing is placed");
        assertEquals(0, noFallback.placement().searchDown());
        assertEquals(0, noFallback.placement().searchUp());
        assertEquals(0, noFallback.placement().horizontalRadius());
        assertTrue(noFallback.placement().requireGround(),
                "05 must require ground so a mid-air home has no valid candidate");
        assertEquals(noFallback.placement().searchDown(), fallbackEntry.placement().searchDown(),
                "04 and 05 must share the search shape: the only difference is search_failure_fallback");
        assertNotEquals(noFallback.placement().searchFailureFallback(),
                fallbackEntry.placement().searchFailureFallback(),
                "04 and 05 differ only in the fallback flag, which is exactly the pair the checklist walks");

        // P8: keep_existing must be set for the duplicate test.
        assertEquals("keep_existing",
                parse(ENTRIES.resolve("06_duplicate_keep_existing.json")).orElseThrow().duplicate().mode());
    }

    @Test
    @DisplayName("the four negative entries are rejected")
    void negativeEntriesAreRejected() throws IOException {
        for (String name : INVALID) {
            Path file = ENTRIES.resolve(name);
            assertTrue(parse(file).isEmpty(),
                    name + " must be rejected; the checklist treats it as a negative test");
        }
    }

    @Test
    @DisplayName("each negative entry carries the shape the checklist expects, and is rejected")
    void negativeEntriesFailForDistinctReasons() throws IOException {
        // R1: schema_version 1 - the v1 structure, rejected by the version gate.
        assertEquals(1, read(ENTRIES.resolve("07_rejected_v1_schema.json")).get("schema_version").getAsInt());

        // R2: the schema v1 flat placement layout - fields straight under "placement" and no mode.
        JsonObject flat = read(ENTRIES.resolve("08_rejected_flat_placement.json"));
        assertEquals(2, flat.get("schema_version").getAsInt());
        assertTrue(flat.getAsJsonObject("placement").has("offset"),
                "08 must actually contain the legacy flat keys");
        assertFalse(flat.getAsJsonObject("placement").has("mode"));

        // R3: has placement.home but no mode at all.
        JsonObject noMode = read(ENTRIES.resolve("09_rejected_missing_mode.json"));
        assertEquals(2, noMode.get("schema_version").getAsInt());
        assertTrue(noMode.getAsJsonObject("placement").has("home"),
                "09 must have a valid mode object so that the missing mode is the layout it documents");
        assertFalse(noMode.getAsJsonObject("placement").has("mode"));

        // R4: the schema v3 layout - an otherwise valid v4 body whose placement.home still declares the
        // protected_blocks field that v4 deleted. v3 is no longer an accepted version, which is exactly the
        // "old structure recognised, migration named" path the checklist documents.
        JsonObject v3 = read(ENTRIES.resolve("10_rejected_v3_schema.json"));
        assertEquals(3, v3.get("schema_version").getAsInt());
        assertEquals("home", v3.getAsJsonObject("placement").get("mode").getAsString(),
                "10 must select a mode, so it is rejected for its version and not for a malformed placement");
        assertTrue(v3.getAsJsonObject("placement").getAsJsonObject("home").has("protected_blocks"),
                "10 must actually contain the v3 protected_blocks field it documents");
        assertEquals("summon", v3.getAsJsonObject("spawn").get("mode").getAsString(),
                "10 must be valid apart from its version, so the version gate is the only rejection reason");
        assertTrue(parse(ENTRIES.resolve("10_rejected_v3_schema.json")).isEmpty(),
                "a v3 rule must not be silently accepted with its protection dropped");

        // 08. 09 and 10 keep their superseded layout on purpose: under v4 they are rejected by the
        // schema_version gate before placement or spawn is read. The dedicated "flat placement" / "missing
        // placement.mode" warnings are pinned by RespawnEntrySchemaTest against a valid v4 body instead.
        for (String name : List.of("08_rejected_flat_placement.json", "09_rejected_missing_mode.json")) {
            JsonObject v2 = read(ENTRIES.resolve(name));
            assertTrue(v2.getAsJsonObject("spawn").has("delay_ticks"),
                    name + ": the flat v2 spawn fields must stay, so the file documents the v2 structure");
            assertFalse(v2.getAsJsonObject("spawn").has("mode"),
                    name + ": a v2 file has no spawn.mode");
        }

        // No negative entry may carry a v4-spelled schema_version: an entry that both claims to be current and
        // is expected to be rejected would make the checklist self-contradictory.
        for (String name : INVALID) {
            assertNotEquals(4, read(ENTRIES.resolve(name)).get("schema_version").getAsInt(),
                    name + ": a negative entry must not declare the supported schema_version");
        }
    }

    @Test
    @DisplayName("the data pack declares a pack_format for 1.21.1")
    void packFormatIsPresent() throws IOException {
        JsonObject meta = JsonParser.parseString(
                Files.readString(DATAPACK.resolve("pack.mcmeta"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(48, meta.getAsJsonObject("pack").get("pack_format").getAsInt(),
                "resource/data pack format for Minecraft 1.21.x is 48");
    }
}
