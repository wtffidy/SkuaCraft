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
        openConsole(accountId, "Default Account");
    }

    public static void openConsole(String accountId, String profileName) {
        Minecraft.getInstance().setScreen(new SkuaPlayerScreen(accountId, profileName));
    }

    public static void openProfileEditor(net.minecraft.world.InteractionHand hand, String profileName) {
        Minecraft.getInstance().setScreen(new SkuaProfileScreen(hand, profileName));
    }
}
