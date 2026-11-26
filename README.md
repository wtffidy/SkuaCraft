# 🥔 SkuaCraft

### Your Skua control deck. Now held in your Minecraft hands.

<p align="center">
  <strong>A Forge 1.20.1 companion mod for VibeSkua</strong><br/>
  A pocket-sized command center, not another script editor.
</p>

<p align="center">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=for-the-badge&logo=minecraft&logoColor=white" />
  <img alt="Forge" src="https://img.shields.io/badge/Forge-47.4.28-orange?style=for-the-badge" />
  <img alt="Java" src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img alt="Status" src="https://img.shields.io/badge/Status-Early%20Development-8B5CF6?style=for-the-badge" />
</p>

---

## ✨ The idea

SkuaCraft brings the VibeSkua remote-control experience into Minecraft.

Craft a **Skua Console**, hold it in your main hand, and right-click. The console opens a custom in-game control deck with live player status and direct controls for the Skua client running on your computer.

Minecraft is the cockpit. VibeSkua remains the engine.

This project does **not** attempt to execute AQW scripts inside Minecraft or reimplement the Skua bot runtime. It talks to the real VibeSkua client through its existing HTTP API, so script execution and game state stay where they belong.

## 🎮 The console

| Deck | What it is for |
| --- | --- |
| **Dashboard** | Live connection, player stats, location and script state |
| **Scripts** | Browse, select, load and start scripts already exposed by VibeSkua |
| **Controls** | Attack, skills, rest and jump-to-spawn |
| **Inventory** | Bag/bank browsing, equip, deposit and withdraw |
| **Quests** | Requirements, readiness checks and quest completion |
| **Drops** | Select/pick up a drop or collect all |
| **Chat** | Recent chat feed and zone-message sending |
| **Logs** | Latest script telemetry |
| **Options** | Live client option toggles |
| **Fleet** | Running tabs, active-tab selection and Army Queue basics |

The first-person item model is designed as a small rugged handheld: dark casing, luminous teal display, tactile controls and a little violet Skua personality. The in-game screen follows the same visual language.

## 📦 Target pack

The primary compatibility target is **Project Infinity 0.1**, release **0.0.52.1**:

| Component | Target |
| --- | --- |
| Minecraft | 1.20.1 |
| Mod loader | Forge |
| Forge build | 47.4.28 |
| Java | 17 |
| Distribution | Client-side control interface; install on both sides when required by the server's Forge mod list |

The mod is intentionally built for the pack's exact 1.20.1 Forge generation rather than chasing unrelated Minecraft versions.

## 🔌 Connection model

SkuaCraft connects to the VibeSkua remote-control service.

- Default host: `127.0.0.1`
- Default port: `8088`
- Protocol: HTTP + JSON
- Default connection target: the same computer running Minecraft and VibeSkua
- Network calls: asynchronous, with short request timeouts

VibeSkua currently binds its gateway to all interfaces in its standard configuration. Keep the default SkuaCraft host on loopback unless you deliberately configure a trusted LAN connection. Do not expose the remote-control port to the public internet.

Connection settings live in `config/skuacraft-client.toml` after the first launch.

## 🧭 Project map

- `src/main/java` — Forge mod, console item, screen and API client
- `src/main/resources` — item model, texture, recipe and mod metadata
- `docs/INTEGRATION.md` — verified VibeSkua API map
- `docs/PROJECT-INFINITY.md` — pack compatibility notes
- `docs/NOTES.md` — design notes and implementation decisions

## 🛠️ Build from source

Requirements: Java 17 and Gradle 8.8.

```bash
gradle build
```

The built mod is written to `build/libs/`.

On Windows, run `Build.bat`.

The GitHub Actions workflow builds the project and publishes the JAR as a workflow artifact.

## 🧪 Development status

This repository is the start of the implementation, not a claim that every Skua feature has shipped. The first milestone is the Forge foundation, crafted handheld, polished screen shell and reliable connection to the existing VibeSkua API. Further panels will be filled in against verified endpoint behavior.

See [the notes](docs/NOTES.md) and [integration reference](docs/INTEGRATION.md) for scope, caveats and next steps.

## 🔗 Source projects

| Project | Role |
| --- | --- |
| [wtffidy/VibeSkua](https://github.com/wtffidy/VibeSkua) | Primary client and remote-control API |
| [wtffidy/Scripts-for-bullshit](https://github.com/wtffidy/Scripts-for-bullshit/tree/Skua) | Script categories and metadata reference only; SkuaCraft does not author scripts |
| [Arglax/MVS v1.0.6](https://github.com/Arglax/MVS/releases/tag/v1.0.6) | Contingency research/reference if the desktop client path ever proves insufficient |

The source projects are references and integration targets. SkuaCraft is developed independently.

---

<p align="center">
  <strong>SKUA SYSTEMS</strong><br/>
  <sub>One tiny console. A whole lot of bot control.</sub> 🥔
</p>
