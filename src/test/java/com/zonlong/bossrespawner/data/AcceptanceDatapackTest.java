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
            "03_force_place_protected.json",
            "04_force_place_overwrite.json",
            "05_no_fallback.json",
            "06_duplicate_keep_existing.json");

    /** Entries that must be rejected, each for a different reason. */
    private static final List<String> INVALID = List.of(
            "07_rejected_v1_schema.json",
            "08_rejected_flat_placement.json",
            "09_rejected_missing_mode.json");

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
        }

        // Home vs death anchors.
        assertEquals(RespawnEntry.PlacementMode.HOME,
                parse(ENTRIES.resolve("01_home_ravager.json")).orElseThrow().placement().mode());
        assertEquals(RespawnEntry.PlacementMode.DEATH,
                parse(ENTRIES.resolve("02_death_zombie.json")).orElseThrow().placement().mode());

        // P4 vs P5: the two entries differ only in protected_blocks, and both avoid chests.
        RespawnEntry protectedEntry = parse(ENTRIES.resolve("03_force_place_protected.json")).orElseThrow();
        RespawnEntry overwriteEntry = parse(ENTRIES.resolve("04_force_place_overwrite.json")).orElseThrow();
        assertTrue(protectedEntry.placement().forcePlace() && overwriteEntry.placement().forcePlace(),
                "both entries must force place, so the only difference is protection");
        assertEquals(List.of("minecraft:chest"), protectedEntry.placement().protectedBlocks(),
                "the protected entry must protect chests");
        assertTrue(overwriteEntry.placement().protectedBlocks().isEmpty(),
                "the overwrite entry must protect nothing");
        assertEquals(protectedEntry.placement().avoidBlocks(), overwriteEntry.placement().avoidBlocks(),
                "both must avoid chests, otherwise P4 does not prove avoid_blocks is bypassed");
        assertEquals(protectedEntry.placement().offset()[1], overwriteEntry.placement().offset()[1],
                "both must use the same vertical offset so they target the same block");

        // P7: the no-fallback entry must be unable to find any candidate at all.
        RespawnEntry noFallback = parse(ENTRIES.resolve("05_no_fallback.json")).orElseThrow();
        assertFalse(noFallback.placement().searchFailureFallback(),
                "05 must disable the fallback so the checklist can prove nothing is placed");
        assertEquals(0, noFallback.placement().searchDown());
        assertEquals(0, noFallback.placement().searchUp());
        assertEquals(0, noFallback.placement().horizontalRadius());
        assertTrue(noFallback.placement().requireGround(),
                "05 must require ground so a mid-air home has no valid candidate");

        // P8: keep_existing must be set for the duplicate test.
        assertEquals("keep_existing",
                parse(ENTRIES.resolve("06_duplicate_keep_existing.json")).orElseThrow().duplicate().mode());
    }

    @Test
    @DisplayName("the three negative entries are rejected")
    void negativeEntriesAreRejected() throws IOException {
        for (String name : INVALID) {
            Path file = ENTRIES.resolve(name);
            assertTrue(parse(file).isEmpty(),
                    name + " must be rejected; the checklist treats it as a negative test");
        }
    }

    @Test
    @DisplayName("each negative entry fails for the reason the checklist expects")
    void negativeEntriesFailForDistinctReasons() throws IOException {
        // R1: schema_version 1.
        assertEquals(1, read(ENTRIES.resolve("07_rejected_v1_schema.json")).get("schema_version").getAsInt());

        // R2: schema_version 2 but a flat placement with no mode - the dedicated v1-layout detection.
        JsonObject flat = read(ENTRIES.resolve("08_rejected_flat_placement.json"));
        assertEquals(2, flat.get("schema_version").getAsInt());
        assertTrue(flat.getAsJsonObject("placement").has("offset"),
                "08 must actually contain the legacy flat keys");
        assertFalse(flat.getAsJsonObject("placement").has("mode"));

        // R3: has placement.home but no mode at all.
        JsonObject noMode = read(ENTRIES.resolve("09_rejected_missing_mode.json"));
        assertEquals(2, noMode.get("schema_version").getAsInt());
        assertTrue(noMode.getAsJsonObject("placement").has("home"),
                "09 must have a valid mode object, so only the missing mode rejects it");
        assertFalse(noMode.getAsJsonObject("placement").has("mode"));
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
