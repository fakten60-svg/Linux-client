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
//    --verify             assert on the frame that was just rendered and exit
//                         non-zero if a check fails: every flat theme surface
//                         must actually be on screen, the filters must really
//                         narrow the list, and the Reduced Motion switch must
//                         scale the timing it promises to. This is what turns
//                         "a human looked at the PNG" into something CI can
//                         gate on. Implies a capture, with or without
//                         --screenshot; run it on settled frames (the default
//                         --frames 60 is well past the appear tween).
// ============================================================================

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

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

/// One grabbed frame, top-down RGB8, held in memory so the PNG a human reviews
/// and the checks the tool asserts on are reading the *same* pixels. A check
/// that re-read the framebuffer could disagree with the saved image.
struct Frame {
    int                        w = 0;
    int                        h = 0;
    std::vector<unsigned char> rgb;
};

bool grab_frame(int w, int h, Frame &out) {
    if (w <= 0 || h <= 0) return false;

    const size_t row   = static_cast<size_t>(w) * 3;
    const size_t total = row * static_cast<size_t>(h);

    out = Frame{};
    out.w = w;
    out.h = h;
    out.rgb.resize(total);

    std::vector<unsigned char> bottom_up(total);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, bottom_up.data());

    // GL hands back bottom-up rows; everything downstream wants top-down.
    for (int y = 0; y < h; ++y) {
        std::memcpy(out.rgb.data() + static_cast<size_t>(y) * row,
                    bottom_up.data() + static_cast<size_t>(h - 1 - y) * row,
                    row);
    }
    return true;
}

// ------------------------------------------------------- rendered-frame checks
//
// The assertions below are deliberately geometric rather than coordinate-exact.
// Counting how many pixels carry a theme color is robust to the layout moving
// (a wider sidebar, one card fewer) while still failing the moment a surface
// stops being drawn — sampling one hardcoded pixel would do the opposite on
// both counts. Antialiasing only touches a surface's edge, so the interior of
// every flat fill matches its token exactly.
//
// Colors are compared against the theme tokens, not against literal hex: the
// tokens are the single source of truth, so a deliberate repaint keeps passing
// and only a *missing* surface fails. The spec values themselves are pinned by
// theme.h and reviewed there.

/// A theme surface plus the pixel count below which it counts as absent.
/// Floors sit well under the measured counts (see the printed `count=` on each
/// line) so ordinary layout churn cannot flake the check.
///
/// `per_card` exists because type scales with how much is on screen: a title
/// covers a handful of pixels, so a fixed floor would either be useless for one
/// card or force every filtered run to fail. The requirement is therefore
/// `base + per_card * visible_cards`.
///
/// `tolerance` exists because flat fills are exact to the byte while
/// antialiased type is almost never exactly its own color — every glyph edge is
/// a blend — so text is matched with a small allowance instead.
struct Surface {
    const char *name;
    ImU32       color;
    size_t      base_pixels;
    size_t      per_card;
    int         tolerance;
};

size_t count_color(const Frame &f, ImU32 packed, int tolerance) {
    // Theme colors are packed 0xAABBGGRR (IM_COL32); the framebuffer is RGB8.
    const int r = static_cast<int>(packed & 0xFFu);
    const int g = static_cast<int>((packed >> 8) & 0xFFu);
    const int b = static_cast<int>((packed >> 16) & 0xFFu);

    const auto near = [tolerance](unsigned char got, int want) {
        const int delta = static_cast<int>(got) - want;
        return delta <= tolerance && delta >= -tolerance;
    };

    size_t n = 0;
    for (size_t i = 0; i + 2 < f.rgb.size(); i += 3) {
        if (near(f.rgb[i], r) && near(f.rgb[i + 1], g) && near(f.rgb[i + 2], b))
            ++n;
    }
    return n;
}

struct Verify {
    int checks = 0;
    int failed = 0;

    void check(bool ok, const char *name, const char *detail) {
        ++checks;
        if (!ok) ++failed;
        std::printf("[woketool] check=%s %-15s %s\n", ok ? "ok  " : "FAIL",
                    name, detail);
    }
};

