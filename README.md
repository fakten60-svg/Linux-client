# woke.wtf — Native C++ Utility Client

A high-performance, native C++ injection utility client for **Minecraft 1.21.11 (Fabric)**,
built as a shared object (`libwoke.so`) for **x86_64 Linux**.

> **Scope & compliance:** built strictly for private-server utility testing, QoL automation,
> and local singleplayer development. Zero public multiplayer use. Adheres to the Minecraft
> EULA. No custom network packets are generated — all modules operate through standard
> client-side game state APIs with explicit, visible user toggles.

## Toolchain Requirements

| Component | Requirement |
|---|---|
| Compiler | GCC 13+ or Clang 17+ recommended (GCC 11.4 verified as working floor), C++20 |
| CMake | 3.27+ |
| OS | Linux x86_64, glibc, kernel 6.x+ |
| OpenGL / X11 | `libgl1-mesa-dev`, `xorg-dev` (GLFW dependency chain) |
| JDK | **Not required** — JNI headers are vendored by the bootstrap script |

## Quick Start

```sh
# 1. System packages (Ubuntu 22.04+ / Debian)
sudo apt-get install -y build-essential cmake xorg-dev libgl1-mesa-dev

# 2. Bootstrap vendored dependencies into third_party/ (pinned revisions)
sh ./scripts/fetch_deps.sh

# 3. Configure + build via presets
cmake --preset linux-debug
cmake --build --preset linux-debug
# → build/linux-debug/libwoke.so

cmake --preset linux-release
cmake --build --preset linux-release
# → build/linux-release/libwoke.so
```

### JVM verification harness

```sh
python3 scripts/gen_stubs.py          # stub classes from mappings.json
/usr/lib/jvm/java-21-openjdk-amd64/bin/javac -d tests/jvm/out \
    tests/jvm/net/minecraft/*.java tests/jvm/WokeProbe.java
/usr/lib/jvm/java-21-openjdk-amd64/bin/java -cp tests/jvm/out \
    WokeProbe build/linux-release/libwoke.so
# → resolves class_310 / method_1551 / field_1724 live and logs the handles
# → posts a dispatch task from a non-game thread; the worker thread attaches to
#   the JVM, calls a real JVM method, and publishes a ClientStartedEvent
```

If your distro's CMake is older than 3.27 (e.g. Ubuntu 22.04 ships 3.22), install a newer
one locally:

```sh
curl -fsSL -o /tmp/cmake.tar.gz \
  https://github.com/Kitware/CMake/releases/download/v3.30.5/cmake-3.30.5-linux-x86_64.tar.gz
tar -xzf /tmp/cmake.tar.gz -C ~/.local --strip-components=1   # → ~/.local/bin/cmake
export PATH="$HOME/.local/bin:$PATH"
```

### Unit tests

```sh
ctest --test-dir build/linux-release --output-on-failure
# → 1/1 Test #1: woke.unit ... Passed
# → OK — 880 checks, 0 failures
```

`woke_unit_tests` (`tests/unit/`) covers the pure, headless layers: `utils/math_utils.h`,
`utils/string_utils.h`, `ui/settings.*` and `ui/animation.*`. It has **no test framework and
no display requirement** — `tests/unit/test_util.h` is a ~70-line assertion context — so it
runs anywhere the build does and is part of CI for both presets. The executable's exit code
is the contract (0 = all checks passed), so nothing parses its output.

Anything that needs a GL context (components, chrome, toasts) is verified by the `woketool`
screenshot harness instead: a unit test that needs a window is a worse test and a flakier CI
job. `-DWOKE_BUILD_TESTS=OFF` drops the target and the ctest entry.

### Build Options

| Preset | Purpose |
|---|---|
| `linux-debug` | `-O0 -g3`, full diagnostics, no LTO |
| `linux-release` | `-O3`, LTO, hidden symbol visibility — the injection artifact |
| `linux-release-asan` | Release + ASan/UBSan for development sessions (never ship) |

Additional CMake switches: `-DWOKE_ENABLE_SANITIZERS=ON`, `-DWOKE_ENABLE_LTO=ON|OFF`,
`-DWOKE_BUILD_TESTS=ON|OFF`.

