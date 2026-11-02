package com.zonlong.bossrespawner.data;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level database of home anchors plus the persistent deferred-placement queue.
 *
 * <p>Mounted on the <b>Overworld</b> {@code DimensionDataStorage} because records are cross-dimensional;
 * that is the same pattern vanilla uses for {@code MapItemSavedData}/{@code MapIndex}. The resulting file
 * is:
 *
 * <pre>
 * saves/&lt;world&gt;/data/boss_respawner_homes.dat
 * </pre>
 *
 * <p><b>The file id must not contain {@code /} or {@code \}.</b> {@code DimensionDataStorage} never creates
 * a sub-directory for saved data and {@code SavedData.save} swallows the resulting {@code IOException} with
 * a single error log line, so a path separator here would make every save fail silently while the game
 * still looked healthy.
 *
 * <p>Two collections are stored:
 * <ul>
 *   <li>{@code homes} — {@code UUID -> HomeRecord}, one entry per observed entity. Entries are consumed
 *       when the entity dies and its cage is successfully placed.</li>
 *   <li>{@code pending} — deferred placements keyed by placement target, plus a
 *       {@code (dimension, ChunkPos)} index so a chunk-load event costs O(1) instead of scanning the
 *       whole queue.</li>
 * </ul>
 */
public class BossHomeSavedData extends SavedData {

    /** Flat file id. Must not contain path separators — see the class javadoc. */
    public static final String FILE_ID = "boss_respawner_homes";

    /** On-disk format version understood by this build. */
    public static final int DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "dataVersion";
    private static final String TAG_HOMES = "homes";
    private static final String TAG_PENDING = "pendingPlacements";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SavedData.Factory<BossHomeSavedData> FACTORY =
            new SavedData.Factory<>(BossHomeSavedData::new, BossHomeSavedData::load);

    private final Map<UUID, HomeRecord> homes = new HashMap<>();
    private final Map<PendingPlacementTask.Key, PendingPlacementTask> pending = new HashMap<>();

    /**
     * {@code (dimension, ChunkPos)} -> task keys.
     *
     * <p>Keyed by {@link ResourceLocation} rather than {@link ResourceKey} on purpose: {@code ResourceKey}
     * does not override {@code equals}/{@code hashCode} and relies on an interning map with weak values, so
     * two keys for the same dimension can be distinct instances. Using one as a hash key would make lookups
     * silently miss. {@code ResourceLocation} has real value semantics.
     */
    private final Map<ResourceLocation, Map<Long, Set<PendingPlacementTask.Key>>> byChunk = new HashMap<>();

    /**
     * Set when the file on disk was written by a newer build. All writes are suppressed in that state so
     * a downgrade cannot replace data it does not understand with an empty table.
     */
    private boolean readOnly;

    public BossHomeSavedData() {
    }

    // ------------------------------------------------------------------ access

    /** Resolves the shared instance from any level's server. */
    public static BossHomeSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    /**
     * Resolves the shared instance from the server directly. Used on paths where the target level could
     * not be resolved (for example an unavailable target dimension), so no {@link ServerLevel} is at hand.
     */
    public static BossHomeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /**
     * True when the on-disk data is newer than this build understands. Such an instance refuses to be
     * written back, so the newer file survives intact.
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    public int homeCount() {
        return homes.size();
    }

    public int pendingCount() {
        return pending.size();
    }

    // ------------------------------------------------------------------ homes

    public HomeRecord get(UUID entityUuid) {
        return homes.get(entityUuid);
    }

    public boolean contains(UUID entityUuid) {
        return homes.containsKey(entityUuid);
    }

    /**
     * Registers {@code record} only when its UUID is not known yet, so an entity that has moved is never
     * re-anchored. This is the normal path used by the entity-join hook.
     *
     * @return {@code true} when a new record was stored
     */
    public boolean registerIfAbsent(HomeRecord record) {
        if (homes.containsKey(record.entityUuid())) {
            return false;
        }
        homes.put(record.entityUuid(), record);
        setDirty();
        return true;
    }

    /**
     * Stores {@code record} unconditionally. Used for the respawn-inheritance path, where the freshly
     * created entity has just joined the level at its spawn position and must be re-pointed at the
     * original home before the join hook could have written the wrong anchor.
     */
    public void registerOrReplace(HomeRecord record) {
        homes.put(record.entityUuid(), record);
        setDirty();
    }

