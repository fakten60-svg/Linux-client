# Changelog — woke.wtf Lite

All notable changes to the Fabric mod in this directory. The format is kept
plain on purpose; the interesting part of an entry is what a player notices and
what was verified, not the versioning scheme.

## 0.1.0 — unreleased

The first release: thirteen QoL modules, all opt-in, for Minecraft 1.21.11 on
Fabric. Nothing here changes what another player sees, and nothing here gives a
gameplay advantage — see [docs/SCOPE.md](docs/SCOPE.md).

### Added

- **HUD** — `hud.fps`, the frame rate counter. The only module enabled for a
  fresh install; it reports what the game already measures.
- **Interface** — `ui.crosshair`, `ui.chat` (timestamps and a longer
  scrollback), `ui.book`, `ui.screenshot`, `ui.waypoints`, `ui.inventory_sort`
  (singleplayer or LAN host only).
- **Convenience** — `util.auto_reconnect`, `util.respawn_confirm`,
  `util.chat_macros`, `util.screenshot_key`, `util.fullscreen_key`,
  `util.stats`.
- **Settings screen**, opened with Right Shift, `/wokewtf config`, or the
  optional Mod Menu entry. Every setting is editable where it is shown, with a
  reset button per row.
- **Config file** `config/wokewtf-lite.json`: atomic writes with a `.bak`,
  unknown keys from a newer build preserved rather than dropped, and a file
  whose schema is newer than this build understands is left alone rather than
  downgraded.
- **Client commands** `/wokewtf config`, `waypoint`, `sort`, `macro`, `stats` —
  parsed and answered by your own client, never sent to a server.
- **First-join chat guide**, printed once per session, with English and German
  text (`en_us.json`, `de_de.json`).
- **Keybind conflict report**: if one of this mod's bindings shares a key with
  anything else, the mod says so on world join. Vanilla pairs that collide on
  purpose are never reported.
- **Client gametest** (`./gradlew runClientGameTest`): five pixel-level checks
  covering HUD placement and colour, the chat timestamp and the settings screen.
  Dev-only; it never enters the shipped jar.
- **Debug Module** setting and `scripts/parse_session_log.sh`, for reading a
  session back afterwards — see [docs/DEBUGGING.md](docs/DEBUGGING.md).

### Notes

- Modules were added one group at a time, and every one is opt-in: a fresh
  install switches nothing on except the read-only FPS counter.
- The schema version is `1`, and the modules added since are purely additive.