### Continuous integration

Every push to `main` and every pull request runs two jobs
(`.github/workflows/ci.yml`):

| Job | What it proves |
|---|---|
| `build-and-verify` | debug + release build with **zero warnings**, the unit suite in both presets, an exported symbol surface of exactly `JNI_OnLoad JNI_OnUnLoad`, the `woketool` checks below under Xvfb (baseline, reduced motion, a search filter, and a settings file surviving a restart), and the live-JVM harness |
| `sanitizers` | the same unit suite under ASan + UBSan with leak detection on, plus the UI harness under the same sanitizers |

The live-JVM step loads `libwoke.so` into a real JVM, lets the bootstrap post a
task from a non-game thread, and requires the worker to attach, call a real JVM
method, publish its event and tear down cleanly — under a `timeout`, so a hung
JVM fails the job instead of stalling the runner. The baseline screenshot is
uploaded as a build artifact, so a palette regression can be looked at rather
than only read about.

## Project Layout

```
├── CMakeLists.txt            # build definition → libwoke.so
├── CMakePresets.json         # linux-debug / linux-release / linux-release-asan
├── mappings.json             # Yarn → intermediary mappings for MC 1.21.11 (provided)
├── scripts/
│   └── fetch_deps.sh         # pins & clones all vendored dependencies
├── third_party/              # gitignored; bootstrap via fetch_deps.sh
│   ├── imgui/                # Dear ImGui (docking branch, pinned SHA)
│   ├── funchook/             # Linux function-hooking engine (MinHook-style shim planned)
│   ├── nlohmann/             # JSON for configs
│   ├── glfw/                 # window/input integration
│   └── jdk-lite/             # JNI headers only (jni.h, jni_md.h)
├── src/
│   ├── entry.cpp             # constructor bootstrap + JNI_OnLoad + init chain
│   ├── core/logger.*         # ANSI console + session/latest.log engine
│   ├── core/event_bus.h      # type-safe publish/subscribe (no RTTI, no std::function)
│   ├── core/thread_dispatch.*# game-thread task queue (the mc.execute() analogue)
│   ├── utils/math_utils.h    # pure easing/lerp/spring primitives (header-only)
│   ├── utils/string_utils.h  # ASCII case-insensitive search helper (header-only)
│   ├── utils/render_utils.*  # DrawList helpers (shadow, gradient, clip) + ambient alpha
│   ├── ui/theme.h            # macOS palette/radii/timing as constexpr tokens
│   ├── ui/animation.*        # named float channels: tween + exponential damp
│   ├── ui/components.*       # PillToggle, ModuleCard, CategoryItem, SearchBar
│   ├── ui/clickgui.*         # macOS chrome: traffic lights, sidebar, filter, cards
│   ├── ui/notifications.*    # toast queue (FixedString, zero-alloc draw)
│   ├── ui/settings.*         # `key = value` settings file (load/save, no heap)
│   ├── jvm/... hooks/...     # JVM reflection + funchook layers (Phases 3–4)
│   └── woke.ld               # version script: only JNI_OnLoad/OnUnLoad export
├── tools/woketool/           # standalone GLFW+GL3 app rendering the ui/ stack
│   └── main.cpp              #   --screenshot/--verify/--search/--config/--set flags
├── tests/unit/               # dependency-free unit suite (ctest, no display needed)
│   ├── test_util.h           #   ~70-line assertion context — the whole harness
│   └── test_{text,math,animation,settings}.cpp
├── tests/jvm/                # live-JVM verification harness (stubs + probe)
└── logs/                     # runtime session logs (gitignored)
```

`CMakeLists.txt` builds four targets: `woke` (the injectable `libwoke.so`), `woke_ui_core`
(the parts of `src/ui` with no GL dependency — shared by `woketool` and the unit tests, so
their source list is not duplicated per consumer), `woketool` (the standalone harness) and
`woke_unit_tests`.

### macOS UI harness (woketool)

The Phase 5 UI stack is developed and verified as a **standalone desktop app**
(`woketool`) instead of an injected overlay: same theme tokens, animation
controller, components and chrome, rendered by GLFW + OpenGL 3.2 + Dear ImGui
in a normal window. Verification:

