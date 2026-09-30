# Manual test checklist

Every path on this list needs a running game, a display and a pair of hands.
The automated suite covers the logic that needs none of those; this file is what
is left, and its job is to be the honest counterpart to a green build.

How to use it: work top to bottom, tick what passes, and file what does not.
Each item says *what to do* and *what a pass looks like*. Anything you cannot
produce is a failure, not a "probably fine".

## Before you start

- [ ] Fresh profile: Fabric Loader 0.19.5+, Fabric API `0.141.6+1.21.11`, Java 21.
- [ ] Back up `config/wokewtf-lite.json` if it exists, then delete it, so the run starts from defaults.
- [ ] Start the game and confirm the log line `woke.wtf Lite v… ready: 13 modules (1 enabled), 24 keybinds`.
- [ ] Confirm the log lists all 13 ids, from `hud.fps` to `util.stats`.
- [ ] Confirm **Options → Controls** shows a "woke.wtf Lite" category with 24 bindings: **Open Settings** on Right Shift, the other 23 unbound.

## Settings screen

- [ ] Press **Right Shift** in game → the settings screen opens.
- [ ] Press **Escape** → it closes and you are back where you were.
- [ ] Type `/wokewtf config` → the same screen opens. (Client commands only exist after joining a world.)
- [ ] Click outside the screen's panels, on the dark background → nothing closes and nothing changes.
- [ ] Confirm the sidebar lists **HUD**, **Interface**, **Convenience** and **Global**, each with a module count.
- [ ] Click **Interface** → its six modules appear on the right with their settings underneath.
- [ ] Click **Interface** again → the right pane empties and the badge stays.
- [ ] Type `cross` into the search box → only the Crosshair module remains, its badge reads `1/6`.
- [ ] Type `größe`, `grosse` and `ß` in turn → the filter treats umlauts and `ß` as their plain letters.
- [ ] Clear the search → every category comes back.
- [ ] Search for something that matches nothing → the pane says so and no empty category header is left behind.
- [ ] Hover a setting label that has a description (e.g. **Position** under the FPS counter) → a tooltip appears. Hovering a label without one shows nothing.
- [ ] Hover the **Reset** button of a changed setting → its own tooltip appears.
- [ ] Flip a module's switch → the accent bar appears, the change takes effect at once, and it survives a restart.
- [ ] Click a boolean setting → it flips between `On` and `Off`.
- [ ] Drag a slider (e.g. **Thickness**) → the value follows the pointer; a click at the far right of the track lands on the maximum.
- [ ] Click an enum setting (e.g. **Shape**) → a list opens under it; pick an entry → the list closes and the value is the one you picked.
- [ ] Press **Escape** with that list still open → the list closes and the screen stays.
- [ ] Click a colour setting → the swatch steps through the presets and the hex value beside it changes.
- [ ] Press **Reset** on a setting you changed → it returns to the default, and the config file follows.
- [ ] Edit the config file by hand while the screen is open, then restart → the file wins, as before.
- [ ] Check the log for exceptions from `wtf.woke.lite` after all of the above: there should be none.

### Mod Menu (only if installed)

- [ ] With Mod Menu `17.0.1` installed → the mod list shows **woke.wtf Lite** with a **Configure** button, and the button opens the same screen.
- [ ] Close that screen → you are back at the mod list, not in the world.
- [ ] Remove Mod Menu from `mods/` and restart → the mod loads exactly as before; only the button is gone. This is the optional-dependency check.
- [ ] Confirm the log says nothing about a missing Mod Menu — it must not be a dependency.
- [ ] Enable the modules under test by editing the config and restarting, and confirm each change survives the restart.

## HUD

### `hud.fps` — FPS Counter (on by default)

- [ ] Join a world → the counter is visible top-left and the number changes.
- [ ] Press F3 to open the debug overlay → the counter still updates.
- [ ] Set `showLabel` to `false` → the `FPS: ` prefix disappears, the number stays.
- [ ] Move `anchor` through all nine positions → the counter follows, never off-screen.
- [ ] Raise `offsetX`/`offsetY` → it moves away from its corner in both axes.
- [ ] Set `hud.background` to `false` → the plate disappears but the text stays legible.

## Interface

### `ui.crosshair` — Crosshair

