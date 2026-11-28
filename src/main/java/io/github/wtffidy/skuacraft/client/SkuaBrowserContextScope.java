package io.github.wtffidy.skuacraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefRequestContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Supplies a distinct Chromium request context to each account's Rinku browser.
 *
 * Rinku normally passes null to CefBrowserOsr, which selects CEF's global context.
 * The mixin consumes the request context from this thread-local while Rinku creates
 * the native browser. The context stays alive for the Minecraft process lifetime.
 */
public final class SkuaBrowserContextScope {
    private static final Map<String, CefRequestContext> REQUEST_CONTEXTS = new HashMap<>();
    private static final ThreadLocal<CefRequestContext> PENDING_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CONTEXT_CONSUMED = new ThreadLocal<>();

    private SkuaBrowserContextScope() {
    }

    public static RinkuBrowser createBrowser(String accountId, String pageUrl, boolean transparent, int width, int height) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("The Skua account profile ID is empty.");
        }
        if (pageUrl == null || pageUrl.isBlank()) {
            throw new IllegalArgumentException("The Skua browser URL is empty.");
        }

        if (!RenderSystem.isOnRenderThread()) {
            return Minecraft.getInstance().submit(
                    () -> createBrowser(accountId, pageUrl, transparent, width, height)
            ).join();
        }

        if (PENDING_CONTEXT.get() != null) {
            throw new IllegalStateException("A Skua account browser is already being created on this render thread.");
        }

        // Rinku's preload pool creates browsers against the global CEF context.
        // Disable it so every account browser is constructed through this scope.
        if (Rinku.getSettings().isBrowserPreloadEnabled()) {
            Rinku.getSettings().setBrowserPreloadEnabled(false);
        }

        CefRequestContext context = REQUEST_CONTEXTS.computeIfAbsent(
                accountId,
                ignored -> CefRequestContext.createContext(null)
        );
        if (context == null) {
            throw new IllegalStateException("CEF could not create an isolated request context for account " + accountId + ".");
        }

        PENDING_CONTEXT.set(context);
        CONTEXT_CONSUMED.set(Boolean.FALSE);

        try {
            RinkuBrowser browser = Rinku.createBrowser(pageUrl, transparent, Math.max(1, width), Math.max(1, height));
            if (!Boolean.TRUE.equals(CONTEXT_CONSUMED.get())) {
                browser.close();
                throw new IllegalStateException(
                        "Rinku did not consume the isolated CEF context. The browser mixin was not applied."
                );
            }

            return browser;
        } finally {
            PENDING_CONTEXT.remove();
            CONTEXT_CONSUMED.remove();
        }
    }

    /**
     * Called only by the RinkuBrowser constructor mixin.
     */
    public static CefRequestContext consumePendingContext() {
        CefRequestContext context = PENDING_CONTEXT.get();
        if (context != null) {
            PENDING_CONTEXT.remove();
            CONTEXT_CONSUMED.set(Boolean.TRUE);
        }

        return context;
    }
}
