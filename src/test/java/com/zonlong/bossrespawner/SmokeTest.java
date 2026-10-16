package com.zonlong.bossrespawner;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Infrastructure smoke test.
 *
 * <p>Proves that Minecraft classes are present on the unit-test runtime classpath without
 * bootstrapping the game. If this test passes, pure-logic tests (JSON schema parsing, SavedData
 * NBT round-trips) can be written as ordinary JUnit tests.
 */
class SmokeTest {
    @Test
    void minecraftClassesAreOnTestRuntimeClasspath() {
        ResourceLocation id = ResourceLocation.parse("boss_respawner:smoke");
        assertEquals("boss_respawner", id.getNamespace());
        assertEquals("smoke", id.getPath());
    }
}
