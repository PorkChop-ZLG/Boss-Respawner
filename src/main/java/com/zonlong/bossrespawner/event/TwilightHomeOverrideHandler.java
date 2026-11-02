package com.zonlong.bossrespawner.event;

import com.zonlong.bossrespawner.DebugLog;
import com.zonlong.bossrespawner.compat.twilightforest.TwilightHomeCompat;
import com.zonlong.bossrespawner.data.BossHomeSavedData;
import com.zonlong.bossrespawner.data.PendingHomeOverride;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Restores the original home of a boss created by a {@code spawn.transform.force_place} spawner.
 *
 * <p>The Lich/Minoshroom spawners are placed on the reward-chest cell, so the native spawner sets the
 * new boss's {@code HomePos} to that cell (or one above it, for the Lich). Without this hook the next
 * reward chest/cage cycle would drift. The pending override is one-shot and persisted in
 * {@link BossHomeSavedData}, because the native spawner may wait for a nearby player.
 */
public final class TwilightHomeOverrideHandler {

    private TwilightHomeOverrideHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        GlobalPos home = TwilightHomeCompat.getHome(entity);
        if (home == null) {
            return;
        }

        BossHomeSavedData data = BossHomeSavedData.get(level);
        long now = level.getGameTime();
        // The Lich's spawner moves up one block (home = spawner + 1); the Minoshroom's home is the
        // spawner cell itself. Check both so one hook covers both native spawner behaviours.
        PendingHomeOverride override = data.takePendingHomeOverride(home.dimension(), home.pos(), now);
        if (override == null) {
            override = data.takePendingHomeOverride(home.dimension(), home.pos().below(), now);
        }
        if (override == null) {
            return;
        }

        GlobalPos anchor = GlobalPos.of(override.anchorDimension(), override.anchorPos());
        if (TwilightHomeCompat.setHome(entity, anchor)) {
            DebugLog.info("Restored Twilight Forest home of {} from {} to {}", entity.getType(), home, anchor);
        }
    }
}
