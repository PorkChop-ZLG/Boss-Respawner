package com.zonlong.bossrespawner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tripwire for a subtle Minecraft API trap that silently broke {@code spawn.nbt} placement.
 *
 * <p><b>Why this is a source check rather than a behavioural test.</b> The defect lives in the interaction
 * between our code and {@code Entity.load()}:
 *
 * <pre>
 * Entity.load():    ListTag listtag = compound.getList("Pos", 6);   // no contains() guard
 *                   this.setPosRaw(listtag.getDouble(0), ...);      // empty list -> 0.0
 * ListTag.getDouble(int index): returns 0.0 when index is out of range
 * </pre>
 *
 * So loading an SNBT payload that has no {@code Pos} tag teleports the entity to {@code (0, 0, 0)}.
 * Reproducing that here would require a live {@code ServerLevel} and a real entity instance, which the
 * unit-test environment does not have. The ordering in {@code trySpawn} is therefore pinned textually:
 * the NBT must be applied <em>before</em> the position, so that the cage position always wins.
 *
 * <p>If this test ever fails, do not "fix" the test — re-read the javadoc on {@code Entity.load} and the
 * note in {@code trySpawn}.
 */
class SpawnNbtOrderingTest {

    private static final Path BLOCK_ENTITY = TestPaths.mainSources()
            .resolve("com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java");

    @Test
    @DisplayName("spawn NBT is applied before the spawn position, so Entity.load cannot reset it to (0,0,0)")
    void nbtIsLoadedBeforePositionIsSet() throws IOException {
        String source = Files.readString(BLOCK_ENTITY, StandardCharsets.UTF_8);

        int methodStart = source.indexOf("private boolean trySpawn");
        assertTrue(methodStart >= 0, "trySpawn not found in BossRespawnerBlockEntity");

        // Bound the search to the trySpawn body: the next method declaration after it.
        int nextMethod = source.indexOf("private static void discardAll", methodStart);
        assertTrue(nextMethod > methodStart, "could not delimit the trySpawn body");
        String body = source.substring(methodStart, nextMethod);

        int loadIndex = body.indexOf("entity.load(spawnNbt)");
        int setPosIndex = body.indexOf("entity.setPos(spawnPos)");

        assertTrue(loadIndex >= 0, "trySpawn no longer applies spawn.nbt via entity.load(spawnNbt)");
        assertTrue(setPosIndex >= 0, "trySpawn no longer positions the entity via entity.setPos(spawnPos)");
        assertTrue(loadIndex < setPosIndex,
                "entity.load(spawnNbt) must run BEFORE entity.setPos(spawnPos).\n"
                        + "Entity.load() has no contains(\"Pos\") guard and ListTag.getDouble() returns 0.0 for a "
                        + "missing element, so loading NBT after positioning teleports the spawned entity to "
                        + "(0, 0, 0) whenever the rule's spawn.nbt omits Pos.");
    }

    @Test
    @DisplayName("the ordering hazard is documented next to the call, not only in this test")
    void theHazardIsExplainedInTheSource() throws IOException {
        String source = Files.readString(BLOCK_ENTITY, StandardCharsets.UTF_8);
        assertTrue(source.contains("ListTag.getDouble() returns 0.0 for a missing element"),
                "the comment explaining the Entity.load ordering trap is missing from trySpawn");
    }
}