/// Returns the number of failed checks, which is also the process exit code.
int verify_frame(const Frame &f, const woke::ui::ClickGui::Diagnostics &d,
                 bool reduced_motion, int card_total, bool filtered) {
    Verify v;

    const int kText = 6; // antialiasing allowance, per channel
    const Surface surfaces[] = {
        {"window_bg",      woke::theme::color::window_bg,      100000, 0,    0},
        {"sidebar_bg",     woke::theme::color::sidebar_bg,      20000, 0,    0},
        {"card_bg",        woke::theme::color::card_bg,         10000, 0,    0},
        {"light_red",      woke::theme::color::light_red,          20, 0,    0},
        {"light_yellow",   woke::theme::color::light_yellow,       20, 0,    0},
        {"light_green",    woke::theme::color::light_green,        20, 0,    0},
        {"pill_off",       woke::theme::color::pill_off,          200, 0,    0},
        {"apple_blue",     woke::theme::color::apple_blue,         30, 0,    0},
        // Type has far fewer exact pixels than a flat fill — a glyph stem is
        // one or two pixels wide — so these floors are small on purpose: a
        // count this size cannot come from anything but the text itself.
        {"text_bright",    woke::theme::color::text_bright,          0, 3, kText},
        {"title_bar_text", woke::theme::color::title_bar_text,     100, 8, kText},
    };

    for (const Surface &s : surfaces) {
        const size_t n = count_color(f, s.color, s.tolerance);
        const size_t min_pixels =
            s.base_pixels + s.per_card * static_cast<size_t>(d.visible_cards);
        char detail[96];
        std::snprintf(detail, sizeof(detail), "count=%zu min=%zu", n, min_pixels);
        v.check(n >= min_pixels, s.name, detail);
    }

    // -- filter semantics: a filtered list must actually be shorter, and an
    // -- unfiltered one must be complete. Without both halves a filter that
    // -- silently stopped working would still look fine in a screenshot.
    char detail[128];
    if (filtered) {
        std::snprintf(detail, sizeof(detail), "visible=%d total=%d",
                      d.visible_cards, card_total);
        v.check(d.visible_cards >= 1 && d.visible_cards < card_total,
                "filter_narrowed", detail);
    } else {
        std::snprintf(detail, sizeof(detail), "visible=%d total=%d",
                      d.visible_cards, card_total);
        v.check(d.visible_cards == card_total, "all_cards_shown", detail);
    }

    // -- scrolling: with every card shown the list has to overflow its pane,
    // -- which is the entire reason the pane scrolls. A filtered list is
    // -- legitimately short, so there is nothing to assert in that case.
    std::snprintf(detail, sizeof(detail), "scroll_max_y=%.1f",
                  static_cast<double>(d.scroll_max_y));
    if (filtered) {
        std::printf("[woketool] note   pane_overflows  skipped: filtered list may "
                    "legitimately fit (%s)\n", detail);
    } else {
        v.check(d.scroll_max_y > 0.0f, "pane_overflows", detail);
    }

    // -- accessibility invariant: the switch is what sets the motion scale,
    // -- and everything timed has to obey it. Asserting the relationship
    // -- rather than "0.35 was passed in" is what makes this a real check.
    const float expected = reduced_motion ? woke::theme::time::reduced_motion_scale
                                          : 1.0f;
    std::snprintf(detail, sizeof(detail), "motion_scale=%.3f expected=%.3f",
                  static_cast<double>(d.motion_scale), static_cast<double>(expected));
    v.check(d.motion_scale > expected - 1e-3f && d.motion_scale < expected + 1e-3f,
            "motion_scale", detail);

    const float scaled = expected * woke::theme::time::window_appear;
    std::snprintf(detail, sizeof(detail), "window_appear=%.4fs expected=%.4fs",
                  static_cast<double>(d.window_appear_s), static_cast<double>(scaled));
    v.check(d.window_appear_s > scaled - 1e-4f && d.window_appear_s < scaled + 1e-4f,
            "appear_scaled", detail);

    std::printf("[woketool] verify checks=%d failed=%d\n", v.checks, v.failed);
    return v.failed;
}

} // namespace

// -------------------------------------------------------------------- main
int main(int argc, char **argv) {
    bool        mode_screenshot = false;
    bool        mode_verify     = false;
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
        } else if (std::strcmp(argv[i], "--verify") == 0) {
            mode_verify = true;
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

    double last      = glfwGetTime();
    int    frames    = 0;
    bool   done      = false;
    int    exit_code = 0;   // only --verify can fail the run
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
        if ((mode_screenshot || mode_verify) && frames >= shot_frames) {
            int fb_w = 0, fb_h = 0;
            glfwGetFramebufferSize(win, &fb_w, &fb_h);

            Frame frame;
            const bool grabbed = grab_frame(fb_w, fb_h, frame);
            const bool png_ok  = !mode_screenshot ||
                (grabbed && woke::png::write_rgb(shot_path, frame.w, frame.h,
                                                 frame.rgb.data()));

            // Machine-checkable evidence: the filters actually narrowed the
            // list, the scrolled pane reports a non-zero extent, and the
            // accessibility switch really shortened the animation timing.
            const auto d = gui.diagnostics();
            const char *head = mode_screenshot ? "png" : "verify";
            const char *state = mode_screenshot ? (png_ok ? "ok" : "failed")
                                                : "frame";
            std::printf("[woketool] %s=%s %dx%d visible_cards=%d "
                        "scroll_max_y=%.1f motion_scale=%.2f "
                        "window_appear=%.3fs enabled_cards=%d\n",
                        head, state, fb_w, fb_h,
                        d.visible_cards, d.scroll_max_y,
                        d.motion_scale, d.window_appear_s, d.enabled_cards);

            if (mode_verify) {
                const int failures =
                    grabbed ? verify_frame(frame, d, gui.reduced_motion(),
                                           gui.card_total(), search_text != nullptr)
                            : 1;
                if (!grabbed)
                    std::printf("[woketool] check=FAIL %-15s framebuffer grab "
                                "failed\n", "frame_grab");
                if (failures > 0) exit_code = 1;
            }
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
    return exit_code;
}
