package com.zonlong.bossrespawner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cage's identity configuration.
 *
 * <p>Every cage must record <em>what</em> it revives and what it costs, whichever spawn mode it uses. A
 * transform cage once skipped that step, and the failure was silent and total: the Jade tooltip had no boss
 * name and no key item, and {@code matchesKeyItem} could never match, so right-clicking the cage did
 * nothing. Nothing in the unit suite noticed, because the cage parsed fine and was placed fine — only its
 * contents were empty.
 *
 * <p>Checked against the source because the real thing needs a server level; the rule being pinned is
 * simply that the shared identity call exists and comes before the mode branch.
 */
class CageIdentityTest {

    private static final Path PLACER = TestPaths.mainSources()
            .resolve("com/zonlong/bossrespawner/placement/RespawnCagePlacer.java");

    @Test
    @DisplayName("the cage is given its entity and key item before either spawn mode is configured")
    void identityIsSetForBothModes() throws IOException {
        String source = stripComments(Files.readString(PLACER, StandardCharsets.UTF_8));

        int identity = source.indexOf("be.setIdentity(");
        assertTrue(identity >= 0,
                "RespawnCagePlacer must call be.setIdentity(...); without it a transform cage is inert");

        int modeBranch = source.indexOf("spawn.mode() == RespawnEntry.SpawnMode.TRANSFORM");
        assertTrue(modeBranch >= 0, "expected the spawn mode branch in writeCage");
        assertTrue(identity < modeBranch,
                "setIdentity must run before the mode branch so both modes get it");

        // The transform path must not have quietly re-implemented only part of the identity.
        assertTrue(source.contains("be.setTransform("),
                "expected the transform configuration call");
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}
