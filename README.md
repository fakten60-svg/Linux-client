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

### Build Options

| Preset | Purpose |
|---|---|
| `linux-debug` | `-O0 -g3`, full diagnostics, no LTO |
| `linux-release` | `-O3`, LTO, hidden symbol visibility — the injection artifact |
| `linux-release-asan` | Release + ASan/UBSan for development sessions (never ship) |

Additional CMake switches: `-DWOKE_ENABLE_SANITIZERS=ON`, `-DWOKE_ENABLE_LTO=ON|OFF`.

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
│   ├── jvm/mappings.*        # mappings.json loader (auto schema detection)
│   ├── jvm/base_jni_hook.*   # guarded JNI reflection (ScopedAttach RAII)
│   ├── jvm/reflection_cache.*# one-line cached jclass/jmethodID/jfieldID lookups
│   ├── jvm/game_context.*    # cached class_310 / player / world handles
│   ├── hooks/hook_manager.*  # MinHook-style API over funchook (Linux)
│   └── woke.ld               # version script: only JNI_OnLoad/OnUnLoad export
├── tests/jvm/                # live-JVM verification harness (stubs + probe)
└── logs/                     # runtime session logs (gitignored)
```

## Engineering Standards

- C++20, RAII, strict pointer hygiene; ~150–200 lines per file maximum.
- Zero heap allocations inside `on_render` / `on_tick` hot paths.
- All JNI handles (`jclass` / `jmethodID` / `jfieldID`) cached once at init —
  no `FindClass` / `GetMethodID` inside execution loops.
- Rendering overhead budget: **< 0.5 ms per frame**; ImGui work fully suppressed
  when the ClickGUI is closed.

## Status

- [x] Phase 1 — build system, presets, dependency bootstrap, entry point
- [x] Phase 2 — logging engine (ANSI console + dual-file session logs)
- [x] Phase 3 — JVM layer: mappings loader, guarded reflection, handle cache,
      game context; funchook-based hook engine with runtime self-test
- [x] Phase 4 — event bus + game-thread dispatch (bounded queue, dedicated
      worker that attaches to the JVM per drain batch, module finaliser joins it)
- [ ] Phase 5 — graphics hooks + ImGui overlay, macOS ClickGUI, modules

### Mappings

`mappings.json` is generated from **yarn 1.21.11+build.6** by
`scripts/gen_mappings.py` (downloads the official mergedv2 tiny file from
FabricMC's maven, translates all descriptors into intermediary namespace, and
asserts canonical IDs such as `class_310` / `field_1724` / `method_1551`).
Members absent from the target build are reported and omitted — never guessed.

## Version Control

Branching: `main` (stable) ← `develop` ← `feat/<system>`. Releases tagged `v0.x.y`.
