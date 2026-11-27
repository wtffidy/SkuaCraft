# 🥔 SkuaCraft

### The real Skua player, living inside Minecraft.

<p align="center">
  <strong>Forge 1.20.1 · Project Infinity 0.1 · Java 17</strong><br/>
  A handheld in-world console for playing AdventureQuest Worlds with the Skua client.
</p>

<p align="center">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=for-the-badge&logo=minecraft&logoColor=white" />
  <img alt="Forge" src="https://img.shields.io/badge/Forge-47.4.28-orange?style=for-the-badge" />
  <img alt="Java" src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img alt="Status" src="https://img.shields.io/badge/Status-Player%20Integration%20Prototype-8B5CF6?style=for-the-badge" />
</p>

---

## 🎮 The actual goal

SkuaCraft is **not** a remote-control dashboard for a separate VibeSkua window.

The goal is to run the actual AQW game client and Skua runtime as a playable experience inside Minecraft:

1. Equip the Skua Console in Minecraft.
2. Open it and see the real AQW game rendered on the handheld display.
3. Log in and play from the Minecraft screen using mouse and keyboard input.
4. Access Skua's script loader, automation, inventory helpers, quests, options, logs and other client tools without switching to a separate desktop Skua window.
5. Close the console and return to the Minecraft world.

The handheld is the front end. The game renderer, Skua runtime and script engine are the real player behind it.

## 🧱 Target environment

| Component | Target |
| --- | --- |
| Modpack | Project Infinity 0.1, release 0.0.52.1 |
| Minecraft | 1.20.1 |
| Loader | Forge 47.4.28 |
| Java | 17 |
| Initial platform | Windows x64 |
| Skua runtime | MVS/Ruffle-derived .NET 10 host with Skua Core |

## 🧠 Player architecture

The desktop VibeSkua window cannot simply be embedded: its current Avalonia game host uses a Windows Shockwave Flash ActiveX/WinForms control.

The portable route is based on the working player stack in [MVS v1.0.6](https://github.com/Arglax/MVS/tree/v1.0.6):

- **Ruffle** renders the AQW SWF in Chromium/WebAssembly.
- **Skua.Flash.Ruffle** hosts the local game page, proxies game traffic and bridges Flash ExternalInterface calls to Skua's `IFlashUtil`.
- **Skua Core** remains responsible for the game model, bot APIs, scripts and automation.
- **Rinku** is the preferred Minecraft-side Chromium library candidate for Forge 1.20.1. It can render a controllable browser view into Minecraft's OpenGL texture pipeline.
- **SkuaCraft** owns the handheld item, first-person presentation, input forwarding and Minecraft-native navigation.
- A local .NET 10 player host will run alongside the Minecraft client. It is a bundled implementation detail, not a separate desktop window the player has to operate.

The browser and runtime will communicate over loopback only. The player host must not expose its local control channel to the LAN.

See [PLAYER-ARCHITECTURE.md](docs/PLAYER-ARCHITECTURE.md) for the integration plan and boundaries.

## 📟 The handheld

The Skua Console is the in-world entry point: graphite hardware, a luminous display, tactile controls and a first-person held presentation. Its screen must show the live AQW player, not a mock dashboard.

Planned display modes include a full game view, a compact control rail, and expandable Skua tools. The two-hand held pose and close-up device framing are part of the product, not optional decoration.

## 📚 Source projects

| Source | Role |
| --- | --- |
| [wtffidy/VibeSkua](https://github.com/wtffidy/VibeSkua) | Skua client behavior, core and script/runtime reference |
| [Arglax/MVS v1.0.6](https://github.com/Arglax/MVS/tree/v1.0.6) | Proven Ruffle player host and portable Skua integration reference |
| [Rinku](https://github.com/Keksuccino/Rinku) | Minecraft Chromium rendering library candidate |
| [Scripts-for-bullshit / Skua](https://github.com/wtffidy/Scripts-for-bullshit/tree/Skua) | Script metadata and category reference only |

The scripts repository remains reference material. SkuaCraft does not generate scripts or replace the existing script workflow.

## 🛠️ Build

The Minecraft mod targets Java 17, Forge 47.4.28 and Gradle 8.8.

```bash
gradle build
```

On Windows, run `Build.bat`.

The player host is a separate .NET 10 component and will be published and packaged with the mod once the runtime integration is in place.

## 🚧 Development status

The current repository contains an initial Forge project and a previous remote-control UI experiment. That experiment is **not the product goal** and will be superseded by the embedded AQW player.

The next milestone is a proof of concept that opens the MVS-derived Ruffle game page inside Minecraft, renders it through Rinku, and forwards mouse/keyboard input correctly. Only after that works does it make sense to fill out the Skua tool panels and polish the handheld presentation.

---

<p align="center">
  <strong>SKUA SYSTEMS</strong><br/>
  <sub>One handheld. The actual game. The actual Skua.</sub> 🥔
</p>
