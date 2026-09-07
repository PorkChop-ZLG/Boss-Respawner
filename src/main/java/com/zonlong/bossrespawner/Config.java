package com.zonlong.bossrespawner;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_MOD = BUILDER
            .comment("Master switch for the whole mod. Set false to disable all placement/spawning logic.")
            .translation("boss_respawner.configuration.general.enableMod")
            .define("general.enableMod", true);

    public static final ModConfigSpec.BooleanValue FORCE_PLACE_ON_NO_SAFE_SPOT = BUILDER
            .comment("When no safe placement position is found after a Boss death, force place the respawner at the search origin.",
                    "This ignores placement safety rules such as require_ground/avoid_fluids/avoid_blocks, but still respects duplicate mode and the global foreignCageBlockIds compatibility list.",
                    "Default: true")
            .translation("boss_respawner.configuration.general.forcePlaceOnNoSafeSpot")
            .define("general.forcePlaceOnNoSafeSpot", true);

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

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOREIGN_CAGE_BLOCK_IDS = BUILDER
            .comment("Block IDs that should be treated as other mods' respawn cages/altars.",
                    "The placement code will avoid placing near these blocks.",
                    "Example: [\"cataclysm:boss_respawner\"]")
            .translation("boss_respawner.configuration.compat.foreignCageBlockIds")
            .defineListAllowEmpty("compat.foreignCageBlockIds", List.of("cataclysm:boss_respawner"),
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
