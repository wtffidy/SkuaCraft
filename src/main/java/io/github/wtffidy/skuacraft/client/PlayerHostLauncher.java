package io.github.wtffidy.skuacraft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.wtffidy.skuacraft.config.SkuaCraftConfig;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class PlayerHostLauncher {
    private static final ExecutorService STARTER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SkuaCraft-PlayerHost");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Process playerHostProcess;
    private static volatile Path stopFile;

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(PlayerHostLauncher::stop, "SkuaCraft-PlayerHost-Shutdown")
        );
    }

    private PlayerHostLauncher() {
    }

    public static void ensureRunning(Consumer<LaunchResult> callback) {
        STARTER.execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            Path gameDirectory = minecraft.gameDirectory.toPath();
            Path configDirectory = gameDirectory.resolve("config/skuacraft");
            Path sessionFile = configDirectory.resolve("player-session.json");
            Path requestedStopFile = configDirectory.resolve("player-stop.request");
            Path logFile = configDirectory.resolve("logs/player-host.log");
            Path executable = SkuaCraftConfig.getPlayerHostExecutable(gameDirectory);
            Path hostDirectory = executable.getParent() == null
                    ? configDirectory.resolve("player-host")
                    : executable.getParent();

            try {
                Files.createDirectories(hostDirectory);
                Files.createDirectories(sessionFile.getParent());
                Files.createDirectories(logFile.getParent());

                LaunchResult existing = readSession(sessionFile);
                if (existing.success() && isSessionProcessAlive(sessionFile)) {
                    dispatch(callback, existing);
                    return;
                }

                Process currentProcess = playerHostProcess;
                if (currentProcess != null && currentProcess.isAlive()) {
                    LaunchResult activeSession = waitForSession(currentProcess, sessionFile, Duration.ofSeconds(90));
                    dispatch(callback, activeSession);
                    return;
                }

                Path hostDll = executable.resolveSibling("SkuaCraft.PlayerHost.dll");
                List<String> command = new ArrayList<>();

                if (Files.isRegularFile(executable)) {
                    command.add(executable.toString());
                } else if (Files.isRegularFile(hostDll)) {
                    command.add("dotnet");
                    command.add(hostDll.toString());
                } else {
                    dispatch(callback, LaunchResult.failed(
                            "Skua PlayerHost is not installed. Run player-host\\Install-PlayerHost.ps1, "
                                    + "select this Minecraft instance, then reopen the console. Expected location: "
                                    + hostDirectory
                    ));
                    return;
                }

                Files.deleteIfExists(sessionFile);
                Files.deleteIfExists(requestedStopFile);
                stopFile = requestedStopFile;

                command.add("--session-file");
                command.add(sessionFile.toString());
                command.add("--stop-file");
                command.add(requestedStopFile.toString());

                ProcessBuilder processBuilder = new ProcessBuilder(command)
                        .directory(hostDirectory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

                // Skua compiles included files in parallel. Keep compilation bounded,
                // and let Minecraft/Rinku win CPU scheduling when both are busy.
                processBuilder.environment().put("DOTNET_PROCESSOR_COUNT", "2");

                playerHostProcess = processBuilder.start();
                LaunchResult result = waitForSession(
                        playerHostProcess,
                        sessionFile,
                        Duration.ofSeconds(90)
                );
                dispatch(callback, result);
            } catch (Exception exception) {
                dispatch(callback, LaunchResult.failed(
                        "Could not start the Skua player host: "
                                + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                ));
            }
        });
    }

    private static LaunchResult waitForSession(Process process, Path sessionFile, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                return LaunchResult.failed("The Skua player host stopped during startup. Check config/skuacraft/logs/player-host.log.");
            }

            LaunchResult result = readSession(sessionFile);
            if (result.success()) {
                return result;
            }

            Thread.sleep(250);
        }

        return LaunchResult.failed("The Skua player host did not publish its local player URL within "
                + timeout.toSeconds() + " seconds.");
    }

    private static LaunchResult readSession(Path sessionFile) {
        if (!Files.isRegularFile(sessionFile)) {
            return LaunchResult.failed("Waiting for the local Skua player host...");
        }

        try {
            String json = Files.readString(sessionFile);
            JsonElement parsed = JsonParser.parseString(json);

            if (!parsed.isJsonObject()) {
                return LaunchResult.failed("The player host returned an invalid session file.");
            }

            JsonObject session = parsed.getAsJsonObject();
            String pageUrl = string(session, "pageUrl");
            String controlUrl = string(session, "controlUrl");
            String controlToken = string(session, "controlToken");

            if (!isSafeLoopbackUrl(pageUrl)) {
                return LaunchResult.failed("The player host returned a URL that is not a loopback address.");
            }

            if (!isSafeLoopbackControlUrl(controlUrl) || controlToken.length() < 32) {
                return LaunchResult.failed("The player host returned an invalid control session.");
            }

            return LaunchResult.ready(pageUrl, controlUrl, controlToken);
        } catch (IOException | RuntimeException exception) {
            return LaunchResult.failed("Could not read the player host session: " + exception.getMessage());
        }
    }

    private static String string(JsonObject object, String property) {
        if (!object.has(property) || object.get(property).isJsonNull()) {
            return "";
        }

        try {
            return object.get(property).getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static boolean isSafeLoopbackUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            String host = uri.getHost();

            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                    && uri.getPort() > 0
                    && uri.getRawPath() != null
                    && uri.getRawPath().length() > 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSafeLoopbackControlUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            String host = uri.getHost();

            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                    && uri.getPort() > 0
                    && "/api/".equals(uri.getRawPath());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSessionProcessAlive(Path sessionFile) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(sessionFile));
            if (!parsed.isJsonObject()) {
                return false;
            }

            JsonObject session = parsed.getAsJsonObject();
            if (!session.has("processId")) {
                return false;
            }

            long processId = session.get("processId").getAsLong();
            return ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception exception) {
            return false;
        }
    }

    private static void dispatch(Consumer<LaunchResult> callback, LaunchResult result) {
        Minecraft.getInstance().execute(() -> callback.accept(result));
    }

    public static void stop() {
        Process process = playerHostProcess;
        Path stopPath = stopFile;

        if (process == null || !process.isAlive()) {
            return;
        }

        try {
            if (stopPath != null) {
                Files.createDirectories(stopPath.getParent());
                Files.writeString(stopPath, "stop");
            }

            process.destroy();
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception exception) {
            try {
                process.destroyForcibly();
            } catch (RuntimeException ignored) {
            }
        } finally {
            playerHostProcess = null;
            stopFile = null;
        }
    }

    public record LaunchResult(boolean success, String pageUrl, String controlUrl, String controlToken, String message) {
        public static LaunchResult ready(String pageUrl, String controlUrl, String controlToken) {
            return new LaunchResult(true, pageUrl, controlUrl, controlToken, "Player host ready");
        }

        public static LaunchResult failed(String message) {
            return new LaunchResult(false, "", "", "", message);
        }
    }
}
