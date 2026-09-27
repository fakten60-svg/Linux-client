// ============================================================================
//  woke.wtf — src/ui/components.h
//
//  UI building blocks for the macOS-style chrome (spec 2g).
//
//  Design decisions:
//   * Components are values with explicit tick()/draw(), not an ImGui-widget
//     tree — the chrome is fully custom-drawn, so we own hit-testing too.
//     That gives exact macOS behavior (press states, hover falloff) instead
//     of stock-widget approximations.
//   * All components are fixed-size values (no heap, no std::string) and
//     reference only static string literals, honoring the zero-allocation
//     render-loop rule.
//   * Fresh UIs get channels via AnimationController::damp/tween keyed by
//     static names, so animation state survives across frames without any
//     per-component heap state.
// ============================================================================

#pragma once

#include <imgui.h>

namespace woke::ui {

class AnimationController;

/// Shared behavior: hover/press tracking + channel naming. Owns no state
/// beyond an id and its interaction flags; subclass draw()s read them.
class BaseUIComponent {
public:
    explicit BaseUIComponent(const char *id);
    virtual ~BaseUIComponent() = default;

    // Copyable on purpose: the ClickGUI declares its components as fixed
    // arrays initialized from temporaries at construction time. Copies never
    // occur inside the render loop, so the zero-allocation rule is intact.

    /// Feed one frame of hover/press state. Returns true when clicked.
    bool tick(ImVec2 min, ImVec2 max, bool enabled = true);

    bool hovered() const { return hovered_; }
    bool pressed() const { return pressed_; }

protected:
    const char *id_;      // static literal; also the animation channel prefix
    bool hovered_ = false;
    bool pressed_ = false;
};

/// Apple-style pill switch: animated knob, off = Dark Gray, on = cross-fade
/// to Apple Blue/Cyan gradient (NSSwitch, Big Sur+).
class PillToggle final : public BaseUIComponent {
public:
    struct State { bool on; };

    PillToggle() : BaseUIComponent("pill") {}
    PillToggle(const char *id, bool initial = false)
        : BaseUIComponent(id), state_on_(initial) {}

    /// Draws at rect (min,max); returns the (possibly new) state.
    State draw(AnimationController &anim, ImVec2 min, ImVec2 max, float dt,
               bool enabled = true);

    /// Programmatic state set (used by ModuleCard and config loading).
    void set_on(bool on) { state_on_ = on; }
    bool is_on() const { return state_on_; }

private:
    bool   state_on_ = false;
};

/// A single module entry: white Title, muted Description, Keybind badge,
/// chevron, and a PillToggle. 8px rounded Dark Slate card per spec.
class ModuleCard final : public BaseUIComponent {
public:
    /// Default ctor exists so fixed-size arrays can outlive their initial-
    /// izer list (spare slots draw nothing — see draw()'s guard).
    ModuleCard()
        : BaseUIComponent("card"), title_(nullptr), description_(nullptr),
          keybind_(nullptr), toggle_("card", false) {}

    ModuleCard(const char *title, const char *description, const char *keybind);

    /// Returns true when the card body was clicked (e.g. to expand).
    bool draw(AnimationController &anim, ImVec2 min, ImVec2 max, float dt);

    void set_on(bool on) { toggle_.set_on(on); }
    bool is_on() const { return toggle_.is_on(); }
    const char *title() const { return title_; }
    /// True when this slot was never initialized with content.
    bool empty() const { return title_ == nullptr; }

private:
    const char *title_;
    const char *description_;
    const char *keybind_;
    PillToggle  toggle_;
};

/// Sidebar entry with selection highlight (14% Apple Blue wash, macOS
/// selection semantics) and an animated left accent bar.
class CategoryItem final : public BaseUIComponent {
public:
    CategoryItem(const char *label, bool selected);

    void set_selected(bool s) { selected_ = s; }
    bool selected() const { return selected_; }
    const char *label() const { return label_; }

    /// Returns true on click (caller switches the active category).
    bool draw(AnimationController &anim, ImVec2 min, ImVec2 max, float dt);

private:
    const char *label_;
    bool        selected_;
};

/// Search field with focus ring (Apple blue when focused), placeholder text,
/// and a fixed 96-char input buffer — no dynamic allocation.
class SearchBar final : public BaseUIComponent {
public:
    static constexpr int kBufferLen = 96;

    SearchBar();

    /// Draws and returns true when the text changed this frame.
    bool draw(AnimationController &anim, ImVec2 min, ImVec2 max, float dt);

    const char *text() const { return buf_; }

private:
    char buf_[kBufferLen] = {};
    bool focused_ = false;
};

} // namespace woke::ui
