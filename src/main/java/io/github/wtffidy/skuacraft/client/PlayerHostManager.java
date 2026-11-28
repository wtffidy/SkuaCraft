package io.github.wtffidy.skuacraft.client;

import io.github.wtffidy.skuacraft.config.SkuaCraftConfig;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Owns the local Skua PlayerHost processes.
 *
 * Every non-legacy account receives an isolated APPDATA tree, session file,
 * stop signal, log, and Ruffle port file. Account identifiers are profile IDs,
 * never credentials.
 */
public final class PlayerHostManager {
    private static final String DEFAULT_ACCOUNT_ID = "default";
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);
    private static final ExecutorService STARTER = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "SkuaCraft-PlayerHost-Manager");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, PlayerHostInstance> INSTANCES = new ConcurrentHashMap<>();
    private static final Map<String, Object> ACCOUNT_LOCKS = new ConcurrentHashMap<>();
    private static final Object CAPACITY_LOCK = new Object();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PlayerHostManager::stopAll, "SkuaCraft-PlayerHost-Shutdown"));
    }

    private PlayerHostManager() {
    }

    public static void ensureRunning(String accountId, Consumer<LaunchResult> callback) {
        String normalizedId = normalizeAccountId(accountId);
        STARTER.execute(() -> startAccount(normalizedId, callback));
    }

    public static List<String> getRunningAccountIds() {
        List<String> accountIds = new ArrayList<>();

        for (Map.Entry<String, PlayerHostInstance> entry : INSTANCES.entrySet()) {
            PlayerHostInstance instance = entry.getValue();
            if (instance.isAlive()) {
                accountIds.add(entry.getKey());
            }
        }

        accountIds.sort(Comparator.naturalOrder());
        return List.copyOf(accountIds);
    }

    public static void stop(String accountId) {
        String normalizedId = normalizeAccountId(accountId);
        PlayerHostInstance instance = INSTANCES.remove(normalizedId);
        if (instance != null) {
            stopInstance(instance);
        }
    }

    public static void stopAll() {
        for (Map.Entry<String, PlayerHostInstance> entry : INSTANCES.entrySet()) {
            if (INSTANCES.remove(entry.getKey(), entry.getValue())) {
                stopInstance(entry.getValue());
            }
        }
    }

    private static void startAccount(String accountId, Consumer<LaunchResult> callback) {
        Object accountLock = ACCOUNT_LOCKS.computeIfAbsent(accountId, ignored -> new Object());

        synchronized (accountLock) {
            PlayerHostInstance current = INSTANCES.get(accountId);
            if (current != null && current.isAlive()) {
                LaunchResult existing = readSession(current.sessionFile);
                if (existing.success()) {
                    dispatch(callback, existing);
                    return;
                }

                Process process = current.process;
                if (process != null && process.isAlive()) {
                    dispatch(callback, waitForSession(process, current.sessionFile, STARTUP_TIMEOUT));
                    return;
                }
            }

            Minecraft minecraft = Minecraft.getInstance();
            Path gameDirectory = minecraft.gameDirectory.toPath();
            Path configDirectory = gameDirectory.resolve("config/skuacraft");
            AccountPaths paths = createAccountPaths(accountId, configDirectory);
            Path executable = SkuaCraftConfig.getPlayerHostExecutable(gameDirectory);
            Path hostDirectory = executable.getParent() == null
                    ? configDirectory.resolve("player-host")
                    : executable.getParent();

            PlayerHostInstance instance = new PlayerHostInstance(accountId, paths.sessionFile(), paths.stopFile());
            synchronized (CAPACITY_LOCK) {
                int maxSessions = SkuaCraftConfig.MAX_RUNNING_SESSIONS.get();
                int activeSessions = (int) INSTANCES.values().stream().filter(PlayerHostInstance::isAlive).count();
                if (current == null && activeSessions >= maxSessions) {
                    dispatch(callback, LaunchResult.failed(
                            "The session limit has been reached (" + activeSessions + "/" + maxSessions
                                    + "). Stop another account or increase player.maxRunningSessions in skuacraft-client.toml."
                    ));
                    return;
                }
                INSTANCES.put(accountId, instance);
            }

            try {
                Files.createDirectories(hostDirectory);
                Files.createDirectories(paths.accountDirectory());
                Files.createDirectories(paths.sessionFile().getParent());
                Files.createDirectories(paths.logFile().getParent());
                Files.createDirectories(paths.tempDirectory());

                LaunchResult existingSession = readSession(paths.sessionFile());
                if (existingSession.success() && isSessionProcessAlive(paths.sessionFile())) {
                    dispatch(callback, existingSession);
                    return;
                }

                List<String> command = buildCommand(executable, hostDirectory);
                if (command.isEmpty()) {
                    INSTANCES.remove(accountId, instance);
                    dispatch(callback, LaunchResult.failed(
                            "Skua PlayerHost is not installed. Run player-host\\Install-PlayerHost.ps1, "
                                    + "select this Minecraft instance, then reopen the console. Expected location: "
                                    + hostDirectory
                    ));
                    return;
                }

                Files.deleteIfExists(paths.sessionFile());
                Files.deleteIfExists(paths.stopFile());

                ProcessBuilder processBuilder = new ProcessBuilder(command)
                        .directory(hostDirectory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(paths.logFile().toFile()));

                Map<String, String> environment = processBuilder.environment();
                environment.put("DOTNET_PROCESSOR_COUNT", "1");
                environment.put("TEMP", paths.tempDirectory().toString());
                environment.put("TMP", paths.tempDirectory().toString());

                // Keep the legacy default profile attached to the user's existing
                // VibeSkua data. New controller profiles get isolated Skua settings.
                if (!DEFAULT_ACCOUNT_ID.equals(accountId)) {
                    Path appData = paths.accountDirectory().resolve("appdata");
                    Path localAppData = paths.accountDirectory().resolve("localappdata");
                    Files.createDirectories(appData);
                    Files.createDirectories(localAppData);
                    environment.put("APPDATA", appData.toString());
                    environment.put("LOCALAPPDATA", localAppData.toString());
                    environment.put("DOTNET_CLI_HOME", paths.accountDirectory().resolve("dotnet").toString());
                    Files.createDirectories(paths.accountDirectory().resolve("dotnet"));
                }

                processBuilder.command().add("--session-file");
                processBuilder.command().add(paths.sessionFile().toString());
                processBuilder.command().add("--stop-file");
                processBuilder.command().add(paths.stopFile().toString());

                Process process = processBuilder.start();
                instance.process = process;

                LaunchResult result = waitForSession(process, paths.sessionFile(), STARTUP_TIMEOUT);
                if (!result.success() && !process.isAlive()) {
                    INSTANCES.remove(accountId, instance);
                }
                dispatch(callback, result);
            } catch (Exception exception) {
                INSTANCES.remove(accountId, instance);
                dispatch(callback, LaunchResult.failed(
                        "Could not start the Skua player host: "
                                + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                ));
            }
        }
    }

    private static AccountPaths createAccountPaths(String accountId, Path configDirectory) {
        Path accountDirectory = DEFAULT_ACCOUNT_ID.equals(accountId)
                ? configDirectory
                : configDirectory.resolve("accounts").resolve(accountId);
        Path sessionFile = accountDirectory.resolve("player-session.json");
        Path stopFile = accountDirectory.resolve("player-stop.request");
        Path logFile = accountDirectory.resolve("logs/player-host.log");
        Path tempDirectory = accountDirectory.resolve("temp");
        return new AccountPaths(accountDirectory, sessionFile, stopFile, logFile, tempDirectory);
    }

    private static List<String> buildCommand(Path executable, Path hostDirectory) {
        Path hostDll = executable.resolveSibling("SkuaCraft.PlayerHost.dll");
        List<String> command = new ArrayList<>();

        if (Files.isRegularFile(executable)) {
            command.add(executable.toString());
        } else if (Files.isRegularFile(hostDll)) {
            command.add("dotnet");
            command.add(hostDll.toString());
        }

        return command;
    }

    private static LaunchResult waitForSession(Process process, Path sessionFile, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                return LaunchResult.failed(
                        "The Skua player host exited during startup (code " + process.exitValue() + "). Check "
                                + sessionFile.getParent().resolve("logs/player-host.log")
                                + " and " + sessionFile.getParent().resolve("logs/player-host-startup.log") + "."
                );
            }

            LaunchResult result = readSession(sessionFile);
            if (result.success()) {
                return result;
            }

            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return LaunchResult.failed("PlayerHost startup was interrupted.");
            }
        }

        return LaunchResult.failed("The Skua player host did not publish its local player URL within "
                + timeout.toSeconds() + " seconds.");
    }

    private static LaunchResult readSession(Path sessionFile) {
        if (!Files.isRegularFile(sessionFile)) {
            return LaunchResult.failed("Waiting for the local Skua player host...");
        }

        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(Files.readString(sessionFile));
            if (!parsed.isJsonObject()) {
                return LaunchResult.failed("The player host returned an invalid session file.");
            }

            com.google.gson.JsonObject session = parsed.getAsJsonObject();
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

    private static String string(com.google.gson.JsonObject object, String property) {
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
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(Files.readString(sessionFile));
            if (!parsed.isJsonObject()) {
                return false;
            }

            com.google.gson.JsonObject session = parsed.getAsJsonObject();
            if (!session.has("processId")) {
                return false;
            }

            long processId = session.get("processId").getAsLong();
            return ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception exception) {
            return false;
        }
    }

    private static void stopInstance(PlayerHostInstance instance) {
        Process process = instance.process;
        try {
            Files.createDirectories(instance.stopFile.getParent());
            Files.writeString(instance.stopFile, "stop");
            if (process != null && process.isAlive()) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } else {
                processId(instance.sessionFile).ifPresent(processId -> {
                    ProcessHandle.of(processId).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroy);
                });
            }
        } catch (Exception exception) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static java.util.Optional<Long> processId(Path sessionFile) {
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(Files.readString(sessionFile));
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("processId")) {
                return java.util.Optional.of(parsed.getAsJsonObject().get("processId").getAsLong());
            }
        } catch (Exception ignored) {
        }

        return java.util.Optional.empty();
    }

    private static String normalizeAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return DEFAULT_ACCOUNT_ID;
        }

        String normalized = accountId.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid Skua account profile ID.");
        }

        return normalized;
    }

    private static void dispatch(Consumer<LaunchResult> callback, LaunchResult result) {
        Minecraft.getInstance().execute(() -> callback.accept(result));
    }

    private record AccountPaths(Path accountDirectory, Path sessionFile, Path stopFile, Path logFile, Path tempDirectory) {
    }

    private static final class PlayerHostInstance {
        private final String accountId;
        private final Path sessionFile;
        private final Path stopFile;
        private volatile Process process;

        private PlayerHostInstance(String accountId, Path sessionFile, Path stopFile) {
            this.accountId = accountId;
            this.sessionFile = sessionFile;
            this.stopFile = stopFile;
        }

        private boolean isAlive() {
            if (process != null && process.isAlive()) {
                return true;
            }
            return isSessionProcessAlive(sessionFile);
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
