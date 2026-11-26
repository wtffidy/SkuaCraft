# 🔌 VibeSkua integration reference

This document records the existing VibeSkua remote-control surface that SkuaCraft consumes. SkuaCraft is a client for the real Skua runtime, not a second bot engine.

## Connection profile

| Setting | Default |
| --- | --- |
| Base URL | `http://127.0.0.1:8088` |
| Health check | `GET /api/ping` |
| Live snapshot | `GET /api/status` |
| Script catalogue | `GET /api/scripts` |
| Payload format | JSON |
| Request model | Asynchronous HTTP |

The VibeSkua host/manager gateway currently defaults to port 8088 and binds to all interfaces in its standard configuration. SkuaCraft therefore defaults to loopback and never silently scans the LAN.

**Network safety:** the inspected API surface does not establish an authentication contract. Do not port-forward the remote-control port or expose it to the public internet. Only configure another host when you deliberately intend to use a trusted private network.

## Verified endpoint map

| Area | Method | Route | Purpose |
| --- | --- | --- | --- |
| Connection | GET | `/api/ping` | Server identity and version |
| Status | GET | `/api/status` | Character, map, stats and script snapshot |
| Scripts | GET | `/api/scripts` | Runtime script catalogue |
| Scripts | POST | `/api/scripts/load` | Load a script |
| Scripts | POST | `/api/scripts/start` | Start or resume a script |
| Scripts | POST | `/api/scripts/pause` | Toggle pause state |
| Scripts | POST | `/api/scripts/stop` | Stop the current script |
| Movement | POST | `/api/player/jump` | Jump to a cell and pad |
| Movement | POST | `/api/player/goto` | Go to a player |
| Movement | POST | `/api/player/join` | Join a map |
| Movement | POST | `/api/player/rest` | Rest |
| Movement | POST | `/api/player/relog` | Relog |
| Combat | POST | `/api/combat/attack` | Attack current target or all |
| Combat | POST | `/api/combat/skill/1..5` | Use a skill |
| Combat | POST | `/api/combat/cancel-target` | Clear target |
| Drops | GET | `/api/drops` | Current drops |
| Drops | POST | `/api/drops/pickup` | Pick up a drop |
| Drops | POST | `/api/drops/pickup-all` | Pick up all drops |
| Quests | GET | `/api/quests` | Active quests and requirements |
| Quests | POST | `/api/quests/complete` | Complete a quest |
| Inventory | GET | `/api/inventory` | Bag contents |
| Inventory | POST | `/api/inventory/equip` | Equip an item |
| Inventory | POST | `/api/inventory/bank` | Deposit an item |
| Inventory | POST | `/api/inventory/unbank` | Withdraw an item |
| Bank | GET | `/api/bank` | Bank contents |
| Bank | POST | `/api/bank/open` | Open bank |
| Bank | POST | `/api/bank/load` | Load bank data |
| Options | GET | `/api/options` | Read options |
| Options | POST | `/api/options/toggle` | Change an option |
| Chat | GET | `/api/chat` | Recent chat messages |
| Chat | POST | `/api/chat/send` | Send chat |
| Logs | GET | `/api/logs` | Script logs |
| Tabs | GET | `/api/tabs` | Running client tabs |
| Tabs | POST | `/api/tabs/select` | Select a tab |
| Fleet | GET | `/api/army/queue` | Army Queue state |
| Fleet | POST | `/api/army/queue/start` | Start queue |
| Fleet | POST | `/api/army/queue/stop` | Stop queue |
| Fleet | POST | `/api/army/queue/pause` | Toggle queue pause |

VibeSkua also exposes manager/fleet operations for broadcasts, account launching and multi-tab control. Those are a later SkuaCraft milestone rather than features guessed into the first screen.

## Payload examples

Load or start a script:

```json
{
  "path": "Farm/GoldFarm.cs"
}
```

Jump to a cell:

```json
{
  "cell": "Enter",
  "pad": "Spawn"
}
```

Toggle a client option:

```json
{
  "option": "AutoRelogin",
  "value": true
}
```

## Status fields used by the console

The live snapshot uses snake_case keys. The first dashboard reads:

- Identity: `app`, `version`, `username`, `level`
- Character: `logged_in`, `playing`, `current_class`, `hp`, `max_hp`, `mp`, `max_mp`, `gold`
- Location: `map`, `server`, `room_id`, `cell`, `pad`
- Combat: `in_combat`, `has_target`, `target_name`, `target_hp`, `target_max_hp`
- Script: `script_running`, `script_paused`, `script_status`, `loaded_script`, `loaded_script_path`
- Progress: `kills`, `deaths`, `drops`, `quests_completed`, `uptime`
- Storage: `inventory_used`, `inventory_max`, `bank_used`, `bank_max`, `bank_loaded`

The script catalogue currently responds with an object containing a `scripts` array and a `count`. Entries include `name`, `fileName`, `path`, `category`, `description`, `tags`, `downloaded`, `size` and `last_modified`.

## Script repository boundary

SkuaCraft displays the script catalogue already exposed by VibeSkua. It does **not** author, generate, edit, compile or upload AQW scripts. The scripts repository is a source of category and metadata context; the desktop Skua workflow remains responsible for script acquisition and execution.

## Source inspection

- [VibeSkua RemoteControlService.cs](https://github.com/wtffidy/VibeSkua/blob/main/Skua.Core/Services/RemoteControlService.cs)
- [VibeSkua Android controller](https://github.com/wtffidy/VibeSkua/tree/main/Skua.App.Android)
- [Scripts-for-bullshit / Skua branch](https://github.com/wtffidy/Scripts-for-bullshit/tree/Skua)

Re-check the desktop service before relying on undocumented fields or adding new actions. Endpoint behavior may evolve with VibeSkua.
