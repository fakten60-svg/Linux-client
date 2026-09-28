// ============================================================================
//  woke.wtf — tools/woketool/main.cpp
//
//  Standalone desktop shell: GLFW + OpenGL 3.2 core + Dear ImGui rendering
//  the macOS-styled UI on a normal desktop — no injection, no game process.
//
//  Step 2f scope: backend init, font setup (DejaVu Sans, ProggyClean
//  fallback), and the "woke.wtf injected" proof-of-life line drawn through
//  the same text path the chrome uses.
//
//  Runtime flags:
//    --screenshot <path>  render --frames frames, save a PNG, exit 0
//    --frames <n>         frame count for screenshot mode (default 60)
//    --once               render one frame and exit (CI smoke test)
//    --search <text>      pre-fill the search field, so a filtered list can be
//                         verified headlessly
//    --reduced-motion     start with the "Reduced Motion" switch on
//    --closed-frames <n>  hold the ClickGUI closed for the first n frames, so
//                         the appear animation can be sampled mid-flight
//    --fixed-dt <sec>     use a constant frame delta; makes animation timing
//                         reproducible for headless A/B comparison
//    --click-motion-at <n>  synthesize a click on the "Reduced Motion" switch
//                         at frame n, exercising the real click -> state ->
//                         animation-scale path with no user present
//    --config <path>      load settings from <path> at startup and write them
//                         back on exit (also what $WOKE_CONFIG would set).
//                         Persistence is OFF without it: a screenshot tool
//                         must not silently rewrite the user's real settings,
//                         and a persisted file would change the outcome of
//                         the headless pixel checks run under this harness.
//    --set <key>=<value>  apply one setting after loading, in the same syntax
//                         the file uses (e.g. card.reduced_motion=1). Repeatable.
//    --park-mouse         keep the pointer off the UI. Without an X input
//                         source the cursor rests at the screen centre, which
//                         hovers whichever card sits there — harmless for a
//                         palette check, but it silently pollutes any diff
//                         between two screenshots.
// ============================================================================

#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <GLFW/glfw3.h>

#include <imgui.h>
#include <backends/imgui_impl_glfw.h>
#include <backends/imgui_impl_opengl3.h>

#include "core/logger.h"
#include "ui/clickgui.h"
#include "ui/theme.h"
#include "ui/notifications.h"
#include "png_writer.h"
#include "utils/render_utils.h"

// ---------------------------------------------------------------- screenshot
namespace {

bool write_screenshot_png(const char *path, int w, int h) {
    const size_t row = static_cast<size_t>(w) * 3;
    const size_t total = row * static_cast<size_t>(h);

    unsigned char *pix = new (std::nothrow) unsigned char[total];
    unsigned char *flip = new (std::nothrow) unsigned char[total];
    if (pix == nullptr || flip == nullptr) {
        delete[] pix;
        delete[] flip;
        return false;
    }

    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, pix);
    for (int y = 0; y < h; ++y) {
        std::memcpy(flip + static_cast<size_t>(y) * row,
                    pix + static_cast<size_t>(h - 1 - y) * row, row);
    }

    const bool ok = woke::png::write_rgb(path, w, h, flip);
    delete[] pix;
    delete[] flip;
    return ok;
}

} // namespace

