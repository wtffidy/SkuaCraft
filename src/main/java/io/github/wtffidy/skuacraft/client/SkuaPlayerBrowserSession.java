package io.github.wtffidy.skuacraft.client;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;

public final class SkuaPlayerBrowserSession {
    private static RinkuBrowser browser;
    private static String pageUrl = "";

    private SkuaPlayerBrowserSession() {
    }

    public static RinkuBrowser openOrReuse(String requestedPageUrl, int width, int height) {
        if (requestedPageUrl == null || requestedPageUrl.isBlank()) {
            throw new IllegalArgumentException("The Skua player URL is empty.");
        }

        if (browser != null && requestedPageUrl.equals(pageUrl)) {
            browser.resize(Math.max(1, width), Math.max(1, height));
            browser.setFocus(true);
            return browser;
        }

        closeCurrentBrowser();

        browser = Rinku.createBrowser(requestedPageUrl, false);
        browser.useBrowserControls(false);
        browser.resize(Math.max(1, width), Math.max(1, height));
        browser.setFocus(true);
        pageUrl = requestedPageUrl;

        return browser;
    }

    public static RinkuBrowser getBrowser() {
        return browser;
    }

    public static void releaseFocus() {
        if (browser != null) {
            browser.setFocus(false);
        }
    }

    private static void closeCurrentBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
            pageUrl = "";
        }
    }
}