- [ ] Enable with `style = CROSS` → the vanilla crosshair is replaced by the mod's four-arm one in HUD **and** in third-person.
- [ ] Set `style = DOT` → a single square replaces it.
- [ ] Set `style = HIDDEN` → no crosshair at all, in any perspective.
- [ ] Change `thickness`, `gap` and `length` → each change is immediately visible.
- [ ] Change `color`; enable `outline` and change `outlineColor` → both are visible against a bright and a dark background.
- [ ] Disable the module → the vanilla crosshair returns exactly as before.

### `ui.chat` — Chat

- [ ] Enable `timestamps` → every new line is prefixed with the local time.
- [ ] Switch `timestampStyle` HOUR_MINUTE ↔ HOUR_MINUTE_SECOND → the prefix changes shape.
- [ ] Set `historyLength` to its maximum → scrolling up in chat reaches further back than vanilla's 100 lines.
- [ ] Set `historyLength` to its minimum → the scrollback is shorter than vanilla's.
- [ ] Disable the module → new lines have no prefix again.

### `ui.book` — Book Page Counter

- [ ] Enable, then open a writable book → a counter appears with the page's character count.
- [ ] Type into the page → the counter follows the edit live.
- [ ] Switch `counter` used ↔ remaining → the number changes to the complement.
- [ ] In a signed book (read-only) → the counter still appears.
- [ ] Close the book → the counter disappears.
- [ ] Change `color` → the counter's colour changes.

### `ui.screenshot` — Screenshot Helper

- [ ] Enable `copyPath` and `notify`, then press F2 → a file appears in `screenshots/`, chat says a screenshot was saved, and pasting puts the file path on the clipboard.
- [ ] Enable `shareLink` → pressing F2 copies a `file://` link instead, and the chat wording changes.
- [ ] Paste the `file://` link into a browser or file manager → the image opens.
- [ ] Set `copyPath` to `false` → the clipboard is left untouched.
- [ ] Set `notify` to `false` → chat stays silent.
- [ ] Take two screenshots in a row → each is reported exactly once, never twice.
- [ ] Confirm nothing was uploaded: no network activity from the mod.

### `ui.waypoints` — Waypoints

- [ ] Enable, then `/wokewtf waypoint add home` → chat confirms and the waypoint appears in the HUD list with a distance.
- [ ] `/wokewtf waypoint list` → the header and every entry appear, nearest first.
- [ ] Walk 100 blocks → the listed distance updates.
- [ ] `/wokewtf waypoint remove home` → it disappears from the HUD and the list.
- [ ] `/wokewtf waypoint add home` twice → the second is refused as a duplicate.
- [ ] Add a 25-character name and a name containing `|` → both are refused with a reason.
- [ ] Fill all 64 slots → one more is refused as full.
- [ ] Set `showCoordinates = false` then `true` → the coordinate triple appears and disappears.
- [ ] Change `maxShown`, `anchor`, `offsetX`, `offsetY` → the list is capped and repositioned.
- [ ] Restart the game → every waypoint is still there.
- [ ] Enter a different world → that world's waypoints are shown, not the previous world's.
- [ ] Disable and re-enable the module → the list comes back unchanged.

### `ui.inventory_sort` — Inventory Sort

- [ ] In singleplayer with the module enabled, `/wokewtf sort` → stacks are reordered and chat reports how many moved.
- [ ] Count your items before and after → the multiset is identical; nothing is merged, spawned or lost.
- [ ] Sort again immediately → the second run reports the inventory is already sorted.
- [ ] Switch `order` NAME ↔ COUNT → the resulting arrangement matches the setting.
- [ ] Enable `includeHotbar` → the hotbar is reordered too.
- [ ] Start a sort and leave the world mid-run → the sort stops and does not resume.
- [ ] Join a server somebody else runs → the module is unavailable and `/wokewtf sort` says why.
- [ ] Sort with a container screen open → it refuses rather than clicking in the wrong window.

## Convenience

### `util.auto_reconnect` — Auto Reconnect (off by default)

