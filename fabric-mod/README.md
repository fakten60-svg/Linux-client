# woke.wtf Lite

A client-side quality-of-life mod for Minecraft **1.21.11** on Fabric.

It is a normal mod jar built against the public Fabric API. No injection, no
native libraries, no bytecode rewriting, no gameplay advantage: everything here
changes what *your* client shows or does for *you*, and nothing here asks a
server to agree to anything. Settings live on your machine, in a plain JSON file
you can read and edit.

This is the companion to the `libwoke.so` / `woketool` project at the repository
root, which stays a standalone library and UI harness. The two share a name and
nothing else; this directory is self-contained.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |

## Installation

1. Install Fabric Loader for 1.21.11.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods/` folder.
3. Put `wokewtf-lite-<version>.jar` in the same `mods/` folder.
4. *Optional:* put [Mod Menu](https://modrinth.com/mod/modmenu) in `mods/` too,
   for a **Configure** button on this mod's entry in the mod list. Without it the
   mod works exactly the same; see [Mod Menu](#mod-menu) below.
5. Start the game with the Fabric profile.

Only `hud.fps` is switched on when the mod runs for the first time. Every other
module changes how the game behaves or looks, so each one is opt-in — turn one on
in the settings screen and it takes effect straight away.

## Features

Thirteen modules in three groups.

### HUD

![](docs/hud.png)

| Module | Default | What it does |
| --- | --- | --- |
| `hud.fps` — FPS Counter | **on** | Shows the current client frame rate. Read-only; it reports what the game already measures. |

### Interface

![](docs/interface.png)

| Module | Default | What it does |
| --- | --- | --- |
| `ui.crosshair` — Crosshair | off | Draws your own crosshair: shape (cross, dot, hidden), colour, thickness, arm length and outline. |
| `ui.chat` — Chat | off | Prefixes chat lines with the local time and keeps a longer scrollback than vanilla's 100 lines. |
| `ui.book` — Book Page Counter | off | Shows how full the page of an open book is, as characters used or characters left. |
| `ui.screenshot` — Screenshot Helper | off | Puts a reference to a new screenshot on your clipboard: its path, or a `file://` link you can paste elsewhere. The image is never read, copied or uploaded. |
| `ui.waypoints` — Waypoints | off | Lists your own saved positions of the world you are in, nearest first. |
| `ui.inventory_sort` — Inventory Sort | off | Reorders your own inventory, as a permutation of the stacks you already carry. **Singleplayer or LAN host only.** |

### Convenience

![](docs/convenience.png)

| Module | Default | What it does |
| --- | --- | --- |
| `util.auto_reconnect` — Auto Reconnect | off | After the connection drops, prints a countdown in chat and reconnects. Cancellable with a keybind. Multiplayer only, one attempt per disconnect, no retry loop. |
| `util.respawn_confirm` — Respawn Confirmation | off | Holds the vanilla respawn button inactive for a few seconds so a stray click cannot send you back in. It never respawns you itself. |
| `util.chat_macros` — Chat Macros | off | Up to 20 keybinds that *type* a saved line into the chat box. Nothing is sent until you press Enter. |
| `util.screenshot_key` — Screenshot Key | off | A key of your own for the game's own screenshot. |
| `util.fullscreen_key` — Fullscreen Key | off | Toggles fullscreen from a key of your own. |
| `util.stats` — Local Stats | off | Counts blocks mined, distance walked and playtime. Written to your config and nowhere else. |

## Keybinds

All bindings register through the game's own keybind system, so they appear in
**Options → Controls** under "woke.wtf Lite" and are saved by the game like any
other binding.

| Binding | Default |
| --- | --- |
| Open Settings | Right Shift |
| Cancel Reconnect | unbound |
| Chat Macro 1 – Chat Macro 20 | unbound |
| Take Screenshot | unbound |
| Toggle Fullscreen | unbound |

Only the settings key ships bound, so there is always a way into the config; it
is bound to Right Shift because nothing in vanilla uses it by default. Every
other action ships unbound, so the mod cannot take a key away from something else
until you decide it should. If two bindings end up on the same key, the mod says
so in chat on the next world join: the game fires both and would otherwise just
look broken.

## Configuration

In game, press **Right Shift** (rebindable in Controls) or run `/wokewtf config`.
The settings screen lists every module by category, searches them by name and
description, and edits every value where it is shown: a switch for an on/off
setting, a slider for a number, a list for a choice, a cycled swatch for a
colour, and a **Reset** button on every row that puts one setting back to its
default. Hovering a setting explains it. Changes take effect immediately and are
written out within a few seconds.

