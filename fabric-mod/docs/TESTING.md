# Testing woke.wtf Lite

Two automated layers cover the mod, and a third — the manual session — covers
what neither can.

| Layer | What it covers | How to run it |
| --- | --- | --- |
| Unit suite | Everything that needs no game: config round-trips, the reconnect state machine, counters, macro rules, search folding, the mixin contract | `./gradlew test` |
| Client gametest | What is drawn: HUD placement and colour, the chat timestamp, the settings screen | `./gradlew runClientGameTest` |
| Manual checklist | Real input, real timing, other players, everything that needs hands | [MANUAL_TEST_CHECKLIST.md](../MANUAL_TEST_CHECKLIST.md) |

## The client gametest

It lives in `src/gametest`: a dev-only source set with its own test mod,
registered through `fabricApi.configureTests`. It is **not** part of the shipped
jar, adds no runtime dependency, talks to nothing but the running client, and
never uploads anything. `./gradlew build` still produces exactly the same
artifacts it did before it existed.

What it does: starts a real client, creates a flat singleplayer world, switches
every module off and a few deliberately on, draws the HUD, takes screenshots, and
reads the pixels back. It prints one `PASS`/`FAIL` line per check and fails the
task if any check failed.

```
PASS fps-counter-plate-in-top-left -- plate pixels #FFFF00FF in the top-left 96px: 1540
PASS crosshair-centre-has-configured-colour -- crosshair pixels #FFFF0000 at the screen centre: 4
PASS chat-timestamp-drawn-in-chat-area -- chat text pixels: 988 without vs 1316 with a timestamp
PASS settings-screen-opens-via-command -- current screen is WokeConfigScreen: true
PASS settings-screen-sidebar-visible -- sidebar pixels: panel 5536, accent 240
```

| Check | What it proves |
| --- | --- |
| `fps-counter-plate-in-top-left` | The HUD overlay draws, and the FPS counter sits where its default anchor puts it. The plate colour is forced to opaque magenta so the match is exact. |
| `crosshair-centre-has-configured-colour` | The custom crosshair replaces the vanilla one, with the colour it was configured to. Forced to opaque red; a dot is one GUI unit, so a handful of pixels is the whole shape. |
| `chat-timestamp-drawn-in-chat-area` | The chat timestamp reaches the screen. Two frames of the same chat text are compared — one without a timestamp, one with — so the extra glyphs are the only difference between them. Counting "text-bright" pixels rather than an exact colour is deliberate: the chat hud draws text at partial opacity while it is unfocused, so vanilla's near-white arrives as a mid grey. |
| `settings-screen-opens-via-command` | `/wokewtf config` opens the settings screen — the command path, not a direct call. |
| `settings-screen-sidebar-visible` | The category sidebar is drawn: the panel colour across the sidebar box, plus the accent bar and accent text of the open category. |

The test forces the probe colours, resets every setting and switches every module
off when it is done, and runs in its own game directory under
`build/run/clientGameTest`. Your `run/` directory, and the config in it, are left
alone.

### Running it

```bash
cd fabric-mod
./gradlew runClientGameTest
```

It draws the game, so it needs a display. On a headless machine:

```bash
xvfb-run -a -s "-screen 0 960x540x24" \
  env LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runClientGameTest
```

Two things to know if it stalls:

- **The world stops at `Preparing spawn area`.** Fabric's network synchroniser,
  which is on by default for gametests, can deadlock the world load on some
  machines. `build.gradle` therefore passes
  `-Dfabric.client.gametest.disableNetworkSynchronizer=true` to this task only.
  If that switch is ever removed, this is the first symptom.
- **Screenshots are kept.** They land in
  `build/run/clientGameTest/screenshots/` and are the evidence for every check,
  so a failure can be looked at rather than guessed at.

### What it cannot check

Anything requiring real input or another player: pressing a key that is not
dispatched by the test, dragging a slider, hovering a tooltip, a server that
refuses a connection. Those are what [MANUAL_TEST_CHECKLIST.md](../MANUAL_TEST_CHECKLIST.md)
is for. A green gametest is not a substitute for the manual session; it is the
part of it that a machine can repeat.

## When something fails

Reading a session back — where the log is, how to tell a module that is off from
one that is broken, and how to check a mixin applied — is
[DEBUGGING.md](DEBUGGING.md). Report what you found with `ISSUE_TEMPLATE.md`.
