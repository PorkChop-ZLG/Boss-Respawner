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
 * Guards the source-level half of the migration: the two removed global config options must be gone and
 * the new protection floor must actually be declared and used.
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
    @DisplayName("the protected-block floor is declared and enforced")
    void protectedBlockFloorIsWired() throws IOException {
        List<SourceFile> sources = mainSources();

        SourceFile config = sources.stream()
                .filter(s -> "Config.java".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Config.java not found"));
        assertTrue(config.text().contains("PROTECTED_BLOCK_IDS"),
                "Config.java must declare PROTECTED_BLOCK_IDS");
        assertTrue(config.text().contains("cataclysm:boss_respawner"),
                "the protected list must default to including the Cataclysm altar block");

        boolean referencedOutsideConfig = sources.stream()
                .filter(s -> !"Config.java".equals(s.name()))
                .anyMatch(s -> s.text().contains("PROTECTED_BLOCK_IDS"));
        assertTrue(referencedOutsideConfig,
                "PROTECTED_BLOCK_IDS must be consulted by the placement code, not merely declared");
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