// -------------------------------------------------------------------- main
int main(int argc, char **argv) {
    bool        mode_screenshot = false;
    bool        mode_once       = false;
    bool        park_mouse      = false;
    // Whether --reduced-motion was actually given: it overrides the loaded
    // setting, so an absent flag must not "override" it back to off.
    bool        reduced_motion  = false;
    const char *shot_path       = nullptr;
    const char *search_text     = nullptr;
    const char *config_path     = nullptr;
    const char *set_kv[16]      = {};
    int         set_kv_count    = 0;
    int         shot_frames     = 60;
    int         closed_frames   = 0;
    int         click_motion_at = -1;
    double      fixed_dt        = 0.0;

    for (int i = 1; i < argc; ++i) {
        if (std::strcmp(argv[i], "--screenshot") == 0 && i + 1 < argc) {
            mode_screenshot = true;
            shot_path = argv[++i];
        } else if (std::strcmp(argv[i], "--frames") == 0 && i + 1 < argc) {
            shot_frames = std::atoi(argv[++i]);
        } else if (std::strcmp(argv[i], "--search") == 0 && i + 1 < argc) {
            search_text = argv[++i];
        } else if (std::strcmp(argv[i], "--closed-frames") == 0 && i + 1 < argc) {
            closed_frames = std::atoi(argv[++i]);
        } else if (std::strcmp(argv[i], "--fixed-dt") == 0 && i + 1 < argc) {
            fixed_dt = std::atof(argv[++i]);
        } else if (std::strcmp(argv[i], "--click-motion-at") == 0 && i + 1 < argc) {
            click_motion_at = std::atoi(argv[++i]);
        } else if (std::strcmp(argv[i], "--config") == 0 && i + 1 < argc) {
            config_path = argv[++i];
        } else if (std::strcmp(argv[i], "--set") == 0 && i + 1 < argc) {
            if (set_kv_count < static_cast<int>(sizeof(set_kv) / sizeof(set_kv[0])))
                set_kv[set_kv_count++] = argv[++i];
        } else if (std::strcmp(argv[i], "--park-mouse") == 0) {
            park_mouse = true;
        } else if (std::strcmp(argv[i], "--reduced-motion") == 0) {
            reduced_motion = true;
        } else if (std::strcmp(argv[i], "--once") == 0) {
            mode_once = true;
        }
    }

    // Settings file: --config wins, then $WOKE_CONFIG. Nothing else — see the
    // flag docs at the top of this file for why a harness default is a bad idea.
    if (config_path == nullptr) {
        const char *env = std::getenv("WOKE_CONFIG");
        if (env != nullptr && env[0] != '\0') config_path = env;
    }

    woke::log::init();

    glfwSetErrorCallback([](int code, const char *desc) {
        std::fprintf(stderr, "glfw error %d: %s\n", code, desc);
    });
    if (!glfwInit()) {
        woke::log::error("woketool", "glfwInit failed");
        return 1;
    }
    // GL 3.2 core + forward compat: what the ImGui GL3 backend asks for on
    // macOS-like platforms; works identically on Mesa/Xvfb.
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
    glfwWindowHint(GLFW_SAMPLES, 4); // MSAA: smooths the custom-drawn circles

    GLFWwindow *win = glfwCreateWindow(960, 600, "woke.wtf — Utility Client",
                                       nullptr, nullptr);
    if (win == nullptr) {
        woke::log::error("woketool", "window creation failed (need GL 3.2 core)");
        glfwTerminate();
        return 1;
    }
    glfwMakeContextCurrent(win);
    glfwSwapInterval(1);

    // -- Dear ImGui context + backends --
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO &io = ImGui::GetIO();
    io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;

    // -- fonts: DejaVu Sans (present on Ubuntu/Debian) with fallbacks --
    {
        const char *candidates[] = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        };
        bool loaded = false;
        for (const char *path : candidates) {
            if (io.Fonts->AddFontFromFileTTF(path, 16.0f) != nullptr) {
                loaded = true;
                break;
            }
        }
        if (!loaded) io.Fonts->AddFontDefault(); // ProggyClean, always exists
    }

    const char *glsl_version = "#version 150";
    ImGui_ImplGlfw_InitForOpenGL(win, true);
    ImGui_ImplOpenGL3_Init(glsl_version);

    woke::ui::ClickGui gui;

    // Settings are loaded before the command-line flags so the flags act as
    // overrides of the stored configuration, not the other way round.
    int config_applied = 0;
    if (config_path != nullptr) {
        gui.set_config_path(config_path);
        config_applied = gui.load_config();
        std::printf("[woketool] config=%s entries=%d applied=%d\n", config_path,
                    gui.config_entries(), config_applied);
    } else {
        std::printf("[woketool] config=(disabled) entries=0 applied=0\n");
    }

    int set_applied = 0;
    for (int i = 0; i < set_kv_count; ++i) {
        // "key=value", split on the first '=' so values may contain more.
        const char *eq = std::strchr(set_kv[i], '=');
        if (eq == nullptr) {
            std::fprintf(stderr, "[woketool] --set expects key=value: %s\n",
                         set_kv[i]);
            continue;
        }
        char key[64];
        const size_t n = static_cast<size_t>(eq - set_kv[i]);
        const size_t copy = n < sizeof(key) - 1 ? n : sizeof(key) - 1;
        std::memcpy(key, set_kv[i], copy);
        key[copy] = '\0';
        if (gui.set_setting(key, eq + 1)) {
            ++set_applied;
        } else {
            std::fprintf(stderr, "[woketool] --set unknown key: %s\n", key);
        }
    }
    if (set_kv_count > 0)
        std::printf("[woketool] set_applied=%d of %d\n", set_applied,
                    set_kv_count);

    if (search_text != nullptr) gui.set_search(search_text);
    // Same state a click on the card produces — the switch drives the
    // animation controller's time scale (see ClickGui::draw). Only when the
    // flag is present: otherwise the value from the settings file stands.
    if (reduced_motion) gui.set_reduced_motion(true);
    gui.toast("woke.wtf", "UI online", woke::ui::notifications::Kind::kSuccess);

    double last = glfwGetTime();
    int    frames = 0;
    bool   done = false;
    while (!done && !glfwWindowShouldClose(win)) {
        glfwPollEvents();

        const double now = glfwGetTime();
        float dt = static_cast<float>(now - last);
        last = now;
        if (dt <= 0.0f || dt > 0.1f) dt = 1.0f / 60.0f;
        if (fixed_dt > 0.0) dt = static_cast<float>(fixed_dt);

        ImGui_ImplOpenGL3_NewFrame();
        ImGui_ImplGlfw_NewFrame();

        // Park the pointer outside the window (8,8 is in the app background,
        // left of the ImGui window at x=60) so no card renders its hover
        // state. Queued before the click below so a synthetic click still wins.
        if (park_mouse) io.AddMousePosEvent(8.0f, 8.0f);

        // Synthetic click on the "Reduced Motion" pill (the 6th card, right-
        // hand switch). The position is fed one frame early so ImGui sees a
        // settled mouse before the button goes down — it deliberately ignores a
        // click that arrives together with a big pointer jump. Events must be
        // queued after the platform backend's own, since the last one wins.
        if (click_motion_at >= 0 && frames >= click_motion_at &&
            frames <= click_motion_at + 1) {
            io.AddMousePosEvent(718.0f, 501.0f);
            io.AddMouseButtonEvent(0, frames == click_motion_at);
        }

        ImGui::NewFrame();

        // Drive the appear animation from a known frame, so a mid-flight
        // sample is at a predictable point in the transition.
        if (closed_frames > 0) gui.set_open(frames >= closed_frames);

        gui.draw(dt);

        // Autosave: the "Config Autosave" switch decides whether a change
        // hits the disk immediately or only on exit. Printed so the two paths
        // are distinguishable in a headless run (the exit save happens either
        // way, so without this line the switch would be unobservable).
        if (gui.config_dirty() && gui.autosave_enabled()) {
            const bool ok = gui.save_config();
            std::printf("[woketool] autosave=%s entries=%d\n",
                        ok ? "ok" : "failed", gui.config_entries());
        }

        // Proof of life (2f): same AddText pipeline the chrome uses.
        ImGui::GetForegroundDrawList()->AddText(
            ImVec2(20, 20), woke::theme::color::text_bright,
            "woke.wtf injected");

        ImGui::Render();
        int dw = 0, dh = 0;
        glfwGetFramebufferSize(win, &dw, &dh);
        glViewport(0, 0, dw, dh);
        glClearColor(0.043f, 0.055f, 0.078f, 1.0f); // #0B0E14
        glClear(GL_COLOR_BUFFER_BIT);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());

        // Capture BEFORE the swap: after glfwSwapBuffers the back buffer holds
        // the previous frame, which would silently offset every measurement by
        // one frame (harmless for settled shots, wrong for timed ones).
        ++frames;
        if (mode_screenshot && frames >= shot_frames) {
            int fb_w = 0, fb_h = 0;
            glfwGetFramebufferSize(win, &fb_w, &fb_h);
            const bool ok = write_screenshot_png(shot_path, fb_w, fb_h);
            // Machine-checkable evidence: the filters actually narrowed the
            // list, the scrolled pane reports a non-zero extent, and the
            // accessibility switch really shortened the animation timing.
            const auto d = gui.diagnostics();
            std::printf("[woketool] png=%s %dx%d visible_cards=%d "
                        "scroll_max_y=%.1f motion_scale=%.2f "
                        "window_appear=%.3fs enabled_cards=%d\n",
                        ok ? "ok" : "failed", fb_w, fb_h,
                        d.visible_cards, d.scroll_max_y,
                        d.motion_scale, d.window_appear_s, d.enabled_cards);
            done = true;
        }

        glfwSwapBuffers(win);
        if (mode_once && frames >= 1) done = true;
    }

    // Save-on-exit: covers the autosave-off case, and means "point the tool at
    // a file" always leaves that file holding the live state. Skipped entirely
    // when no path was given.
    if (gui.config_enabled()) {
        const bool ok = gui.save_config();
        std::printf("[woketool] config_save=%s path=%s entries=%d\n",
                    ok ? "ok" : "failed", gui.config_path(),
                    gui.config_entries());
    }

    // -- teardown in reverse init order --
    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplGlfw_Shutdown();
    ImGui::DestroyContext();
    glfwDestroyWindow(win);
    glfwTerminate();
    woke::log::shutdown();
    return 0;
}