```sh
cmake --build --preset linux-release
timeout 60 xvfb-run -a ./build/linux-release/woketool --screenshot ui.png --frames 45
# filtered state (proves search + category filtering, not just layout):
timeout 60 xvfb-run -a ./build/linux-release/woketool \
    --screenshot ui-filtered.png --frames 45 --search motion
```

Each screenshot run prints machine-checkable evidence, so filtering, the scroll
extent and the active animation scale are verifiable without a mouse:

```
[woketool] png=ok 960x600 visible_cards=16 scroll_max_y=722.0 motion_scale=1.00 window_appear=0.220s enabled_cards=3
[woketool] png=ok 960x600 visible_cards=1  scroll_max_y=0.0   motion_scale=1.00 window_appear=0.220s enabled_cards=3   # --search motion
[woketool] png=ok 960x600 visible_cards=0  scroll_max_y=0.0   motion_scale=1.00 window_appear=0.220s enabled_cards=3   # --search zzz
```

The pointer rests at the screen centre under a bare Xvfb display, which hovers
whichever card sits there and quietly pollutes any screenshot-to-screenshot
diff; `--park-mouse` keeps it off the UI for that reason.

`--verify` turns that same capture into an assertion instead of a picture, and
exits non-zero if any check fails — which is what lets CI gate on a rendered
frame rather than on a human looking at a PNG:

```sh
xvfb-run -a ./build/linux-release/woketool --screenshot ui.png --frames 60 \
    --park-mouse --verify
# [woketool] check=ok   window_bg       count=215201 min=100000
# [woketool] check=ok   light_red       count=91 min=20
# [woketool] check=ok   all_cards_shown visible=16 total=16
# [woketool] check=ok   motion_scale    motion_scale=1.000 expected=1.000
# [woketool] verify checks=14 failed=0
```

It counts how many pixels of the frame carry each theme surface and compares
against the `theme.h` tokens, so a deliberate repaint keeps passing and only a
*missing* surface fails; it checks that a filtered list is genuinely shorter
while an unfiltered one is complete, that the card pane really overflows, and
that the motion scale actually applied to the timing is the one the Reduced
Motion switch asks for. Flat fills are matched exactly; type is matched with a
±6 per-channel allowance, because antialiased glyphs are almost never exactly
their own colour.

Because the appear tween scales the ambient alpha, `--verify` waits for the
window to report itself fully open before capturing — a mid-flight frame is a
blend of every surface rather than the palette. That has to come from the
tween's own state and not from a frame count: on a fast machine a filtered list
can finish 60 frames before the 220 ms tween ends. An intentional mid-flight
`--screenshot` (the `--closed-frames` A/B above) is unaffected.

#### Settings persistence

The card switches and the active sidebar group are stored in a plain-text file
(`src/ui/settings.*`), one `key = value` per line:

```
ui.category = 0
card.notifications = 1
card.reduced_motion = 0
...
```

Keys are derived from card titles (`Reduced Motion` -> `card.reduced_motion`),
so reordering the card array cannot silently reinterpret an existing file, and
unknown keys survive a load/save round-trip so a file written by a newer build
keeps its extra settings. Booleans accept `1/0`, `true/false`, `on/off` and
`yes/no` — the file is meant to be hand-edited.

`ClickGui` is path-agnostic; the shell decides where settings live. `woketool`
persists only when pointed at a file, because a screenshot tool must not rewrite
the user's real settings (and a persisted file would change the outcome of the
pixel checks above):

```sh
# process 1: flip a switch through the real click path, write the file
xvfb-run -a ./build/linux-release/woketool --park-mouse --config /tmp/woke.conf \
    --click-motion-at 30 --screenshot a.png --frames 60
# process 2: same file, no flags — the switch comes back on by itself
xvfb-run -a ./build/linux-release/woketool --park-mouse --config /tmp/woke.conf \
    --screenshot b.png --frames 60
# process 3: a fresh path starts from the factory defaults
xvfb-run -a ./build/linux-release/woketool --park-mouse --config /tmp/fresh.conf \
    --screenshot c.png --frames 60
```

