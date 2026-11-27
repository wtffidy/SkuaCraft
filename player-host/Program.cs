using CommunityToolkit.Mvvm.DependencyInjection;
using CommunityToolkit.Mvvm.Messaging;
using Microsoft.Extensions.DependencyInjection;
using Skua.Core.AppStartup;
using Skua.Core.GameProxy;
using Skua.Core.Interfaces;
using Skua.Core.Interfaces.Services;
using Skua.Core.Models;
using Skua.Core.Options;
using Skua.Core.Plugins;
using Skua.Core.Scripts;
using Skua.Core.Scripts.Helpers;
using Skua.Core.Services;
using Skua.Core.Skills;
using Skua.Flash.Ruffle;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Reflection;
using System.Text;
using System.Text.Json;

namespace SkuaCraft.PlayerHost;

public static class Program
{
    public static async Task<int> Main(string[] args)
    {
        string sessionFile = GetArgument(args, "--session-file")
            ?? Path.Combine(AppContext.BaseDirectory, "player-session.json");
        string stopFile = GetArgument(args, "--stop-file")
            ?? Path.Combine(AppContext.BaseDirectory, "player-stop.request");
        string? sessionDirectory = Path.GetDirectoryName(sessionFile);
        string startupLogDirectory = Path.Combine(
            string.IsNullOrWhiteSpace(sessionDirectory) ? AppContext.BaseDirectory : sessionDirectory,
            "logs"
        );
        string startupLogFile = Path.Combine(startupLogDirectory, "player-host-startup.log");

        WriteStartupDiagnostic(startupLogFile, $"[Startup] Main entered. PID={Environment.ProcessId}, BaseDirectory={AppContext.BaseDirectory}");
        AppDomain.CurrentDomain.UnhandledException += (_, eventArgs) =>
            WriteStartupDiagnostic(startupLogFile, $"[Startup] UNHANDLED EXCEPTION: {eventArgs.ExceptionObject}");
        AppDomain.CurrentDomain.ProcessExit += (_, _) =>
            WriteStartupDiagnostic(startupLogFile, "[Startup] ProcessExit event fired.");

        using CancellationTokenSource shutdown = new();
        Console.CancelKeyPress += (_, eventArgs) =>
        {
            eventArgs.Cancel = true;
            WriteStartupDiagnostic(startupLogFile, "[Startup] Console.CancelKeyPress received.");
            shutdown.Cancel();
        };

        try
        {
            try
            {
                using Process currentProcess = Process.GetCurrentProcess();
                currentProcess.PriorityClass = ProcessPriorityClass.BelowNormal;
                Console.WriteLine("[Runtime] PlayerHost process priority set to BelowNormal.");
            }
            catch (Exception exception) when (exception is InvalidOperationException or System.ComponentModel.Win32Exception or PlatformNotSupportedException)
            {
                Console.WriteLine($"[Runtime] Could not lower PlayerHost process priority: {exception.Message}");
            }

            WriteStartupDiagnostic(startupLogFile, "[Startup] Configuring dependency injection.");
            ServiceCollection services = new();
            ConfigureServices(services);
            WriteStartupDiagnostic(startupLogFile, "[Startup] Building dependency injection container.");

            await using ServiceProvider serviceProvider = services.BuildServiceProvider(
                new ServiceProviderOptions
                {
                    ValidateOnBuild = true,
                    ValidateScopes = true
                }
            );

            WriteStartupDiagnostic(startupLogFile, "[Startup] Dependency injection container built.");
            Ioc.Default.ConfigureServices(serviceProvider);
            using PlayerScriptDiagnostics scriptDiagnostics = new();
            Console.WriteLine($"[Runtime] PlayerHost is limited to {Environment.ProcessorCount} logical processors.");

            WriteStartupDiagnostic(startupLogFile, "[Startup] Initializing Skua client files.");
            IClientFilesService clientFiles = serviceProvider.GetRequiredService<IClientFilesService>();
            clientFiles.CreateDirectories();
            clientFiles.CreateFiles();
            WriteStartupDiagnostic(startupLogFile, "[Startup] Skua client files initialized.");

            WriteStartupDiagnostic(startupLogFile, "[Startup] Initializing Skua settings.");
            ISettingsService settings = serviceProvider.GetRequiredService<ISettingsService>();
            settings.SetApplicationVersion();
            WriteStartupDiagnostic(startupLogFile, "[Startup] Skua settings initialized.");

            WriteStartupDiagnostic(startupLogFile, "[Startup] Initializing Skua plugins.");
            IPluginManager plugins = serviceProvider.GetRequiredService<IPluginManager>();
            plugins.Initialize();
            WriteStartupDiagnostic(startupLogFile, "[Startup] Skua plugins initialized.");

            WriteStartupDiagnostic(startupLogFile, "[Startup] Resolving Skua script interface.");
            IScriptInterface bot = GetRequiredServiceWithFileLockRetry<IScriptInterface>(
                serviceProvider,
                "Skua script interface"
            );
            WriteStartupDiagnostic(startupLogFile, "[Startup] Resolving Skua managers and Ruffle services.");
            IScriptInterfaceManager botManager = serviceProvider.GetRequiredService<IScriptInterfaceManager>();
            IScriptManager scriptManager = serviceProvider.GetRequiredService<IScriptManager>();
            IGetScriptsService scriptCatalogue = serviceProvider.GetRequiredService<IGetScriptsService>();
            RuffleFlashUtil flash = serviceProvider.GetRequiredService<RuffleFlashUtil>();
            RuffleHost playerHost = serviceProvider.GetRequiredService<RuffleHost>();
            WriteStartupDiagnostic(startupLogFile, "[Startup] Skua managers and Ruffle services resolved.");
            string rufflePortFile = GetRufflePortFilePath(sessionFile);
            int savedRufflePort = ReadRufflePort(rufflePortFile);
            if (savedRufflePort > 0)
            {
                playerHost.Options.Port = savedRufflePort;
                Console.WriteLine($"[Ruffle] Reusing persistent player origin port {savedRufflePort}.");
            }

            Console.WriteLine("[Startup] Applying the embedded Ruffle bridge patch.");
            WriteStartupDiagnostic(startupLogFile, "[Startup] Entering ApplyRuffleScalingPatch.");
            ApplyRuffleScalingPatch(playerHost, startupLogFile);
            WriteStartupDiagnostic(startupLogFile, "[Startup] ApplyRuffleScalingPatch completed.");
            Console.WriteLine("[Startup] Ruffle bridge patch applied.");
            playerHost.Control.PageLog += message =>
            {
                if (message.StartsWith("AQW root fit", StringComparison.Ordinal)
                    || message.StartsWith("AQW root fit failed", StringComparison.Ordinal))
                {
                    Console.WriteLine($"[AQW scaling] {message}");
                }
                else if (message.StartsWith("AQW ExternalInterface slow call:", StringComparison.Ordinal)
                    || message.StartsWith("AQW ExternalInterface throughput:", StringComparison.Ordinal))
                {
                    Console.WriteLine($"[AQW bridge] {message}");
                }
            };

            // Mirrors MVS's SkuaStartupHandler.LoadGame behaviour. Ruffle requests
            // the Skua SWF after its page and ExternalInterface bridge are ready.
            bot.Flash.FlashCall += HandleFlashCall;

            Console.WriteLine("[Startup] Initializing the Skua script interface.");
            botManager.Initialize();
            Console.WriteLine("[Startup] Skua script interface initialized.");
            Console.WriteLine("[Startup] Starting the Ruffle player host.");
            flash.InitializeFlash();
            Console.WriteLine("[Startup] Ruffle player host initialized.");
            SaveRufflePort(rufflePortFile, playerHost.Port);
            Console.WriteLine($"[Ruffle] Persistent player origin port is {playerHost.Port}.");

            await using PlayerControlServer controlServer = new(bot, scriptManager, scriptCatalogue);
            controlServer.Start();

            WriteSessionFile(sessionFile, playerHost.PageUrl, controlServer.BaseUrl, controlServer.Token);
            WriteStartupDiagnostic(startupLogFile, "[Startup] Player session published successfully.");
            Console.WriteLine($"SKUA_PLAYER_READY {playerHost.PageUrl}");

            await WaitForStopAsync(stopFile, shutdown.Token).ConfigureAwait(false);
            return 0;

            void HandleFlashCall(string function, params object[] arguments)
            {
                if (string.Equals(function, "requestLoadGame", StringComparison.Ordinal))
                {
                    bot.Flash.Call("loadClient");
                }
            }
        }
        catch (OperationCanceledException exception) when (shutdown.IsCancellationRequested)
        {
            WriteStartupDiagnostic(startupLogFile, $"[Startup] Shutdown cancellation received: {exception.Message}");
            return 0;
        }
        catch (Exception exception)
        {
            WriteStartupDiagnostic(startupLogFile, $"[Startup] FATAL: {exception}");
            Console.Error.WriteLine($"SkuaCraft player host failed: {exception}");
            return 1;
        }
        finally
        {
            WriteStartupDiagnostic(startupLogFile, "[Startup] Cleaning up the player session file.");
            TryDeleteFile(sessionFile);
        }
    }

