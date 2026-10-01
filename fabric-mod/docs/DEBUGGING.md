# Debugging woke.wtf Lite

How to read a session, how to tell a module that is off from one that is broken,
and how to check that a mixin really applied. Everything here is read-only: no
step changes the game or the config except where it says so.

## Where the log is

| File | What is in it |
| --- | --- |
| `run/logs/latest.log` | The current session, INFO and above. A normal install keeps the same file under `.minecraft/logs/`. |
| `run/logs/debug.log` | Everything, DEBUG included. This is where the detail lives. |

## Which lines are ours

Every line this mod writes is tagged with its logger name — but the two
appenders render that name differently, so both spellings are ours:

| File | Tag | Example |
| --- | --- | --- |
| `latest.log` | `(wtf Lite)` | `[10:12:04] [Render thread/INFO] (wtf Lite) woke.wtf Lite v0.1.0 ready: 13 modules (1 enabled), 24 keybinds, config loaded` |
| `debug.log` | `(woke.wtf Lite)` | `[10:12:04] [Render thread/DEBUG] (woke.wtf Lite) watch 'hud.fps' tick: enabled=true, active=true` |

It is one logger, `"woke.wtf Lite"`; `latest.log` simply shortens the name at
the last dot. So **match `wtf Lite)`**, which catches both. Grepping for
`(wtf Lite)` in `debug.log` finds nothing and would look as though the mod
never spoke.

Anything tagged with neither is the game, Fabric, or whatever the machine felt
like printing. A log is full of things that have nothing to do with this mod —
a missing narrator, an OpenAL failure, a Realms error — so filter on the tag
before deciding anything was our fault.

Two startup lines say the mod is loaded and what it found:

```
woke.wtf Lite v0.1.0 ready: 13 modules (N enabled), 24 keybinds, config ...
Registered modules: hud.fps, ui.crosshair, ... util.stats
```

The second names every module one by one, so a module that went missing shows
up as a name that is not there.

## Is a module active

Three answers, cheapest first:

1. The startup line's `(N enabled)` count.
2. `run/config/wokewtf-lite.json`, which has `"enabled": true|false` per module.
3. The config screen, which shows the switch as it is right now.

## What one module is actually doing

Set the global **Debug Module** setting to a single module id, e.g. `hud.fps`.
The dispatcher then logs that module's state at every event, at DEBUG, so the
lines land in `debug.log` and never in `latest.log`:

```
watch 'hud.fps' tick: enabled=true, active=true
watch 'hud.fps' world join: enabled=true, active=true
```

That is how "off" is told from "broken":

| Line | Meaning |
| --- | --- |
| `enabled=true, active=true` | On and running. |
| `enabled=false, active=false` | Switched off. |
| `enabled=true, active=false` | On, but unavailable right now: no world joined, or a server that is not yours (Inventory Sort works like this). |
| no line at all | The id is wrong, or the setting is empty. |

Tick lines are throttled to one in a hundred so the log stays readable — the
first tick after you set the id is always logged, so you get an answer at once.
Join and leave lines are never held back.

The setting is a plain string under `global` in `run/config/wokewtf-lite.json`,
so it is quickest to edit there by hand:

```json
"global": { "debugModule": "hud.fps" }
```

The config screen shows it too, under **Global**, read-only: a text box that
could be mistyped into a wrong id is worse than a file you can read. Empty
means watch nothing, and so does a typo — the setting narrows the log down, it
never widens it to all thirteen modules.

## Did a mixin apply

Mixin logs at DEBUG only, so look in `debug.log` for
`wokewtf-lite.client.mixins.json`:

```
Mixing ChatHudMixin from wokewtf-lite.client.mixins.json into net.minecraft.client.gui.hud.ChatHud
```

A mixin appears when its target class first loads, so `BookEditScreenMixin` and
`ScreenshotShareMixin` are absent until you open a book or take a screenshot.
Absence before that is expected, not a failure.

## Did a module fail

Always visible, with no setting to turn on. The dispatcher logs this at ERROR
and latches the module off so it cannot keep throwing:

```
Module 'ui.chat' threw during tick; disabling it
```

`during tick` can also be `during world join` or `during world leave`. A module
that has been latched stays off until the game restarts.

## Summarise a whole session

`scripts/parse_session_log.sh` reads the log and the config file and prints, per
module, whether it was on and how many errors, warnings and log lines it
produced, then the totals.

```bash
cd fabric-mod
bash scripts/parse_session_log.sh                      # run/logs/latest.log
bash scripts/parse_session_log.sh run/logs/latest.log  # a specific log
bash scripts/parse_session_log.sh <logfile> <config>   # both explicit
```

It takes the module list from the log's own `Registered modules:` line, so it
cannot drift away from what the build registers. Mod errors are counted
separately from the game's own errors. An empty result is a result: if a test
failed and the summary says nothing, that silence is the finding to report.

## Reporting what you found

Use `ISSUE_TEMPLATE.md` with the test id from `MANUAL_TEST_CHECKLIST.md`, and
attach at most 20 log lines around the failure.
