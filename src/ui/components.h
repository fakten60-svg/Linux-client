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
//   * The animation channel key is built ONCE at construction ("mc.Watermark",
//     "pt.Watermark", ...) and stored in a fixed char buffer. Components are
//     constructed outside the render loop, so draw() never formats a string.
// ============================================================================

#pragma once

#include <cstddef>

#include <imgui.h>

namespace woke::ui {

class AnimationController;

/// Shared behavior: hover/press tracking + channel naming. Owns no state
/// beyond its channel key and its interaction flags; subclass draw()s read them.
class BaseUIComponent {
public:
    /// `prefix` namespaces the channel ("mc" card, "pt" pill, "ci" category,
    /// "sb" search) and `id` identifies the instance within it. The two are
    /// joined into key() at construction; both are static literals.
    BaseUIComponent(const char *prefix, const char *id);
    virtual ~BaseUIComponent() = default;

    // Copyable on purpose: the ClickGUI declares its components as fixed
    // arrays initialized from temporaries at construction time. Copies never
    // occur inside the render loop, so the zero-allocation rule is intact.

    /// Feed one frame of hover/press state. Returns true when clicked.
    bool tick(ImVec2 min, ImVec2 max, bool enabled = true);

    bool hovered() const { return hovered_; }
    bool pressed() const { return pressed_; }

    /// AnimationController channel for this component ("pt.Watermark").
    const char *key() const { return key_; }

protected:
    char key_[40] = {};
    bool hovered_ = false;
    bool pressed_ = false;
};

/// What a card click resolved to. The row and the pill share a rect, so the
/// component reports which one the pointer hit instead of one ambiguous bool.
enum class CardEvent : unsigned char {
    kNone,
    kBodyClicked,    ///< open/detail affordance
    kToggleChanged,  ///< the pill switch flipped
};

/// Apple-style pill switch: animated knob, off = Dark Gray, on = cross-fade
/// to Apple Blue/Cyan gradient (NSSwitch, Big Sur+).
class PillToggle final : public BaseUIComponent {
public:
    struct State { bool on; };

    PillToggle() : BaseUIComponent("pt", "pill") {}
    PillToggle(const char *id, bool initial = false)
        : BaseUIComponent("pt", id), state_on_(initial) {}

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
        : BaseUIComponent("mc", "card"), title_(nullptr),
          description_(nullptr), keybind_(nullptr), category_(0),
          toggle_("card", false) {}

    /// `category` is the 1-based sidebar group this card belongs to (0 = unset,
    /// matched only by the "All" view). `initial_on` is the factory default
    /// before any settings file is loaded, so a fresh install has a usable
    /// configuration instead of everything off.
    ModuleCard(const char *title, const char *description, const char *keybind,
               int category = 0, bool initial_on = false);

    /// Draws the card and reports which control the pointer hit. The card row
    /// and its pill share a rect, so the event is disambiguated here.
    CardEvent draw(AnimationController &anim, ImVec2 min, ImVec2 max, float dt);

    void set_on(bool on) { toggle_.set_on(on); }
    bool is_on() const { return toggle_.is_on(); }
    const char *title() const { return title_; }
    const char *description() const { return description_; }
    int category() const { return category_; }
    /// True when this slot was never initialized with content.
    bool empty() const { return title_ == nullptr; }

private:
    const char *title_;
    const char *description_;
    const char *keybind_;
    int         category_;
    PillToggle  toggle_;
};

/// Sidebar entry with selection highlight (14% Apple Blue wash, macOS
/// selection semantics) and an animated left accent bar.
class CategoryItem final : public BaseUIComponent {
public:
    CategoryItem(const char *label, bool selected)
        : BaseUIComponent("ci", label), label_(label), selected_(selected) {}

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
    bool empty() const { return buf_[0] == '\0'; }

    /// Pre-fill the field (woketool's --search flag, config restore).
    void set_text(const char *text);

private:
    char buf_[kBufferLen] = {};
    bool focused_ = false;
};

} // namespace woke::ui
