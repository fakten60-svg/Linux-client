# Third-party notices

`libwoke.so` and `woketool` are MIT-licensed, and they are built on top of
vendored third-party code that is fetched by `scripts/fetch_deps.sh` into
`third_party/` (pinned to immutable revisions, and never committed here). The
project's own licence does not change the terms of that code. This file is the
record of what is linked in and under which terms.

## Linked into the released binaries

| Component | Licence | Where |
| --- | --- | --- |
| [funchook](https://github.com/kubo/funchook) | **GPL-2.0-or-later, with a linking exception** | `libwoke.so` (static) |
| [Dear ImGui](https://github.com/ocornut/imgui) | MIT | `woketool` (static) |
| [GLFW](https://www.glfw.org/) | zlib/libpng | `woketool` (static) |
| [nlohmann/json](https://github.com/nlohmann/json) | MIT | `libwoke.so` (header-only) |

## Not linked — build-time only

| Component | Licence | Where |
| --- | --- | --- |
| OpenJDK 21 `jni.h` / `jni_md.h` | GPL-2.0-with-classpath-exception | `third_party/jdk-lite/include` (headers only) |

## Why an MIT binary can contain funchook

funchook is GPL-2.0-or-later, which would normally make any binary that links
it GPL as well. It ships an explicit exception that avoids exactly that, and it
is worth quoting because it is what makes this distribution legitimate:

> As a special exception, the copyright holders of this library give you
> permission to link this library with independent modules to produce an
> executable, regardless of the license terms of these independent modules, and
> to copy and distribute the resulting executable under terms of your choice,
> provided that you also meet, for each linked independent module, the terms
> and conditions of the license of that module. An independent module is a
> module which is not derived from or based on this library.

Our own code is an independent module — it is not derived from funchook — so
the combined `libwoke.so` may be distributed under the terms of our choice
(MIT), provided each independent module's own terms are met (they are: our code
is MIT, ImGui and nlohmann are MIT, GLFW is zlib/libpng).

Two obligations follow, and this file is how they are met:

1. funchook's own licence text ships with it (`third_party/funchook/LICENSE`)
   and must be redistributed with any binary that links it.
2. Modifying funchook would require carrying its exception forward to the
   modified version. **This project does not modify funchook** —
   `scripts/fetch_deps.sh` checks out a pinned upstream revision and nothing
   patches it. The one build tweak is a warning flag
   (`-Wno-format-overflow`, see `CMakeLists.txt`), which changes compilation,
   not the source.

The JNI headers are GPLv2-with-classpath-exception, which exists precisely to
let non-GPL programs use them. They are compiled against, never linked in, and
are not part of any released binary.

## The Fabric mod

`fabric-mod/` is a separate artifact with its own dependencies — Fabric Loader,
Fabric API, Gson, SLF4J, JUnit, all MIT or Apache-2.0 — declared in
`fabric-mod/gradle.properties`. It links none of the above and contains no
vendored code. See `fabric-mod/LICENSE` for its licence.
