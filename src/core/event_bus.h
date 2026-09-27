// ============================================================================
//  woke.wtf — src/core/event_bus.h
//
//  Centralized, decoupled, type-safe publish/subscribe dispatch.
//
//    * Type safety: subscribe/publish are templated on the event struct —
//      no base class, no RTTI (build uses -fno-rtti).
//    * Zero allocation on publish: handlers are plain function pointers in
//      intrusive singly-linked lists with function-local static heads.
//      `new` happens only in subscribe() (init/config time, capped per type).
//    * Concurrency contract: subscribe()/unsubscribe() must happen before
//      steady-state publishing (module init, GUI construction). publish()
//      is lock-free and callable from any thread; handlers run on the
//      publishing thread.
//
//  Header-only: the templates are tiny and used across every subsystem.
// ============================================================================

#pragma once

#include "core/logger.h"

#include <atomic>
#include <cstddef>

namespace woke::event_bus {

using HandlerId = unsigned;

namespace detail {

inline std::atomic<HandlerId> g_next_id{1};

inline HandlerId next_id() { return g_next_id.fetch_add(1); }

constexpr std::size_t kMaxListenersPerEvent = 64;

} // namespace detail

template <typename E>
struct Subscription {
    using Handler = void (*)(const E &, void *);
    Handler handler;
    void *user;
    HandlerId id;
    Subscription *next;
};

/// Per-event registry head (function-local static: thread-safe, no heap).
template <typename E>
Subscription<E> **head() {
    static Subscription<E> *h = nullptr;
    return &h;
}

template <typename E>
std::size_t listener_count() {
    std::size_t n = 0;
    for (auto *s = *head<E>(); s != nullptr; s = s->next) {
        ++n;
    }
    return n;
}

/// Register a handler. Returns a non-zero id on success, 0 when the per-event
/// cap would be exceeded (logged; publish loop is never allowed to grow).
template <typename E>
HandlerId subscribe(void (*handler)(const E &, void *), void *user = nullptr) {
    if (listener_count<E>() >= detail::kMaxListenersPerEvent) {
        log::error("events", "listener cap reached — subscription rejected");
        return 0;
    }
    auto *node = new Subscription<E>{handler, user, detail::next_id(), *head<E>()};
    *head<E>() = node;
    return node->id;
}

/// Remove a handler by id. Returns true when found and removed.
template <typename E>
bool unsubscribe(HandlerId id) {
    Subscription<E> **link = head<E>();
    while (*link != nullptr) {
        if ((*link)->id == id) {
            Subscription<E> *dead = *link;
            *link = dead->next;
            delete dead;
            return true;
        }
        link = &(*link)->next;
    }
    return false;
}

/// Dispatch to all listeners. Lock-free, allocation-free, O(listeners).
/// Handlers run synchronously on the calling thread.
template <typename E>
void publish(const E &event) {
    for (Subscription<E> *s = *head<E>(); s != nullptr; s = s->next) {
        s->handler(event, s->user);
    }
}

// ---------------------------------------------------------------------------
// Core lifecycle events (fired by entry/thread_dispatch; consumed anywhere)
// ---------------------------------------------------------------------------

struct ClientStartedEvent {
    const char *message;   // static string — no ownership
};

struct ClientShutdownEvent {
    const char *reason;
};

} // namespace woke::event_bus
