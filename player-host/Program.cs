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

        using CancellationTokenSource shutdown = new();
        Console.CancelKeyPress += (_, eventArgs) =>
        {
            eventArgs.Cancel = true;
            shutdown.Cancel();
        };

        try
        {
            ServiceCollection services = new();
            ConfigureServices(services);

            await using ServiceProvider serviceProvider = services.BuildServiceProvider(
                new ServiceProviderOptions
                {
                    ValidateOnBuild = true,
                    ValidateScopes = true
                }
            );

            Ioc.Default.ConfigureServices(serviceProvider);

            IClientFilesService clientFiles = serviceProvider.GetRequiredService<IClientFilesService>();
            clientFiles.CreateDirectories();
            clientFiles.CreateFiles();

            ISettingsService settings = serviceProvider.GetRequiredService<ISettingsService>();
            settings.SetApplicationVersion();

            IPluginManager plugins = serviceProvider.GetRequiredService<IPluginManager>();
            plugins.Initialize();

            IScriptInterface bot = serviceProvider.GetRequiredService<IScriptInterface>();
            IScriptInterfaceManager botManager = serviceProvider.GetRequiredService<IScriptInterfaceManager>();
            IScriptManager scriptManager = serviceProvider.GetRequiredService<IScriptManager>();
            IGetScriptsService scriptCatalogue = serviceProvider.GetRequiredService<IGetScriptsService>();
            RuffleFlashUtil flash = serviceProvider.GetRequiredService<RuffleFlashUtil>();
            RuffleHost playerHost = serviceProvider.GetRequiredService<RuffleHost>();

            ApplyRuffleScalingPatch(playerHost);
            playerHost.Control.PageLog += message =>
            {
                if (message.StartsWith("AQW root fit", StringComparison.Ordinal)
                    || message.StartsWith("AQW root fit failed", StringComparison.Ordinal))
                {
                    Console.WriteLine($"[AQW scaling] {message}");
                }
            };

            // Mirrors MVS's SkuaStartupHandler.LoadGame behaviour. Ruffle requests
            // the Skua SWF after its page and ExternalInterface bridge are ready.
            bot.Flash.FlashCall += HandleFlashCall;

            botManager.Initialize();
            flash.InitializeFlash();

            await using PlayerControlServer controlServer = new(bot, scriptManager, scriptCatalogue);
            controlServer.Start();

            WriteSessionFile(sessionFile, playerHost.PageUrl, controlServer.BaseUrl, controlServer.Token);
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
        catch (OperationCanceledException)
        {
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"SkuaCraft player host failed: {exception}");
            return 1;
        }
        finally
        {
            TryDeleteFile(sessionFile);
        }
    }

    private static void ApplyRuffleScalingPatch(RuffleHost playerHost)
    {
        FieldInfo? cacheField = typeof(RuffleHost).GetField("_resourceCache", BindingFlags.Instance | BindingFlags.NonPublic);
        if (cacheField?.GetValue(playerHost) is not ConcurrentDictionary<string, byte[]> resourceCache)
        {
            throw new InvalidOperationException("Could not access the embedded Ruffle resource cache to apply responsive SWF scaling.");
        }

        Assembly ruffleAssembly = typeof(RuffleHost).Assembly;
        string? bridgeResourceName = ruffleAssembly.GetManifestResourceNames()
            .FirstOrDefault(name => string.Equals(name.Replace('\\', '/'), "web/bridge.js", StringComparison.Ordinal));

        if (bridgeResourceName is null)
        {
            throw new InvalidOperationException("The embedded Ruffle bridge.js resource was not found.");
        }

        using Stream? bridgeStream = ruffleAssembly.GetManifestResourceStream(bridgeResourceName);
        if (bridgeStream is null)
        {
            throw new InvalidOperationException("The embedded Ruffle bridge.js resource could not be opened.");
        }

        using StreamReader reader = new(bridgeStream);
        string bridge = reader.ReadToEnd();

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

        const string fitFunctionMarker = "    window.__skuaExt = function (name, args) {";
        if (!bridge.Contains(fitFunctionMarker, StringComparison.Ordinal))
        {
            throw new InvalidOperationException("The Skua ExternalInterface bridge changed; the AQW stage-fit patch was not applied.");
        }

        const string stageDiagnostic = """
    let aqwFitTimer = 0;
    let lastAqwFit = "";

    function readAqwNumber(game, path) {
        try {
            const value = Number(JSON.parse(game.callExternalInterface("getGameObject", path)));
            return Number.isFinite(value) ? value : 0;
        } catch (error) {
            return 0;
        }
    }

    function fitAqwRootToStage() {
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

            // DisplayObject.width/height include the current transform. Convert
            // back to the unscaled bounds so repeated checks remain idempotent.
            const baseWidth = rootWidth / Math.abs(currentScaleX);
            const baseHeight = rootHeight / Math.abs(currentScaleY);
            const fitScale = Math.min(stageWidth / baseWidth, stageHeight / baseHeight);
            if (!Number.isFinite(fitScale) || fitScale <= 0) return;

            const targetScaleX = Math.sign(currentScaleX) * fitScale;
            const targetScaleY = Math.sign(currentScaleY) * fitScale;

            if (Math.abs(currentScaleX - targetScaleX) > 0.001)
                game.callExternalInterface("setGameObject", "scaleX", targetScaleX);
            if (Math.abs(currentScaleY - targetScaleY) > 0.001)
                game.callExternalInterface("setGameObject", "scaleY", targetScaleY);

            const actualScaleX = readAqwNumber(game, "scaleX");
            const actualScaleY = readAqwNumber(game, "scaleY");
            const actualWidth = readAqwNumber(game, "width");
            const actualHeight = readAqwNumber(game, "height");
            const signature = [baseWidth, baseHeight, stageWidth, stageHeight, fitScale, actualWidth, actualHeight]
                .map(value => value.toFixed(2)).join("|");
            if (signature !== lastAqwFit) {
                lastAqwFit = signature;
                log("AQW root fit: source " + baseWidth.toFixed(1) + "x" + baseHeight.toFixed(1)
                    + ", stage " + stageWidth + "x" + stageHeight
                    + ", target scale " + fitScale.toFixed(5)
                    + ", actual scale " + actualScaleX.toFixed(5) + "," + actualScaleY.toFixed(5)
                    + ", actual root " + actualWidth.toFixed(1) + "x" + actualHeight.toFixed(1));
            }
        } catch (error) {
            log("AQW root fit failed: " + error);
        }
    }

    function startAqwFitMonitor() {
        if (aqwFitTimer) clearInterval(aqwFitTimer);
        fitAqwRootToStage();
        aqwFitTimer = setInterval(fitAqwRootToStage, 500);
    }

""";
        bridge = bridge.Replace(fitFunctionMarker, stageDiagnostic + fitFunctionMarker, StringComparison.Ordinal);
        bridge = bridge.Replace(
            loadedHandler,
            loadedHandler[..^1] + " setTimeout(startAqwFitMonitor, 0); }",
            StringComparison.Ordinal
        );

        const string scalePatch = scaleOption
            + "\n            forceScale: true,"
            + "\n            salign: \"\","
            + "\n            forceAlign: true,";

        bridge = bridge.Replace(scaleOption, scalePatch, StringComparison.Ordinal);
        resourceCache["web/bridge.js"] = Encoding.UTF8.GetBytes(bridge);

        Console.WriteLine("[Ruffle] Using SHOW_ALL and automatic AQW root fitting.");
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
