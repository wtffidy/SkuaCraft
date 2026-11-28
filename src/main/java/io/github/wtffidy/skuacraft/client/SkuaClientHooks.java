package io.github.wtffidy.skuacraft.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SkuaClientHooks {
    private SkuaClientHooks() {
    }

    public static void openConsole() {
        openConsole(PlayerHostLauncher.DEFAULT_ACCOUNT_ID);
    }

    public static void openConsole(String accountId) {
        Minecraft.getInstance().setScreen(new SkuaPlayerScreen(accountId));
    }
}
