// ============================================================================
//  woke.wtf — src/ui/clickgui.h
//
//  The main macOS-style window: title bar with traffic lights, sidebar with
//  categories + search, and a scrolling card list (spec 2h). Owns the
//  AnimationController, the card data, the toast queue and the settings file,
//  so a single call site (the app shell) drives everything.
//
//  NOTE ON DEMO CONTENT: the cards below are plain UI/settings mock data
//  (interface toggles, HUD options) — deliberately NOT gameplay modules.
//  They exist to exercise PillToggle/ModuleCard rendering states and the
//  sidebar filter/search paths.
// ============================================================================

#pragma once

#include <imgui.h>

#include "ui/animation.h"
#include "ui/components.h"
#include "ui/notifications.h"
#include "ui/settings.h"

namespace woke::ui {

class ClickGui {
public:
    static constexpr int kMaxCards = 24;
    /// Sidebar entries: index 0 is the "All" pseudo-category, 1..N-1 are the
    /// real groups a ModuleCard can belong to.
    static constexpr int kMaxCats  = 5;

    ClickGui();

    /// One UI frame: advance animations, draw chrome + cards + toasts.
    void draw(float dt);

    /// Toggle visibility (window open/close animation retargets).
    void set_open(bool open) { open_ = open; }
    bool open() const { return open_; }

    /// Show a toast (delegates to the notification queue).
    void toast(const char *title, const char *message,
               notifications::Kind kind = notifications::Kind::kInfo);

    /// Pre-fill the search field (woketool's --search flag, config restore).
    void set_search(const char *text) { search_.set_text(text); }

    /// The "Reduced Motion" switch is not a mock setting: it drives the
    /// animation controller's global time scale. This is the same state a
    /// click on that card produces, exposed for the harness and config load.
    void set_reduced_motion(bool on) {
        cards_[kReducedMotionIndex].set_on(on);
    }
    bool reduced_motion() const {
        return cards_[kReducedMotionIndex].is_on();
    }

    /// "Config Autosave": when on, a change is written to disk immediately;
    /// when off it is still written, but only when the shell saves on exit.
    bool autosave_enabled() const { return cards_[kAutosaveIndex].is_on(); }

    // -- settings persistence ------------------------------------------------
    // The GUI is path-agnostic: the shell decides where settings live (and
    // whether to persist at all) and hands the path over.

    /// Point the GUI at a settings file. An empty path disables persistence.
    void set_config_path(const char *path);
    bool config_enabled() const { return config_path_[0] != '\0'; }
    const char *config_path() const { return config_path_; }

    /// Read the file and apply every recognised entry to the live UI. Unknown
    /// keys are kept so a later save round-trips them. A missing file is the
    /// first-run case: defaults stay, nothing is reported as an error.
    /// Returns the number of entries applied.
    int load_config();

    /// Refresh the known entries from the live UI and write the file.
    /// Returns false when no path is set or the write failed.
    bool save_config();

    /// True when the UI state has changed since the last successful save.
    bool config_dirty() const { return config_dirty_; }

    /// Entries held in memory (applied + unknown) — what a save will write.
    int config_entries() const { return config_.count(); }

    /// Apply one `key = value` edit from outside the UI (woketool's --set, and
    /// the same path the file loader uses). Marks the settings dirty so the
    /// change is written back. Returns false for an unrecognised key.
    bool set_setting(const char *key, const char *value);

    /// Per-frame read-out for the standalone harness: how many cards survived
    /// the filters, how far the card pane can scroll, and which animation
    /// scale is in force. Printed by woketool so filtering, scrolling and the
    /// Reduced Motion effect are all verifiable without a mouse.
    struct Diagnostics {
        int   visible_cards = 0;
        float scroll_max_y  = 0.0f;
        /// Active accessibility factor (1.0 = full motion).
        float motion_scale  = 1.0f;
        /// Effective duration of the window appear/close tween under that
        /// factor — the value the pixel-level A/B of animation timing reads.
        float window_appear_s = 0.0f;
        /// Switches currently on, across the whole (unfiltered) card list.
        int   enabled_cards = 0;
    };
    Diagnostics diagnostics() const { return diag_; }

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

    /// Category + search predicate. Both filters apply; an empty search and
    /// the "All" category are pass-throughs.
    bool card_visible(const ModuleCard &card) const;
    int  count_visible() const;

    /// "card.reduced_motion" — the settings-file key for a card, derived from
    /// its title so the file stays readable and index-independent.
    void card_key(int index, char *out, int cap) const;
    /// Apply one parsed entry; false when the key is not ours to own.
    bool apply_setting(const char *key, const char *value);
    /// Write the live UI state into the in-memory store (save path).
    void refresh_settings();

    AnimationController anim_;
    NotificationQueue   toasts_;

    SearchBar search_;
    CategoryItem categories_[kMaxCats] = {
        CategoryItem("All",        true),
        CategoryItem("General",    false),
        CategoryItem("Appearance", false),
        CategoryItem("HUD",        false),
        CategoryItem("System",     false),
    };

    /// Index of the accessibility switch inside cards_, which is the one card
    /// whose state is read by the UI itself. Keep in sync with the Appearance
    /// block of the initializer below.
    static constexpr int kReducedMotionIndex = 5;
    /// Same, for the autosave switch (see autosave_enabled()).
    static constexpr int kAutosaveIndex = 13;

    // Interface-settings demo cards (see header note). Parameters are
    // (title, description, keybind, sidebar group, factory default) — the
    // defaults matter because they are what a first run (no settings file)
    // starts from, so the UI opens in a usable state.
    ModuleCard cards_[kMaxCards] = {
        ModuleCard("Notifications",  "In-app toast notifications.",        "N", 1, true),
        ModuleCard("Watermark",      "Show the overlay watermark.",        "W", 1, true),
        ModuleCard("Toast Sounds",   "Play a chime for notifications.", nullptr, 1),
        ModuleCard("Compact Cards",  "Denser card list.",                  "C", 1),

        ModuleCard("Auto Layout",    "Remember card positions.",           "L", 2),
        ModuleCard("Reduced Motion", "Shorten every UI animation.",     nullptr, 2),
        ModuleCard("Card Shadows",   "Soft drop shadows on cards.",        "H", 2),
        ModuleCard("Accent Tint",    "Use the system accent colour.",      "T", 2),

        ModuleCard("FPS Counter",    "Frame-rate readout in the corner.",   "F", 3),
        ModuleCard("Coordinates",    "Show your position on the HUD.",      "P", 3),
        ModuleCard("Ping Meter",     "Round-trip latency readout.",         "G", 3),
        ModuleCard("Clock",          "Local time in the HUD strip.",      nullptr, 3),

        ModuleCard("Log Toasts",     "Mirror toasts into the log file.",    "J", 4),
        ModuleCard("Config Autosave","Write settings on every change.",     "A", 4, true),
        ModuleCard("Startup Check",  "Report missing mappings at boot.",    "S", 4),
        ModuleCard("Debug Overlay",  "Draw channel and frame timings.",    "D", 4),
    };
    int card_count_ = 16;

    Diagnostics   diag_;
    SettingsStore config_;
    /// Settings file path, or empty for "persistence disabled".
    char config_path_[256] = {};
    /// Set on any user-visible settings change; cleared by a successful save.
    bool config_dirty_     = false;
    int  active_category_  = 0;   // index into categories_
    /// Set when the category changes so the card pane jumps back to the top on
    /// the next frame (the scroll offset has to be applied inside the child).
    bool scroll_reset_    = false;
    bool open_            = true;
    bool close_requested_ = false;
};

} // namespace woke::ui
