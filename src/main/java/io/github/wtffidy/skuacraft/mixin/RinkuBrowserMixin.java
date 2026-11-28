package io.github.wtffidy.skuacraft.mixin;

import io.github.wtffidy.skuacraft.client.SkuaBrowserContextScope;
import de.keksuccino.rinku.RinkuBrowser;
import org.cef.browser.CefRequestContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = RinkuBrowser.class, remap = false)
public abstract class RinkuBrowserMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/cef/browser/CefBrowserOsr;<init>(Lorg/cef/CefClient;Ljava/lang/String;ZLorg/cef/browser/CefRequestContext;)V",
                    remap = false
            ),
            index = 3
    )
    private CefRequestContext skuacraft$useAccountRequestContext(CefRequestContext defaultContext) {
        CefRequestContext accountContext = SkuaBrowserContextScope.consumePendingContext();
        return accountContext == null ? defaultContext : accountContext;
    }
}
