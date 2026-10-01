#!/usr/bin/env bash
# ==============================================================================
#  woke.wtf Lite — scripts/parse_session_log.sh
#
#  Summarises one client session: per module, is it switched on, how many errors
#  and warnings did it produce, and did the log say anything about it at all.
#
#  It reads two files and touches nothing else: the log the session wrote, and
#  the config file that session left behind. No network, no dependencies beyond
#  the shell and the grep/sed already on every box.
#
#  Usage:
#    scripts/parse_session_log.sh                       # run/logs/latest.log
#    scripts/parse_session_log.sh <logfile>             # a specific log
#    scripts/parse_session_log.sh <logfile> <config>    # a specific config
#
#  The module list comes from the log's own "Registered modules:" line, so it
#  cannot drift away from what the build actually registers. The config file
#  supplies the on/off state; without it the state is reported as "unknown".
# ==============================================================================
set -euo pipefail

LOG="${1:-run/logs/latest.log}"
CONFIG="${2:-run/config/wokewtf-lite.json}"

# The logger is called "woke.wtf Lite", but the two appenders render it
# differently: latest.log shortens it to "(wtf Lite)", debug.log keeps
# "(woke.wtf Lite)". Match both, or a debug.log would look like the mod never
# spoke.
TAG_PATTERN='\((woke\.)?wtf Lite\)'

if [ ! -f "$LOG" ]; then
  echo "No such log file: $LOG" >&2
  exit 2
fi

# grep -c exits 1 when it finds nothing, which would end a strict shell.
count() {
  local n
  n=$(grep -c "$@" "$LOG" 2>/dev/null || true)
  echo "${n:-0}"
}

# Same, but only over the lines this mod wrote. Everything else in a log is the
# game, Fabric, or whatever the machine running it felt like printing, and
# counting that as "our" errors would make every session look broken.
mod_count() {
  local n
  n=$(grep -E "$TAG_PATTERN" "$LOG" 2>/dev/null | grep -c "$1" 2>/dev/null || true)
  echo "${n:-0}"
}

# --- which modules did this build register? -----------------------------------
# The startup line names them one by one. Falling back to the config file covers
# a log that was cut short before startup finished.
module_ids() {
  sed -n 's/.*Registered modules: //p' "$LOG" | tail -1 \
    | tr ',' '\n' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' | grep -E '^[A-Za-z0-9_.]+$'
}

IDS=$(module_ids || true)
if [ -z "$IDS" ] && [ -f "$CONFIG" ]; then
  IDS=$(grep -oE '"[A-Za-z0-9_.]+":[[:space:]]*\{[[:space:]]*"enabled"' "$CONFIG" \
        | sed 's/":.*//; s/^"//' || true)
fi

if [ -z "$IDS" ]; then
  echo "Nothing to summarise: no 'Registered modules:' line in $LOG" >&2
  echo "and no module entries in $CONFIG." >&2
  exit 3
fi

# --- per-module state ---------------------------------------------------------
enabled_state() {
  local id="$1" hit
  [ -f "$CONFIG" ] || { echo "unknown"; return; }
  hit=$(grep -o "\"$id\":[[:space:]]*{[[:space:]]*\"enabled\":[[:space:]]*[a-z]*" "$CONFIG" || true)
  case "$hit" in
    *true)  echo "enabled" ;;
    *false) echo "disabled" ;;
    *)      echo "unknown" ;;
  esac
}

echo "woke.wtf Lite — session summary"
echo "  log:    $LOG"
if [ -f "$CONFIG" ]; then
  echo "  config: $CONFIG"
else
  echo "  config: $CONFIG  (missing: on/off state shown as unknown)"
fi
echo
printf '%-20s %-10s %7s %9s %9s\n' "module" "state" "errors" "warnings" "mentions"
printf '%-20s %-10s %7s %9s %9s\n' "------" "-----" "------" "--------" "--------"

for id in $IDS; do
  # A module's own failure is reported as: Module '<id>' threw during <phase>
  errors=$(count "Module '$id' threw")
  warnings=$(grep 'WARN' "$LOG" 2>/dev/null | grep -c -F "$id" || true)
  mentions=$(count -F "$id")
  printf '%-20s %-10s %7s %9s %9s\n' \
    "$id" "$(enabled_state "$id")" "$errors" "${warnings:-0}" "$mentions"
done

echo
echo "totals"
echo "  mod log lines:     $(count -E "$TAG_PATTERN")"
echo "  mod errors:        $(mod_count 'ERROR')"
echo "  mod warnings:      $(mod_count 'WARN')"
echo "  mod exceptions:    $(mod_count 'Exception')"
echo "  keybind conflicts: $(count 'share a key with another binding')"
echo "  modules logged:    $(echo "$IDS" | grep -c . || true)"
echo
echo "  other log errors:  $(($(count 'ERROR') - $(mod_count 'ERROR')))  (game, Fabric, machine noise — not this mod)"

# --- what the debug watch saw -------------------------------------------------
watched=$(sed -n "s/.*watch '\([^']*\)'.*/\1/p" "$LOG" | sort -u | tr '\n' ' ')
if [ -n "${watched% }" ]; then
  echo "  debug watch saw:   ${watched% }"
  echo "    (per-module verbose logging was on; the detail is in debug.log)"
fi

echo
echo "Remember: an empty log is a result. If a test failed and this says nothing,"
echo "that is the finding to report."
