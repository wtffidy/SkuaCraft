package io.github.wtffidy.skuacraft.client;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.Minecraft;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SkuaPlayerBrowserSession {
    private static final int BACKGROUND_WIDTH = 480;
    private static final int BACKGROUND_HEIGHT = 276;
    private static final Map<String, BrowserSession> BROWSERS = new HashMap<>();

    private SkuaPlayerBrowserSession() {
    }

    /**
     * Creates or keeps alive the browser that actually executes AQW for an account.
     * Background browsers remain loaded but do not own keyboard focus.
     */
    public static void ensureBackground(String accountId, String pageUrl) {
        if (accountId == null || accountId.isBlank() || pageUrl == null || pageUrl.isBlank()) {
            return;
        }

        if (!Rinku.isInitialized()) {
            Rinku.scheduleForInit(success -> {
                if (success) {
                    Minecraft.getInstance().execute(() -> ensureBackground(accountId, pageUrl));
                }
            });
            return;
        }

        Minecraft.getInstance().execute(() -> {
            synchronized (SkuaPlayerBrowserSession.class) {
                BrowserSession session = BROWSERS.get(accountId);
                if (session != null && pageUrl.equals(session.pageUrl())) {
                    session.browser().setFocus(false);
                    return;
                }

                if (session != null) {
                    session.browser().close();
                }

                RinkuBrowser browser = Rinku.createBrowser(pageUrl, false, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
                browser.useBrowserControls(false);
                browser.setFocus(false);
                BROWSERS.put(accountId, new BrowserSession(pageUrl, browser));
            }
        });
    }

    public static synchronized RinkuBrowser openOrReuse(String accountId, String requestedPageUrl, int width, int height) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("The Skua account profile ID is empty.");
        }
        if (requestedPageUrl == null || requestedPageUrl.isBlank()) {
            throw new IllegalArgumentException("The Skua player URL is empty.");
        }

        releaseFocusExcept(accountId);

        BrowserSession session = BROWSERS.get(accountId);
        if (session != null && requestedPageUrl.equals(session.pageUrl())) {
            session.browser().resize(Math.max(1, width), Math.max(1, height));
            session.browser().setFocus(true);
            return session.browser();
        }

        if (session != null) {
            session.browser().close();
        }

        RinkuBrowser browser = Rinku.createBrowser(requestedPageUrl, false);
        browser.useBrowserControls(false);
        browser.resize(Math.max(1, width), Math.max(1, height));
        browser.setFocus(true);
        BROWSERS.put(accountId, new BrowserSession(requestedPageUrl, browser));
        return browser;
    }

    public static synchronized RinkuBrowser getBrowser(String accountId) {
        BrowserSession session = BROWSERS.get(accountId);
        return session == null ? null : session.browser();
    }

    public static synchronized List<String> getAccountIds() {
        return BROWSERS.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    public static synchronized void releaseFocus(String accountId) {
        BrowserSession session = BROWSERS.get(accountId);
        if (session != null) {
            session.browser().setFocus(false);
        }
    }

    public static synchronized void releaseFocusExcept(String accountId) {
        for (Map.Entry<String, BrowserSession> entry : BROWSERS.entrySet()) {
            entry.getValue().browser().setFocus(entry.getKey().equals(accountId));
        }
    }

    public static synchronized void close(String accountId) {
        BrowserSession session = BROWSERS.remove(accountId);
        if (session != null) {
            session.browser().close();
        }
    }

    public static synchronized void closeAll() {
        for (BrowserSession session : BROWSERS.values()) {
            session.browser().close();
        }
        BROWSERS.clear();
    }

    private record BrowserSession(String pageUrl, RinkuBrowser browser) {
    }
}
