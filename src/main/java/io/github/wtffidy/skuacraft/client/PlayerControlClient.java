package io.github.wtffidy.skuacraft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class PlayerControlClient {
    private static final String TOKEN_HEADER = "X-SkuaCraft-Token";

    private static final ExecutorService IO = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "SkuaCraft-Control");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static volatile String controlUrl = "";
    private static volatile String controlToken = "";

    private PlayerControlClient() {
    }

    public static void connect(String baseUrl, String token) {
        if (baseUrl == null || token == null || baseUrl.isBlank() || token.isBlank()) {
            disconnect();
            return;
        }

        controlUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        controlToken = token;
    }

    public static void disconnect() {
        controlUrl = "";
        controlToken = "";
    }

    public static boolean isConnected() {
        return !controlUrl.isBlank() && !controlToken.isBlank();
    }

    public static void get(String endpoint, Consumer<JsonElement> callback) {
        request("GET", endpoint, null, callback);
    }

    public static void post(String endpoint, JsonObject body, Consumer<JsonElement> callback) {
        request("POST", endpoint, body, callback);
    }

    private static void request(String method, String endpoint, JsonObject body, Consumer<JsonElement> callback) {
        String baseUrl = controlUrl;
        String token = controlToken;

        if (baseUrl.isBlank() || token.isBlank()) {
            dispatch(callback, error("The embedded Skua host is not connected."));
            return;
        }

        String relativePath = endpoint == null ? "" : endpoint.replaceFirst("^/+", "");
        IO.execute(() -> {
            JsonElement result;

            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + relativePath))
                        .timeout("POST".equals(method) ? Duration.ofMinutes(10) : Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header(TOKEN_HEADER, token);

                if ("POST".equals(method)) {
                    builder.header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString()));
                } else {
                    builder.GET();
                }

                HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                String responseBody = response.body() == null ? "" : response.body();

                if (responseBody.isBlank()) {
                    JsonObject empty = new JsonObject();
                    empty.addProperty("success", response.statusCode() >= 200 && response.statusCode() < 300);
                    empty.addProperty("message", "HTTP " + response.statusCode());
                    result = empty;
                } else {
                    try {
                        result = JsonParser.parseString(responseBody);
                    } catch (RuntimeException parseException) {
                        result = error("Skua returned an invalid control response (HTTP " + response.statusCode() + ").");
                    }
                }

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (result.isJsonObject()) {
                        result.getAsJsonObject().addProperty("http_status", response.statusCode());
                        result.getAsJsonObject().addProperty("success", false);
                    }
                }
            } catch (Exception exception) {
                String message = exception instanceof java.net.http.HttpTimeoutException
                        ? "Skua Core did not respond within 10 minutes. Check config/skuacraft/logs/player-host.log; the script may still be processing."
                        : exception instanceof IOException
                            ? "Could not reach the embedded Skua runtime."
                            : exception.getMessage() == null ? "Unknown Skua control error." : exception.getMessage();
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                result = error(message);
            }

            dispatch(callback, result);
        });
    }

    private static JsonObject error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        result.addProperty("message", message);
        return result;
    }

    private static void dispatch(Consumer<JsonElement> callback, JsonElement result) {
        Minecraft.getInstance().execute(() -> callback.accept(result));
    }
}
