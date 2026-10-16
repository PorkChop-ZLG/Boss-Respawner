package com.zonlong.bossrespawner;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common (server-visible) configuration for the mod.
 *
 * <p>Placement and compatibility policy lives in the data-driven JSON rules; the common config only keeps
 * the master switch, the client-side highlight settings and one non-negotiable safety floor.
 */
public final class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_MOD = BUILDER
            .comment("Master switch for the whole mod. Set false to disable all placement/spawning logic.")
            .translation("boss_respawner.configuration.general.enableMod")
            .define("general.enableMod", true);

    public static final ModConfigSpec.BooleanValue HIGHLIGHT_BOSS_RESPAWNER = BUILDER
            .comment("Render a vanilla-style glowing outline around Boss Respawner blocks so players can see them through walls.",
                    "This is a client-side rendering effect, but it is configured here in the common config.",
                    "When false, no outline is requested and no outline rendering work is performed.")
            .translation("boss_respawner.configuration.general.highlightBossRespawner")
            .define("general.highlightBossRespawner", true);

    public static final ModConfigSpec.IntValue HIGHLIGHT_BOSS_RESPAWNER_RANGE = BUILDER
            .comment("Maximum distance in blocks at which Boss Respawner blocks are outlined.",
                    "Default: 32")
            .translation("boss_respawner.configuration.general.highlightBossRespawnerRange")
            .defineInRange("general.highlightBossRespawnerRange", 32, 1, 256);

    public static final ModConfigSpec.BooleanValue DEBUG_INFO = BUILDER
            .comment("Debug logging. When true, prints debug information about Boss respawner placement and spawning to the log.",
                    "This setting lives in its own [debug] group.")
            .translation("boss_respawner.configuration.debug.debugInfo")
            .define("debug.debugInfo", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
