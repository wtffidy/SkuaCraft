using Skua.Core.Interfaces;

namespace SkuaCraft.PlayerHost;

public sealed class PlayerDispatcherService : IDispatcherService
{
    public void Invoke(Action action)
    {
        ThreadPool.QueueUserWorkItem(_ =>
        {
            try
            {
                action();
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine($"[Skua dispatcher] {exception}");
            }
        });
    }
}
