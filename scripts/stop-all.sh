#!/usr/bin/env bash
# Остановка микросервисов, запущенных scripts/run-all.sh
cd "$(dirname "$0")/.."
for pid_file in logs/*.pid; do
  [ -f "$pid_file" ] || continue
  kill "$(cat "$pid_file")" 2>/dev/null || true
  rm -f "$pid_file"
done
