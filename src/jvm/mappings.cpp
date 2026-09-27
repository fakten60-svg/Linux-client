// ============================================================================
//  woke.wtf — src/jvm/mappings.cpp
//  See mappings.h for the design contract.
// ============================================================================

#include "jvm/mappings.h"

#include "core/logger.h"

#include <nlohmann/json.hpp>

#include <cstdio>
#include <unordered_map>
#include <string>
#include <utility>
#include <vector>

namespace woke::jvm::mappings {
namespace {

using nlohmann::json;

constexpr const char *kTag = "mappings";

struct ClassEntry {
    const char *intermediary = nullptr;
    std::unordered_map<std::string, MemberInfo> fields;
    std::unordered_map<std::string, MemberInfo> methods;
};

/// Everything the loader owns, behind a function-local static.
///
/// This is deliberately NOT a set of namespace-scope globals: their dynamic
/// initialisers run in `.init_array` order, and the constructor-attribute
/// bootstrap in entry.cpp runs before other translation units' initialisers.
/// Touching a still-zeroed std::unordered_map from there is undefined
/// behaviour (observed: SIGFPE, bucket_count 0). Lazy construction removes
/// the ordering dependency entirely.
struct Store {
    std::vector<std::string> strings;  // interned storage; every const char* points in here
    std::unordered_map<std::string, ClassEntry> classes;
    bool loaded = false;
};

Store &store() {
    static Store s;
    return s;
}

const char *intern(const std::string &s) {
    store().strings.push_back(s);
    return store().strings.back().c_str();
}

// --- schema adapters --------------------------------------------------------
// Curated format (primary):
//   { "classes": { "<key>": { "intermediary": …,
//       "fields": { "<name>": { "intermediary": …, "descriptor": … } },
//       "methods": { "<name>": { "intermediary": …, "signature": … } } } } }
// Flat fallback: { "<key>": { "class"|"intermediary": …, "fields": …, "methods": … } }
// Array fallback: [ { "key"|"name": …, "intermediary"|"class": …, … } ]

bool parse_member_map(const json &node, const char *sig_key,
                      std::unordered_map<std::string, MemberInfo> *out) {
    if (!node.is_object()) {
        return true; // absent member map is fine
    }
    for (auto it = node.begin(); it != node.end(); ++it) {
        const json &m = it.value();
        MemberInfo info{};
        // Accept "intermediary" or "id" for the identifier, and the given
        // sig key ("descriptor" for fields, "signature" for methods) or "sig".
        const char *id_keys[] = {"intermediary", "id"};
        for (const char *k : id_keys) {
            if (m.contains(k) && m[k].is_string()) {
                info.intermediary = intern(m[k].get<std::string>());
                break;
            }
        }
        if (m.contains(sig_key) && m[sig_key].is_string()) {
            info.sig = intern(m[sig_key].get<std::string>());
        } else if (m.contains("sig") && m["sig"].is_string()) {
            info.sig = intern(m["sig"].get<std::string>());
        }
        if (info.intermediary == nullptr) {
            log::warn(kTag, "member '%s' lacks an intermediary id — skipped", it.key().c_str());
            continue;
        }
        out->emplace(it.key(), info);
    }
    return true;
}

bool parse_class_node(const std::string &key, const json &node, ClassEntry *out) {
    const char *class_keys[] = {"intermediary", "class", "intermediary_class"};
    for (const char *k : class_keys) {
        if (node.contains(k) && node[k].is_string()) {
            out->intermediary = intern(node[k].get<std::string>());
            break;
        }
    }
    if (out->intermediary == nullptr) {
        return false;
    }
    parse_member_map(node.contains("fields") ? node["fields"] : json::object(),
                     "descriptor", &out->fields);
    parse_member_map(node.contains("methods") ? node["methods"] : json::object(),
                     "signature", &out->methods);
    return true;
}

} // namespace

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

bool load(const char *path) {
    if (store().loaded) {
        return true;
    }

    FILE *f = std::fopen(path, "rb");
    if (f == nullptr) {
        log::warn(kTag, "%s not found — JVM lookups unavailable until provided", path);
        return false;
    }
    std::string body;
    char buf[4096];
    size_t n = 0;
    while ((n = std::fread(buf, 1, sizeof(buf), f)) > 0) {
        body.append(buf, n);
    }
    std::fclose(f);

    json root;
    try {
        root = json::parse(body);
    } catch (const json::exception &e) {
        log::error(kTag, "parse failed: %s", e.what());
        return false;
    }

    size_t count = 0;

    if (root.is_object() && root.contains("classes") && root["classes"].is_object()) {
        for (auto it = root["classes"].begin(); it != root["classes"].end(); ++it) {
            ClassEntry entry;
            if (parse_class_node(it.key(), it.value(), &entry)) {
                store().classes.emplace(it.key(), std::move(entry));
                ++count;
            }
        }
    } else if (root.is_object()) {
        for (auto it = root.begin(); it != root.end(); ++it) {
            if (!it.value().is_object()) {
                continue;
            }
            ClassEntry entry;
            if (parse_class_node(it.key(), it.value(), &entry)) {
                store().classes.emplace(it.key(), std::move(entry));
                ++count;
            }
        }
    } else if (root.is_array()) {
        for (const json &node : root) {
            if (!node.is_object()) {
                continue;
            }
            std::string key = node.value("key", node.value("name", std::string()));
            if (key.empty()) {
                continue;
            }
            ClassEntry entry;
            if (parse_class_node(key, node, &entry)) {
                store().classes.emplace(key, std::move(entry));
                ++count;
            }
        }
    }

    if (count == 0) {
        log::error(kTag, "no class entries recognized in %s (schema unknown)", path);
        return false;
    }

    store().loaded = true;
    log::info(kTag, "loaded %zu curated classes from %s", count, path);
    return true;
}

bool loaded() { return store().loaded; }

size_t class_count() { return store().classes.size(); }

const ClassInfo *find_class(const char *class_key) {
    if (class_key == nullptr || !store().loaded) {
        return nullptr;
    }
    auto it = store().classes.find(class_key);
    if (it == store().classes.end()) {
        return nullptr;
    }
    // ClassInfo is layout-compatible with what callers need; expose via the
    // public struct.
    static_assert(sizeof(ClassInfo) == sizeof(const char *) * 1, "unexpected layout");
    return reinterpret_cast<const ClassInfo *>(&it->second.intermediary);
}

const MemberInfo *find_field(const char *class_key, const char *member_key) {
    if (class_key == nullptr || member_key == nullptr || !store().loaded) {
        return nullptr;
    }
    auto it = store().classes.find(class_key);
    if (it == store().classes.end()) {
        return nullptr;
    }
    auto m = it->second.fields.find(member_key);
    return m == it->second.fields.end() ? nullptr : &m->second;
}

const MemberInfo *find_method(const char *class_key, const char *member_key) {
    if (class_key == nullptr || member_key == nullptr || !store().loaded) {
        return nullptr;
    }
    auto it = store().classes.find(class_key);
    if (it == store().classes.end()) {
        return nullptr;
    }
    auto m = it->second.methods.find(member_key);
    return m == it->second.methods.end() ? nullptr : &m->second;
}

void unload() {
    store().classes.clear();
    store().strings.clear();
    store().loaded = false;
}

} // namespace woke::jvm::mappings
