using Skua.Core.Interfaces;

namespace SkuaCraft.PlayerHost;

/// <summary>
/// Supplies a valid transparent image when a desktop screenshot is requested.
/// The Minecraft-hosted Ruffle surface is rendered by the Minecraft client and
/// is not exposed as a desktop window that Skua Core can capture.
/// </summary>
public sealed class PlayerScreenshotService : IScreenshotService
{
    private static readonly byte[] EmptyPng = Convert.FromBase64String(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pZsAAAAASUVORK5CYII="
    );

    public Task<byte[]> TakeScreenshotAsync() => Task.FromResult(EmptyPng);
}
