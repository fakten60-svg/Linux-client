# ============================================================================
#  woke.wtf — CMake build configuration
#
#  Produces libwoke.so: a C++20 shared object injected into the Minecraft
#  1.21.11 / Fabric JVM process (x86_64 Linux, private-testing scope only).
#
#  Presets: linux-debug, linux-release  (see CMakePresets.json)
#
#  Before configuring, bootstrap vendored dependencies:
#      sh ./scripts/fetch_deps.sh
# ============================================================================
cmake_minimum_required(VERSION 3.27)

project(woke
    VERSION 0.1.0
    DESCRIPTION "woke.wtf — native injection utility client (private testing scope)"
    LANGUAGES C CXX)

find_package(OpenGL REQUIRED)   # woketool (standalone UI harness) links GL

# ---------------------------------------------------------------------------
# Global toolchain policy
# ---------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)   # everything links into a shared object
set(CMAKE_COLOR_DIAGNOSTICS ON)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# ---------------------------------------------------------------------------
# Options
# ---------------------------------------------------------------------------
option(WOKE_ENABLE_SANITIZERS "Build with ASan/UBSan (development sessions only)" OFF)
option(WOKE_ENABLE_LTO        "Enable link-time optimization for Release builds" ON)

# ---------------------------------------------------------------------------
# Compiler flags
# ---------------------------------------------------------------------------
# Deliberately language-neutral: the vendored C dependencies inherit these, and
# a C++-only option here emits one warning per C translation unit.
add_compile_options(
    -fvisibility=hidden             # only JNI_OnLoad / JNI_OnUnload are exported
    -fno-plt                        # cheaper cross-DSO calls; injection friendly
)

if(WOKE_ENABLE_SANITIZERS)
    add_compile_options(-fsanitize=address,undefined -fno-omit-frame-pointer)
    add_link_options(-fsanitize=address,undefined)
endif()

# ---------------------------------------------------------------------------
# Vendored dependency layout (populated by scripts/fetch_deps.sh)
# ---------------------------------------------------------------------------
set(WOKE_ROOT     ${CMAKE_CURRENT_SOURCE_DIR})
set(THIRD_PARTY   ${WOKE_ROOT}/third_party)
set(IMGUI_DIR     ${THIRD_PARTY}/imgui)
set(FUNCHOOK_DIR  ${THIRD_PARTY}/funchook)
set(NLOHMANN_DIR  ${THIRD_PARTY}/nlohmann)
set(GLFW_DIR      ${THIRD_PARTY}/glfw)
set(JNI_LITE_DIR  ${THIRD_PARTY}/jdk-lite)

foreach(dep_dir IN ITEMS ${IMGUI_DIR} ${FUNCHOOK_DIR} ${NLOHMANN_DIR} ${GLFW_DIR} ${JNI_LITE_DIR})
    if(NOT EXISTS ${dep_dir})
        message(FATAL_ERROR
            "Missing ${dep_dir}\n"
            "Bootstrap vendored dependencies first:  sh ./scripts/fetch_deps.sh")
    endif()
endforeach()

# --- Dear ImGui (core library; backends arrive with the hook layer) ---------
file(GLOB WOKE_IMGUI_SOURCES
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp)

add_library(imgui STATIC ${WOKE_IMGUI_SOURCES})
target_include_directories(imgui PUBLIC ${IMGUI_DIR})

# --- GLFW (window/input integration for the ImGui platform backend) ---------
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_DOCS     OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL        OFF CACHE BOOL "" FORCE)
# Minecraft ships LWJGL/GLFW on X11/XWayland — no Wayland backend needed,
# and this removes the wayland-scanner host dependency.
set(GLFW_BUILD_WAYLAND  OFF CACHE BOOL "" FORCE)
add_subdirectory(${GLFW_DIR} glfw EXCLUDE_FROM_ALL)

# --- ImGui platform backends for the standalone tool (not linked into the
# --- injection artifact; libwoke never renders itself) ----------------------
add_library(imgui_tool_backends STATIC
    ${IMGUI_DIR}/backends/imgui_impl_glfw.cpp
    ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp)
target_link_libraries(imgui_tool_backends PUBLIC imgui glfw)

