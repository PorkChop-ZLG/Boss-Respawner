package com.zonlong.bossrespawner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the source-level half of the migration: the removed global config options must be gone, the
 * protection floor that v4 deleted must not creep back, and the two-pass avoid search must stay wired.
 *
 * <p>These assertions are cheap and catch a half-finished refactor that still compiles because a stale
 * constant happened to be left behind.
 */
class SourceMigrationTest {

    private static final Path MAIN_SOURCES = TestPaths.mainSources();

    private record SourceFile(Path file, String text) {
        String name() {
            return file.getFileName().toString();
        }
    }

    private static List<SourceFile> mainSources() throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCES),
                "expected main sources at " + MAIN_SOURCES.toAbsolutePath()
                        + " - the test working directory must be the project directory");
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            List<SourceFile> files = new ArrayList<>();
            for (Path path : walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                files.add(new SourceFile(path, Files.readString(path, StandardCharsets.UTF_8)));
            }
            return files;
        }
    }

    @Test
    @DisplayName("the removed global config options are no longer referenced in production code")
    void removedConfigOptionsAreGone() throws IOException {
        for (SourceFile source : mainSources()) {
            assertFalse(source.text().contains("FORCE_PLACE_ON_NO_SAFE_SPOT"),
                    source.name() + " still references the removed FORCE_PLACE_ON_NO_SAFE_SPOT");
            assertFalse(source.text().contains("FOREIGN_CAGE_BLOCK_IDS"),
                    source.name() + " still references the removed FOREIGN_CAGE_BLOCK_IDS");
        }
    }

    @Test
    @DisplayName("the removed protected-block floor stays removed, and the two-pass avoid search stays wired")
    void protectionFloorIsGoneAndAvoidRetries() throws IOException {
        List<SourceFile> sources = mainSources();

        // v4 deleted the field, the global config option and the refusal path: a protected-block floor
        // layered on top of a hard avoid filter is what made Minoshroom / Alpha Yeti / Snow Queen cages fail
        // to appear at all (avoid -> no candidate -> fallback -> refused). Re-adding any of it here must fail
        // this test before it can ship.
        for (SourceFile source : sources) {
            assertFalse(source.text().contains("PROTECTED_BLOCK_IDS"),
                    source.name() + " reintroduces the removed global protection list");
            assertFalse(source.text().contains("protectedBlockIds"),
                    source.name() + " reintroduces the removed protectedBlockIds config option");
            assertFalse(source.text().contains("protectedBlocks"),
                    source.name() + " reintroduces a protected-blocks field on the placement rule");
            assertFalse(source.text().contains("Refusing to place the respawn cage"),
                    source.name() + " reintroduces the protected-block refusal that abandoned placements");
        }

        // The replacement is the two-pass search in PlacementResolver: pass one honours avoid_blocks, pass two
        // repeats the identical sweep with the avoid filter dropped, so the filter can never lose a cage.
        SourceFile resolver = sources.stream()
                .filter(s -> "PlacementResolver.java".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PlacementResolver.java not found"));
        assertTrue(resolver.text().contains("search(level, base, placement, true)"),
                "the first search pass must honour avoid_blocks");
        assertTrue(resolver.text().contains("search(level, base, placement, false)"),
                "the second search pass must drop the avoid filter instead of giving up");
    }

    @Test
    @DisplayName("no unloaded_target JSON field was introduced")
    void noUnloadedTargetField() throws IOException {
        for (SourceFile source : mainSources()) {
            assertFalse(source.text().contains("unloaded_target"),
                    source.name() + " mentions unloaded_target; the unloaded-chunk policy is fixed in Java");
        }
    }

    @Test
    @DisplayName("the old reflection-based HomePos locator is not reintroduced")
    void noReflectionLocator() throws IOException {
        for (SourceFile source : mainSources()) {
            assertFalse(source.text().contains("HomePosLocator"),
                    source.name() + " reintroduces HomePosLocator");
            assertFalse(source.text().contains("getRestrictCenter"),
                    source.name() + " calls a third-party home accessor");
        }
    }

    @Test
    @DisplayName("the respawn cage file id has no path separator, or every save would fail silently")
    void savedDataFileIdIsFlat() throws IOException {
        SourceFile savedData = mainSources().stream()
                .filter(s -> "BossHomeSavedData.java".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("BossHomeSavedData.java not found"));
        assertTrue(savedData.text().contains("FILE_ID = \"boss_respawner_homes\""),
                "the saved-data file id must be a single flat name");
    }
}