    private static void ApplyRuffleScalingPatch(RuffleHost playerHost, string startupLogFile)
    {
        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] Looking up embedded resource cache.");
        FieldInfo? cacheField = typeof(RuffleHost).GetField("_resourceCache", BindingFlags.Instance | BindingFlags.NonPublic);
        if (cacheField?.GetValue(playerHost) is not ConcurrentDictionary<string, byte[]> resourceCache)
        {
            throw new InvalidOperationException("Could not access the embedded Ruffle resource cache to apply responsive SWF scaling.");
        }

        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] Resource cache found. Enumerating Ruffle resources.");
        Assembly ruffleAssembly = typeof(RuffleHost).Assembly;
        string[] resourceNames = ruffleAssembly.GetManifestResourceNames();
        WriteStartupDiagnostic(startupLogFile, $"[RufflePatch] Found {resourceNames.Length} embedded resources.");
        string? bridgeResourceName = resourceNames
            .FirstOrDefault(name => string.Equals(name.Replace('\\', '/'), "web/bridge.js", StringComparison.Ordinal));

        if (bridgeResourceName is null)
        {
            throw new InvalidOperationException("The embedded Ruffle bridge.js resource was not found.");
        }

        WriteStartupDiagnostic(startupLogFile, $"[RufflePatch] Opening bridge resource '{bridgeResourceName}'.");
        using Stream? bridgeStream = ruffleAssembly.GetManifestResourceStream(bridgeResourceName);
        if (bridgeStream is null)
        {
            throw new InvalidOperationException("The embedded Ruffle bridge.js resource could not be opened.");
        }

        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] Reading bridge resource.");
        using StreamReader reader = new(bridgeStream);
        string bridge = reader.ReadToEnd();
        WriteStartupDiagnostic(startupLogFile, $"[RufflePatch] Bridge resource read ({bridge.Length:N0} characters).");

        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] Validating Ruffle bridge markers.");
        const string scaleOption = "            scale: \"showAll\",";
        if (!bridge.Contains(scaleOption, StringComparison.Ordinal))
        {
            throw new InvalidOperationException("The Ruffle scaling configuration changed; the responsive scaling patch was not applied.");
        }

        const string loadedHandler = "        if (name === \"loaded\") { setStatus(\"\"); setTimeout(applyQuality, 0); setTimeout(applyTouchUi, 0); setTimeout(defaultCrowd, 0); setTimeout(defaultUntargetSelf, 0); }";
        if (!bridge.Contains(loadedHandler, StringComparison.Ordinal))
        {
            throw new InvalidOperationException("The Skua game-loaded callback changed; the AQW stage-fit patch was not applied.");
        }

        const string externalCallMonitor = """
    let aqwCallWindowStarted = performance.now();
    let aqwCallWindowCount = 0;
    let aqwCallSlowCount = 0;

    function reportAqwExternalCall(name, startedAt) {
        const elapsed = performance.now() - startedAt;
        aqwCallWindowCount++;

        if (elapsed >= 100) {
            aqwCallSlowCount++;
            log("AQW ExternalInterface slow call: " + name + " took " + elapsed.toFixed(1) + " ms");
        }

        const now = performance.now();
        if (now - aqwCallWindowStarted >= 1000 && aqwCallWindowCount > 0) {
            log("AQW ExternalInterface throughput: " + aqwCallWindowCount + " calls/s, " + aqwCallSlowCount + " slow");
            aqwCallWindowStarted = now;
            aqwCallWindowCount = 0;
            aqwCallSlowCount = 0;
        }
    }

""";
        const string fitFunctionMarker = "    window.__skuaExt = function (name, args) {";
        if (!bridge.Contains(fitFunctionMarker, StringComparison.Ordinal))
        {
            throw new InvalidOperationException("The Skua ExternalInterface bridge changed; the AQW stage-fit patch was not applied.");
        }

        const string stageDiagnostic = """
    let aqwScaleMonitor = 0;
    let lastAqwViewport = "";

    function readAqwNumber(game, path) {
        try {
            const value = Number(JSON.parse(game.callExternalInterface("getGameObject", path)));
            return Number.isFinite(value) ? value : 0;
        } catch (error) {
            return 0;
        }
    }

    function keepAqwNativeLayout() {
        if (!player || !player.ruffle) return;

        try {
            const game = player.ruffle();
            const stageWidth = readAqwNumber(game, "stage.stageWidth") || 958;
            const stageHeight = readAqwNumber(game, "stage.stageHeight") || 550;
            const rootWidth = readAqwNumber(game, "width");
            const rootHeight = readAqwNumber(game, "height");
            const currentScaleX = readAqwNumber(game, "scaleX") || 1;
            const currentScaleY = readAqwNumber(game, "scaleY") || 1;

            if (rootWidth <= 0 || rootHeight <= 0 || stageWidth <= 0 || stageHeight <= 0) return;

            // The root bounds include oversized/off-stage AQW children. They are
            // not the intended viewport size. Keep AQW's own authored coordinates
            // and let the 958x550 Flash stage clip the off-stage portion naturally.
            if (Math.abs(currentScaleX - 1) > 0.001)
                game.callExternalInterface("setGameObject", "scaleX", 1);
            if (Math.abs(currentScaleY - 1) > 0.001)
                game.callExternalInterface("setGameObject", "scaleY", 1);

            const actualRootWidth = readAqwNumber(game, "width");
            const actualRootHeight = readAqwNumber(game, "height");
            const loginWidth = readAqwNumber(game, "mcLogin.width");
            const loginHeight = readAqwNumber(game, "mcLogin.height");
            const loginX = readAqwNumber(game, "mcLogin.x");
            const loginY = readAqwNumber(game, "mcLogin.y");
            const actualScaleX = readAqwNumber(game, "scaleX");
            const actualScaleY = readAqwNumber(game, "scaleY");
            const signature = [stageWidth, stageHeight, actualRootWidth, actualRootHeight, loginWidth, loginHeight, loginX, loginY, actualScaleX, actualScaleY]
                .map(value => value.toFixed(2)).join("|");

            if (signature !== lastAqwViewport) {
                lastAqwViewport = signature;
                log("AQW native layout: stage " + stageWidth + "x" + stageHeight
                    + ", root bounds " + actualRootWidth.toFixed(1) + "x" + actualRootHeight.toFixed(1)
                    + ", scale " + actualScaleX.toFixed(5) + "," + actualScaleY.toFixed(5)
                    + ", login " + loginWidth.toFixed(1) + "x" + loginHeight.toFixed(1)
                    + " at " + loginX.toFixed(1) + "," + loginY.toFixed(1));
            }
        } catch (error) {
            log("AQW native layout failed: " + error);
        }
    }

    function startAqwLayoutMonitor() {
        if (aqwScaleMonitor) clearInterval(aqwScaleMonitor);
        keepAqwNativeLayout();
        aqwScaleMonitor = setInterval(keepAqwNativeLayout, 500);
    }

""";
        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] Injecting AQW layout and ExternalInterface diagnostics.");
        bridge = bridge.Replace(
            fitFunctionMarker,
            stageDiagnostic + externalCallMonitor + fitFunctionMarker,
            StringComparison.Ordinal
        );

        const string externalCallStart = "            case \"call\": {\n                let reply;";
        const string instrumentedExternalCallStart = "            case \"call\": {\n                const callStartedAt = performance.now();\n                let reply;";
        if (!bridge.Contains(externalCallStart, StringComparison.Ordinal))
        {
            throw new InvalidOperationException("The Ruffle ExternalInterface call handler changed; call diagnostics were not applied.");
        }

        bridge = bridge.Replace(externalCallStart, instrumentedExternalCallStart, StringComparison.Ordinal);
        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] ExternalInterface start instrumentation applied.");

        const string externalCallEnd = "                send(reply);\n                break;\n            }\n            case \"batch\": {";
        const string instrumentedExternalCallEnd = "                send(reply);\n                reportAqwExternalCall(msg.f, callStartedAt);\n                break;\n            }\n            case \"batch\": {";
        if (!bridge.Contains(externalCallEnd, StringComparison.Ordinal))
        {
            throw new InvalidOperationException("The Ruffle ExternalInterface response handler changed; call diagnostics were not applied.");
        }

        bridge = bridge.Replace(externalCallEnd, instrumentedExternalCallEnd, StringComparison.Ordinal);
        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] ExternalInterface end instrumentation applied.");
        bridge = bridge.Replace(
            loadedHandler,
            loadedHandler[..^1] + " setTimeout(startAqwLayoutMonitor, 0); }",
            StringComparison.Ordinal
        );

        const string scalePatch = scaleOption
            + "\n            forceScale: true,"
            + "\n            salign: \"\","
            + "\n            forceAlign: true,";

        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] Applying final scale settings.");
        bridge = bridge.Replace(scaleOption, scalePatch, StringComparison.Ordinal);
        byte[] patchedBridge = Encoding.UTF8.GetBytes(bridge);
        WriteStartupDiagnostic(startupLogFile, $"[RufflePatch] Updating cached bridge ({patchedBridge.Length:N0} bytes).");
        resourceCache["web/bridge.js"] = patchedBridge;

        WriteStartupDiagnostic(startupLogFile, "[RufflePatch] Patch completed successfully.");
        Console.WriteLine("[Ruffle] Using SHOW_ALL with AQW's native 958x550 layout.");
    }

    private static T GetRequiredServiceWithFileLockRetry<T>(IServiceProvider serviceProvider, string serviceName)
        where T : notnull
    {
        const int maxAttempts = 8;
        IOException? lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++)
        {
            try
            {
                return serviceProvider.GetRequiredService<T>();
            }
            catch (IOException exception) when (IsFileSharingViolation(exception))
            {
                lastException = exception;
                if (attempt == maxAttempts)
                {
                    break;
                }

                Console.WriteLine(
                    $"[Startup] {serviceName} hit a temporary file lock. Retrying ({attempt}/{maxAttempts - 1})."
                );
                Thread.Sleep(500);
            }
        }

        throw new IOException(
            $"Could not initialize {serviceName} because a Skua profile file remained locked after {maxAttempts} attempts.",
            lastException
        );
    }

    private static bool IsFileSharingViolation(IOException exception)
    {
        int nativeError = exception.HResult & 0xFFFF;
        return nativeError is 32 or 33;
    }

    private static void WriteStartupDiagnostic(string path, string message)
    {
        // Mirror every checkpoint into the existing PlayerHost console log. The
        // dedicated startup log remains useful when stdout is unavailable.
        Console.WriteLine(message);

        try
        {
            string? directory = Path.GetDirectoryName(path);
            if (!string.IsNullOrWhiteSpace(directory))
            {
                Directory.CreateDirectory(directory);
            }

            File.AppendAllText(
                path,
                $"{DateTimeOffset.Now:O} {message}{Environment.NewLine}",
                Encoding.UTF8
            );
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or ArgumentException)
        {
            // Startup diagnostics must never prevent the embedded player from launching.
        }
    }

    private static void ConfigureServices(IServiceCollection services)
    {
        Skua.Core.AppStartup.Services.AddCommonServices(services);
        services.AddTransient(Skua.Core.AppStartup.Services.CreateCompiler);

        services.AddSingleton<IGetScriptsService, PlayerScriptsService>();
        services.AddSingleton<ICustomScriptService, CustomScriptService>();
        services.AddSingleton<IScriptPathResolver, ScriptPathResolver>();

        services.AddSingleton<ISettingsService, PlayerSettingsService>();
        services.AddSingleton<IScreenshotService, PlayerScreenshotService>();
        services.AddSingleton<IDialogService, PlayerDialogService>();
        services.AddSingleton<IDispatcherService, PlayerDispatcherService>();

        RegisterScriptableObjects(services);

        services.AddSingleton(_ => new RuffleHost(new RuffleHostOptions
        {
            Port = 0,
            Quality = "high",
            FrameRate = 60,
            MaxPixelRatio = 2,
            PreferredRenderer = "webgl",
            EngineMemory = "fork"
        }));

        services.AddSingleton<RuffleFlashUtil>(provider => new RuffleFlashUtil(
            provider.GetRequiredService<IMessenger>(),
            new Lazy<IScriptManager>(() => provider.GetRequiredService<IScriptManager>()),
            provider.GetRequiredService<RuffleHost>()
        ));

        services.AddSingleton<IFlashUtil>(provider => provider.GetRequiredService<RuffleFlashUtil>());
    }

    private static void RegisterScriptableObjects(IServiceCollection services)
    {
        services.AddSingleton<IScriptInterface, ScriptInterface>();
        services.AddSingleton<IScriptInterfaceManager>(provider =>
            provider.GetRequiredService<IScriptInterface>() as IScriptInterfaceManager
            ?? throw new InvalidOperationException("The Skua ScriptInterface does not implement IScriptInterfaceManager."));
        services.AddSingleton<IScriptManager, ScriptManager>();
        services.AddSingleton<IScriptStatus, ScriptManager>();
        services.AddSingleton<IScriptInventoryHelper, ScriptInventoryHelper>();
        services.AddSingleton<IScriptInventory, ScriptInventory>();
        services.AddSingleton<IScriptHouseInv, ScriptHouseInv>();
        services.AddSingleton<IScriptTempInv, ScriptTempInv>();
        services.AddSingleton<IScriptBank, ScriptBank>();
        services.AddSingleton<IAdvancedSkillContainer, AdvancedSkillContainer>();
        services.AddSingleton<IUltraBossHelper, UltraBossHelper>();
        services.AddSingleton<IScriptCombat, ScriptCombat>();
        services.AddSingleton<IScriptKill, ScriptKill>();
        services.AddSingleton<IScriptHunt, ScriptHunt>();
        services.AddSingleton<IScriptSkill, ScriptSkill>();
        services.AddSingleton<IScriptAuto, ScriptAuto>();
        services.AddSingleton<IScriptSelfAuras, ScriptSelfAuras>();
        services.AddSingleton<IScriptTargetAuras, ScriptTargetAuras>();
        services.AddSingleton<IScriptFaction, ScriptFaction>();
        services.AddSingleton<IScriptMonster, ScriptMonster>();
        services.AddSingleton<IScriptPlayer, ScriptPlayer>();
        services.AddSingleton<IScriptQuest, ScriptQuest>();
        services.AddSingleton<IScriptBoost, ScriptBoost>();
        services.AddSingleton<IScriptShop, ScriptShop>();
        services.AddSingleton<IScriptDrop, ScriptDrop>();
        services.AddSingleton<IScriptMap, ScriptMap>();
        services.AddSingleton<IScriptServers, ScriptServers>();
        services.AddSingleton<IScriptEvent, ScriptEvent>();
        services.AddSingleton<IScriptSend, ScriptSend>();
        services.AddTransient<IScriptOptionContainer, ScriptOptionContainer>();
        services.AddTransient<IOptionContainer, OptionContainer>();
        services.AddSingleton<IScriptOption, ScriptOption>();
        services.AddSingleton<IScriptLite, ScriptLite>();
        services.AddSingleton<IScriptBotStats, ScriptBotStats>();
        services.AddSingleton<IScriptHandlers, ScriptHandlers>();
        services.AddSingleton<IScriptWait, ScriptWait>();
        services.AddSingleton<IScriptAccounts, ScriptAccounts>();
        services.AddSingleton<ICaptureProxy, CaptureProxy>();
        services.AddSingleton<IMapService, MapService>();
        services.AddSingleton<ILogService, LogService>();
        services.AddSingleton<IQuestDataLoaderService, QuestDataLoaderService>();
        services.AddSingleton<IAuraMonitorService, AuraMonitorService>();
        services.AddSingleton<IJunkService, JunkService>();

        // Keep Skua's native client file and plugin services alive even though
        // the Avalonia desktop shell is not part of this Minecraft player.
        services.AddSingleton<IClientFilesService, ClientFilesService>();
        services.AddSingleton<IPluginHelper, PlayerPluginHelper>();
        services.AddSingleton<IPluginManager, PluginManager>();
        // PluginManager constructs PluginContainer with the discovered plugin instance.
        // Registering the container itself makes DI validate a phantom ISkuaPlugin dependency.
    }

    private static string GetRufflePortFilePath(string sessionFile)
    {
        string? directory = Path.GetDirectoryName(sessionFile);
        return Path.Combine(
            string.IsNullOrWhiteSpace(directory) ? AppContext.BaseDirectory : directory,
            "ruffle-port.txt"
        );
    }

    private static int ReadRufflePort(string path)
    {
        try
        {
            return int.TryParse(File.ReadAllText(path).Trim(), out int port)
                && port is >= 1024 and <= 65535
                ? port
                : 0;
        }
        catch (IOException)
        {
            return 0;
        }
        catch (UnauthorizedAccessException)
        {
            return 0;
        }
    }

    private static void SaveRufflePort(string path, int port)
    {
        if (port is < 1024 or > 65535)
        {
            return;
        }

        string? directory = Path.GetDirectoryName(path);
        if (string.IsNullOrWhiteSpace(directory))
        {
            return;
        }

        Directory.CreateDirectory(directory);
        string temporaryPath = path + ".tmp";
        File.WriteAllText(temporaryPath, port.ToString(System.Globalization.CultureInfo.InvariantCulture));
        File.Move(temporaryPath, path, true);
    }

    private static string? GetArgument(string[] args, string name)
    {
        for (int i = 0; i < args.Length - 1; i++)
        {
            if (string.Equals(args[i], name, StringComparison.OrdinalIgnoreCase))
            {
                return args[i + 1];
            }
        }

        return null;
    }

    private static void WriteSessionFile(string path, string pageUrl, string controlUrl, string controlToken)
    {
        string? directory = Path.GetDirectoryName(path);
        if (!string.IsNullOrWhiteSpace(directory))
        {
            Directory.CreateDirectory(directory);
        }

        string temporaryPath = path + ".tmp";
        string json = JsonSerializer.Serialize(new
        {
            pageUrl,
            controlUrl,
            controlToken,
            processId = Environment.ProcessId,
            version = typeof(Program).Assembly.GetName().Version?.ToString() ?? "0.1.0"
        });

        File.WriteAllText(temporaryPath, json);
        File.Move(temporaryPath, path, true);
    }

    private static async Task WaitForStopAsync(string stopFile, CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested && !File.Exists(stopFile))
        {
            await Task.Delay(250, cancellationToken).ConfigureAwait(false);
        }
    }

    private static void TryDeleteFile(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }
}
