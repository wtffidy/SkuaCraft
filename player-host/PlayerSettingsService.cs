using Skua.Core.Interfaces;
using Skua.Core.Models;
using Skua.Core.Services;

namespace SkuaCraft.PlayerHost;

public sealed class PlayerSettingsService : ISettingsService
{
    private readonly UnifiedSettingsService settings = new();

    public PlayerSettingsService()
    {
        settings.Initialize(AppRole.Client);
    }

    public T? Get<T>(string key) => settings.Get<T>(key);

    public T Get<T>(string key, T defaultValue) => settings.Get(key, defaultValue);

    public void Set<T>(string key, T value) => settings.Set(key, value);

    public void Initialize(AppRole role) => settings.Initialize(role);

    public SharedSettings GetShared() => settings.GetShared();

    public ClientSettings GetClient() => settings.GetClient();

    public ManagerSettings GetManager() => settings.GetManager();

    public void SetApplicationVersion() => settings.SetApplicationVersion();

    public void ReloadSettings() => settings.ReloadSettings();

    public bool TryReloadSettings() => settings.TryReloadSettings();

    public bool TryUpdate<T>(string key, T defaultValue, Func<T, T> change, out T result)
        => settings.TryUpdate(key, defaultValue, change, out result);
}
