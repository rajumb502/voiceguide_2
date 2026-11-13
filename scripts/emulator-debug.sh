#!/usr/bin/env bash
# Run from a normal host terminal; the Codex sandbox cannot bind emulator/ADB ports.
set -euo pipefail
avd_name="${1:-a34play}"
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
log_dir="$repo_dir/diagnostics/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$log_dir"
printf 'Emulator logs: %s\n' "$log_dir/emulator.log"
printf 'Hardware video decoding disabled for this run; AVD user data is preserved.\n'
set +e
emulator -avd "$avd_name" -feature -HardwareDecoder -no-snapshot -verbose 2>&1 | tee "$log_dir/emulator.log"
emulator_status=${PIPESTATUS[0]}
set -e
printf '%s\n' "$emulator_status" > "$log_dir/exit-status.txt"
printf 'Emulator exited with status %s\n' "$emulator_status"
exit "$emulator_status"