Underneath, the settings are a plain JSON file, `config/wokewtf-lite.json` in
your game directory: written atomically (a `.bak` of the previous version is
kept), and unknown keys from a newer build are preserved rather than dropped.
The schema version is `1`; the modules added since are purely additive. Editing
it by hand still works, and is still the quickest way to set an exact colour.

Some things are easier from the chat box. These are **client** commands: they
are parsed and answered by your own client and are never sent to a server.

| Command | What it does |
| --- | --- |
| `/wokewtf config` | Opens the settings screen. |
| `/wokewtf waypoint add <name>` | Saves your current position in this world. |
| `/wokewtf waypoint remove <name>` | Drops a saved waypoint of this world. |
| `/wokewtf waypoint list` | Lists this world's waypoints, nearest first. |
| `/wokewtf sort` | Sorts your inventory (module must be enabled, own world only). |
| `/wokewtf macro set <name> <text>` | Saves a chat macro; up to 20, name ≤ 32 chars, text ≤ 256 chars. |
| `/wokewtf macro remove <name>` | Drops a macro. |
| `/wokewtf macro list` | Lists your macros, in slot order. |
| `/wokewtf stats` | Shows your local counters. |
| `/wokewtf stats reset` | Sets your local counters back to zero. |

Client commands only exist once you have joined a world, which is when Fabric
builds the client command dispatcher.

## Scope

What the mod deliberately does **not** do is the point of the project, not a
disclaimer bolted on: the full list lives in [docs/SCOPE.md](docs/SCOPE.md).

## Mod Menu

[Mod Menu](https://modrinth.com/mod/modmenu) is supported as an optional extra.
Install it (`17.0.1` is the Minecraft 1.21.11 build) and this mod's entry in the
mod list gains a **Configure** button that opens the settings screen above.
Nothing else about the mod changes.

The dependency is soft in both directions. It is listed under `suggests` in
`fabric.mod.json` rather than `depends`, and the integration class is compiled
against Mod Menu but never bundled — so without Mod Menu the mod loads exactly as
before and only that button is missing. Nothing here can fail to load because of
it.

## Development

```bash
cd fabric-mod
./gradlew build       # compile, run every test, and build the jar
./gradlew test        # the unit suite only
./gradlew runClient   # launch a development client with the mod loaded
```

The jar lands in `build/libs/`. The unit suite is dependency-free and headless:
it pins the logic that needs no game (config round-trips, the reconnect state
machine, counters, macro rules, search folding) and the mixin contract, so a
Minecraft or mappings bump fails the build instead of silently disabling a
feature. The rules the code is held to are mechanical: no source file reaches
200 lines, the mod's own sources compile with zero warnings, and every class
carrying pure logic has tests.

`MANUAL_TEST_CHECKLIST.md` in this directory lists the paths that need a running
game and a pair of hands — the ones the automated suite cannot reach.

## Debugging

A failed manual test is diagnosed from the log: `run/logs/latest.log` for the
current session and `run/logs/debug.log` for everything else (a normal install
keeps the same two files under `.minecraft/logs/`).

- **This mod's lines** all carry the tag `(wtf Lite)`, e.g.
  `[Render thread/INFO] (wtf Lite) ...`. Anything else is the game or Fabric.
- **Loaded, and how many modules?** Startup prints one line:
  `woke.wtf Lite v0.1.0 ready: 13 modules (N enabled), 24 keybinds, config ...`,
  then `Registered modules: hud.fps, ui.crosshair, ...`.
- **Is a module active?** There is no per-module line yet; `(N enabled)` and the
  config file are the current answer. A line per module would be a small add-on.
- **Did a mixin apply?** Mixin logs at DEBUG only: search `debug.log` for
  `wokewtf-lite.client.mixins.json` to find `Mixing <Name> from
  wokewtf-lite.client.mixins.json into ...`. A mixin appears when its target
  first loads, so `BookEditScreenMixin` and `ScreenshotShareMixin` may be absent
  until you open a book or take a screenshot.
- **Per-module verbose logging?** None yet. Failures stay visible: the
  dispatcher logs `Module '<id>' threw during <phase>; disabling it` at ERROR.
  A per-module toggle is worth building only if the manual session keeps turning
  up module behaviour the log cannot explain.

Report a failed test with the template in `ISSUE_TEMPLATE.md`, using the ids in
`MANUAL_TEST_CHECKLIST.md`.

## License

MIT. See [LICENSE](LICENSE).