# --- funchook (Linux function-hooking engine; MinHook-style shim planned) ---
set(FUNCHOOK_BUILD_SHARED  OFF CACHE BOOL "" FORCE)
set(FUNCHOOK_BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory(${FUNCHOOK_DIR} funchook EXCLUDE_FROM_ALL)

# --- nlohmann/json (settings & config persistence) --------------------------
set(JSON_BuildTests OFF CACHE BOOL "" FORCE)
add_subdirectory(${NLOHMANN_DIR} nlohmann EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# libwoke.so — the injectable client
# ---------------------------------------------------------------------------
add_library(woke SHARED
    src/entry.cpp
    src/core/logger.cpp
    src/core/thread_dispatch.cpp
    src/jvm/mappings.cpp
    src/jvm/base_jni_hook.cpp
    src/jvm/reflection_cache.cpp
    src/jvm/game_context.cpp
    src/hooks/hook_manager.cpp)

target_include_directories(woke PRIVATE
    ${WOKE_ROOT}/src
    ${JNI_LITE_DIR}/include
    ${JNI_LITE_DIR}/include/linux
    ${FUNCHOOK_DIR}/include)

target_compile_features(woke PRIVATE cxx_std_20)

# Warnings are our policy for OUR sources only — the vendored trees must not be
# able to fail a build that gates on warning-free output.
target_compile_options(woke PRIVATE
    -fno-rtti                       # no typeid/vtype_info (drives the event bus design)
    -fvisibility-inlines-hidden     # C++-only, so it cannot be a global option
    -Wall -Wextra
    -Wno-unused-parameter)          # JNI / hook signatures carry unused params

target_link_libraries(woke PRIVATE
    imgui
    glfw
    funchook-static
    nlohmann_json::nlohmann_json
    ${CMAKE_DL_LIBS}
    pthread)

# Every symbol must resolve at link time: JVM symbols are reached through
# dlsym(RTLD_DEFAULT) at runtime, so nothing may stay undefined here.
# The version script pins the exported surface to exactly the two JNI hooks,
# keeping libstdc++ template noise and all internals local to the object.
target_link_options(woke PRIVATE
    -Wl,--no-undefined
    -Wl,--version-script=${WOKE_ROOT}/src/woke.ld)

# ---------------------------------------------------------------------------
# woketool — standalone macOS-UI demo/verification app (no injection)
# ---------------------------------------------------------------------------
# Renders the same ui/ stack (theme, animation, components, clickgui,
# notifications) inside a normal GLFW desktop window. Serves as the visual
# test harness for Phase 5: --screenshot writes a PNG for review.
add_executable(woketool
    tools/woketool/main.cpp
    src/ui/animation.cpp
    src/ui/components.cpp
    src/ui/clickgui.cpp
    src/ui/notifications.cpp
    src/utils/render_utils.cpp
    src/core/logger.cpp)

target_include_directories(woketool PRIVATE
    ${WOKE_ROOT}/src
    ${WOKE_ROOT}/tools/woketool)

target_compile_features(woketool PRIVATE cxx_std_20)

target_compile_options(woketool PRIVATE
    -fno-rtti
    -fvisibility-inlines-hidden
    -Wall -Wextra -Wpedantic
    -Wno-unused-parameter)

target_link_libraries(woketool PRIVATE
    imgui
    imgui_tool_backends
    glfw
    OpenGL::GL
    ${CMAKE_DL_LIBS})

if(WOKE_ENABLE_LTO AND NOT WOKE_ENABLE_SANITIZERS)
    set_target_properties(woketool PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)
endif()

# LTO for release (skipped under sanitizers, where it is counterproductive)
if(WOKE_ENABLE_LTO AND NOT WOKE_ENABLE_SANITIZERS)
    include(CheckIPOSupported)
    check_ipo_supported(RESULT woke_ipo_ok OUTPUT woke_ipo_msg)
    if(woke_ipo_ok)
        set_target_properties(woke imgui PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)
    else()
        message(STATUS "LTO unavailable: ${woke_ipo_msg}")
    endif()
endif()

set_target_properties(woke PROPERTIES
    OUTPUT_NAME "woke"
    PREFIX      "lib"
    SUFFIX      ".so")
