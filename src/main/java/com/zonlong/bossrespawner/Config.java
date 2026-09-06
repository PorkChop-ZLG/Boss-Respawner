package com.zonlong.bossrespawner;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_MOD = BUILDER
            .comment("Master switch for the whole mod. Set false to disable all placement/spawning logic.")
            .translation("boss_respawner.configuration.general.enableMod")
            .define("general.enableMod", true);

    public static final ModConfigSpec.BooleanValue DEBUG_INFO = BUILDER
            .comment("Debug logging. When true, prints debug information about Boss respawner placement and spawning to the log.",
                    "NOTE: The TOML key remains general.logPlacement for compatibility with older configs; it now controls debug information, not just placement logs.")
            .translation("boss_respawner.configuration.general.debugInfo")
            .define("general.logPlacement", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOREIGN_CAGE_BLOCK_IDS = BUILDER
            .comment("Block IDs that should be treated as other mods' respawn cages/altars.",
                    "The placement code will avoid placing near these blocks.",
                    "Example: [\"cataclysm:boss_respawner\"]")
            .translation("boss_respawner.configuration.compat.foreignCageBlockIds")
            .defineListAllowEmpty("compat.foreignCageBlockIds", List.of("cataclysm:boss_respawner"),
                    () -> "", obj -> obj instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
