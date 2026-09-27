#!/usr/bin/env python3
"""
woke.wtf — mappings.json generator.

Downloads the pinned Yarn mappings for the target MC version, parses tiny-v2,
and emits a curated mappings.json (repo root) containing only the classes and
members the client resolves at runtime, with all descriptors converted to the
*intermediary* namespace (what an intermediary-mapped production jar exposes).

Member candidates that do not exist in this yarn build are reported as
warnings and simply omitted — never guessed.

Dev-time tool only — run manually when mappings need regenerating:
    python3 scripts/gen_mappings.py
"""

import json
import sys
import urllib.request
import zipfile
from io import BytesIO

# yarn 1.21.11+build.6 — verified via meta.fabricmc.net (stable build for 1.21.11)
YARN_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/1.21.11%2Bbuild.6/yarn-1.21.11%2Bbuild.6-mergedv2.jar"
TARGET_VERSION = "1.21.11"
YARN_BUILD = "yarn 1.21.11+build.6"

OUT_PATH = "mappings.json"

# ---------------------------------------------------------------------------
# Curation table: stable symbolic key -> (yarn class name, members)
#   members: (kind, yarn_name, named_descriptor_or_None)
# Descriptors below are yarn-NAMED descriptors; the generator translates
# every emitted descriptor into intermediary space.
# ---------------------------------------------------------------------------

CLASSES = {
    "minecraft_client": ("net.minecraft.client.MinecraftClient", [
        ("field",  "player",            None),
        ("field",  "world",             None),
        ("field",  "interactionManager", None),
        ("field",  "options",           None),
        ("method", "getInstance",       "()Lnet/minecraft/client/MinecraftClient;"),
        ("method", "getSession",        "()Lnet/minecraft/client/session/Session;"),
        ("method", "getCameraEntity",   "()Lnet/minecraft/entity/Entity;"),
    ]),
    "client_player_entity": ("net.minecraft.client.network.ClientPlayerEntity", []),
    "client_world":         ("net.minecraft.client.world.ClientWorld", []),
    "interaction_manager":  ("net.minecraft.client.network.ClientPlayerInteractionManager", []),
    "game_options":         ("net.minecraft.client.option.GameOptions", []),
    "entity":               ("net.minecraft.entity.Entity", [
        ("method", "isOnGround",  "()Z"),
        ("method", "getX",        "()D"),
        ("method", "getY",        "()D"),
        ("method", "getZ",        "()D"),
    ]),
    "living_entity":        ("net.minecraft.entity.LivingEntity", [
        ("method", "getHealth",     "()F"),
        ("method", "getMaxHealth",  "()F"),
    ]),
    "item_stack":           ("net.minecraft.item.ItemStack", [
        ("method", "getCount",    "()I"),
        ("method", "isEmpty",     "()Z"),
        ("method", "getItem",     "()Lnet/minecraft/item/Item;"),
    ]),
    "player_inventory":     ("net.minecraft.entity.player.PlayerInventory", [
        ("method", "getSelectedSlot", "()I"),
    ]),
    "hit_result":           ("net.minecraft.util.hit.HitResult", []),
    "entity_hit_result":    ("net.minecraft.util.hit.EntityHitResult", []),
    "session":              ("net.minecraft.client.session.Session", [
        ("method", "getUsername", "()Ljava/lang/String;"),
    ]),
}

# Canonical IDs that MUST hold if the generator is wired correctly — a cheap
# guard against silent namespace mixups.
CANONICAL_ASSERTIONS = {
    ("minecraft_client", None): "net/minecraft/class_310",
    ("minecraft_client", "field:player"): "field_1724",
    ("minecraft_client", "field:world"): "field_1687",
    ("minecraft_client", "method:getInstance"): "method_1551",
}


