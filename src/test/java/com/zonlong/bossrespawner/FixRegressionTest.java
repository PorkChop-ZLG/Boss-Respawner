package com.zonlong.bossrespawner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level tripwires for the 2026-11-03 review fixes that need a live server to test behaviourally.
 *
 * <p>The project already uses this style for the spawn-NBT ordering, the removed protection floor and the
 * read-only data format. These tests pin the structural decisions so a later refactor cannot silently
 * reintroduce the same defects; the actual spawn/refund behaviour still needs the manual acceptance matrix.
 */
class FixRegressionTest {

    private static String read(String relative) throws IOException {
        Path path = TestPaths.mainSources().resolve(relative);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("home registration never touches saved data from the EntityJoinLevelEvent thread")
    void homeRegistrationIsDeferredToServerThread() throws IOException {
        String source = read("com/zonlong/bossrespawner/event/EntityHomeRegistrationHandler.java");
        int join = source.indexOf("public static void onEntityJoinLevel");
        int leave = source.indexOf("public static void onEntityLeaveLevel");
        assertTrue(join >= 0 && leave > join, "could not delimit the join handler");
        String joinBody = source.substring(join, leave);

        assertTrue(joinBody.contains("event.isCanceled()"), "cancelled join events must be ignored");
        assertTrue(joinBody.contains("server.isSameThread()"), "server-thread fast path is missing");
        assertTrue(joinBody.contains("server.execute"), "worker-thread registrations must be deferred");
        assertFalse(joinBody.contains("BossHomeSavedData.get"),
                "the join handler must not call BossHomeSavedData on the chunk worker");
        assertTrue(source.contains("Entity.RemovalReason.DISCARDED"),
                "discarded entities must have their home record cleaned up");
    }

    @Test
    @DisplayName("the master switch is checked at every execution entry point")
    void masterSwitchIsCheckedEverywhere() throws IOException {
        assertTrue(read("com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java")
                .contains("Config.ENABLE_MOD.getAsBoolean()"));
        assertTrue(read("com/zonlong/bossrespawner/block/BossRespawnerBlock.java")
                .contains("Config.ENABLE_MOD.getAsBoolean()"));
        assertTrue(read("com/zonlong/bossrespawner/event/PendingPlacementService.java")
                .contains("Config.ENABLE_MOD.getAsBoolean()"));
        assertTrue(read("com/zonlong/bossrespawner/event/TwilightSettlementService.java")
                .contains("Config.ENABLE_MOD.getAsBoolean()"));
        assertTrue(read("com/zonlong/bossrespawner/event/EntityHomeRegistrationHandler.java")
                .contains("Config.ENABLE_MOD.getAsBoolean()"));
    }

    @Test
    @DisplayName("spawn/transform finalization, UUID isolation, passenger/persistence and refund hooks stay wired")
    void spawnAndPaymentHardening() throws IOException {
        String source = read("com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java");
        assertTrue(source.contains("EventHooks.finalizeMobSpawn"),
                "FinalizeSpawnEvent must be fired instead of calling Mob.finalizeSpawn directly");
        assertTrue(source.contains("toLoad.remove(\"UUID\")"),
                "spawn.nbt UUIDs must not hijack another boss's home");
        assertTrue(source.contains("tryAddFreshEntityWithPassengers"),
                "passengers must be added together with the boss");
        assertTrue(source.contains("setPersistenceRequired"),
                "cage-spawned bosses must not despawn while the player is away");
        assertTrue(source.contains("PaymentAmount"), "the payment record must be persisted");
        assertTrue(source.contains("refundPayment"), "failed placements must refund the key payment");
        assertTrue(source.contains("RetryCooldown"), "the explicit retry cooldown must be persisted");
        assertTrue(source.contains("onlyOpCanSetNbt"),
                "non-ops must not be able to inject transform/spawn NBT through block_entity_data");
    }

    @Test
    @DisplayName("home cleanup, invalid targets, cross-chunk cage index and write rollback stay wired")
    void placementHardening() throws IOException {
        String placer = read("com/zonlong/bossrespawner/placement/RespawnCagePlacer.java");
        assertTrue(placer.contains("dropHomeRecord(request)"),
                "ABANDONED placement paths must drop the dead entity's home record");
        assertTrue(placer.contains("isOutsideBuildHeight"),
                "targets outside build height must fail immediately instead of creating endless tasks");
        assertTrue(placer.contains("hasCageNear"),
                "the cross-chunk duplicate check must consult the persistent cage index");
        assertTrue(placer.contains("addCage"), "new cages must be added to the cage index");
        assertTrue(placer.contains("refundPaymentOnRemoval"),
                "replace_existing must refund a paid old cage");
        assertTrue(placer.contains("restoring the previous block"),
                "writeCage must roll back when the block entity is missing");

        String block = read("com/zonlong/bossrespawner/block/BossRespawnerBlock.java");
        assertTrue(block.contains("onRemove"), "removed cages must leave the cage index");
        assertTrue(block.contains("removeCage"), "removed cages must leave the cage index");

        String death = read("com/zonlong/bossrespawner/event/LivingDeathHandler.java");
        assertTrue(death.contains("BossHomeSavedData.get(serverLevel).remove"),
                "death conditions that produce no cage must clean the home record");
    }
}
