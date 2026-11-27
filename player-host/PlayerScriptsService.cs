using CommunityToolkit.Mvvm.ComponentModel;
using Skua.Core.Interfaces;
using Skua.Core.Models;
using Skua.Core.Models.GitHub;
using Skua.Core.Scripts;
using Skua.Core.Utils;
using System.Collections.Concurrent;
using System.Net.Http;
using System.Security.Cryptography;

namespace SkuaCraft.PlayerHost;

public sealed class PlayerScriptsService : ObservableObject, IGetScriptsService
{
    private const string RawRoot = "https://raw.githubusercontent.com/wtffidy/Scripts-for-bullshit/refs/heads/Skua/";
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(45) };
    private readonly ConcurrentDictionary<string, SemaphoreSlim> downloads = new(StringComparer.OrdinalIgnoreCase);

    public RangedObservableCollection<ScriptInfo> Scripts { get; } = new();

    public PlayerScriptsService()
    {
        using Stream stream = typeof(PlayerScriptsService).Assembly
            .GetManifestResourceStream("SkuaCraft.PlayerHost.ScriptCatalogue.json")
            ?? throw new FileNotFoundException("The bundled Scripts-for-bullshit catalogue is missing.");

        using StreamReader reader = new(stream);
        string json = reader.ReadToEnd();
        List<ScriptInfo> entries = Newtonsoft.Json.JsonConvert.DeserializeObject<List<ScriptInfo>>(json)
            ?? throw new InvalidDataException("The bundled script catalogue could not be parsed.");

        foreach (ScriptInfo info in entries)
        {
            if (info is null || !IsSafeRelativePath(info.FilePath))
            {
                continue;
            }

            info.DownloadUrl = BuildDownloadUrl(info.FilePath);
            if (string.Equals(info.Description, "null", StringComparison.OrdinalIgnoreCase))
            {
                info.Description = string.Empty;
            }

            info.Tags = (info.Tags ?? Array.Empty<string>())
                .Where(tag => !string.IsNullOrWhiteSpace(tag) && !string.Equals(tag, "null", StringComparison.OrdinalIgnoreCase))
                .ToArray();

            info.CachedDownloaded = File.Exists(info.LocalFile);
            Scripts.Add(info);
        }
    }

    public int Downloaded => Scripts.Count(info => info.Downloaded);
    public int Outdated => Scripts.Count(info => info.Outdated);
    public int Total => Scripts.Count;
    public int Missing => Total - Downloaded;

    public ValueTask<List<ScriptInfo>> GetScriptsAsync(IProgress<string>? progress, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        progress?.Report($"Loaded {Scripts.Count:N0} scripts from wtffidy/Scripts-for-bullshit (Skua).");
        return ValueTask.FromResult(ScriptInfo.Snapshot(Scripts));
    }

    public Task RefreshScriptsAsync(IProgress<string>? progress, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        progress?.Report("Using the pinned wtffidy/Scripts-for-bullshit (Skua) catalogue.");
        return Task.CompletedTask;
    }

    public Task<bool> RefreshListCacheAsync(CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        return Task.FromResult(Scripts.Count > 0);
    }

    public Task UpdateScriptDatesAsync(IProgress<string>? progress, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        progress?.Report("Script dates are supplied by the pinned catalogue.");
        return Task.CompletedTask;
    }

    public Task<int> IncrementalUpdateScriptsAsync(IProgress<string>? progress, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        progress?.Report("The pinned catalogue is read-only. Update SkuaCraft to refresh its snapshot.");
        return Task.FromResult(0);
    }

    public Task<long> CheckAdvanceSkillSetsUpdates() => Task.FromResult(0L);

    public async Task DownloadScriptAsync(ScriptInfo info, CancellationToken token = default)
    {
        ArgumentNullException.ThrowIfNull(info);
        if (!IsSafeRelativePath(info.FilePath))
        {
            throw new InvalidDataException($"The script path '{info.FilePath}' is not a safe relative path.");
        }

        string root = Path.GetFullPath(ClientFileSources.SkuaScriptsDIR) + Path.DirectorySeparatorChar;
        string destination = Path.GetFullPath(info.LocalFile);
        if (!destination.StartsWith(root, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException($"Refusing to download '{info.FilePath}' outside the Skua scripts folder.");
        }

        string key = info.FilePath.Replace('\\', '/');
        SemaphoreSlim gate = downloads.GetOrAdd(key, static _ => new SemaphoreSlim(1, 1));
        await gate.WaitAsync(token).ConfigureAwait(false);

        try
        {
            if (File.Exists(destination))
            {
                string localHash = await ComputeSha256Async(destination, token).ConfigureAwait(false);
                if (string.IsNullOrWhiteSpace(info.Sha256)
                    || string.Equals(localHash, info.Sha256, StringComparison.OrdinalIgnoreCase))
                {
                    info.CachedDownloaded = true;
                    info.CachedLocalSize = (int)new FileInfo(destination).Length;
                    return;
                }
            }

            Uri downloadUri = new(BuildDownloadUrl(info.FilePath), UriKind.Absolute);
            using HttpResponseMessage response = await Http.GetAsync(
                downloadUri,
                HttpCompletionOption.ResponseHeadersRead,
                token
            ).ConfigureAwait(false);
            response.EnsureSuccessStatusCode();

            byte[] content = await response.Content.ReadAsByteArrayAsync(token).ConfigureAwait(false);
            string remoteHash = Convert.ToHexString(SHA256.HashData(content)).ToLowerInvariant();
            if (!string.IsNullOrWhiteSpace(info.Sha256)
                && !string.Equals(remoteHash, info.Sha256, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException($"SHA-256 verification failed for '{info.FilePath}'.");
            }

            string? directory = Path.GetDirectoryName(destination);
            if (string.IsNullOrWhiteSpace(directory))
            {
                throw new InvalidDataException("The script destination folder is invalid.");
            }

            Directory.CreateDirectory(directory);
            string temporaryFile = destination + $".{Guid.NewGuid():N}.tmp";
            try
            {
                await File.WriteAllBytesAsync(temporaryFile, content, token).ConfigureAwait(false);
                File.Move(temporaryFile, destination, true);
            }
            finally
            {
                TryDeleteFile(temporaryFile);
            }

            info.CachedDownloaded = true;
            info.CachedLocalSize = content.Length;
            ScriptTrust.RecordOfficial(info.FilePath, content);
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<int> DownloadAllWhereAsync(
        Func<ScriptInfo, bool> predicate,
        IProgress<string>? progress = null,
        CancellationToken token = default)
    {
        ArgumentNullException.ThrowIfNull(predicate);
        List<ScriptInfo> selected = Scripts.Where(predicate).ToList();
        int completed = 0;

        foreach (ScriptInfo info in selected)
        {
            token.ThrowIfCancellationRequested();
            await DownloadScriptAsync(info, token).ConfigureAwait(false);
            completed++;
            progress?.Report($"Downloaded {completed}/{selected.Count}: {info.Name}");
        }

        return completed;
    }

    public Task DeleteScriptAsync(ScriptInfo info)
    {
        ArgumentNullException.ThrowIfNull(info);
        if (!IsSafeRelativePath(info.FilePath))
        {
            throw new InvalidDataException($"The script path '{info.FilePath}' is not a safe relative path.");
        }

        string root = Path.GetFullPath(ClientFileSources.SkuaScriptsDIR) + Path.DirectorySeparatorChar;
        string destination = Path.GetFullPath(info.LocalFile);
        if (!destination.StartsWith(root, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException($"Refusing to delete '{info.FilePath}' outside the Skua scripts folder.");
        }

        TryDeleteFile(destination);
        info.CachedDownloaded = false;
        info.CachedLocalSize = 0;
        return Task.CompletedTask;
    }

    public Task<bool> UpdateSkillSetsFile() => Task.FromResult(false);
    public Task<bool> UpdateQuestDataFile() => Task.FromResult(false);
    public Task<long> CheckJunkItemsUpdates() => Task.FromResult(0L);
    public Task<bool> UpdateJunkItemsFile() => Task.FromResult(false);

    private static string BuildDownloadUrl(string filePath)
    {
        string escapedPath = string.Join("/",
            filePath.Replace('\\', '/').Split('/').Select(Uri.EscapeDataString));
        return RawRoot + escapedPath;
    }

    private static bool IsSafeRelativePath(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || Path.IsPathRooted(path))
        {
            return false;
        }

        string[] segments = path.Replace('\\', '/').Split('/');
        return segments.All(segment =>
            !string.IsNullOrWhiteSpace(segment)
            && segment != "."
            && segment != ".."
            && !segment.Contains(':'));
    }

    private static async Task<string> ComputeSha256Async(string path, CancellationToken token)
    {
        await using FileStream stream = new(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        byte[] hash = await SHA256.HashDataAsync(stream, token).ConfigureAwait(false);
        return Convert.ToHexString(hash).ToLowerInvariant();
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
