package com.zonlong.bossrespawner.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence, de-duplication and indexing behaviour of {@link BossHomeSavedData}.
 *
 * <p>These tests exercise the on-disk format directly (NBT round-trips) without a running server, which
 * is exactly the part that has to survive a restart.
 */
class BossHomeSavedDataTest {

    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    private static final ResourceKey<Level> NETHER = Level.NETHER;

    private static final ResourceLocation WARDEN = ResourceLocation.parse("minecraft:warden");

    // ------------------------------------------------------------------ helpers

    private static RespawnEntry entry(String entity, String placementBody) {
        JsonObject root = JsonParser.parseString("""
                {
                  "schema_version": 4,
                  "entity": "%s",
                  "activation": { "item": "minecraft:echo_shard", "amount": 3, "consume": false },
                  "placement": %s,
                  "spawn": { "mode": "summon", "summon": { "count": 2, "delay_ticks": 7, "nbt": "{Glowing:1b}" } },
                  "duplicate": { "mode": "keep_existing", "search_radius": 12 }
                }
                """.formatted(entity, placementBody)).getAsJsonObject();
        return RespawnEntry.fromJson(ResourceLocation.parse("test:entry"), root).orElseThrow();
    }

    private static RespawnEntry defaultEntry() {
        return entry("minecraft:warden", """
                {
                  "mode": "home",
                  "home": {
                    "offset": [1, -2, 3],
                    "force_place": true,
                    "search_failure_fallback": false,
                    "search_down": 9,
                    "search_up": 5,
                    "horizontal_radius": 2,
                    "require_ground": false,
                    "avoid_fluids": true,
                    "avoid_blocks": ["minecraft:bedrock"]
                  }
                }
                """);
    }

    private static HomeRecord home(UUID uuid, BlockPos pos) {
        return new HomeRecord(uuid, WARDEN, OVERWORLD, pos);
    }

    private static PendingPlacementTask task(ResourceKey<Level> dimension, BlockPos base, UUID source,
                                             HomeRecord homeSnapshot, RespawnRuleSnapshot rules) {
        // No place_delay_ticks: due as soon as it is queued, which is the pre-v3 behaviour.
        return new PendingPlacementTask(dimension, base, source, WARDEN, homeSnapshot, rules, 1234L, 1234L);
    }

    private static PendingPlacementTask delayedTask(ResourceKey<Level> dimension, BlockPos base, UUID source,
                                                   HomeRecord homeSnapshot, RespawnRuleSnapshot rules,
                                                   long notBefore) {
        return new PendingPlacementTask(dimension, base, source, WARDEN, homeSnapshot, rules, 1234L, notBefore);
    }

    private static long chunkOf(BlockPos pos) {
        return BossHomeSavedData.chunkKey(pos);
    }

    // ------------------------------------------------------------------ home records

    @Test
    @DisplayName("a home record survives an NBT round-trip")
    void homeRecordRoundTrips() {
        UUID uuid = UUID.randomUUID();
        HomeRecord original = new HomeRecord(uuid, WARDEN, NETHER, new BlockPos(-12, 64, 300));

        HomeRecord restored = HomeRecord.fromTag(original.toTag());

        assertEquals(original, restored);
    }

    @Test
    @DisplayName("registerIfAbsent never overwrites an existing anchor")
    void registerIfAbsentDoesNotOverwrite() {
        BossHomeSavedData data = new BossHomeSavedData();
        UUID uuid = UUID.randomUUID();
        HomeRecord first = home(uuid, new BlockPos(10, 64, 10));
        HomeRecord moved = home(uuid, new BlockPos(999, 70, -999));

        assertTrue(data.registerIfAbsent(first), "the first registration must store the record");
        assertFalse(data.registerIfAbsent(moved), "a second registration must be refused");
        assertEquals(new BlockPos(10, 64, 10), data.get(uuid).homePos());
    }

    @Test
    @DisplayName("registerOrReplace overwrites, which is what respawn inheritance needs")
    void registerOrReplaceOverwrites() {
        BossHomeSavedData data = new BossHomeSavedData();
        UUID uuid = UUID.randomUUID();
        data.registerIfAbsent(home(uuid, new BlockPos(0, 0, 0)));

        HomeRecord inherited = home(uuid, new BlockPos(500, 80, 500));
        data.registerOrReplace(inherited);

        assertEquals(new BlockPos(500, 80, 500), data.get(uuid).homePos());
        assertEquals(1, data.homeCount());
    }

