package io.github.wtffidy.skuacraft.client;

import java.util.List;
import java.util.function.Consumer;

/**
 * Compatibility facade for the original single-account launcher.
 * New callers should supply the account profile ID explicitly.
 */
public final class PlayerHostLauncher {
    public static final String DEFAULT_ACCOUNT_ID = "default";

    private PlayerHostLauncher() {
    }

    public static void ensureRunning(Consumer<LaunchResult> callback) {
        ensureRunning(DEFAULT_ACCOUNT_ID, callback);
    }

    public static void ensureRunning(String accountId, Consumer<LaunchResult> callback) {
        PlayerHostManager.ensureRunning(accountId, result -> callback.accept(
                new LaunchResult(result.success(), result.pageUrl(), result.controlUrl(), result.controlToken(), result.message())
        ));
    }

    /**
     * Starts the host and the hidden browser required for AQW to keep executing.
     * The browser is created without taking input focus or replacing the visible screen.
     */
    public static void ensureRunningInBackground(String accountId, Consumer<LaunchResult> callback) {
        ensureRunning(accountId, result -> {
            if (result.success()) {
                SkuaPlayerBrowserSession.ensureBackground(accountId, result.pageUrl());
            }

            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    public static List<String> getRunningAccountIds() {
        return PlayerHostManager.getRunningAccountIds();
    }

    public static void stop(String accountId) {
        SkuaPlayerBrowserSession.close(accountId);
        PlayerHostManager.stop(accountId);
    }

    public static void stop() {
        SkuaPlayerBrowserSession.closeAll();
        PlayerHostManager.stopAll();
    }

    public record LaunchResult(boolean success, String pageUrl, String controlUrl, String controlToken, String message) {
    }
}
