# Manual test checklist

Every path here needs a running game, a display and a pair of hands. The
automated suite covers the logic that needs none of those; this file is what is
left, and its job is to be the honest counterpart to a green build.

## Before you start

- [ ] Java 21, Fabric Loader 0.19.5+, Fabric API `0.141.6+1.21.11`.
- [ ] `./gradlew clean build` is green.
- [ ] Start the client with `scripts/test_session.sh`. It resets
      `run/config/wokewtf-lite.json` so **every module is off**, which is the
      baseline each test below assumes.
- [ ] In `run/logs/latest.log`, confirm
      `woke.wtf Lite v0.1.0 ready: 13 modules (0 enabled), 24 keybinds` and the
      list `Registered modules: hud.fps, … util.stats`.
- [ ] **Options → Controls** shows a `woke.wtf Lite` category with 24 bindings.
- [ ] Client commands (`/wokewtf …`) exist only after you join a world.

## How to read an entry

Each entry has a unique **test ID**, e.g. `T-07`. Work top to bottom, tick what
passes, and report what does not as "T-07 failed" using the template in
`ISSUE_TEMPLATE.md`. **Do** is what you perform, **Expect** is a pass, and
**Fail** is anything in between — including "nothing happened".

## T-00 — Build and startup (prerequisite)

- **Do:** build the mod, then start via `scripts/test_session.sh` and read the log.
- **Expect:** the build is green; startup logs 13 modules and 24 keybinds with no
  WARN or ERROR from `(wtf Lite)`, and no `Keybind conflict` line while no mod key
  is bound.
- **Fail:** any `(wtf Lite)` WARN or ERROR, fewer modules or keybinds, or a crash.

## Settings screen

### T-01 — Settings screen

- **Do:** press **Right Shift**; press **Escape**; run `/wokewtf config`; search
  `cross`, then `größe` / `grosse` / `ß`; hover a label; drag a slider; open an
  enum list and press **Escape**; click a colour a few times; press **Reset**;
  click outside the panels.
- **Expect:** opens and closes both ways; search narrows the list with a
  `shown/total` badge and folds umlauts and `ß` to their plain letters; tooltips
  appear; widgets edit live; **Escape** closes an open list but not the screen;
  **Reset** restores one setting's default; an outside click does nothing.
- **Fail:** it will not open or close, edits do nothing, a list traps **Escape**,
  or **Reset** changes the wrong value.

## HUD

### T-02 — `hud.fps` — FPS Counter

- **Do:** with every module off, enable **FPS Counter**; join a world; press F3;
  hide `showLabel`; move `anchor` through the corners; raise the offsets.
- **Expect:** a plate in the chosen corner with a number that keeps changing,
  including with F3 open; the label can be hidden; it follows the anchor and
  never leaves the screen.
- **Fail:** no counter, a frozen number, or it drifts off-screen.

## Interface

### T-03 — `ui.crosshair` — Crosshair

- **Do:** enable it; switch `Shape` through **Cross**, **Dot** and **Hidden**;
  change thickness, gap, arm length, colour and outline.
- **Expect:** the vanilla crosshair is replaced in first- and third-person;
  every edit is visible at once; **Hidden** removes it entirely; disabling
  restores vanilla exactly.
- **Fail:** both crosshairs draw, changes need a restart, or the vanilla one is
  gone while the module is off.

### T-04 — `ui.chat` — Chat

- **Do:** enable it; send a line; switch `Timestamp Format`; set `History
  Length` to its maximum, then its minimum, scrolling back each time.
- **Expect:** new lines carry the local time in the chosen shape; the scrollback
  is longer, then shorter, than vanilla's 100 lines; disabling removes the prefix.
- **Fail:** no prefix, a wrong or doubled prefix, or the history length ignored.

### T-05 — `ui.book` — Book Page Counter

- **Do:** enable it; open a writable book and type on a page; open a signed
  (read-only) book; switch `Counter Style` used ↔ remaining; close the book.
- **Expect:** a counter shows characters used or left, follows typing live, and
  appears for both book kinds; it disappears when the book closes.
- **Fail:** no counter, a stale count, or it lingers after closing.

### T-06 — `ui.screenshot` — Screenshot Helper

- **Do:** enable it with `Copy Path` and `Chat Notification`; press F2; enable
  `Share Link` and press F2 again; then turn both off and press F2.
- **Expect:** each F2 writes one file to `screenshots/` and says so once; the
  clipboard holds the path, then a `file://` link; with both off the clipboard is
  untouched.
- **Fail:** no file, a doubled message per press, a clipboard that never changes,
  or any network activity.

### T-07 — `ui.waypoints` — Waypoints

