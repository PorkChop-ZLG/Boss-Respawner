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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the optional-dependency contract of the Jade integration.
 *
 * <p>Jade is an optional dependency and is never on the classpath at runtime unless the player installs
 * it. The integration only works because Jade loads our plugin classes itself; if any always-loaded class
 * (the {@code @Mod} class, event handlers, config, registries) referenced a Jade type or our plugin
 * classes, the mod would fail with {@code NoClassDefFoundError} for every player without Jade.
 *
 * <p>That failure mode is invisible in a dev environment, where Jade is always present — hence this test.
 * It is intentionally source-level so it runs without launching the game.
 */
class JadeOptionalityTest {

    private static final Path MAIN_SOURCES = TestPaths.mainSources();
    private static final String JADE_PACKAGE_PATH = "com/zonlong/bossrespawner/compat/jade";

    private static final List<String> FORBIDDEN_REFERENCES = List.of(
            "snownee.jade",                 // any Jade API type
            "BossRespawnerJadePlugin",      // our own plugin entry point
            "BossRespawnerJadeProvider",    // our own provider
            "com.zonlong.bossrespawner.compat.jade");

    @Test
    @DisplayName("no always-loaded class references Jade or the Jade plugin classes")
    void jadeReferencesStayInsideTheCompatPackage() throws IOException {
        Path jadePackage = MAIN_SOURCES.resolve(JADE_PACKAGE_PATH);
        assertTrue(Files.isDirectory(jadePackage), "expected the Jade compat package at " + jadePackage);

        List<String> offenders = new ArrayList<>();
        for (Path path : javaSources()) {
            if (path.startsWith(jadePackage)) {
                continue;
            }
            String code = stripComments(Files.readString(path, StandardCharsets.UTF_8));
            for (String forbidden : FORBIDDEN_REFERENCES) {
                if (code.contains(forbidden)) {
                    offenders.add(MAIN_SOURCES.relativize(path) + " -> " + forbidden);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "These classes always load, so referencing Jade from them breaks the mod when Jade is "
                        + "absent. Move the reference into " + JADE_PACKAGE_PATH + ": " + offenders);
    }

    @Test
    @DisplayName("the Jade integration is exactly the plugin and its provider")
    void jadeIntegrationIsMinimal() throws IOException {
        Path jadePackage = MAIN_SOURCES.resolve(JADE_PACKAGE_PATH);
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(jadePackage)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .forEach(p -> files.add(p.getFileName().toString()));
        }
        assertEquals(List.of("BossRespawnerJadePlugin.java", "BossRespawnerJadeProvider.java"), files,
                "the compat package should hold only the plugin and its provider");
    }

    @Test
    @DisplayName("the plugin is annotated for Jade's annotation scan and only registers a block component")
    void pluginIsDiscoverable() throws IOException {
        String plugin = Files.readString(
                MAIN_SOURCES.resolve(JADE_PACKAGE_PATH + "/BossRespawnerJadePlugin.java"),
                StandardCharsets.UTF_8);
        assertTrue(plugin.contains("@WailaPlugin"),
                "Jade discovers plugins by scanning for @WailaPlugin; without it the plugin never loads");
        assertTrue(plugin.contains("implements IWailaPlugin"),
                "the annotated class must implement IWailaPlugin or Jade skips it");
        assertTrue(plugin.contains("registerBlockComponent"),
                "the tooltip must be registered as a block component");

        // Jade's own plugins leave the annotation value empty when the plugin ships in the same mod.
        assertFalse(plugin.contains("@WailaPlugin("),
                "no required-mod value: the plugin lives in this mod, and a value would gate it on a modid");

        // No mob model may be rendered, so no icon override and no server-side data may be registered.
        assertFalse(plugin.contains("registerBlockIcon"),
                "registerBlockIcon would replace the block icon; the tooltip must stay text-only");
        assertFalse(plugin.contains("register("),
                "no IWailaCommonRegistration is needed: the block entity already syncs its data");
    }

    private static List<Path> javaSources() throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCES),
                "expected main sources at " + MAIN_SOURCES.toAbsolutePath()
                        + " - the test working directory must be the project directory");
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Removes block and line comments, so a comment that merely mentions Jade (for example in this
     * feature's own documentation) is not mistaken for a class-loading reference.
     */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