    @Test
    @DisplayName("remove drops a record exactly once")
    void removeDropsRecord() {
        BossHomeSavedData data = new BossHomeSavedData();
        UUID uuid = UUID.randomUUID();
        data.registerIfAbsent(home(uuid, BlockPos.ZERO));

        assertTrue(data.remove(uuid));
        assertFalse(data.remove(uuid));
        assertFalse(data.contains(uuid));
    }

    // ------------------------------------------------------------------ pending queue

    @Test
    @DisplayName("a full rule snapshot survives an NBT round-trip")
    void ruleSnapshotRoundTrips() {
        RespawnEntry source = defaultEntry();

        RespawnRuleSnapshot restored = RespawnRuleSnapshot.fromTag(RespawnRuleSnapshot.of(source).toTag());

        assertActivationEquals(source.activation(), restored.activation());
        assertPlacementEquals(source.placement(), restored.placement());
        assertSpawnEquals(source.spawn(), restored.spawn());
        assertEquals(source.duplicate(), restored.duplicate());
    }

    @Test
    @DisplayName("pending tasks survive save + load, including the chunk index")
    void pendingTasksSurviveSaveAndLoad() {
        BossHomeSavedData data = new BossHomeSavedData();
        RespawnRuleSnapshot rules = RespawnRuleSnapshot.of(defaultEntry());
        HomeRecord snapshot = home(UUID.randomUUID(), new BlockPos(40, 70, 40));
        UUID sourceUuid = UUID.randomUUID();
        BlockPos base = new BlockPos(41, 69, 43);
        PendingPlacementTask original = task(OVERWORLD, base, sourceUuid, snapshot, rules);

        data.registerIfAbsent(snapshot);
        data.enqueuePendingPlacement(original);

        CompoundTag serialized = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        BossHomeSavedData reloaded = BossHomeSavedData.load(serialized, RegistryAccess.EMPTY);

        assertEquals(1, reloaded.homeCount());
        assertEquals(1, reloaded.pendingCount());

        List<PendingPlacementTask> fromIndex = reloaded.pendingForChunk(OVERWORLD, chunkOf(base));
        assertEquals(1, fromIndex.size(), "the chunk index must be rebuilt on load");

        PendingPlacementTask restored = fromIndex.get(0);
        assertEquals(original.targetDimension(), restored.targetDimension());
        assertEquals(original.targetBase(), restored.targetBase());
        assertEquals(original.lastSourceEntityUuid(), restored.lastSourceEntityUuid());
        assertEquals(original.entityType(), restored.entityType());
        assertEquals(original.homeSnapshot(), restored.homeSnapshot());
        assertEquals(original.createdGameTime(), restored.createdGameTime());
        assertPlacementEquals(rules.placement(), restored.ruleSnapshot().placement());
        assertPlacementEquals(rules.placement(), restored.ruleSnapshot().placement());
        assertEquals(snapshot, restored.homeSnapshot());
    }

    @Test
    @DisplayName("tasks de-duplicate on (targetDimension, targetBase) and report the displaced UUID")
    void enqueueDeduplicatesByPlacementTarget() {
        BossHomeSavedData data = new BossHomeSavedData();
        RespawnRuleSnapshot rules = RespawnRuleSnapshot.of(defaultEntry());
        HomeRecord snapshot = home(UUID.randomUUID(), BlockPos.ZERO);
        BlockPos base = new BlockPos(100, 64, 100);

        UUID firstSource = UUID.randomUUID();
        UUID secondSource = UUID.randomUUID();

        assertNull(data.enqueuePendingPlacement(task(OVERWORLD, base, firstSource, snapshot, rules)),
                "the first task for a target displaces nothing");

        UUID displaced = data.enqueuePendingPlacement(task(OVERWORLD, base, secondSource, snapshot, rules));

        assertEquals(firstSource, displaced, "the merged-away death's UUID must be reported");
        assertEquals(1, data.pendingCount(), "only one task may exist per placement target");
        assertEquals(secondSource,
                data.pendingPlacement(PendingPlacementTask.Key.of(OVERWORLD, base)).lastSourceEntityUuid(),
                "the latest death wins");
    }

