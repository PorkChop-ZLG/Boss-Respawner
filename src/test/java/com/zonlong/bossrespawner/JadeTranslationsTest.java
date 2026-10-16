package com.zonlong.bossrespawner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Jade integration's translation keys.
 *
 * <p>Two distinct failures are covered here, both of which were observed in game:
 *
 * <ol>
 *   <li><b>Jade's config screen.</b> Jade renders a per-provider toggle looked up as {@code
 *       config.jade.plugin_<namespace>.<path>} ({@code JadeClient.java}). In a development environment it
 *       does not merely show the raw key: it throws an {@code AssertionError} from {@code JadeClient#onGui}
 *       when any registered provider lacks that translation. So a missing key breaks the title screen.</li>
 *   <li><b>Tooltip labels.</b> Jade's theme helpers treat a non-{@code MutableComponent} argument as
 *       literal text, so passing a translation key to {@code IThemeHelper.title(...)} prints the key
 *       itself. Every label must be a {@code Component.translatable} and must have a translation.</li>
 * </ol>
 *
 * <p>These are checked against the source and lang files rather than by loading the game, so they run in
 * the ordinary unit-test task.
 */
class JadeTranslationsTest {

    private static final Path LANG_DIR = TestPaths.mainResources().resolve("assets/boss_respawner/lang");

    private static final Path PROVIDER = TestPaths.mainSources()
            .resolve("com/zonlong/bossrespawner/compat/jade/BossRespawnerJadeProvider.java");

    /** Languages that must stay complete; the mod ships both and players may switch between them. */
    private static final List<String> LANGUAGES = List.of("en_us.json", "zh_cn.json");

    /**
     * The provider id is {@code boss_respawner:cage_contents}; Jade derives the config key from it as
     * {@code config.jade.plugin_<namespace>.<path>}. Keep this in step with {@code getUid()}.
     */
    private static final String CONFIG_KEY = "config.jade.plugin_boss_respawner.cage_contents";

    @Test
    @DisplayName("the Jade config key for this provider exists in every shipped language")
    void jadeConfigKeyIsTranslated() throws IOException {
        // If the provider id is ever renamed, this fails first and points at the key to update.
        String providerSource = Files.readString(PROVIDER, StandardCharsets.UTF_8);
        assertTrue(providerSource.contains("\"cage_contents\""),
                "the provider id path changed; update " + CONFIG_KEY + " and this test together");

        for (String language : LANGUAGES) {
            JsonObject lang = readLang(language);
            assertTrue(lang.has(CONFIG_KEY),
                    language + " is missing " + CONFIG_KEY
                            + " - Jade throws an AssertionError in dev without it");
            assertFalse(lang.get(CONFIG_KEY).getAsString().isBlank(),
                    language + " has a blank translation for " + CONFIG_KEY);
        }
    }

    @Test
    @DisplayName("every translation key referenced by the provider is present, and none is passed as a raw string")
    void providerKeysAreTranslated() throws IOException {
        String source = Files.readString(PROVIDER, StandardCharsets.UTF_8);

        Set<String> referenced = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\"(boss_respawner\\.jade\\.[a-z_]+)\"").matcher(source);
        while (matcher.find()) {
            referenced.add(matcher.group(1));
        }
        assertFalse(referenced.isEmpty(), "expected the provider to reference boss_respawner.jade.* keys");

        for (String language : LANGUAGES) {
            JsonObject lang = readLang(language);
            List<String> missing = referenced.stream().filter(key -> !lang.has(key)).sorted().toList();
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }

    @Test
    @DisplayName("the provider never hands a raw translation key to a theme helper")
    void noRawKeyPassedToThemeHelpers() throws IOException {
        // Comments are stripped first: this feature's own documentation explains *why* no theme helper is
        // used, so it necessarily mentions IThemeHelper by name.
        String source = stripComments(Files.readString(PROVIDER, StandardCharsets.UTF_8));

        // IThemeHelper.title(Object)/info(Object)/... turn a String into Component.literal, which is what
        // printed "boss_respawner.jade.key_item" on screen. No colour helper may be used with a key.
        List<String> offenders = new ArrayList<>();
        Matcher call = Pattern.compile("(title|info|success|warning|danger|failure)\\(\\s*\"([^\"]+)\"\\s*\\)")
                .matcher(source);
        while (call.find()) {
            offenders.add(call.group(1) + "(\"" + call.group(2) + "\")");
        }

        assertTrue(offenders.isEmpty(),
                "a theme helper received a raw String, which renders as literal text: " + offenders);

        // Unstyled text is what makes the line follow the theme's normal colour; see the provider's
        // "Colouring rule" documentation.
        assertFalse(source.contains("IThemeHelper"),
                "these lines must stay unstyled so Jade paints them in the theme's normal colour");
    }

    /** Removes block and line comments so prose in a javadoc cannot trip a source-level assertion. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static JsonObject readLang(String language) throws IOException {
        Path file = LANG_DIR.resolve(language);
        assertTrue(Files.isRegularFile(file), "missing language file " + file);
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    @DisplayName("every shipped language defines the same key set")
    void languagesAreInSync() throws IOException {
        Set<String> reference = readLang(LANGUAGES.get(0)).keySet();
        for (String language : LANGUAGES.subList(1, LANGUAGES.size())) {
            Set<String> keys = readLang(language).keySet();
            Set<String> missing = new LinkedHashSet<>(reference);
            missing.removeAll(keys);
            Set<String> extra = new LinkedHashSet<>(keys);
            extra.removeAll(reference);
            assertTrue(missing.isEmpty(), language + " is missing keys present in " + LANGUAGES.get(0) + ": " + missing);
            assertTrue(extra.isEmpty(), language + " has keys absent from " + LANGUAGES.get(0) + ": " + extra);
        }
        assertEquals(reference.size(), readLang(LANGUAGES.get(0)).keySet().size());
    }
}
