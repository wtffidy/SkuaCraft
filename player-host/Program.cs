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
        bridge = bridge.Replace(fitFunctionMarker, stageDiagnostic + fitFunctionMarker, StringComparison.Ordinal);
        bridge = bridge.Replace(
            loadedHandler,
            loadedHandler[..^1] + " setTimeout(startAqwLayoutMonitor, 0); }",
            StringComparison.Ordinal
        );

        const string scalePatch = scaleOption
            + "\n            forceScale: true,"
            + "\n            salign: \"\","
            + "\n            forceAlign: true,";

        bridge = bridge.Replace(scaleOption, scalePatch, StringComparison.Ordinal);
        resourceCache["web/bridge.js"] = Encoding.UTF8.GetBytes(bridge);

        Console.WriteLine("[Ruffle] Using SHOW_ALL with AQW's native 958x550 layout.");
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
