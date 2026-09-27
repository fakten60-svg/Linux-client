// ============================================================================
//  woke.wtf — src/jvm/game_context.h
//
//  Cached access to the core game instance handles:
//    * MinecraftClient (class_310)   — the client singleton class
//    * its `player`   (field_1724)   — ClientPlayerEntity instance
//    * its `world`    (field_1687)   — ClientWorld instance
//
//  Read-only, explicit, game-state-only access. All handle resolution goes
//  through ReflectionCache + mappings.json. Refreshes happen from the game
//  thread path (thread_dispatch arrives in a later phase); init performs one
//  static resolution so a live JVM proves the chain end to end.
// ============================================================================

#pragma once

#include <jni.h>

namespace woke::jvm {

class GameContext {
public:
    /// Resolve MinecraftClient (class_310) + its static accessors from the
    /// mappings table. Safe on a stub/partial classpath: missing pieces are
    /// logged and the context reports reduced capability instead of failing
    /// the host.
    static bool init(JNIEnv *env);

    /// Drop cached instance references (class ids stay in ReflectionCache).
    static void shutdown();

    /// True when the MinecraftClient class itself resolved.
    static bool ready();

    /// Re-read instance fields (player/world) into cached global refs.
    /// Call from the game thread; harmless (nulls) outside a running game.
    static void refresh(JNIEnv *env);

    // --- cached handles (null before refresh / outside a running game) -----
    static jobject client_instance();  // MinecraftClient.getInstance()
    static jobject player_instance();  // client.player  (field_1724)
    static jobject world_instance();   // client.world   (field_1687)

    /// Curated key constants — single source of truth for callers.
    static constexpr const char *kClientKey   = "minecraft_client";
    static constexpr const char *kPlayerKey   = "client_player_entity";
    static constexpr const char *kWorldKey    = "client_world";
};

} // namespace woke::jvm
