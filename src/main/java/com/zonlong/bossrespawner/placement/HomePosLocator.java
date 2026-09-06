package com.zonlong.bossrespawner.placement;

import com.zonlong.bossrespawner.UniversalBossRespawner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public final class HomePosLocator {
    private HomePosLocator() {
    }

    /**
     * Returns a home position in the same dimension as the entity, if one can be found.
     * The Cataclysm integration is intentionally reflection-based so this mod never hard-depends on Cataclysm.
     */
    public static Optional<BlockPos> findHomePos(LivingEntity entity) {
        // Cataclysm IHomeEntity#getHomePos() -> GlobalPos
        try {
            Class<?> clazz = Class.forName("com.github.L_Ender.cataclysm.entity.etc.IHomeEntity");
            if (clazz.isInstance(entity)) {
                Object home = entity.getClass().getMethod("getHomePos").invoke(entity);
                if (home instanceof GlobalPos globalPos
                        && globalPos.dimension().equals(entity.level().dimension())) {
                    return Optional.of(globalPos.pos());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Cataclysm not present or API changed; ignore.
        }

        // Generic duck-typed "restrict center" / "home position" getters.
        Optional<BlockPos> duck = findViaMethod(entity, "getRestrictCenter");
        if (duck.isPresent()) {
            return duck;
        }
        duck = findViaMethod(entity, "getHomePosition");
        if (duck.isPresent()) {
            return duck;
        }
        duck = findViaMethod(entity, "getHomePos");
        if (duck.isPresent()) {
            return duck;
        }

        UniversalBossRespawner.LOGGER.debug("No home position found for {}", entity.getType());
        return Optional.empty();
    }

    private static Optional<BlockPos> findViaMethod(LivingEntity entity, String methodName) {
        try {
            if ("getRestrictCenter".equals(methodName) && !hasActiveRestriction(entity)) {
                return Optional.empty();
            }

            Object value = entity.getClass().getMethod(methodName).invoke(entity);
            if (value instanceof BlockPos blockPos) {
                return Optional.of(blockPos);
            }
            if (value instanceof GlobalPos globalPos
                    && globalPos.dimension().equals(entity.level().dimension())) {
                return Optional.of(globalPos.pos());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Ignore: method absent or inaccessible.
        }
        return Optional.empty();
    }

    private static boolean hasActiveRestriction(LivingEntity entity) {
        try {
            Object value = entity.getClass().getMethod("hasRestriction").invoke(entity);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // If the duck-typed method does not provide a hasRestriction flag, accept the home value.
            return true;
        }
    }
}