    /** @return {@code true} when a record was present and removed */
    public boolean remove(UUID entityUuid) {
        if (homes.remove(entityUuid) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    // ------------------------------------------------------------------ pending placements

    /**
     * Adds or merges a deferred placement.
     *
     * <p>Merging replaces the stored snapshot with {@code task}'s (the latest death wins) and reports the
     * UUID that was displaced, so the caller can drop the now-useless home record of the merged death.
     *
     * @return the displaced source UUID, or {@code null} when this target had no task yet
     */
    public UUID enqueuePendingPlacement(PendingPlacementTask task) {
        PendingPlacementTask previous = pending.put(task.key(), task);
        index(task);
        setDirty();
        if (previous == null || previous.lastSourceEntityUuid().equals(task.lastSourceEntityUuid())) {
            return null;
        }
        return previous.lastSourceEntityUuid();
    }

    /** @return {@code true} when a task was present and removed */
    public boolean removePendingPlacement(PendingPlacementTask.Key key) {
        PendingPlacementTask removed = pending.remove(key);
        if (removed == null) {
            return false;
        }
        unindex(removed);
        setDirty();
        return true;
    }

    public PendingPlacementTask pendingPlacement(PendingPlacementTask.Key key) {
        return pending.get(key);
    }

    /**
     * O(1) lookup of the tasks waiting on one chunk. Deliberately does not iterate the whole queue:
     * {@code ChunkEvent.Load} fires extremely often, so a linear scan would degrade with world size.
     */
    public List<PendingPlacementTask> pendingForChunk(ResourceKey<Level> dimension, long chunkPos) {
        Map<Long, Set<PendingPlacementTask.Key>> perDimension = byChunk.get(dimension.location());
        if (perDimension == null) {
            return List.of();
        }
        Set<PendingPlacementTask.Key> keys = perDimension.get(chunkPos);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<PendingPlacementTask> result = new ArrayList<>(keys.size());
        for (PendingPlacementTask.Key key : keys) {
            PendingPlacementTask task = pending.get(key);
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    public List<PendingPlacementTask> pendingPlacements() {
        return List.copyOf(pending.values());
    }

    /**
     * Live, allocation-free view of the queue, for the per-tick scan that runs tasks whose
     * {@code place_delay_ticks} has elapsed.
     *
     * <p>Callers must only read it, and must collect anything they intend to act on before acting:
     * finishing a task removes it from the map, which would otherwise be a concurrent modification.
     */
    public Collection<PendingPlacementTask> pendingView() {
        return pending.values();
    }

    private void index(PendingPlacementTask task) {
        byChunk.computeIfAbsent(task.targetDimension().location(), k -> new HashMap<>())
                .computeIfAbsent(task.chunkPos(), k -> new HashSet<>())
                .add(task.key());
    }

    private void unindex(PendingPlacementTask task) {
        Map<Long, Set<PendingPlacementTask.Key>> perDimension =
                byChunk.get(task.targetDimension().location());
        if (perDimension == null) {
            return;
        }
        Set<PendingPlacementTask.Key> keys = perDimension.get(task.chunkPos());
        if (keys == null) {
            return;
        }
        keys.remove(task.key());
        if (keys.isEmpty()) {
            perDimension.remove(task.chunkPos());
        }
        if (perDimension.isEmpty()) {
            byChunk.remove(task.targetDimension().location());
        }
    }

    // ------------------------------------------------------------------ dirty handling

    @Override
    public void setDirty(boolean dirty) {
        if (readOnly) {
            // Never mark a file we refused to parse as dirty: that is what keeps the newer data safe.
            return;
        }
        super.setDirty(dirty);
    }

    @Override
    public boolean isDirty() {
        return !readOnly && super.isDirty();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putInt(TAG_DATA_VERSION, DATA_VERSION);

        ListTag homesTag = new ListTag();
        for (HomeRecord record : homes.values()) {
            homesTag.add(record.toTag());
        }
        tag.put(TAG_HOMES, homesTag);

        ListTag pendingTag = new ListTag();
        for (PendingPlacementTask task : pending.values()) {
            pendingTag.add(task.toTag());
        }
        tag.put(TAG_PENDING, pendingTag);

        return tag;
    }

    public static BossHomeSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        BossHomeSavedData data = new BossHomeSavedData();

        int version = tag.contains(TAG_DATA_VERSION, Tag.TAG_INT) ? tag.getInt(TAG_DATA_VERSION) : DATA_VERSION;
        if (version > DATA_VERSION) {
            data.readOnly = true;
            LOGGER.error("Refusing to read {}: dataVersion {} is newer than the supported version {}. "
                            + "The file will not be rewritten, so its contents are preserved. "
                            + "Update the mod to load it.",
                    FILE_ID, version, DATA_VERSION);
            return data;
        }

        ListTag homesTag = tag.getList(TAG_HOMES, Tag.TAG_COMPOUND);
        for (int i = 0; i < homesTag.size(); i++) {
            CompoundTag recordTag = homesTag.getCompound(i);
            try {
                HomeRecord record = HomeRecord.fromTag(recordTag);
                data.homes.put(record.entityUuid(), record);
            } catch (Exception e) {
                LOGGER.warn("Skipping corrupt home record #{} in {}: {}", i, FILE_ID, e.toString());
            }
        }

        ListTag pendingTag = tag.getList(TAG_PENDING, Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingTag.size(); i++) {
            CompoundTag taskTag = pendingTag.getCompound(i);
            try {
                PendingPlacementTask task = PendingPlacementTask.fromTag(taskTag);
                data.pending.put(task.key(), task);
                data.index(task);
            } catch (Exception e) {
                LOGGER.warn("Skipping corrupt pending placement #{} in {}: {}", i, FILE_ID, e.toString());
            }
        }

        LOGGER.info("Loaded {} home records and {} deferred placements from {}",
                data.homes.size(), data.pending.size(), FILE_ID);
        return data;
    }

    // ------------------------------------------------------------------ diagnostics

    /** Read-only view of all home records; intended for diagnostics and tests. */
    public Map<UUID, HomeRecord> homes() {
        return Collections.unmodifiableMap(homes);
    }

    /** @return the chunk position key used by {@link #pendingForChunk} */
    public static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
