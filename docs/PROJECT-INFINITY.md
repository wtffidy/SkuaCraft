# 🧭 Project Infinity 0.1 compatibility target

SkuaCraft is being developed against the pack release selected as its first real deployment target.

| Setting | Pinned target |
| --- | --- |
| Pack | Project Infinity 0.1 |
| Pack release | 0.0.52.1 |
| Minecraft | 1.20.1 |
| Mod loader | Forge |
| Forge | 47.4.28 |
| Java | 17 |

## Compatibility policy

- Keep the first supported build on Minecraft 1.20.1 and the pack's Forge 47.4.28 line.
- Avoid optional dependencies on other pack mods. SkuaCraft should not care whether AE2, Mekanism, Botania or another technology/magic mod is installed.
- Treat the console as a client-driven control surface. The VibeSkua desktop process is the source of truth for AQW state and script execution.
- The console is registered as a normal Forge item. A modded server that enforces synchronized registries must also have SkuaCraft installed.
- Keep client-only classes out of common initialization paths.

## Verified references

- [Project Infinity 0.1 release 0.0.52.1](https://www.curseforge.com/minecraft/modpacks/project-infinity-0-1/files/9290836)
- [Project Infinity build metadata](https://modpacks.ch/modpacks/1266680/project-infinity-0-1)

This is a pack-specific target, not a promise of compatibility with every 1.20.1 modpack.