- **Do:** enable it; `/wokewtf waypoint add home`; walk about 100 blocks;
  `/wokewtf waypoint list`; `/wokewtf waypoint remove home`; add it again;
  restart; enter another world.
- **Expect:** the HUD lists it with a live distance; duplicates, over-long names
  and `|` are refused with a reason; `remove` drops it; the list survives a
  restart and is per-world.
- **Fail:** a waypoint lost on restart, a wrong distance, or names leaking
  between worlds.

### T-08 — `ui.inventory_sort` — Inventory Sort

- **Do:** in singleplayer, enable it; fill your inventory; `/wokewtf sort`; count
  items before and after; sort again; switch `Order` and `Include Hotbar`; join a
  server someone else runs and try `/wokewtf sort`.
- **Expect:** stacks are reordered with an identical item multiset; a second run
  says already sorted; the settings change the arrangement; on a foreign server
  it refuses and says why.
- **Fail:** items lost, merged or duplicated; sorting works on a remote server;
  `Include Hotbar` ignored.

## Convenience

### T-09 — `util.auto_reconnect` — Auto Reconnect

- **Do:** leave it off and kill the connection; then enable it, bind **Cancel
  Reconnect**, and drop the connection; press the cancel key during the
  countdown; let it run out; quit to title deliberately.
- **Expect:** off → nothing; on → one countdown line per second, cancellable,
  then one reconnect attempt; a deliberate quit or a singleplayer drop does not
  start it; there is no retry loop.
- **Fail:** it reconnects with no countdown, retries in a loop, or ignores the
  cancel keybind.

### T-10 — `util.respawn_confirm` — Respawn Confirmation

- **Do:** enable it with a 3-second hold; die; click the respawn button during
  the hold; wait it out; die in hardcore; disable it and die again.
- **Expect:** the button is inactive and shows the remaining seconds, then
  becomes usable; the hardcore **Spectate** button is untouched; when disabled
  respawn is instant.
- **Fail:** you respawn during the hold, the button stays stuck, or **Spectate**
  is disabled.

### T-11 — `util.chat_macros` — Chat Macros

- **Do:** `/wokewtf macro set greet Hello there`; `/wokewtf macro list`; bind
  **Chat Macro 1**; enable the module; press the key; press **Escape**; press it
  again and **Enter**; switch the module off and press it; press an empty slot;
  `/wokewtf macro remove greet`.
- **Expect:** the chat box opens prefilled, nothing is sent until **Enter**; off
  or an empty slot says so; removing a macro shifts the rest up.
- **Fail:** a line is sent without **Enter**, it opens while off, or the wrong
  slot's text appears.

### T-12 — `util.screenshot_key` — Screenshot Key

- **Do:** bind **Take Screenshot**; enable the module; press it; also enable
  `ui.screenshot` and press again; disable and press.
- **Expect:** each press saves one screenshot, identical to an F2 shot; the
  helper also offers it on the clipboard; nothing happens when disabled.
- **Fail:** no file, two files per press, or it still fires when disabled.

### T-13 — `util.fullscreen_key` — Fullscreen Key

- **Do:** bind **Toggle Fullscreen**; enable the module; press it twice;
  disable and press.
- **Expect:** the window toggles both ways and restores its size; nothing
  happens when disabled.
- **Fail:** the window does not restore, or the key still fires when disabled.

### T-14 — `util.stats` — Local Stats

- **Do:** enable it; `/wokewtf stats`; break a block; have another player break
  one nearby; walk a known distance; `/tp`; restart; `/wokewtf stats reset`.
- **Expect:** blocks-mined rises by one per block **you** break; distance grows
  by roughly what you walked and ignores teleports; counters persist across a
  restart and return to zero on reset.
- **Fail:** another player's block counts, a teleport inflates distance, or a
  reset does not stick.

## Across modules

### T-15 — Keybind conflicts and coexistence

- **Do:** in **Options → Controls**, set **Open Settings** and one other mod
  binding (e.g. **Chat Macro 1**) to the same key, e.g. **F1**; join a world;
  restart and rejoin; then enable several modules at once.
- **Expect:** one chat line per conflict naming the key and saying to rebind; the
  same is reported again after a restart; a pair of **vanilla** bindings that
  share a key (the debug keys on A, S, D …) is **never** reported; busy modules
  do not interfere and the HUD stays readable.
- **Fail:** vanilla-only pairs appear in chat or in the log, a real conflict
  involving one of our keys stays silent, or two modules fight for the screen.

## After the session

- [ ] The log has no exception from `wtf.woke.lite` after all of the above.
- [ ] Your own settings are in `run/config/wokewtf-lite.json.test-session-backup`
      if you want them back.
