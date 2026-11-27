# 🥔 SkuaCraft field notes

> **Product definition:** the actual AQW client and Skua runtime, played inside Minecraft through a handheld console.

## 01 · Non-negotiable definition

A remote-control dashboard for a separate desktop Skua process is not the product. The handheld must display the real game, run the Skua runtime and eventually expose Skua's normal tools within Minecraft.

## 02 · Implemented integration foundation

- [x] Forge 1.20.1 project and craftable Skua Console
- [x] Rinku 3.0.4 dependency for Forge 1.20.1
- [x] Minecraft browser screen that renders the Rinku texture and forwards input
- [x] Persistent browser session when the console is hidden and reopened
- [x] Player-host launcher with a loopback session file and stop-file lifecycle
- [x] MVS v1.0.6 pinned Git submodule
- [x] .NET 10 host project referencing Skua Core and Skua.Flash.Ruffle
- [x] Headless Skua settings, dialog and dispatcher adapters
- [x] Skua client-file and plugin service registrations

These are source-level integration steps. They are not a claim that the actual AQW login/gameplay path has passed a live Minecraft test yet.

## 03 · Player architecture

| Layer | Responsibility |
| --- | --- |
| Minecraft Forge | Item, handheld screen, navigation and user input |
| Rinku | Embedded Chromium surface rendered to Minecraft |
| Ruffle | Flash SWF renderer running in WebAssembly |
| MVS Skua.Flash.Ruffle | Player host, AQW proxy, ExternalInterface bridge |
| Skua Core | Live game state, bot APIs and C# script runtime |
| Script repository | Source and category reference only |

The .NET host runs hidden beside the game. There is no separate desktop Skua game window in this design.

## 04 · Remaining proof-of-concept checks

- [ ] Build the Java mod with the Rinku library.
- [ ] Build the .NET 10 sidecar against the pinned MVS submodule.
- [ ] Install both artifacts in Project Infinity 0.1.
- [ ] Confirm the Rinku browser initializes on the target PC.
- [ ] Confirm Ruffle loads the Skua SWF and reaches the AQW login screen.
- [ ] Verify login, map loading, mouse, keyboard, audio and WebSocket proxy.
- [ ] Confirm live game state reaches Skua Core.
- [ ] Run an existing script from the in-game Skua tools.
- [ ] Hide and reopen the handheld without killing the game session.

## 05 · Full Skua feature surface

Once the game loop is proved, the Minecraft-native Skua shell should expose:

- Script discovery, categories, search, load, run, pause and stop.
- Auto/Hunt and combat controls.
- Map travel, player and server controls.
- Inventory, bank, drops and quest turn-ins.
- Chat, logs, client options, packets and diagnostics.
- Existing plugin and multi-account features where supported by the headless host.

SkuaCraft will not generate or author AQW scripts. The existing script workflow and repository remain the source.

## 06 · Handheld theatre

- [ ] First-person two-hand console pose with visible arms.
- [ ] Screen bezel and proper perspective in the held view.
- [ ] Device opening/closing transitions.
- [ ] Button press feedback and subtle screen glow.
- [ ] Optional compact player HUD and expanded tool view.

## 07 · Release hardening

- [ ] Windows x64 self-contained host package.
- [ ] Clean install from mod + player-host artifacts.
- [ ] Startup recovery and process cleanup.
- [ ] Verify Rinku first-run download/cache behaviour.
- [ ] Test on the exact Project Infinity instance and Forge build.

---

**North star:** Minecraft is the console. The screen is the real AQW client. Skua is the brain behind it.
