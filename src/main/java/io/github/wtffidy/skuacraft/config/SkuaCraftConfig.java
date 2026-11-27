package io.github.wtffidy.skuacraft.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.nio.file.Path;

public final class SkuaCraftConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> PLAYER_HOST_EXECUTABLE;
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("player");

        PLAYER_HOST_EXECUTABLE = BUILDER
                .comment("Path to SkuaCraft.PlayerHost.exe. Relative paths are resolved from the Minecraft instance directory.")
                .define("playerHostExecutable", "config/skuacraft/player-host/SkuaCraft.PlayerHost.exe");

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private SkuaCraftConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                SPEC,
                "skuacraft-client.toml"
        );
    }

    public static Path getPlayerHostExecutable(Path gameDirectory) {
        Path configuredPath = Path.of(PLAYER_HOST_EXECUTABLE.get().trim());

        return configuredPath.isAbsolute()
                ? configuredPath.normalize()
                : gameDirectory.resolve(configuredPath).normalize();
    }
}
