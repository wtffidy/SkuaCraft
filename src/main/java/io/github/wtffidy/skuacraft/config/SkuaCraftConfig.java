package io.github.wtffidy.skuacraft.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class SkuaCraftConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> HOST;
    public static final ForgeConfigSpec.IntValue PORT;
    public static final ForgeConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("connection");

        HOST = BUILDER
                .comment("VibeSkua host. Keep 127.0.0.1 when Minecraft and VibeSkua run on the same PC.")
                .define("host", "127.0.0.1");

        PORT = BUILDER
                .comment("VibeSkua Remote Control port.")
                .defineInRange("port", 8088, 1, 65535);

        REQUEST_TIMEOUT_SECONDS = BUILDER
                .comment("Maximum time to wait for one API request.")
                .defineInRange("requestTimeoutSeconds", 5, 1, 30);

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

    public static String getBaseUrl() {
        String host = HOST.get().trim();

        if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        } else if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        }

        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }

        return "http://" + host + ":" + PORT.get();
    }
}
