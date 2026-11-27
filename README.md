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
  <img alt="Status" src="https://img.shields.io/badge/Status-Embedded%20Player-8B5CF6?style=for-the-badge" />
</p>

---

## 🎮 What SkuaCraft is

SkuaCraft is **the AQW game and Skua runtime inside Minecraft**, not a remote-control dashboard for another desktop window.

Equip the Skua Console, right-click it, and the console opens the real AQW player. The game is rendered by Ruffle inside a Chromium browser surface owned by Minecraft. The hidden Skua player host supplies the actual Skua Core runtime, game model and script engine.

The browser session stays alive when the handheld is hidden, so reopening the console can return to the same running game instead of forcing a fresh player launch.

## 🧱 Target environment

| Component | Target |
| --- | --- |
| Modpack | Project Infinity 0.1, release 0.0.52.1 |
| Minecraft | 1.20.1 |
| Loader | Forge 47.4.28 |
| Java | 17 |
| Browser surface | Rinku 3.0.4 for Forge 1.20.1 |
| Player host | MVS v1.0.6 Ruffle + Skua Core, .NET 10 |
| Initial platform | Windows x64 |

## 🧠 How the player is assembled

- **Rinku** hosts a Chromium browser view and exposes its rendered texture and input methods to the Minecraft screen.
- **Ruffle** renders the Skua SWF through WebAssembly/WebGL.
- **MVS Skua.Flash.Ruffle** serves the local player page, proxies AQW game traffic and bridges Flash ExternalInterface calls.
- **Skua Core** provides the real IScriptInterface, bot APIs, script manager and runtime.
- **SkuaCraft** owns the handheld, in-game display, input forwarding and Minecraft lifecycle.

The MVS v1.0.6 source is pinned as a Git submodule at vendor/MVS. The desktop VibeSkua ActiveX/WinForms Flash window is not launched or shown.

## 📦 Installation

Every successful push to `main` now updates a rolling [latest main prerelease](https://github.com/wtffidy/SkuaCraft/releases/tag/main-latest). It contains the guided Windows installer and individual downloads with stable filenames, so the download links do not change between builds. Versioned releases are published separately from `v*` tags on the [Releases page](https://github.com/wtffidy/SkuaCraft/releases). To create one manually, open **Actions → Release SkuaCraft → Run workflow**. You can enter an exact version, choose the next build number, promote alpha to beta, or publish the `1.0.0` stable release.

1. Download [SkuaCraft-Setup.exe](https://github.com/wtffidy/SkuaCraft/releases/download/main-latest/SkuaCraft-Setup.exe) from the latest main prerelease.
2. Run the installer and browse to your Minecraft Forge instance folder. Select the instance root, the folder containing `mods` and `config`, not the `mods` folder itself.
   - **CurseForge:** open the modpack profile menu and choose **Open Folder**.
   - **Official Minecraft Launcher:** open **Installations**, find your Forge profile, choose **Edit > More Options**, then open the **Game Directory**.
3. Click Install. The installer places SkuaCraft and its required Rinku 3.0.4 dependency in `mods/`, and PlayerHost in `config/skuacraft/player-host/`.
4. Launch Minecraft, equip the Skua Console and right-click it.

The installer does not overwrite SkuaCraft's existing configuration or saved login data. It is designed for Windows x64 and Forge 1.20.1. Advanced users can download the [standalone mod JAR](https://github.com/wtffidy/SkuaCraft/releases/download/main-latest/SkuaCraft.jar), [Rinku dependency](https://github.com/wtffidy/SkuaCraft/releases/download/main-latest/Rinku-3.0.4.jar) and [PlayerHost ZIP](https://github.com/wtffidy/SkuaCraft/releases/download/main-latest/SkuaCraft-PlayerHost-win-x64.zip) from the same rolling prerelease.

## 🛠️ Build from source

Clone with the MVS runtime submodule:

~~~bash
git clone --recurse-submodules https://github.com/wtffidy/SkuaCraft.git
cd SkuaCraft
~~~

Requirements: JDK 17, .NET 10 SDK, and Windows x64. Run `Build.bat`; it builds the Minecraft mod, self-contained Windows PlayerHost and guided Windows installer. It searches common JDK 17 install locations and existing Gradle 8.8 installations. If JDK 17 or Gradle 8.8 is missing, it downloads the verified distributions into the local SkuaCraft tools folder without changing your system Java. If Inno Setup 6 is missing, the installer build attempts to install it for the current user through WinGet.

~~~bat
Build.bat
~~~

The mod JAR is copied into the repository's `build/` folder, PlayerHost is published to `player-host/publish/win-x64/`, and the installer EXE is written to `release/SkuaCraft-Setup-<version>.exe`. The `installer/` folder contains the installer source and build script, not the compiled EXE. At the end, the build asks whether to copy the JAR elsewhere. Answer `Y` and paste the destination folder path (usually your instance's `mods` folder). It then asks whether to install PlayerHost. Answer `Y` and paste the Minecraft instance root path (the folder containing `mods` and/or `config`). PlayerHost is copied automatically into `config/skuacraft/player-host/`. No folder picker is used.

## 🔐 Local runtime safety

- The Ruffle host binds to loopback only.
- Its page URL includes a random session token.
- Minecraft only accepts 127.0.0.1 or localhost player URLs.
- No AQW credentials are stored in Minecraft config or passed as process arguments.
- Network and script work stays in the Skua host, away from Minecraft's render thread.

## 📚 Source projects

| Source | Role |
| --- | --- |
| [wtffidy/VibeSkua](https://github.com/wtffidy/VibeSkua) | Current client behaviour and API reference |
| [Arglax/MVS v1.0.6](https://github.com/Arglax/MVS/tree/v1.0.6) | Pinned Ruffle player host and portable Skua Core runtime |
| [Rinku](https://github.com/Keksuccino/Rinku) | Chromium browser surface for Minecraft |
| [auqw/Scripts (Skua)](https://github.com/auqw/Scripts/tree/Skua) | Official upstream script catalogue, metadata, source files and update feed |

SkuaCraft reads the live catalogue from the upstream `auqw/Scripts` `Skua` branch, downloads official script files directly from that branch, and checks its metadata for updates. SkuaCraft does not generate, author or modify AQW scripts.

---

<p align="center">
  <strong>SKUA SYSTEMS</strong><br/>
  <sub>Minecraft is the console. The screen is the real game. Skua is the brain.</sub> 🥔
</p>
