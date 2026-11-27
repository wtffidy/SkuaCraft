using Skua.Core.Interfaces;
using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace SkuaCraft.PlayerHost;

public sealed class PlayerControlServer : IAsyncDisposable
{
    private const int MaxHeaderBytes = 32 * 1024;
    private const int MaxBodyBytes = 1024 * 1024;
    private const string TokenHeader = "X-SkuaCraft-Token";

    private readonly TcpListener listener = new(IPAddress.Loopback, 0);
    private readonly CancellationTokenSource shutdown = new();
    private readonly IScriptInterface bot;
    private readonly IScriptManager scriptManager;
    private readonly IGetScriptsService scripts;
    private readonly ConcurrentBag<Task> handlers = new();
    private Task? acceptTask;
    private bool started;

    public string Token { get; } = Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();

    public string BaseUrl
    {
        get
        {
            if (!started)
            {
                throw new InvalidOperationException("The control server has not started.");
            }

            int port = ((IPEndPoint)listener.LocalEndpoint).Port;
            return $"http://127.0.0.1:{port}/api/";
        }
    }

    public PlayerControlServer(IScriptInterface bot, IScriptManager scriptManager, IGetScriptsService scripts)
    {
        this.bot = bot;
        this.scriptManager = scriptManager;
        this.scripts = scripts;
    }

    public void Start()
    {
        if (started)
        {
            return;
        }

        listener.Start(16);
        started = true;
        acceptTask = AcceptClientsAsync(shutdown.Token);
    }

    private async Task AcceptClientsAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            TcpClient client;
            try
            {
                client = await listener.AcceptTcpClientAsync(cancellationToken).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }
            catch (SocketException) when (cancellationToken.IsCancellationRequested)
            {
                break;
            }

