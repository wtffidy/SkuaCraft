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
  <img alt="Status" src="https://img.shields.io/badge/Status-Embedded%20Player%20Prototype-8B5CF6?style=for-the-badge" />
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

The Minecraft mod and Windows player host are built as separate CI artifacts.

1. Install the SkuaCraft mod JAR and its required Rinku 3.0.4 library in the Project Infinity instance.
2. Download the **SkuaCraft PlayerHost win-x64** artifact from the same GitHub Actions run.
3. Extract the player-host files into:
   <escape><code>&lt;Minecraft instance&gt;/config/skuacraft/player-host/</code></escape>
4. Launch Minecraft, equip the Skua Console and right-click it.

On first launch, Rinku may download its matching Chromium/JCEF runtime. The player host then starts locally, writes a short-lived session file and serves the Ruffle page through a tokenized loopback URL.

The default player-host path can be changed in config/skuacraft-client.toml.

## 🛠️ Build from source

Clone with the MVS runtime submodule:

~~~bash
git clone --recurse-submodules https://github.com/wtffidy/SkuaCraft.git
cd SkuaCraft
~~~

Minecraft mod requirements: Java 17 and Gradle 8.8.

~~~bash
gradle build
~~~

Player-host requirements: .NET 10 SDK and Windows x64.

~~~powershell
git submodule update --init --recursive
./player-host/Publish-PlayerHost.ps1
~~~

The script publishes a self-contained player host to player-host/publish/win-x64/.

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
| [Scripts-for-bullshit / Skua](https://github.com/wtffidy/Scripts-for-bullshit/tree/Skua) | Script metadata and category reference only |

The scripts repository is reference material. SkuaCraft does not generate, author or modify AQW scripts.

## 🚧 Development status

The repository now contains the first embedded-player path: the handheld screen, Rinku browser integration, hidden .NET player-host project and pinned MVS runtime source.

The next verification gate is a real end-to-end run in the Project Infinity instance: Ruffle loads the Skua SWF, AQW login succeeds, input and audio work, and the game state reaches Skua Core. Minecraft-native Skua tool screens and the two-hand handheld animation follow that proof of concept.

**The player host compiling is not the same as a verified live AQW session.** The full in-game login/play test still needs to pass before this can be called a working release.

---

<p align="center">
  <strong>SKUA SYSTEMS</strong><br/>
  <sub>Minecraft is the console. The screen is the real game. Skua is the brain.</sub> 🥔
</p>
