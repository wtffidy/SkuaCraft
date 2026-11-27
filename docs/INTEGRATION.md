# 🔌 Embedded player integration

SkuaCraft hosts the real AQW + Skua runtime. It does not drive a separate VibeSkua desktop window through the old remote-control REST API.

## Player stack

| Layer | Implementation | Responsibility |
| --- | --- | --- |
| Minecraft UI | Forge 1.20.1 | Handheld item, player screen, mouse and keyboard input |
| Browser surface | Rinku 3.0.4 | Chromium view rendered to a Minecraft texture |
| Flash player | Ruffle WebAssembly | Render the Skua SWF |
| Player host | MVS Skua.Flash.Ruffle | Local page, HTTP/WS proxy, ExternalInterface bridge |
| Bot runtime | MVS Skua.Core | Game state, script manager, automation and bot APIs |

## Pinned runtime source

The repository pins [Arglax/MVS v1.0.6](https://github.com/Arglax/MVS/tree/v1.0.6) as the vendor/MVS Git submodule.

The embedded projects are:

- Skua.Core: actual Skua interfaces and bot runtime.
- Skua.Flash.Ruffle: IFlashUtil implementation, local Ruffle web host and ExternalInterface bridge.
- Skua.AS3/skua/bin/skua.swf: Skua's Flash game client.

SkuaCraft's player host is a small .NET 10 executable. It composes those projects with headless settings, dialog and dispatch services instead of starting the Avalonia desktop shell or its Windows ActiveX Flash control.

## Session lifecycle

1. The player screen asks the player-host launcher to start or reuse the .NET process.
2. The host creates the Skua Core dependency graph and the client file directories.
3. RuffleFlashUtil.InitializeFlash() starts a loopback-only RuffleHost.
4. The host writes a session file containing the tokenized page URL and process ID.
5. Minecraft verifies that the page URL is loopback and opens it in Rinku.
6. Ruffle loads the Skua SWF. Its requestLoadGame ExternalInterface event is routed to IFlashUtil.Call("loadClient").
7. Ruffle's ExternalInterface bridge continues to deliver Flash calls to the live Skua ScriptInterface.
8. Hiding the console releases browser focus but keeps the browser and Skua host alive. Reopening the console reuses that same browser session.

## Network boundary

- RuffleHost binds to 127.0.0.1; it is not exposed to the LAN.
- Ruffle uses a random session token in the local page URL and WebSocket path.
- The Minecraft side rejects player URLs that are not loopback HTTP URLs.
- Account credentials are not included in Minecraft config, session metadata or command-line arguments.
- AQW game traffic goes through the Ruffle host's intended proxy routes.

## Skua tools and scripts

The .NET host initializes the real IScriptInterface, IScriptManager, inventory, bank, quests, drops, options and other Skua services. The next UI milestone is to expose those services through Minecraft-native panels.

Scripts-for-bullshit remains a source of script categories and metadata only. SkuaCraft does not generate or edit scripts. Existing Skua scripts will run through the pinned Skua Core script manager once script discovery and load controls are wired into the Minecraft UI.

## Verification gates

The source integration is not considered complete until the following are tested in the actual Project Infinity client:

- Rinku initializes and displays the local Ruffle page.
- Ruffle loads the Skua SWF and reaches the AQW login screen.
- Login, map loading, movement, chat, audio and WebSocket reconnection work.
- The live player state reaches Skua Core.
- An existing script can be loaded and run from the in-game Skua tools.
- Closing and reopening the handheld preserves the running player session.
- Player-host crashes and Minecraft shutdown clean up the local process.
