package com.zonlong.bossrespawner.compat.twilightforest;

import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Reflection-only access to Twilight Forest's {@code EnforcedHomePoint} home point.
 *
 * <p>This class deliberately has no Twilight Forest imports: the mod must start without TF installed.
 * The methods are only invoked after a {@code spawn.transform.force_place} cage has recorded a pending
 * home override, so a non-TF entity never reaches {@link #setHome(Entity, GlobalPos)}.
 */
public final class TwilightHomeCompat {

    private TwilightHomeCompat() {
    }

    /** @return the entity's TF home, or {@code null} when it does not implement the interface */
    @Nullable
    public static GlobalPos getHome(Entity entity) {
        try {
            Method method = entity.getClass().getMethod("getRestrictionPoint");
            Object value = method.invoke(entity);
            return value instanceof GlobalPos home ? home : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Restores the entity's home via {@code EnforcedHomePoint#setRestrictionPoint(GlobalPos)}.
     *
     * @return {@code true} when the method was found and invoked
     */
    public static boolean setHome(Entity entity, GlobalPos home) {
        try {
            Method method = entity.getClass().getMethod("setRestrictionPoint", GlobalPos.class);
            method.invoke(entity, home);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            UniversalBossRespawner.LOGGER.warn(
                    "Could not restore the Twilight Forest home of {} to {}: {}",
                    entity.getType(), home, e.toString());
            return false;
        }
    }
}
