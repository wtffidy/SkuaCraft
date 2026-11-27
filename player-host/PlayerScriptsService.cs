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
    private const string CatalogueUrl = "https://raw.githubusercontent.com/auqw/Scripts/refs/heads/Skua/scripts.json";
    private const string RawRoot = "https://raw.githubusercontent.com/auqw/Scripts/refs/heads/Skua/";
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(90) };
    private readonly ConcurrentDictionary<string, SemaphoreSlim> downloads = new(StringComparer.OrdinalIgnoreCase);
    private readonly SemaphoreSlim catalogueGate = new(1, 1);
    private bool remoteCatalogueLoaded;

    public RangedObservableCollection<ScriptInfo> Scripts { get; } = new();

    public PlayerScriptsService()
    {
        LoadCachedCatalogue();
    }

    public int Downloaded => Scripts.Count(info => info.Downloaded);
    public int Outdated => Scripts.Count(info => info.Outdated);
    public int Total => Scripts.Count;
    public int Missing => Total - Downloaded;

    public async ValueTask<List<ScriptInfo>> GetScriptsAsync(IProgress<string>? progress, CancellationToken token)
    {
        try
        {
            await RefreshCatalogueAsync(progress, token, false).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is not OperationCanceledException && Scripts.Count > 0)
        {
            progress?.Report($"Couldn't refresh the auqw/Scripts catalogue. Using the last cached list: {ex.Message}");
        }

        token.ThrowIfCancellationRequested();
        progress?.Report($"Loaded {Scripts.Count:N0} scripts from auqw/Scripts (Skua).");
        return ScriptInfo.Snapshot(Scripts);
    }

    public async Task RefreshScriptsAsync(IProgress<string>? progress, CancellationToken token)
    {
        await RefreshCatalogueAsync(progress, token, true).ConfigureAwait(false);
    }

    public async Task<bool> RefreshListCacheAsync(CancellationToken token)
    {
        try
        {
            await RefreshCatalogueAsync(null, token, true).ConfigureAwait(false);
            return Scripts.Count > 0;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            return false;
        }
    }

    public Task UpdateScriptDatesAsync(IProgress<string>? progress, CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        progress?.Report("Script metadata is supplied by the live auqw/Scripts catalogue.");
        return Task.CompletedTask;
    }

    public async Task<int> IncrementalUpdateScriptsAsync(IProgress<string>? progress, CancellationToken token)
    {
        await RefreshScriptsAsync(progress, token).ConfigureAwait(false);
        List<ScriptInfo> outdated = Scripts.Where(info => info.Downloaded && info.Outdated).ToList();
        if (outdated.Count == 0)
        {
            progress?.Report("Downloaded scripts are already up to date.");
            return 0;
        }

        HashSet<string> outdatedPaths = outdated
            .Select(info => info.FilePath.Replace('\\', '/'))
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        return await DownloadAllWhereAsync(
            info => outdatedPaths.Contains(info.FilePath.Replace('\\', '/')),
            progress,
            token
        ).ConfigureAwait(false);
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
            if (File.Exists(destination) && !string.IsNullOrWhiteSpace(info.Sha256))
            {
                string localHash = await ComputeSha256Async(destination, token).ConfigureAwait(false);
                if (string.Equals(localHash, info.Sha256, StringComparison.OrdinalIgnoreCase))
                {
                    byte[] localContents = await File.ReadAllBytesAsync(destination, token).ConfigureAwait(false);
                    ScriptTrust.RecordOfficial(info.FilePath, localContents);
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
        CancellationToken token = default
    )
    {
        ArgumentNullException.ThrowIfNull(predicate);
        List<ScriptInfo> selected = Scripts.Where(predicate).ToList();
        int completed = 0;
        int failed = 0;

        foreach (ScriptInfo info in selected)
        {
            token.ThrowIfCancellationRequested();
            try
            {
                await DownloadScriptAsync(info, token).ConfigureAwait(false);
                completed++;
                progress?.Report($"Downloaded {completed}/{selected.Count}: {info.Name}");
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception ex)
            {
                failed++;
                progress?.Report($"Failed to download {info.Name}: {ex.Message}");
            }
        }

        if (failed > 0)
        {
            progress?.Report($"Downloaded {completed} scripts; {failed} failed.");
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

    private async Task RefreshCatalogueAsync(IProgress<string>? progress, CancellationToken token, bool force)
    {
        await catalogueGate.WaitAsync(token).ConfigureAwait(false);
        try
        {
            if (!force && remoteCatalogueLoaded)
            {
                return;
            }

            progress?.Report("Checking auqw/Scripts for the latest Skua script catalogue...");
            using HttpResponseMessage response = await Http.GetAsync(
                CatalogueUrl,
                HttpCompletionOption.ResponseHeadersRead,
                token
            ).ConfigureAwait(false);
            response.EnsureSuccessStatusCode();

            string json = await response.Content.ReadAsStringAsync(token).ConfigureAwait(false);
            List<ScriptInfo> entries = Newtonsoft.Json.JsonConvert.DeserializeObject<List<ScriptInfo>>(json)
                ?? throw new InvalidDataException("The auqw/Scripts catalogue could not be parsed.");

            List<ScriptInfo> validEntries = NormalizeEntries(entries);
            if (validEntries.Count == 0)
            {
                throw new InvalidDataException("The auqw/Scripts catalogue did not contain any valid script entries.");
            }

            SaveCatalogueCache(json);
            Scripts.ReplaceRange(validEntries);
            remoteCatalogueLoaded = true;
            progress?.Report($"Loaded {validEntries.Count:N0} scripts from auqw/Scripts (Skua).");
        }
        finally
        {
            catalogueGate.Release();
        }
    }

    private void LoadCachedCatalogue()
    {
        try
        {
            string cacheFile = ClientFileSources.SkuaScriptsCacheFile;
            if (!File.Exists(cacheFile))
            {
                return;
            }

            string json = File.ReadAllText(cacheFile);
            List<ScriptInfo>? entries = Newtonsoft.Json.JsonConvert.DeserializeObject<List<ScriptInfo>>(json);
            if (entries is null || entries.Count == 0)
            {
                return;
            }

            List<ScriptInfo> validEntries = NormalizeEntries(entries);
            if (validEntries.Count > 0)
            {
                Scripts.ReplaceRange(validEntries);
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or Newtonsoft.Json.JsonException)
        {
            System.Diagnostics.Trace.WriteLine($"[Scripts] Cached catalogue could not be loaded: {ex.Message}");
        }
    }

    private static List<ScriptInfo> NormalizeEntries(IEnumerable<ScriptInfo?> entries)
    {
        List<ScriptInfo> normalized = new();
        HashSet<string> seenPaths = new(StringComparer.OrdinalIgnoreCase);

        foreach (ScriptInfo? info in entries)
        {
            if (info is null || !IsSafeRelativePath(info.FilePath))
            {
                continue;
            }

            string normalizedPath = info.FilePath.Replace('\\', '/');
            if (!seenPaths.Add(normalizedPath))
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
            if (!info.CachedDownloaded.GetValueOrDefault())
            {
                info.CachedLocalSize = 0;
            }

            normalized.Add(info);
        }

        return normalized;
    }

    private static void SaveCatalogueCache(string json)
    {
        try
        {
            string cacheFile = ClientFileSources.SkuaScriptsCacheFile;
            string? directory = Path.GetDirectoryName(cacheFile);
            if (string.IsNullOrWhiteSpace(directory))
            {
                return;
            }

            Directory.CreateDirectory(directory);
            string temporaryFile = cacheFile + $".{Guid.NewGuid():N}.tmp";
            try
            {
                File.WriteAllText(temporaryFile, json);
                File.Move(temporaryFile, cacheFile, true);
            }
            finally
            {
                TryDeleteFile(temporaryFile);
            }
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            System.Diagnostics.Trace.WriteLine($"[Scripts] Could not save the upstream catalogue cache: {ex.Message}");
        }
    }

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