    @Test
    @DisplayName("re-enqueueing the same source UUID reports no displacement")
    void reEnqueueWithSameSourceDoesNotReportDisplacement() {
        BossHomeSavedData data = new BossHomeSavedData();
        RespawnRuleSnapshot rules = RespawnRuleSnapshot.of(defaultEntry());
        HomeRecord snapshot = home(UUID.randomUUID(), BlockPos.ZERO);
        BlockPos base = new BlockPos(5, 64, 5);
        UUID source = UUID.randomUUID();

        data.enqueuePendingPlacement(task(OVERWORLD, base, source, snapshot, rules));
        assertNull(data.enqueuePendingPlacement(task(OVERWORLD, base, source, snapshot, rules)));
        assertEquals(1, data.pendingCount());
    }

    @Test
    @DisplayName("the same coordinates in another dimension are a different target")
    void sameCoordsInOtherDimensionAreDistinct() {
        BossHomeSavedData data = new BossHomeSavedData();
        RespawnRuleSnapshot rules = RespawnRuleSnapshot.of(defaultEntry());
        HomeRecord snapshot = home(UUID.randomUUID(), BlockPos.ZERO);
        BlockPos base = new BlockPos(7, 64, 7);

        data.enqueuePendingPlacement(task(OVERWORLD, base, UUID.randomUUID(), snapshot, rules));
        data.enqueuePendingPlacement(task(NETHER, base, UUID.randomUUID(), snapshot, rules));

        assertEquals(2, data.pendingCount());
        assertEquals(1, data.pendingForChunk(OVERWORLD, chunkOf(base)).size());
        assertEquals(1, data.pendingForChunk(NETHER, chunkOf(base)).size());
    }

    @Test
    @DisplayName("pendingForChunk only returns tasks for the requested chunk")
    void chunkIndexIsSelective() {
        BossHomeSavedData data = new BossHomeSavedData();
        RespawnRuleSnapshot rules = RespawnRuleSnapshot.of(defaultEntry());
        HomeRecord snapshot = home(UUID.randomUUID(), BlockPos.ZERO);

        BlockPos near = new BlockPos(3, 64, 3);
        BlockPos far = new BlockPos(500, 64, 500);
        data.enqueuePendingPlacement(task(OVERWORLD, near, UUID.randomUUID(), snapshot, rules));
        data.enqueuePendingPlacement(task(OVERWORLD, far, UUID.randomUUID(), snapshot, rules));

        assertEquals(1, data.pendingForChunk(OVERWORLD, chunkOf(near)).size());
        assertEquals(1, data.pendingForChunk(OVERWORLD, chunkOf(far)).size());
        assertEquals(0, data.pendingForChunk(OVERWORLD, chunkOf(new BlockPos(9000, 64, 9000))).size());

        assertTrue(data.removePendingPlacement(PendingPlacementTask.Key.of(OVERWORLD, near)));
        assertEquals(0, data.pendingForChunk(OVERWORLD, chunkOf(near)).size(),
                "removal must also clear the chunk index");
        assertEquals(1, data.pendingCount());
    }

    // ------------------------------------------------------------------ dataVersion

    @Test
    @DisplayName("a newer dataVersion is refused and never written back")
    void newerDataVersionIsReadOnly() {
        CompoundTag foreign = new CompoundTag();
        foreign.putInt("dataVersion", BossHomeSavedData.DATA_VERSION + 1);
        foreign.put("homes", new ListTag());
        foreign.put("pendingPlacements", new ListTag());

        BossHomeSavedData data = BossHomeSavedData.load(foreign, RegistryAccess.EMPTY);

        assertTrue(data.isReadOnly());
        assertFalse(data.isDirty());

        // Even an explicit mutation must not mark the file for writing.
        data.registerIfAbsent(home(UUID.randomUUID(), BlockPos.ZERO));
        data.setDirty();
        assertFalse(data.isDirty(), "a refused file must stay clean so the newer data survives");
    }

    @Test
    @DisplayName("a current dataVersion is writable")
    void currentDataVersionIsWritable() {
        BossHomeSavedData data = new BossHomeSavedData();
        assertFalse(data.isReadOnly());
        data.registerIfAbsent(home(UUID.randomUUID(), BlockPos.ZERO));
        assertTrue(data.isDirty());
    }

    // ------------------------------------------------------------------ corruption tolerance

