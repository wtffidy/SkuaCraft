# 🎮 SkuaCraft player architecture

## The product

The user opens a handheld console in Minecraft and plays the real AdventureQuest Worlds game client, with the Skua bot runtime available inside that same experience.

## Proven building blocks

### Browser renderer

Rinku is the preferred integration candidate: it is a Chromium/JCEF library mod with a Forge 1.20.1 build. It exposes the browser view through Minecraft's render/input pipeline. MCEF 2.1.6 is the fallback because its example code demonstrates the needed API: create a browser, render `browser.getRenderer().getTextureID()`, resize it, forward mouse/key/wheel events and close it cleanly.

### AQW game player

MVS v1.0.6 contains a working Ruffle web player:
- `Skua.Flash.Ruffle/RuffleHost.cs`: local loopback HTTP server, embedded HTML/JS/WASM/SWF resources, AQW HTTP proxy, socket proxy and WebSocket control channel.
- `Skua.Flash.Ruffle/ControlChannel.cs`: request/response and asynchronous ExternalInterface calls between JavaScript and .NET.
- `Skua.Flash.Ruffle/RuffleFlashUtil.cs`: implements Skua `IFlashUtil`, translates calls and connects them to the runtime.
- `Skua.AS3/skua/bin/skua.swf`: the Skua SWF loaded by the browser.

The web page loads Ruffle WebAssembly, then the Skua SWF. Its bridge proxies cross-origin game requests through the local host and routes ExternalInterface calls over a WebSocket.

### Skua runtime

Skua Core is .NET 10 and remains responsible for scripts, game state and automation. Minecraft's Java runtime must not be asked to execute those C# scripts or reimplement their API.

Initial packaging direction: a hidden .NET 10 player-host process launched by the mod. It owns RuffleHost + Skua Core and exposes only a loopback/token-protected session interface to Minecraft. The Minecraft browser loads the local page from this same process.

## Runtime lifecycle

1. Minecraft loads SkuaCraft and initializes Rinku.
2. The console is opened; the mod starts the player host if it is not already running.
3. The host binds a random port on `127.0.0.1` and creates a random session token.
4. RuffleHost serves the page, SWF and browser assets; the mod creates a Chromium view at the generated local URL.
5. The browser establishes its control WebSocket. Skua Core's `RuffleFlashUtil` receives game calls and answers them from the live runtime.
6. The browser texture is rendered on the handheld display. Input is forwarded to the browser only while the console is focused.
7. Closing the console pauses/dims or backgrounds the browser according to runtime policy; exiting the world shuts down the child process cleanly.

## Security and reliability

- Loopback-only sockets; never bind to `0.0.0.0`.
- Random session token; no unauthenticated control URL.
- No account passwords in logs, Minecraft config or process arguments.
- Do not block Minecraft's render thread on C# or browser calls.
- Supervise the child process, detect early exit, restart the session and show a visible error state.
- Dispose browser textures and native browser instances on close/world exit.
- Keep AQW game and script state in Skua Core, not duplicated in Java.

## Important verification gates

- Verify Ruffle's bundled WebAssembly and WebGL build against Rinku's actual Chromium version.
- Verify the MVS Skua SWF and server proxy behave correctly on Windows x64.
- Confirm the exact MVS Skua Core snapshot is compatible with the chosen VibeSkua fork/API surface.
- Prove a real login and playable world before investing in the full handheld model/animation.
