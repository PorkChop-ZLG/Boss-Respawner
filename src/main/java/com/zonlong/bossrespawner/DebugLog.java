package com.zonlong.bossrespawner;

/**
 * Small helper for config-controlled debug logging.
 * All messages are suppressed unless the "调试信息 / Debug Information" config option is enabled.
 */
public final class DebugLog {
    private DebugLog() {
    }

    public static void info(String message, Object... args) {
        if (Config.DEBUG_INFO.getAsBoolean()) {
            UniversalBossRespawner.LOGGER.info("[BossRespawner-Debug] " + message, args);
        }
    }
}
