# Changelog

Notable changes to this repository. It holds two separate artifacts that happen
to share a name; each has its own version line below, and the mod's detailed
history lives in [`fabric-mod/CHANGELOG.md`](fabric-mod/CHANGELOG.md).

## 1.0.0

The first tagged release, covering both artifacts.

### libwoke.so + woketool (C++/Linux)

What this is, plainly: an **infrastructure demonstration**. `libwoke.so` is a
logging layer, a JVM-discovery and reflection layer, an event bus, a
thread-dispatch queue and a funchook wrapper. It contains **no graphics hooks,
no modules and no overlay** — `hooks::install()` is called from nothing but its
own self-test. `woketool` is the standalone UI harness that draws the theme and
component library so it can be screenshotted and pixel-checked without a game.

#### Fixed

- **`JNI_OnUnload` was spelled `JNI_OnUnLoad`, so the JVM never called it.**
  The JNI entry point is looked up under the exact spec name (`jni.h`:
  `JNI_OnUnload`, lowercase "load"), and the mismatch meant the whole teardown
  path — joining the bootstrap thread, shutting down the dispatcher, game
  context, reflection cache, hook engine, mappings and logger — silently never
  ran. Fixed in `src/entry.cpp`, `src/woke.ld`, and the export assertion in
  `.github/workflows/ci.yml`.
- **Data races on the shared JVM state.** `g_vm`, `g_jvm_ready` and
  `g_bootstrap_complete` were `volatile`, which stops the compiler folding
  accesses but does nothing for two threads writing them. ThreadSanitizer
  flagged writes from both the constructor's bootstrap thread and `JNI_OnLoad`.
  They are `std::atomic` now, with release/acquire ordering.
- **`pthread_join` on a detached thread.** The bootstrap thread was detached at
  spawn and then "joined" at teardown, which is undefined and fails outright —
  so teardown never actually waited for it. The thread is still detached (a
  library that is never unloaded now retains nothing) but teardown waits on an
  explicit completion handshake instead.

#### Added

- `THIRD_PARTY_NOTICES.md`: what is linked in and under which terms, including
  why an MIT binary may contain GPL-2.0 funchook (its linking exception) and
  the obligation to redistribute its licence text.

### woke.wtf Lite (Fabric mod)

See [`fabric-mod/CHANGELOG.md`](fabric-mod/CHANGELOG.md) for the full entry. In
short: thirteen opt-in QoL modules for Minecraft 1.21.11 — HUD, interface and
convenience — a settings screen, client commands, an English/German first-join
guide, a client gametest and session-debugging tooling.

#### Fixed

- Macro and waypoint names/text now reject the section sign, control characters
  and DEL — the same characters the game refuses at the keyboard.

### Verification

- C++: 880 unit checks across `linux-debug`, `linux-release` and
  `linux-release-asan`, 0 warnings from the project's own sources, exported
  symbol surface of exactly `JNI_OnLoad JNI_OnUnload`, ASan/UBSan/LeakSanitizer
  clean, ThreadSanitizer clean over the live-JVM harness, and the 14-check UI
  harness passing under sanitizers.
- Java: 330 JUnit tests, 0 failures, 0 errors, 0 skipped, 0 warnings from the
  mod's own sources; the 5-check client gametest passing.