    @Test
    @DisplayName("a corrupt record is skipped without failing the whole load")
    void corruptRecordsAreSkipped() {
        UUID goodUuid = UUID.randomUUID();
        CompoundTag goodHome = home(goodUuid, new BlockPos(1, 2, 3)).toTag();

        CompoundTag brokenHome = new CompoundTag();
        brokenHome.putString("EntityType", "minecraft:warden"); // no UUID, no dimension, no pos

        ListTag homes = new ListTag();
        homes.add(brokenHome);
        homes.add(goodHome);

        ListTag pending = new ListTag();
        pending.add(new CompoundTag()); // entirely empty

        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", BossHomeSavedData.DATA_VERSION);
        tag.put("homes", homes);
        tag.put("pendingPlacements", pending);

        BossHomeSavedData data = BossHomeSavedData.load(tag, RegistryAccess.EMPTY);

        assertEquals(1, data.homeCount(), "the good record must survive");
        assertNotNull(data.get(goodUuid));
        assertEquals(0, data.pendingCount(), "the corrupt task must be dropped");
        assertFalse(data.isReadOnly());
    }

    @Test
    @DisplayName("a missing dataVersion is treated as the current version")
    void missingDataVersionDefaultsToCurrent() {
        CompoundTag tag = new CompoundTag();
        tag.put("homes", new ListTag());
        tag.put("pendingPlacements", new ListTag());

        BossHomeSavedData data = BossHomeSavedData.load(tag, RegistryAccess.EMPTY);

        assertFalse(data.isReadOnly());
    }

    // ------------------------------------------------------------------ field-wise assertions
    // The rule records embed int[] fields, so record equals() is identity based. Compare field-wise.

    private static void assertActivationEquals(RespawnEntry.ActivationRule expected,
                                               RespawnEntry.ActivationRule actual) {
        assertEquals(expected.item(), actual.item());
        assertEquals(expected.amount(), actual.amount());
        assertEquals(expected.consume(), actual.consume());
    }

    private static void assertPlacementEquals(RespawnEntry.PlacementRule expected,
                                              RespawnEntry.PlacementRule actual) {
        assertEquals(expected.mode(), actual.mode());
        assertArrayEquals(expected.offset(), actual.offset());
        assertEquals(expected.forcePlace(), actual.forcePlace());
        assertEquals(expected.searchFailureFallback(), actual.searchFailureFallback());
        assertEquals(expected.searchDown(), actual.searchDown());
        assertEquals(expected.searchUp(), actual.searchUp());
        assertEquals(expected.horizontalRadius(), actual.horizontalRadius());
        assertEquals(expected.requireGround(), actual.requireGround());
        assertEquals(expected.avoidFluids(), actual.avoidFluids());
        assertEquals(expected.avoidBlocks(), actual.avoidBlocks());
        // place_delay_ticks is part of the rule too: losing it in the snapshot would make a deferred placement
        // fire before the boss death animation (and its loot chest) has finished.
        assertEquals(expected.placeDelayTicks(), actual.placeDelayTicks());
    }

    private static void assertSpawnEquals(RespawnEntry.SpawnRule expected, RespawnEntry.SpawnRule actual) {
        assertEquals(expected.mode(), actual.mode());
        assertEquals(expected.summon() == null, actual.summon() == null);
        assertEquals(expected.transform() == null, actual.transform() == null);
        assertEquals(expected.nbt(), actual.nbt(), "spawn nbt (either mode)");
        if (expected.summon() != null) {
            RespawnEntry.SummonRule e = expected.summon();
            RespawnEntry.SummonRule a = actual.summon();
            assertEquals(e.delayTicks(), a.delayTicks());
            assertEquals(e.requirePlayerNearby(), a.requirePlayerNearby());
            assertEquals(e.playerRange(), a.playerRange());
            assertEquals(e.allowPeaceful(), a.allowPeaceful());
            assertEquals(e.count(), a.count());
            assertArrayEquals(e.spawnOffset(), a.spawnOffset());
            assertEquals(e.finalizeSpawn(), a.finalizeSpawn());
            assertEquals(e.maxAttempts(), a.maxAttempts());
            assertEquals(e.retryIntervalTicks(), a.retryIntervalTicks());
        }
        if (expected.transform() != null) {
            RespawnEntry.TransformRule e = expected.transform();
            RespawnEntry.TransformRule a = actual.transform();
            assertEquals(e.block(), a.block());
            assertEquals(e.delayTicks(), a.delayTicks());
            assertEquals(e.allowPeaceful(), a.allowPeaceful());
        }
    }
}
