package io.github.wtffidy.skuacraft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.wtffidy.skuacraft.config.SkuaCraftConfig;
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

public final class SkuaApiClient {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SkuaCraft-API");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private SkuaApiClient() {
    }

    public static void get(String path, Consumer<JsonElement> callback) {
        request("GET", path, null, callback);
    }

    public static void post(String path, JsonObject body, Consumer<JsonElement> callback) {
        request("POST", path, body, callback);
    }

    private static void request(String method, String path, JsonObject body, Consumer<JsonElement> callback) {
        IO.execute(() -> {
            JsonElement result;

            try {
                URI uri = URI.create(SkuaCraftConfig.getBaseUrl() + path);
                HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(SkuaCraftConfig.REQUEST_TIMEOUT_SECONDS.get()))
                        .header("Accept", "application/json");

                if ("POST".equals(method)) {
                    String json = body == null ? "{}" : body.toString();
                    request.header("Content-Type", "application/json; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString(json));
                } else {
                    request.GET();
                }

                HttpResponse<String> response = HTTP.send(
                        request.build(),
                        HttpResponse.BodyHandlers.ofString()
                );

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
                        JsonObject error = new JsonObject();
                        error.addProperty("success", false);
                        error.addProperty("message", "VibeSkua returned an unreadable response (HTTP " + response.statusCode() + ").");
                        result = error;
                    }
                }

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (result.isJsonObject()) {
                        result.getAsJsonObject().addProperty("http_status", response.statusCode());
                    }
                }
            } catch (Exception exception) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("message", exception instanceof IOException
                        ? "Cannot reach VibeSkua at " + SkuaCraftConfig.getBaseUrl()
                        : exception.getMessage() == null ? "Unknown connection error." : exception.getMessage());
                result = error;
            }

            JsonElement finalResult = result;
            Minecraft.getInstance().execute(() -> callback.accept(finalResult));
        });
    }
}
