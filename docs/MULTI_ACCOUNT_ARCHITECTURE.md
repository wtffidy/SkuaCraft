# Multi-account rack architecture

## Goal

Make the SkuaCraft rack behave like VibeSkua's account tabs, using the multi-client ideas already present in MoVibeSkua (MVS). A rack owns several account sessions; one is selected for display and input while the others continue running in the background. A handheld controller represents an account profile and can be inserted into or removed from a rack.

The existing `skuacraft:skua_console` item remains supported. It becomes the portable controller rather than being replaced by the rack.

## What MVS already provides

The pinned MVS source (the `vendor/MVS` submodule) contains an Army implementation:

- `Army.MaxClients` is 12.
- Each Android Army client runs in a distinct Android process (`:army2` through `:army12`).
- Each non-primary Android client calls `WebView.SetDataDirectorySuffix` to isolate WebView storage.
- MVS publishes per-client status and has memory-management behaviour for background clients.
- The MVS README describes background clients continuing to play/bot while only the foreground client draws the game.

This is the behaviour to emulate, not code we can directly transplant. Android Activities, Services, Android process names, and Android WebView APIs do not exist in the Windows Minecraft host.

## Current SkuaCraft runtime

SkuaCraft currently launches one Windows `.NET 10` PlayerHost process and creates one Rinku browser through `SkuaPlayerBrowserSession`.

Each PlayerHost process creates its own dependency-injection container, Skua script interface, script manager, Ruffle host, and control server. The Ruffle port and session files are already stored beside the selected session file, which gives each future slot a natural place for per-session runtime metadata.

There are two important single-instance assumptions to address:

1. `PlayerHostLauncher` currently stores one process and one stop-file path.
2. MVS `ClientFileSources` resolves its settings, scripts, options, plugins, and themes below the process user's `%APPDATA%/VibeSkua`. Separate processes launched with the same Windows profile would therefore share mutable Skua files.

The multi-account implementation must give each account a separate Skua profile directory (for example, by setting a per-session `APPDATA` before the PlayerHost process starts). Shared script distribution can be designed separately; account settings and credentials must not be shared accidentally.

## Planned runtime model

- **Rack manager:** owns the installed account slots, selected slot, and lifecycle actions.
- **Account profile:** stable identity/configuration for one account. Minecraft item NBT may carry a non-secret profile identifier, never passwords, cookies, or session tokens.
- **PlayerHost process:** one isolated process per running account, with its own profile directory, session file, stop file, Ruffle port file, logs, and control token.
- **Browser session:** one Rinku browser per loaded game session. The selected browser receives input and supplies the visible texture. Background browsers remain alive but are not focused.
- **Rack display:** shows the selected account's live Rinku texture. If no account is active, show an idle/boot display.
- **Handheld controller:** can start its profile standalone, insert that profile into a rack, or be retrieved from a rack. Ejection must explicitly choose whether to stop the session or leave it running in the rack.

## Resource and safety rules

- MVS's 12-client limit is an Android implementation ceiling, not an automatic safe limit for Windows. SkuaCraft should expose a configurable account limit and report process/memory use before allowing additional sessions.
- Do not assume hidden browser instances are free. Rendering and Ruffle execution costs must be measured; background/headless behaviour needs verification.
- Never put account passwords, browser cookies, control tokens, or other secrets in item NBT, block-entity update packets, world saves, or multiplayer-synced data.
- The rack is client-local infrastructure. A Minecraft server should not receive account credentials or be responsible for starting desktop processes.
- Process startup, shutdown, and account switching must stay off Minecraft's render thread.
- Existing single-account launch and stop behaviour must remain functional throughout the migration.

## Implementation milestones

1. Add a multi-session process manager with per-account directories and lifecycle/status tracking. Keep the existing launcher API as a compatibility facade for the default controller.
2. Add multiple Rinku browser sessions and selected-session texture/input routing. Verify Rinku supports simultaneous independent browser instances and storage isolation before relying on it.
3. Add the rack block, block entity, inventory slots, and controller insertion/ejection interactions.
4. Add the rack management screen with tab-like account switching and per-account status/actions.
5. Add the live rack display, idle screen, configurable transparency, and nearby-render activation.
6. Add automated smoke tests for starting, switching, stopping, and recovering multiple PlayerHost processes.

## First verification gate

Before enabling multiple live sessions by default, verify that two PlayerHost processes can run concurrently with separate `APPDATA` roots, distinct Ruffle ports, independent game login storage, and independent control endpoints. Then verify that two Rinku browser instances can coexist without sharing browser state or stealing focus. Until those checks pass, the rack UI should expose the design without claiming multi-account execution is working.