- [ ] Leave it off, kill the connection → nothing happens. This is the default and the most important check here.
- [ ] Enable it, bind "Cancel Reconnect", join a server and restart that server → a countdown is printed in chat, one line per second.
- [ ] Press the cancel keybind during the countdown → chat says it was cancelled and no connection is attempted.
- [ ] Let the countdown run out → the connect screen appears and you rejoin the server.
- [ ] Disconnect deliberately (Quit to title) → the countdown does **not** start.
- [ ] Disconnect from a singleplayer world → nothing happens.
- [ ] Get kicked by a server operator → at most one reconnect attempt, which the server is free to refuse, and **no repeat attempts afterwards**.
- [ ] Set `countdownSeconds` to minimum and maximum → the countdown honours both bounds.
- [ ] Confirm the mod never reconnects without a visible countdown first.

### `util.respawn_confirm` — Respawn Confirmation

- [ ] Enable with a 3-second hold, then die → the respawn button is inactive and shows how long is left.
- [ ] Wait it out → the button returns to its normal label and works.
- [ ] Click the button during the hold → nothing happens; you do not respawn.
- [ ] Leave the death screen open through the whole hold → the button becomes usable on its own.
- [ ] Die in hardcore mode → the Spectate button is **not** touched.
- [ ] Disable the module → the respawn button is instant again.

### `util.chat_macros` — Chat Macros (off by default)

- [ ] `/wokewtf macro set greet Hello there` → chat confirms the macro was saved.
- [ ] `/wokewtf macro list` → `greet` is listed as slot 1.
- [ ] Bind "Chat Macro 1", enable the module, press the key in-game → the chat box opens with `Hello there` already in it.
- [ ] Press Escape → nothing is sent.
- [ ] Press the key again and press Enter → the line is sent exactly once.
- [ ] With the module **off**, press the key → nothing opens.
- [ ] Press a key whose slot is empty → chat says no macro is in that slot.
- [ ] `/wokewtf macro set greet Changed` → slot 1 now types `Changed`.
- [ ] Try a 33-character name and a 257-character line → both are refused with a reason.
- [ ] Add 20 macros, then try a 21st → refused as full.
- [ ] `/wokewtf macro remove greet` → slot 2's macro shifts up into slot 1.
- [ ] Restart the game → macros and their keybinds are unchanged.

### `util.screenshot_key` — Screenshot Key (off by default)

- [ ] Bind "Take Screenshot", enable the module, press the key → a screenshot is saved and chat reports it.
- [ ] Enable `ui.screenshot` too → the new file is also offered on the clipboard.
- [ ] Disable the module → the key does nothing.
- [ ] Confirm the resulting file is identical in naming and location to an F2 screenshot.

### `util.fullscreen_key` — Fullscreen Key (off by default)

- [ ] Bind "Toggle Fullscreen", enable the module, press the key → the window toggles.
- [ ] Press it again → it toggles back, and the window size is restored.
- [ ] Disable the module → the key does nothing.

### `util.stats` — Local Stats

- [ ] Enable, then `/wokewtf stats` → three counters and a formatted playtime are shown.
- [ ] Break a block → the mined count goes up by one.
- [ ] Let another player break a block in front of you → the count does **not** move.
- [ ] Walk a known distance → the walked figure grows by roughly that much.
- [ ] Teleport (`/tp`) → the walked figure does **not** jump.
- [ ] Fly, swim and fall → decide what you consider correct and write down what actually happened.
- [ ] Restart the game → the counters continue from where they were.
- [ ] `/wokewtf stats reset` → all counters return to zero and stay there.
- [ ] Confirm no stats leave the machine: no upload, no chat announcement, no server traffic.

## Across every module

- [ ] Bind two of the mod's keybinds to the same key, then join a world → chat reports the conflict and names the key.
- [ ] Check that a conflict is reported again after a restart.
- [ ] Turn on several modules at once → they do not interfere; the HUD stays readable.
- [ ] Edit `config/wokewtf-lite.json` by hand: change a value → it is honoured on the next launch.
- [ ] Corrupt that file on purpose → it is quarantined as `.corrupt-…`, the game starts with defaults, and the log says why.
- [ ] Set `schemaVersion` to `99` → the mod refuses to overwrite the file and says so.
- [ ] With a world loaded, confirm `stats` writes reach the file within about half a minute.
- [ ] Check the log for exceptions from `wtf.woke.lite` at the end of the session: there should be none.