`--set card.reduced_motion=1` (repeatable, same syntax as the file) applies an
edit after loading and reports it as `set_applied`, so the file format, the
parser and the UI can be driven from one command line. The `Config Autosave`
card decides whether a change is written immediately (`autosave=ok` line) or
only on exit; the exit save happens either way, so a run pointed at a file
always leaves that file holding the live state. The app shell passes
`$HOME/.config/woke/woke.conf` (overridable with `$WOKE_CONFIG`).

#### Reduced Motion (accessibility)

The "Reduced Motion" card is not demo data: it writes the animation
controller's global time scale (`theme::time::reduced_motion_scale = 0.35`),
which shortens every tween duration, every damp smoothing constant and the
toast slide — including its 40 px travel — at once. The hold time of a toast is
deliberately left alone, because a dwell is not motion.

Timing is reproducible headlessly with `--fixed-dt` (constant frame delta) and
`--closed-frames` (hold the window shut so the appear transition can be sampled
mid-flight):

```sh
D=0.0166667   # 60 fps
# capture 7 frames into the appear transition, switch off and on
xvfb-run -a ./build/linux-release/woketool --screenshot off.png \
    --frames 13 --fixed-dt $D --closed-frames 6
xvfb-run -a ./build/linux-release/woketool --screenshot on.png \
    --frames 13 --fixed-dt $D --closed-frames 6 --reduced-motion
```

The window learns its full-opacity state (card body `#1C222D`) by frame 13 with
the switch on, and is still only a third of the way there with it off;
`--click-motion-at <frame>` synthesizes a click on the switch itself, so the
real click -> state -> animation-scale path is exercised without a user.

The build gates on zero warnings, and `--verify` asserts on the frame the
harness actually rendered: every spec surface must be on screen (traffic lights
#FF5F56/#FFBD2E/#27C93F, backdrop #0B0E14, sidebar #10141C, cards #1C222D), the
filters must narrow the list, and the accessibility switch must rescale the
timing it claims to. Every design value carries a one-sentence rationale inline
(spec line or macOS platform behavior). Demo cards are neutral
interface-settings mock data — no gameplay modules.

## Engineering Standards

- C++20, RAII, strict pointer hygiene; ~150–200 lines per file maximum.
- Zero heap allocations inside `on_render` / `on_tick` hot paths.
- All JNI handles (`jclass` / `jmethodID` / `jfieldID`) cached once at init —
  no `FindClass` / `GetMethodID` inside execution loops.
- Rendering overhead budget: **< 0.5 ms per frame**; ImGui work fully suppressed
  when the ClickGUI is closed.
- Logic that can be tested without a display is tested without a display; a new
  pure helper lands with unit coverage in the same change.

## Status

- [x] Phase 1 — build system, presets, dependency bootstrap, entry point
- [x] Phase 2 — logging engine (ANSI console + dual-file session logs)
- [x] Phase 3 — JVM layer: mappings loader, guarded reflection, handle cache,
      game context; funchook-based hook engine with runtime self-test
- [x] Phase 4 — event bus + game-thread dispatch (bounded queue, dedicated
      worker that attaches to the JVM per drain batch, module finaliser joins it)
- [x] Phase 5 — macOS UI stack: theme/animation/components/chrome/toasts plus
      live search + category filtering over the card list, a working
      Reduced Motion switch that rescales every animation, and settings
      persistence, all verified via the standalone `woketool` harness
      (injection-side graphics hooks intentionally not pursued)
- [x] Testing — dependency-free unit suite over the pure layers (text helpers,
      animation math, settings store, animation controller), run by `ctest` in
      both presets and again under ASan + UBSan on every CI build; GL-level
      behaviour is gated by the `woketool` harness, whose `--verify` mode asserts
      the rendered palette, the filters and the accessibility scale under Xvfb

### Mappings

`mappings.json` is generated from **yarn 1.21.11+build.6** by
`scripts/gen_mappings.py` (downloads the official mergedv2 tiny file from
FabricMC's maven, translates all descriptors into intermediary namespace, and
asserts canonical IDs such as `class_310` / `field_1724` / `method_1551`).
Members absent from the target build are reported and omitted — never guessed.

## Version Control

Branching: `main` (stable) ← `develop` ← `feat/<system>`. Releases tagged `v0.x.y`.
