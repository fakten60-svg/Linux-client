#!/usr/bin/env bash
#
# Starts a development client for the manual in-game tests, from a defined
# state: every module of woke.wtf Lite switched off, so each test in
# MANUAL_TEST_CHECKLIST.md starts from the same baseline.
#
# No xvfb: this is meant to run on a real desktop with a real screen.
#
# Usage:
#   scripts/test_session.sh              # reset config, then ./gradlew runClient
#   scripts/test_session.sh --tail       # the same, plus a live tail of the log
#   scripts/test_session.sh --reset-only # only write the all-off config, no client
#
set -euo pipefail

# Run from the fabric-mod directory no matter where the script is called from.
cd "$(dirname "$0")/.."

CONFIG_DIR="run/config"
CONFIG_FILE="${CONFIG_DIR}/wokewtf-lite.json"
LOG_FILE="run/logs/latest.log"

# Must mirror WokeLiteModules; every module id the mod registers.
MODULES=(
  hud.fps
  ui.crosshair
  ui.chat
  ui.book
  ui.screenshot
  ui.waypoints
  ui.inventory_sort
  util.auto_reconnect
  util.respawn_confirm
  util.chat_macros
  util.screenshot_key
  util.fullscreen_key
  util.stats
)

rewrite_config() {
  mkdir -p "$CONFIG_DIR"

  # Keep whatever was there; the file is gitignored, but a half day of settings
  # is still worth a copy next to it.
  if [ -f "$CONFIG_FILE" ]; then
    cp "$CONFIG_FILE" "${CONFIG_FILE}.test-session-backup"
    echo "Backed up the previous config to ${CONFIG_FILE}.test-session-backup"
  fi

  # A minimal but valid document: the loader fills in every missing setting from
  # the shipped defaults, so listing only "enabled" is enough to force the state.
  {
    printf '{ "schemaVersion": 1, "mod": "wokewtf-lite", "modules": {'
    first=1
    for id in "${MODULES[@]}"; do
      if [ "$first" -eq 0 ]; then
        printf ','
      fi
      first=0
      printf ' "%s": { "enabled": false }' "$id"
    done
    printf ' } }\n'
  } > "$CONFIG_FILE"

  echo "Wrote ${CONFIG_FILE}: all ${#MODULES[@]} modules off."
}

tail_log() {
  mkdir -p "$(dirname "$LOG_FILE")"
  touch "$LOG_FILE"
  tail -n 0 -F "$LOG_FILE" &
  TAIL_PID=$!
  trap 'kill "$TAIL_PID" 2>/dev/null || true' EXIT
  echo "Following ${LOG_FILE} (Ctrl-C stops the game and the tail)."
}

TAIL_PID=""
RESET_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --tail|-t) tail_log ;;
    --reset-only|-r) RESET_ONLY=1 ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

rewrite_config

if [ "$RESET_ONLY" -eq 1 ]; then
  echo "Reset only: the client was not started."
  exit 0
fi

echo "Starting ./gradlew runClient ..."
./gradlew runClient
