#!/usr/bin/env python3
"""
woke.wtf — JVM verification stub generator.

Reads mappings.json and emits minimal Java stub classes mirroring the real
1.21.11 intermediary names (class_310 + its player/world/session types), so
the native JVM layer can be verified end-to-end on a live JVM without
shipping Minecraft itself.

Dev-time tool:  python3 scripts/gen_stubs.py
"""

import json
import os

OUT_DIR = "tests/jvm/net/minecraft"

with open("mappings.json") as f:
    m = json.load(f)

mc = m["classes"]["minecraft_client"]
client_inter = mc["intermediary"].split("/")[-1]          # class_310
session_inter = mc["methods"]["getSession"]["signature"]  # Lnet/minecraft/class_320;
session_inter = session_inter[2:-1].split("/")[-1]
player_inter = mc["fields"]["player"]["descriptor"][2:-1].split("/")[-1]  # class_746
world_inter = mc["fields"]["world"]["descriptor"][2:-1].split("/")[-1]    # class_638

os.makedirs(OUT_DIR, exist_ok=True)

stubs = {
    f"{player_inter}.java": f"""package net.minecraft;

/** Stub for {player_inter} (ClientPlayerEntity). Verification only. */
public class {player_inter} {{
}}
""",
    f"{world_inter}.java": f"""package net.minecraft;

/** Stub for {world_inter} (ClientWorld). Verification only. */
public class {world_inter} {{
}}
""",
    f"{session_inter}.java": f"""package net.minecraft;

/** Stub for {session_inter} (Session). Verification only. */
public class {session_inter} {{
}}
""",
    f"{client_inter}.java": f"""package net.minecraft;

/**
 * Stub for {client_inter} (MinecraftClient). Mirrors the real intermediary
 * names: method_1551 (static getInstance), method_1548 (getSession),
 * field_1724 (player), field_1687 (world). Verification only.
 */
public class {client_inter} {{
    public {player_inter} field_1724 = new {player_inter}();
    public {world_inter} field_1687 = new {world_inter}();

    private static final {client_inter} INSTANCE = new {client_inter}();

    public static {client_inter} method_1551() {{
        return INSTANCE;
    }}

    public {session_inter} method_1548() {{
        return new {session_inter}();
    }}
}}
""",
}

for name, content in stubs.items():
    path = os.path.join(OUT_DIR, name)
    with open(path, "w") as f:
        f.write(content)
    print(f"[stubs] wrote {path}")
print(f"[stubs] done (client={client_inter}, player={player_inter}, world={world_inter}, session={session_inter})")
