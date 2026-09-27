#!/usr/bin/env sh
# ============================================================================
#  woke.wtf — third-party dependency bootstrap
#
#  Clones pinned, immutable revisions into third_party/. Idempotent: re-running
#  re-syncs each dependency to its pinned ref (shallow where possible).
#
#    Dear ImGui     docking branch @ 3bae66c735670619baf51391eba7f3d90a25d125
#    funchook       v1.1.3            (Linux hook engine; MinHook-style shim later)
#    nlohmann/json  v3.11.3
#    GLFW           3.4
#    jdk-lite       jni.h + jni_md.h from OpenJDK jdk-21-ga (headers only —
#                   no JDK installation is required to build libwoke.so)
#
#  Usage:   sh ./scripts/fetch_deps.sh
# ============================================================================
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TP="$ROOT/third_party"
mkdir -p "$TP"

IMGUI_URL="https://github.com/ocornut/imgui.git"
IMGUI_REF="3bae66c735670619baf51391eba7f3d90a25d125"  # docking branch tip
FUNCHOOK_URL="https://github.com/kubo/funchook.git"
FUNCHOOK_REF="v1.1.3"
NLOHMANN_URL="https://github.com/nlohmann/json.git"
NLOHMANN_REF="v3.11.3"
GLFW_URL="https://github.com/glfw/glfw.git"
GLFW_REF="3.4"
JDK_RAW="https://raw.githubusercontent.com/openjdk/jdk/jdk-21-ga/src/java.base"

note() { printf '[fetch_deps] %s\n' "$1"; }
fail() { printf '[fetch_deps] ERROR: %s\n' "$1" >&2; exit 1; }

# sync_git <name> <url> <ref: branch | tag | commit-sha>
sync_git() {
    name=$1
    url=$2
    ref=$3
    dir="$TP/$name"

    if [ -d "$dir/.git" ]; then
        note "$name: present — syncing to $ref"
        git -C "$dir" fetch --quiet --depth 1 origin "$ref" 2>/dev/null \
            || git -C "$dir" fetch --quiet origin \
                '+refs/heads/*:refs/remotes/origin/*' '+refs/tags/*:refs/tags/*'
        git -C "$dir" checkout --quiet --detach "$ref" 2>/dev/null \
            || git -C "$dir" checkout --quiet --detach FETCH_HEAD
    else
        note "$name: cloning $url @ $ref"
        rm -rf "$dir"
        # Shallow clone works for branches/tags; a full clone is the fallback
        # for raw commit SHAs (git rejects --branch with a SHA).
        git clone --quiet --depth 1 --branch "$ref" "$url" "$dir" 2>/dev/null || {
            git clone --quiet "$url" "$dir"
            git -C "$dir" checkout --quiet "$ref"
        }
    fi

    # funchook vendors distorm as a git submodule — required at build time
    if [ -f "$dir/.gitmodules" ]; then
        note "$name: initializing submodules"
        git -C "$dir" submodule update --init --quiet --depth 1
    fi
    note "$name: pinned at $(git -C "$dir" rev-parse --short HEAD)"
}

fetch_jni_headers() {
    inc="$TP/jdk-lite/include"
    mkdir -p "$inc/linux"
    note "jdk-lite: fetching JNI headers (OpenJDK jdk-21-ga)"
    curl -fsSL "$JDK_RAW/share/native/include/jni.h" -o "$inc/jni.h" \
        || fail "jni.h download failed"
    curl -fsSL "$JDK_RAW/unix/native/include/jni_md.h" -o "$inc/linux/jni_md.h" \
        || fail "jni_md.h download failed"
    [ "$(wc -c < "$inc/jni.h")" -gt 20000 ] || fail "jni.h looks truncated"
    note "jdk-lite: jni.h $(wc -c < "$inc/jni.h") bytes, jni_md.h $(wc -c < "$inc/linux/jni_md.h") bytes"
}

verify() {
    note "verifying layout"
    test -f "$TP/imgui/imgui.h"                  || fail "imgui incomplete"
    test -f "$TP/funchook/CMakeLists.txt"        || fail "funchook incomplete"
    test -d "$TP/funchook/distorm"               || fail "funchook distorm submodule missing"
    test -f "$TP/nlohmann/single_include/nlohmann/json.hpp" -o -f "$TP/nlohmann/json.hpp" \
                                                 || fail "nlohmann incomplete"
    test -f "$TP/glfw/CMakeLists.txt"            || fail "glfw incomplete"
    test -f "$TP/jdk-lite/include/jni.h"         || fail "jdk-lite incomplete"
    test -f "$TP/jdk-lite/include/linux/jni_md.h" || fail "jdk-lite incomplete"
    note "all dependencies ready."
}

sync_git imgui    "$IMGUI_URL"    "$IMGUI_REF"
sync_git funchook "$FUNCHOOK_URL" "$FUNCHOOK_REF"
sync_git nlohmann "$NLOHMANN_URL" "$NLOHMANN_REF"
sync_git glfw     "$GLFW_URL"     "$GLFW_REF"
fetch_jni_headers
verify

note "done — next: cmake --preset linux-debug && cmake --build --preset linux-debug"
