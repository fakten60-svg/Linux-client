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
    const char *shot_path       = nullptr;
    const char *search_text     = nullptr;
    int         shot_frames     = 60;

    for (int i = 1; i < argc; ++i) {
        if (std::strcmp(argv[i], "--screenshot") == 0 && i + 1 < argc) {
            mode_screenshot = true;
            shot_path = argv[++i];
        } else if (std::strcmp(argv[i], "--frames") == 0 && i + 1 < argc) {
            shot_frames = std::atoi(argv[++i]);
        } else if (std::strcmp(argv[i], "--search") == 0 && i + 1 < argc) {
            search_text = argv[++i];
        } else if (std::strcmp(argv[i], "--once") == 0) {
            mode_once = true;
        }
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
    if (search_text != nullptr) gui.set_search(search_text);
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

        ImGui_ImplOpenGL3_NewFrame();
        ImGui_ImplGlfw_NewFrame();
        ImGui::NewFrame();

        gui.draw(dt);
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
        glfwSwapBuffers(win);

        ++frames;
        if (mode_once && frames >= 1) done = true;
        if (mode_screenshot && frames >= shot_frames) {
            int fb_w = 0, fb_h = 0;
            glfwGetFramebufferSize(win, &fb_w, &fb_h);
            const bool ok = write_screenshot_png(shot_path, fb_w, fb_h);
            // Machine-checkable evidence: the filters actually narrowed the
            // list, and the scrolled pane reports a non-zero extent.
            const auto d = gui.diagnostics();
            std::printf("[woketool] png=%s %dx%d visible_cards=%d "
                        "scroll_max_y=%.1f\n",
                        ok ? "ok" : "failed", fb_w, fb_h,
                        d.visible_cards, d.scroll_max_y);
            done = true;
        }
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
