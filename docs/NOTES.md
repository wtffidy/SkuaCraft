# 🥔 SkuaCraft field notes

> **Product definition:** a real AQW + Skua player hosted inside Minecraft, presented as an in-world handheld console.

## 01 · Non-negotiable definition

SkuaCraft must run the actual AQW game client and the actual Skua runtime in the Minecraft client experience.

A dashboard that sends HTTP commands to a separate desktop VibeSkua window does **not** satisfy the goal. The old remote-control panels are a prototype and are not the final architecture.

The player must support the real game loop: load the SWF, connect to AQW, log in, render the game, receive mouse/keyboard input, expose Skua's game APIs to the bot runtime, and run scripts from inside Minecraft.

## 02 · Runtime investigation

### Desktop VibeSkua

The current desktop app uses an Avalonia native host around the Windows Shockwave Flash ActiveX/WinForms control. That host is Windows-specific and cannot be moved directly into Minecraft's Java/Forge process.

Reference files:
- [WinFormsFlashHost.cs](https://github.com/wtffidy/VibeSkua/blob/main/Skua.App.Avalonia/Controls/WinFormsFlashHost.cs)
- [Skua.App.Avalonia.csproj](https://github.com/wtffidy/VibeSkua/blob/main/Skua.App.Avalonia/Skua.App.Avalonia.csproj)

### MVS v1.0.6

MVS contains the practical portable route:

- `Skua.Flash.Ruffle` is a .NET 10 library implementing `IFlashUtil`.
- `RuffleHost` serves the local HTML/JavaScript/Ruffle WebAssembly assets and the `skua.swf`.
- The host proxies AQW HTTP requests and WebSocket game sockets.
- `ControlChannel` bridges Ruffle's JavaScript ExternalInterface calls to .NET.
- `RuffleFlashUtil` connects that bridge to Skua Core, so Skua scripts continue to use the real game model and interfaces.
- The Android app displays the player through Android WebView, but that Android UI itself is not the target to port.

Source: [Arglax/MVS v1.0.6](https://github.com/Arglax/MVS/tree/v1.0.6).

## 03 · Intended architecture

| Component | Responsibility |
| --- | --- |
| Minecraft Forge mod | Item, first-person handheld, Minecraft screens, navigation and key bindings |
| Rinku browser view | Render the local Ruffle player inside Minecraft and forward input |
| Ruffle page | Run the AQW SWF using WebAssembly/WebGL |
| .NET 10 player host | Local Ruffle HTTP/WebSocket host and Skua runtime |
| Skua Core | Game state, scripts, automation, client options, inventory, quests and other bot interfaces |
| Script catalogue | Existing script metadata and sources; no new script-generation system |

The .NET player host is started as a hidden child process by SkuaCraft. It is not a second visible Skua window. The Minecraft client owns the visible presentation and input experience.

### Local connection rules

- Bind all player-host endpoints to loopback only.
- Use a random per-session token for the Ruffle page and control websocket.
- Do not expose the player host to the local network.
- Do not save account credentials in Minecraft config or logs.
- Keep game networking and bot execution in the Skua runtime, not in Minecraft's render thread.

## 04 · Browser integration candidate

**Preferred:** Rinku, Forge 1.20.1 library release 3.0.4.

Rinku is a maintained Chromium/JCEF browser library and supports a controllable browser rendered into Minecraft. Its project documentation lists Windows, macOS and Linux x64/ARM64 support and Chromium 151.0.7922.34.

**Fallback:** MCEF 2.1.6 for Forge 1.20.1. Its example API exposes a browser texture ID and mouse/keyboard input forwarding, which is the exact integration surface needed for the first proof of concept.

The initial proof of concept must verify that the bundled Ruffle page works correctly in the selected browser engine, especially WebAssembly, WebGL, game audio, clipboard/keyboard input, cookies and the AQW socket proxy.

## 05 · The handheld experience

### Physical presentation

- Craftable, one-stack Skua Console.
- A convincing first-person held device with visible hands/arms.
- Device screen displays the live AQW player, not an imitation dashboard.
- Buttons and shell detail are modeled as physical hardware.
- Opening the console brings the player into focus; closing it returns control to Minecraft.

### In-game player layout

- Main display: live AQW client.
- Compact Skua rail: start/stop script, player status, key tools.
- Full tool pages: script loader/repo, automation, inventory/bank, quests, drops, options, logs, packets and plugins.
- Mouse/keyboard input is forwarded to the browser while the player is focused.
- Minecraft input is restored on close, with no stuck keys or mouse buttons.

The actual game view is the primary content. Skua tools are supporting controls around it, not the reverse.

## 06 · Milestones

### A — Browser/player proof of concept
- [ ] Add Rinku (or MCEF fallback) as the in-game Chromium backend.
- [ ] Port or host the MVS Ruffle HTTP/WebSocket service.
- [ ] Load the bundled Skua SWF and display the AQW game inside a Minecraft screen.
- [ ] Forward mouse, keyboard, wheel, focus and resize events.
- [ ] Confirm real AQW login, world rendering, movement, chat and audio.

### B — Real Skua runtime
- [ ] Host Skua Core and `RuffleFlashUtil` in the bundled .NET 10 player process.
- [ ] Connect the Ruffle ExternalInterface bridge to Skua Core.
- [ ] Verify live `IScriptInterface` state while in the game.
- [ ] Run an existing script from the script repository.
- [ ] Add runtime lifecycle, crash recovery and clean shutdown.

### C — Minecraft-native Skua tools
- [ ] Script list, categories, search, load, start, pause and stop.
- [ ] Auto/Hunt, combat options, player/map controls.
- [ ] Inventory, bank, drops and quests.
- [ ] Logs, options, chat, packets and advanced tools.
- [ ] Multi-account and Army support where the runtime permits.

### D — Handheld polish
- [ ] First-person two-hand held console model/pose.
- [ ] Live game texture on the handheld screen.
- [ ] Device open/close animations and button feedback.
- [ ] Resizable display modes and crisp high-DPI rendering.
- [ ] Sound and subtle screen treatment.

### E — Pack validation
- [ ] Build against Project Infinity 0.1, Minecraft 1.20.1, Forge 47.4.28 and Java 17.
- [ ] Test on the actual pack instance.
- [ ] Test runtime download, offline startup, connection loss and player recovery.
- [ ] Verify no server-side dependency is required for the client-only player.
- [ ] Package the mod and bundled player host for installation.

## 07 · Explicit non-goals

- Do not make a separate desktop VibeSkua window the visible game player.
- Do not stop at HTTP remote-control panels.
- Do not emulate the AQW game world in Minecraft blocks/entities.
- Do not rewrite Skua's bot engine in Java.
- Do not create or author AQW scripts inside this project.
- Do not port the Android UI wholesale; reuse its proven player/runtime pieces.

---

**North star:** Minecraft is the console. The screen is the real game. Skua is the brain behind it.
