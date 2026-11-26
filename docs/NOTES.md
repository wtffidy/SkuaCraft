# 🥔 SkuaCraft field notes

> **Project mood:** premium pocket hardware meets a slightly over-engineered AQW command center.

This is the creative brief and the technical guardrail sheet. The console should feel like an object that belongs in the Minecraft world, not a webpage pasted over the pause menu.

## 01 · The object

**Working product name:** Skua Console  
**Silhouette:** compact rugged handheld, substantial bezel, visible physical controls  
**Palette:** graphite casing, slate trim, signal teal, ultraviolet accents and warm amber status lights  
**Screen language:** sharp typography, quiet dividers, tiny system labels and restrained glow  
**Material language:** scuffed metal, dark glass, hard edges and retro-futurist equipment-room energy

### The interaction fantasy

1. Craft the console.
2. Put it in the main hand.
3. See the device held naturally in first person.
4. Right-click to bring up the close-up control deck.
5. Close the deck and return to the world without pausing the simulation.

The first item model has first-person and third-person transforms, and the custom screen is framed as a field console. The more cinematic two-hand pose, visible arms, open/close transition, device tilt and sound design are a dedicated visual milestone. The initial model should not be mistaken for that finished presentation.

## 02 · The software inside

The handheld is a remote, not a pocket-sized clone of the entire desktop application.

| Layer | Responsibility |
| --- | --- |
| Minecraft item | Craftable physical entry point |
| Console screen | Navigation, status and player controls |
| API client | Background HTTP calls, timeouts and JSON parsing |
| VibeSkua | Actual AQW game state, script runtime and automation |
| Script repository | Source and metadata reference, not a script-generation target |

The desktop client remains authoritative. If the API is offline, Minecraft must remain responsive and show a clear disconnected state.

## 03 · Visual rules

- Keep a consistent bezel, content inset and baseline grid.
- Use teal for healthy/active states, amber for attention and red for destructive actions.
- Use violet as a secondary accent, not as a competing primary color.
- Avoid giant debug labels, raw JSON dumps and default vanilla-looking button rows.
- Truncate long usernames, script names and map names safely.
- Keep all panels readable at normal game resolutions and degrade gracefully on smaller windows.
- Prefer meaningful iconography and status indicators over ornamental noise.
- Keep the on-screen notice area useful: show the actual result returned by VibeSkua.

## 04 · Safety rules

- Default to `127.0.0.1:8088`.
- Never silently scan the LAN or connect to a discovered host.
- Never store AQW account credentials in the Minecraft config.
- Never port-forward the unauthenticated remote API.
- Send network work off the render thread.
- Put confirmations in front of future destructive or bulk actions.
- Never let a disconnected or malformed API response crash the game screen.
- Keep the Minecraft client responsive even if VibeSkua is not running.

## 05 · Feature milestones

### Milestone A · Handheld foundation
- [x] Forge 1.20.1 project structure
- [x] Pack-aligned Forge 47.4.28 target
- [x] Client connection config
- [x] Craftable Skua Console
- [x] Custom item model and first-person transforms
- [x] Non-blocking API client
- [x] Ten-panel navigation and live dashboard
- [x] Script browsing, selection, load and start
- [x] Combat actions, rest and jump-to-spawn
- [x] Bag/bank browsing, equip, deposit and withdraw
- [x] Quest requirement inspection and guarded completion
- [x] Drop list, pickup and pickup-all
- [x] Chat feed and zone-message sending
- [x] Script log viewer
- [x] Live client option toggles
- [x] Client tab list, active-tab selection and Army Queue basics

### Milestone B · Full control deck
- [ ] Script search, category filter and paging polish
- [ ] Account/tab selector and per-tab routing
- [ ] Script search, category filters and richer paging
- [ ] Per-tab routing for every command and manager-wide broadcast actions
- [ ] Full inventory item metadata and advanced bulk actions
- [ ] Army Queue step editing, presets and scheduler management

### Milestone C · Handheld theatre
- [ ] Dedicated two-hand/arm pose while the console is open
- [ ] Open/close animation and sound
- [ ] Device tilt and subtle screen scan animation
- [ ] Button press feedback and connection pulse
- [ ] Optional compact HUD mode
- [ ] Final in-game model and texture pass

### Milestone D · Release hardening
- [ ] Build and launch in the exact Project Infinity instance
- [ ] Test singleplayer and dedicated-server registry behavior
- [ ] Test offline Skua, invalid host, slow responses and server errors
- [ ] Test every action against the current VibeSkua build
- [ ] Produce release JAR, install instructions and compatibility notes

## 06 · Mobile fallback

The Android project is a contingency reference, not the default implementation route.

The existing VibeSkua desktop service already exposes the core actions needed for a native Minecraft control deck. Porting the mobile client only makes sense if desktop API coverage proves incomplete or unusable. Until then, the direct API route is smaller, more maintainable and avoids dragging a mobile UI framework into Forge.

Reference: [MVS v1.0.6](https://github.com/Arglax/MVS/releases/tag/v1.0.6).

---

**North star:** the player should feel like they pulled out a piece of in-world kit, not opened a spreadsheet with a Minecraft background.
