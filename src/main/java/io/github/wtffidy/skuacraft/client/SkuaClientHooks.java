package io.github.wtffidy.skuacraft.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SkuaClientHooks {
    private SkuaClientHooks() {
    }

    public static void openConsole() {
        Minecraft.getInstance().setScreen(new SkuaPlayerScreen());
    }
}