            Task handler = HandleClientAsync(client, cancellationToken);
            handlers.Add(handler);
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken cancellationToken)
    {
        using (client)
        await using (NetworkStream stream = client.GetStream())
        {
            try
            {
                HttpRequest? request = await ReadRequestAsync(stream, cancellationToken).ConfigureAwait(false);
                if (request is null)
                {
                    return;
                }

                if (!HasValidToken(request.Headers))
                {
                    await WriteResponseAsync(stream, 401, new { success = false, message = "Unauthorised player control request." }, cancellationToken).ConfigureAwait(false);
                    return;
                }

                (int statusCode, object response) = await RouteAsync(request, cancellationToken).ConfigureAwait(false);
                await WriteResponseAsync(stream, statusCode, response, cancellationToken).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
            }
            catch (Exception exception)
            {
                try
                {
                    await WriteResponseAsync(stream, 500, new
                    {
                        success = false,
                        message = exception.Message
                    }, CancellationToken.None).ConfigureAwait(false);
                }
                catch
                {
                }
            }
        }
    }

    private async Task<(int StatusCode, object Response)> RouteAsync(HttpRequest request, CancellationToken cancellationToken)
    {
        if (request.Method.Equals("GET", StringComparison.OrdinalIgnoreCase)
            && request.Path.Equals("/api/status", StringComparison.OrdinalIgnoreCase))
        {
            return (200, new
            {
                success = true,
                logged_in = bot.Player.LoggedIn,
                playing = bot.Player.Playing,
                username = bot.Player.Username,
                level = bot.Player.Level,
                hp = bot.Player.Health,
                max_hp = bot.Player.MaxHealth,
                mp = bot.Player.Mana,
                max_mp = bot.Player.MaxMana,
                gold = bot.Player.Gold,
                map = bot.Map.Name,
                server = bot.Servers.LastName,
                current_class = bot.Player.CurrentClass?.Name ?? string.Empty,
                script_running = scriptManager.ScriptRunning,
                script_paused = scriptManager.ScriptPaused,
                loaded_script = scriptManager.LoadedScript
            });
        }

        if (request.Method.Equals("GET", StringComparison.OrdinalIgnoreCase)
            && request.Path.Equals("/api/scripts", StringComparison.OrdinalIgnoreCase))
        {
            List<ScriptInfo> entries = ScriptInfo.Snapshot(scripts.Scripts);
            return (200, new
            {
                success = true,
                count = entries.Count,
                scripts = entries.Select(info => new
                {
                    name = info.Name,
                    fileName = info.FileName,
                    path = info.FilePath,
                    description = info.Description,
                    tags = info.Tags,
                    size = info.Size,
                    downloaded = info.Downloaded,
                    localSize = info.LocalSize,
                    outdated = info.Outdated,
                    category = GetCategory(info.FilePath)
                }).ToArray()
            });
        }

        if (request.Method.Equals("GET", StringComparison.OrdinalIgnoreCase)
            && request.Path.Equals("/api/scripts/state", StringComparison.OrdinalIgnoreCase))
        {
            return (200, new
            {
                success = true,
                loaded_script = scriptManager.LoadedScript,
                script_running = scriptManager.ScriptRunning,
                script_paused = scriptManager.ScriptPaused
            });
        }

        if (!request.Method.Equals("POST", StringComparison.OrdinalIgnoreCase))
        {
            return (405, new { success = false, message = "Method not allowed." });
        }

        using JsonDocument body = JsonDocument.Parse(
            string.IsNullOrWhiteSpace(request.Body) ? "{}" : request.Body
        );
        JsonElement root = body.RootElement;

        if (request.Path.Equals("/api/scripts/load", StringComparison.OrdinalIgnoreCase)
            || request.Path.Equals("/api/scripts/start", StringComparison.OrdinalIgnoreCase)
            || request.Path.Equals("/api/scripts/download", StringComparison.OrdinalIgnoreCase))
        {
            string path = GetString(root, "path");
            ScriptInfo? info = scripts.Scripts.FirstOrDefault(script =>
                string.Equals(script.FilePath, path, StringComparison.OrdinalIgnoreCase));

            if (info is null)
            {
                return (404, new { success = false, message = "That script is not in the configured Skua catalogue." });
            }

            await scripts.DownloadScriptAsync(info, cancellationToken).ConfigureAwait(false);

            if (request.Path.EndsWith("/download", StringComparison.OrdinalIgnoreCase))
            {
                return (200, new { success = true, message = $"Downloaded {info.Name}.", path = info.FilePath });
            }

            scriptManager.SetLoadedScript(info.LocalFile);

            if (request.Path.EndsWith("/load", StringComparison.OrdinalIgnoreCase))
            {
                return (200, new { success = true, message = $"Loaded {info.Name}.", path = info.FilePath });
            }

            Exception? startError = await scriptManager.StartScript().ConfigureAwait(false);
            return startError is null
                ? (200, new { success = true, message = $"Started {info.Name}.", path = info.FilePath })
                : (400, new { success = false, message = startError.Message, path = info.FilePath });
        }

        if (request.Path.Equals("/api/scripts/stop", StringComparison.OrdinalIgnoreCase))
        {
            await scriptManager.StopScript().ConfigureAwait(false);
            return (200, new { success = true, message = "Script stop requested." });
        }

        if (request.Path.Equals("/api/scripts/pause", StringComparison.OrdinalIgnoreCase))
        {
            bool requested = root.TryGetProperty("paused", out JsonElement pausedValue)
                && pausedValue.ValueKind is JsonValueKind.True or JsonValueKind.False
                ? pausedValue.GetBoolean()
                : !scriptManager.ScriptPaused;

            bool accepted = await scriptManager.SetPausedAsync(requested).ConfigureAwait(false);
            return accepted
                ? (200, new { success = true, message = requested ? "Script paused." : "Script resumed.", script_paused = scriptManager.ScriptPaused })
                : (400, new { success = false, message = "The script pause state could not be changed." });
        }

        return (404, new { success = false, message = "Unknown Skua player endpoint." });
    }

    private bool HasValidToken(IReadOnlyDictionary<string, string> headers)
    {
        if (!headers.TryGetValue(TokenHeader, out string? supplied) || supplied.Length != Token.Length)
        {
            return false;
        }

        return CryptographicOperations.FixedTimeEquals(
            Encoding.UTF8.GetBytes(supplied),
            Encoding.UTF8.GetBytes(Token)
        );
    }

    private static async Task<HttpRequest?> ReadRequestAsync(NetworkStream stream, CancellationToken cancellationToken)
    {
        using MemoryStream headerBuffer = new();
        byte[] singleByte = new byte[1];
        int state = 0;

        while (headerBuffer.Length < MaxHeaderBytes)
        {
            int read = await stream.ReadAsync(singleByte.AsMemory(0, 1), cancellationToken).ConfigureAwait(false);
            if (read == 0)
            {
                return null;
            }

            byte value = singleByte[0];
            headerBuffer.WriteByte(value);

            state = (state, value) switch
            {
                (0, 13) => 1,
                (1, 10) => 2,
                (2, 13) => 3,
                (3, 10) => 4,
                (_, 13) => 1,
                _ => 0
            };

            if (state == 4)
            {
                break;
            }
        }

        if (state != 4)
        {
            return null;
        }

        string headerText = Encoding.ASCII.GetString(headerBuffer.ToArray());
        string[] lines = headerText.Split("\r\n", StringSplitOptions.RemoveEmptyEntries);
        if (lines.Length == 0)
        {
            return null;
        }

        string[] requestParts = lines[0].Split(' ', 3, StringSplitOptions.RemoveEmptyEntries);
        if (requestParts.Length < 2)
        {
            return null;
        }

        Dictionary<string, string> headers = new(StringComparer.OrdinalIgnoreCase);
        foreach (string line in lines.Skip(1))
        {
            int separator = line.IndexOf(':');
            if (separator > 0)
            {
                headers[line[..separator].Trim()] = line[(separator + 1)..].Trim();
            }
        }

        int contentLength = 0;
        if (headers.TryGetValue("Content-Length", out string? lengthText)
            && (!int.TryParse(lengthText, out contentLength) || contentLength < 0 || contentLength > MaxBodyBytes))
        {
            return null;
        }

        byte[] body = new byte[contentLength];
        int offset = 0;
        while (offset < body.Length)
        {
            int read = await stream.ReadAsync(body.AsMemory(offset), cancellationToken).ConfigureAwait(false);
            if (read == 0)
            {
                return null;
            }

            offset += read;
        }

        string path = requestParts[1].Split('?', 2)[0];
        return new HttpRequest(requestParts[0], path, headers, Encoding.UTF8.GetString(body));
    }

    private static async Task WriteResponseAsync(NetworkStream stream, int statusCode, object response, CancellationToken cancellationToken)
    {
        byte[] body = JsonSerializer.SerializeToUtf8Bytes(response);
        string statusText = statusCode switch
        {
            200 => "OK",
            400 => "Bad Request",
            401 => "Unauthorized",
            404 => "Not Found",
            405 => "Method Not Allowed",
            _ => "Internal Server Error"
        };

        string header = $"HTTP/1.1 {statusCode} {statusText}\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + $"Content-Length: {body.Length}\r\n"
            + "Connection: close\r\n"
            + "Cache-Control: no-store\r\n\r\n";

        byte[] headerBytes = Encoding.ASCII.GetBytes(header);
        await stream.WriteAsync(headerBytes, cancellationToken).ConfigureAwait(false);
        await stream.WriteAsync(body, cancellationToken).ConfigureAwait(false);
        await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
    }

    private static string GetString(JsonElement body, string property)
    {
        return body.TryGetProperty(property, out JsonElement value)
            && value.ValueKind == JsonValueKind.String
            ? value.GetString() ?? string.Empty
            : string.Empty;
    }

    private static string GetCategory(string path)
    {
        string? directory = Path.GetDirectoryName(path.Replace('/', Path.DirectorySeparatorChar));
        return string.IsNullOrWhiteSpace(directory) ? "Root" : directory.Replace('\\', '/');
    }

    public async ValueTask DisposeAsync()
    {
        shutdown.Cancel();
        if (started)
        {
            listener.Stop();
        }

        if (acceptTask is not null)
        {
            try
            {
                await acceptTask.ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
            }
            catch (ObjectDisposedException)
            {
            }
        }

        try
        {
            await Task.WhenAll(handlers.ToArray()).ConfigureAwait(false);
        }
        catch
        {
        }

        shutdown.Dispose();
    }

    private sealed record HttpRequest(
        string Method,
        string Path,
        IReadOnlyDictionary<string, string> Headers,
        string Body
    );
}
