using System.Collections.Concurrent;
using Skua.Core.Interfaces;

namespace SkuaCraft.PlayerHost;

public sealed class PlayerPluginHelper : IPluginHelper
{
    private readonly ConcurrentDictionary<string, Action> menuActions = new(StringComparer.OrdinalIgnoreCase);

    public IReadOnlyCollection<string> MenuButtonNames => menuActions.Keys.ToArray();

    public void AddMenuButton(string text, Action action)
    {
        if (string.IsNullOrWhiteSpace(text) || action is null)
        {
            return;
        }

        menuActions[text] = action;
    }

    public void RemoveMenuButton(string text)
    {
        if (!string.IsNullOrWhiteSpace(text))
        {
            menuActions.TryRemove(text, out _);
        }
    }

    public bool TryInvoke(string text)
    {
        if (string.IsNullOrWhiteSpace(text) || !menuActions.TryGetValue(text, out Action? action))
        {
            return false;
        }

        action.Invoke();
        return true;
    }
}
