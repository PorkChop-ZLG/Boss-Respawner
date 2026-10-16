package com.zonlong.bossrespawner;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the project directory for tests that inspect files on disk.
 *
 * <p>NeoForge's JUnit runner sets the working directory to the game directory
 * ({@code build/minecraft-junit}), not the project directory, so neither a relative path nor
 * {@code user.dir} can be used directly. Walk up until the project markers appear instead.
 */
public final class TestPaths {

    private static final int MAX_DEPTH = 8;

    private TestPaths() {
    }

    public static Path projectDir() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path candidate = start;
        for (int depth = 0; depth < MAX_DEPTH && candidate != null; depth++, candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("gradle.properties"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not locate the project directory (looking for gradle.properties + src/main/java) "
                        + "starting from " + start);
    }

    public static Path mainSources() {
        return projectDir().resolve("src/main/java");
    }

    public static Path mainResources() {
        return projectDir().resolve("src/main/resources");
    }

    public static Path dataPackRoot() {
        return mainResources().resolve("data");
    }
}
