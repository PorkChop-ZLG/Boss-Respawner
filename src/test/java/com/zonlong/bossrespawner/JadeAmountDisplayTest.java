package com.zonlong.bossrespawner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guard for how the Jade tooltip renders the required amount.
 *
 * <p>This is checked against the provider's source rather than by loading it, because Jade is a
 * {@code compileOnly} dependency and its {@code IDisplayHelper} drags in client-only classes that a plain
 * JUnit JVM cannot link. The rules being pinned are small and textual, so a source check is enough.
 *
 * <h2>What went wrong before</h2>
 *
 * <p>The icon used to be built as {@code new ItemStack(item, min(required, maxStackSize))}, which rendered
 * the vanilla count badge, and an extra {@code ×N} label was appended once the amount exceeded a stack. An
 * amount of 100 therefore displayed a badge reading {@code 64} <em>and</em> the text {@code ×100} — two
 * different numbers for the same requirement. The badge is gone: the icon always holds one item and the
 * exact amount is printed, so there is only ever one number and it is never clamped.
 */
class JadeAmountDisplayTest {

    private static final Path PROVIDER = TestPaths.mainSources()
            .resolve("com/zonlong/bossrespawner/compat/jade/BossRespawnerJadeProvider.java");

    @Test
    @DisplayName("the icon never carries a count badge, so it cannot compete with the printed amount")
    void iconHoldsASingleItem() throws IOException {
        String source = code();

        List<String> stacks = new ArrayList<>();
        Matcher matcher = Pattern.compile("new ItemStack\\(([^)]*)\\)").matcher(source);
        while (matcher.find()) {
            stacks.add(matcher.group(1).trim());
        }

        assertFalse(stacks.isEmpty(), "expected the provider to build an ItemStack for the icon");
        for (String args : stacks) {
            // The capture stops before the closing paren, so this reads "item, 1".
            assertTrue(args.endsWith(", 1"),
                    "the tooltip icon must hold exactly one item (found 'new ItemStack(" + args
                            + ")'); a count would render a badge alongside the ×N label");
        }
    }

    @Test
    @DisplayName("the printed amount is never clamped to a stack size")
    void amountIsNeverClamped() throws IOException {
        String source = code();

        assertFalse(source.contains("getDefaultMaxStackSize"),
                "clamping the amount to a stack size is what produced '64' next to '×100'");
        assertFalse(source.contains("Math.min"),
                "no clamping of the required amount is allowed on this line");

        // The exact requirement is what gets printed.
        assertTrue(source.contains("cage.getKeyAmount()"),
                "the amount must come from the cage's configured requirement");
    }

    @Test
    @DisplayName("no number is printed when a single item is all that is needed")
    void singleItemPrintsNoNumber() throws IOException {
        String source = code();
        assertTrue(source.contains("required > 1 ? required : 0"),
                "the amount helper must suppress the number for a single item");
    }

    /** The provider source with comments removed, so prose cannot satisfy or trip an assertion. */
    private static String code() throws IOException {
        String source = Files.readString(PROVIDER, StandardCharsets.UTF_8);
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
