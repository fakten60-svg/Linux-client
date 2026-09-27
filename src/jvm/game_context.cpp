// ============================================================================
//  woke.wtf — src/jvm/game_context.cpp
//  See game_context.h for the design contract.
// ============================================================================

#include "jvm/game_context.h"

#include "core/logger.h"
#include "jvm/base_jni_hook.h"
#include "jvm/reflection_cache.h"

namespace woke::jvm {
namespace {

constexpr const char *kTag = "game";

jobject g_client = nullptr;  // global ref to the MinecraftClient instance
jobject g_player = nullptr;  // global ref to client.player
jobject g_world  = nullptr;  // global ref to client.world
bool    g_ready  = false;

void drop_ref(JNIEnv *env, jobject *ref) {
    if (*ref != nullptr && env != nullptr) {
        env->DeleteGlobalRef(*ref);
    }
    *ref = nullptr;
}

} // namespace

bool GameContext::init(JNIEnv *env) {
    if (env == nullptr) {
        return false;
    }
    if (g_ready) {
        return true;
    }

    // 1) The client class itself — populates the ReflectionCache for the
    //    primary curated key and proves intermediary resolution end to end.
    jclass mc = ReflectionCache::cls(kClientKey);
    if (mc == nullptr) {
        log::warn(kTag, "MinecraftClient (%s) not present — stub/partial classpath?",
                  kClientKey);
        return false;
    }

    // 2) The static accessor getInstance() — canonical 1.21.11 chain:
    //    class_310.method_1551()Lnet/minecraft/class_310;
    const jmethodID get_instance =
        ReflectionCache::method(kClientKey, "getInstance");
    if (get_instance == nullptr) {
        log::warn(kTag, "getInstance() unresolved — class chain incomplete");
    }

    // 3) Instance field ids (resolved once; values fetched on refresh()).
    ReflectionCache::field(kClientKey, "player");
    ReflectionCache::field(kClientKey, "world");

    g_ready = true;
    log::info(kTag, "MinecraftClient context resolved (%s ready)",
              kClientKey);
    return true;
}

void GameContext::shutdown() {
    JNIEnv *env = BaseJNIHook::env();
    drop_ref(env, &g_client);
    drop_ref(env, &g_player);
    drop_ref(env, &g_world);
    g_ready = false;
}

bool GameContext::ready() { return g_ready; }

void GameContext::refresh(JNIEnv *env) {
    if (env == nullptr || !g_ready) {
        return;
    }

    // Static instance: MinecraftClient.getInstance() — call form must match
    // how the id was resolved (HotSpot quirk; see BaseJNIHook::method_id).
    const jmethodID get_instance = ReflectionCache::method(kClientKey, "getInstance");
    if (get_instance != nullptr) {
        jobject local = nullptr;
        if (ReflectionCache::method_is_static(kClientKey, "getInstance")) {
            local = env->CallStaticObjectMethod(ReflectionCache::cls(kClientKey),
                                                get_instance);
        } else {
            local = env->CallObjectMethod(ReflectionCache::cls(kClientKey),
                                          get_instance);
        }
        if (BaseJNIHook::exception_ok(env, "getInstance()")) {
            jobject global = BaseJNIHook::new_global_ref(env, local);
            if (local != nullptr) {
                env->DeleteLocalRef(local);
            }
            if (global != g_client) {
                drop_ref(env, &g_client);
                g_client = global;
            } else if (global != nullptr) {
                env->DeleteGlobalRef(global);
            }
        }
    }

    // Instance fields: player (field_1724), world (field_1687)
    const jfieldID player_fid = ReflectionCache::field(kClientKey, "player");
    if (player_fid != nullptr && g_client != nullptr) {
        jobject local = env->GetObjectField(g_client, player_fid);
        jobject global = BaseJNIHook::new_global_ref(env, local);
        if (local != nullptr) {
            env->DeleteLocalRef(local);
        }
        if (global != g_player) {
            drop_ref(env, &g_player);
            g_player = global;
        } else if (global != nullptr) {
            env->DeleteGlobalRef(global);
        }
    }

    const jfieldID world_fid = ReflectionCache::field(kClientKey, "world");
    if (world_fid != nullptr && g_client != nullptr) {
        jobject local = env->GetObjectField(g_client, world_fid);
        jobject global = BaseJNIHook::new_global_ref(env, local);
        if (local != nullptr) {
            env->DeleteLocalRef(local);
        }
        if (global != g_world) {
            drop_ref(env, &g_world);
            g_world = global;
        } else if (global != nullptr) {
            env->DeleteGlobalRef(global);
        }
    }
}

jobject GameContext::client_instance() { return g_client; }
jobject GameContext::player_instance() { return g_player; }
jobject GameContext::world_instance() { return g_world; }

} // namespace woke::jvm
