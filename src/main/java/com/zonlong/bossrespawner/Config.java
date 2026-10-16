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

    /**
     * Hard safety floor: blocks this mod must never overwrite, regardless of {@code force_place}.
     *
     * <p>This is deliberately global rather than per-rule. "Do not destroy another mod's block" is a
     * cross-cutting guarantee that must not depend on every datapack author remembering to declare it.
     * A rule's own {@code protected_blocks} can only add to this list, never subtract from it.
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_BLOCK_IDS = BUILDER
            .comment("Block IDs that this mod must never overwrite, in any placement mode, even when force_place is true.",
                    "Rules can add more protection via their own protected_blocks list, but cannot remove entries listed here.",
                    "Example: [\"cataclysm:boss_respawner\"]")
            .translation("boss_respawner.configuration.compat.protectedBlockIds")
            .defineListAllowEmpty("compat.protectedBlockIds", List.of("cataclysm:boss_respawner"),
                    () -> "", obj -> obj instanceof String);

    public static final ModConfigSpec.BooleanValue DEBUG_INFO = BUILDER
            .comment("Debug logging. When true, prints debug information about Boss respawner placement and spawning to the log.",
                    "This setting lives in its own [debug] group.")
            .translation("boss_respawner.configuration.debug.debugInfo")
            .define("debug.debugInfo", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