def main() -> int:
    print(f"[gen] downloading {YARN_URL}")
    with urllib.request.urlopen(YARN_URL, timeout=60) as resp:
        jar_bytes = resp.read()
    print(f"[gen] {len(jar_bytes)} bytes")

    tiny = None
    with zipfile.ZipFile(BytesIO(jar_bytes)) as zf:
        for name in zf.namelist():
            if name.endswith(".tiny"):
                tiny = zf.read(name).decode("utf-8")
                break
    if tiny is None:
        print("[gen] ERROR: no .tiny file in jar")
        return 1

    # ---- tiny-v2 parse ---------------------------------------------------
    lines = tiny.splitlines()
    header = lines[0].split("\t")
    if header[0] != "tiny" or header[1] != "2":
        print(f"[gen] ERROR: unexpected tiny header: {header[:3]}")
        return 1
    ns_official, ns_inter, ns_named = header[3], header[4], header[5]
    print(f"[gen] namespaces: {ns_official} -> {ns_inter} -> {ns_named}")

    classes = {}          # official_name -> class info
    cur_class = None

    for line in lines[1:]:
        if not line or line.startswith("#"):
            continue
        # tiny-v2 indents member rows with a leading TAB; class rows do not.
        parts = line.lstrip("\t").split("\t")
        kind = parts[0]
        if kind == "c":
            if len(parts) < 4:
                continue  # malformed/anonymous stub row
            cur_class = {
                "official": parts[1],     # obfuscated name (slash form)
                "inter": parts[2],        # intermediary name (slash form)
                "named": parts[3],        # yarn/named name (slash form)
                "fields": {},             # (named_desc, official_name) -> info
                "methods": {},
            }
            classes[parts[1]] = cur_class
        elif kind in ("f", "m") and cur_class is not None:
            if len(parts) < 5:
                continue  # malformed row
            desc_named = parts[1]
            table = "fields" if kind == "f" else "methods"
            cur_class[table][(desc_named, parts[2])] = {
                "inter": parts[3], "named": parts[4], "desc_named": desc_named,
            }

    # ---- descriptor translation (official -> intermediary) ----------------
    # tiny-v2 member descriptors are in the OFFICIAL namespace (e.g. Lhnh;),
    # so translate them against the class table keyed by official names.
    class_by_official = classes
    # Curation keys are yarn-named names; resolve classes through the named
    # index (the class table itself is keyed by official/obfuscated names).
    class_by_named = {info["named"]: info for info in classes.values()}

    def desc_to_inter(desc_official: str) -> str:
        out, i, n = [], 0, len(desc_official)
        while i < n:
            ch = desc_official[i]
            if ch == "L":
                end = desc_official.index(";", i)
                internal = desc_official[i + 1:end]   # official name, slash form
                best, best_len = None, -1
                for official, info in class_by_official.items():
                    if internal == official or internal.startswith(official + "$"):
                        if len(official) > best_len:
                            best, best_len = info, len(official)
                out.append("L" + (best["inter"] if best else internal) + ";")
                i = end + 1
            else:
                out.append(ch)
                i += 1
        return "".join(out)

    def named_to_official(desc_named: str) -> str:
        """Translate a yarn-named descriptor into official space (for lookups
        against tiny-v2 member keys, which use official descriptors)."""
        out, i, n = [], 0, len(desc_named)
        while i < n:
            ch = desc_named[i]
            if ch == "L":
                end = desc_named.index(";", i)
                internal = desc_named[i + 1:end]   # yarn-named, slash form
                info = class_by_named.get(internal)
                out.append("L" + (info["official"] if info else internal) + ";")
                i = end + 1
            else:
                out.append(ch)
                i += 1
        return "".join(out)

    def member_lookup(cinfo, table, yarn_name, desc_named):
        # tiny-v2 keys members by OFFICIAL descriptor; convert the curated
        # yarn-named descriptor into official space before matching.
        target = named_to_official(desc_named) if desc_named else None
        for (d, _off), info in cinfo[table].items():
            if info["named"] == yarn_name and (target is None or d == target):
                return info
        return None

    # ---- emit ---------------------------------------------------------------
    out = {
        "meta": {
            "target": TARGET_VERSION,
            "yarn_build": YARN_BUILD,
            "namespaces": {"official": ns_official, "intermediary": ns_inter, "named": ns_named},
            "note": "Descriptors/signatures are in intermediary namespace; keys are stable symbolic names.",
        },
        "classes": {},
    }

    missing = []
    for key, (yarn_name, members) in CLASSES.items():
        yarn_slash = yarn_name.replace(".", "/")   # named-form lookup
        cinfo = class_by_named.get(yarn_slash)
        if cinfo is None:
            missing.append(yarn_name)
            continue
        entry = {"intermediary": cinfo["inter"], "named": cinfo["named"],
                 "fields": {}, "methods": {}}
        for mkind, mname, mdesc in members:
            table = mkind + "s"
            info = member_lookup(cinfo, table, mname, mdesc)
            if info is None:
                missing.append(f"{yarn_name}#{mname} {mdesc or ''}")
                continue
            sig = desc_to_inter(info["desc_named"])
            slot = {"intermediary": info["inter"], "named": info["named"], "sig": sig}
            if mkind == "field":
                slot["descriptor"] = sig
            else:
                slot["signature"] = sig
            entry[table][mname] = slot
        out["classes"][key] = entry

    if missing:
        print("[gen] WARNING — unresolved (omitted, never guessed):")
        for m in missing:
            print("       -", m)

    # ---- canonical sanity assertions ---------------------------------------
    for (ckey, member), expect in CANONICAL_ASSERTIONS.items():
        centry = out["classes"].get(ckey)
        if centry is None:
            print(f"[gen] ASSERTION FAIL: class '{ckey}' missing")
            return 1
        got = centry["intermediary"]
        if member is not None:
            kind, name = member.split(":", 1)
            slot = centry[kind + "s"].get(name)
            got = slot["intermediary"] if slot else None
        if got != expect:
            print(f"[gen] ASSERTION FAIL: {ckey}:{member} = {got}, expected {expect}")
            return 1
    print("[gen] canonical ID assertions passed (class_310 / field_1724 / field_1687 / method_1551)")

    with open(OUT_PATH, "w") as f:
        json.dump(out, f, indent=2)
    print(f"[gen] wrote {OUT_PATH} with {len(out['classes'])} classes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
