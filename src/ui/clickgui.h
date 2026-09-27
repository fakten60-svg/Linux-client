// ============================================================================
//  woke.wtf — src/ui/clickgui.h
//
//  The main macOS-style window: title bar with traffic lights, sidebar with
//  categories + search, and a scrolling card list (spec 2h). Owns the
//  AnimationController, the demo cards, and the toast queue so a single
//  call site (the app shell) drives everything.
//
//  NOTE ON DEMO CONTENT: the cards below are plain UI/settings mock data
//  (interface toggles, HUD options) — deliberately NOT gameplay modules.
//  They exist to exercise PillToggle/ModuleCard rendering states only.
// ============================================================================

#pragma once

#include <imgui.h>

#include "ui/animation.h"
#include "ui/components.h"
#include "ui/notifications.h"

namespace woke::ui {

class ClickGui {
public:
    static constexpr int kMaxCards  = 24;
    static constexpr int kMaxCats   = 4;

    ClickGui();

    /// One UI frame: advance animations, draw chrome + cards + toasts.
    void draw(float dt);

    /// Toggle visibility (window open/close animation retargets).
    void set_open(bool open) { open_ = open; }
    bool open() const { return open_; }

    /// Show a toast (delegates to the notification queue).
    void toast(const char *title, const char *message,
               notifications::Kind kind = notifications::Kind::kInfo);

    /// Escape closes the GUI — macOS sheet semantics.
    bool consume_close_request() {
        const bool r = close_requested_;
        close_requested_ = false;
        return r;
    }

private:
    void draw_title_bar(ImVec2 win_min, ImVec2 win_max, float dt);
    void draw_sidebar(ImVec2 win_min, ImVec2 win_max, float dt);
    void draw_cards(ImVec2 win_min, ImVec2 win_max, float dt);

    AnimationController anim_;
    NotificationQueue   toasts_;

    SearchBar search_;
    CategoryItem categories_[kMaxCats] = {
        CategoryItem("General",     true),
        CategoryItem("Appearance",  false),
        CategoryItem("HUD",         false),
        CategoryItem("System",      false),
    };

    // Interface-settings demo cards (see header note).
    ModuleCard cards_[kMaxCards] = {
        ModuleCard("Notifications", "In-app toast notifications.",       "N"),
        ModuleCard("Watermark",     "Show the overlay watermark.",       "W"),
        ModuleCard("FPS Counter",   "Frame-rate readout in the corner.", "F"),
        ModuleCard("Toast Sounds",  "Play a chime for notifications.",   "S"),
        ModuleCard("Auto Layout",   "Remember card positions.",          "L"),
        ModuleCard("Compact Cards", "Denser card list.",                 "C"),
        ModuleCard("Show Keybinds", "Keybind badges on every card.",     "K"),
        ModuleCard("Reduced Motion","Ease animations for accessibility.","M"),
    };
    int card_count_ = 8;

    const char *active_category_ = "General";
    bool open_            = true;
    bool close_requested_ = false;
};

} // namespace woke::ui
