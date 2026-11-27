using CommunityToolkit.Mvvm.Messaging;
using Skua.Core.Messaging;

namespace SkuaCraft.PlayerHost;

/// <summary>
/// Writes script lifecycle events to the PlayerHost log so startup stalls can be
/// distinguished from problems inside the running script thread.
/// </summary>
public sealed class PlayerScriptDiagnostics : IDisposable
{
    public PlayerScriptDiagnostics()
    {
        StrongReferenceMessenger.Default.Register<PlayerScriptDiagnostics, ScriptStartedMessage, int>(
            this,
            (int)MessageChannels.ScriptStatus,
            static (_, _) => Console.WriteLine("[Script lifecycle] Script thread starting.")
        );

        StrongReferenceMessenger.Default.Register<PlayerScriptDiagnostics, ScriptStoppingMessage, int>(
            this,
            (int)MessageChannels.ScriptStatus,
            static (_, _) => Console.WriteLine("[Script lifecycle] Script stopping.")
        );

        StrongReferenceMessenger.Default.Register<PlayerScriptDiagnostics, ScriptStoppedMessage, int>(
            this,
            (int)MessageChannels.ScriptStatus,
            static (_, _) => Console.WriteLine("[Script lifecycle] Script stopped.")
        );

        StrongReferenceMessenger.Default.Register<PlayerScriptDiagnostics, ScriptErrorMessage, int>(
            this,
            (int)MessageChannels.ScriptStatus,
            static (_, message) => Console.Error.WriteLine(
                $"[Script lifecycle] Script error: {message.Exception}"
            )
        );
    }

    public void Dispose()
    {
        StrongReferenceMessenger.Default.UnregisterAll(this);
    }
}
