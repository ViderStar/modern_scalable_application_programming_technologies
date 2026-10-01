#!/usr/bin/env bash
# Запуск всех микросервисов банка. Логи пишутся в logs/, остановка — scripts/stop-all.sh
# Использование: scripts/run-all.sh [client account deposit credit atm]
set -euo pipefail
cd "$(dirname "$0")/.."

services=("$@")
if [ ${#services[@]} -eq 0 ]; then
  services=(client account deposit credit atm)
fi

missing=0
for name in "${services[@]}"; do
  [ -f "$name-service/target/$name-service-1.0.0.jar" ] || missing=1
done
if [ "$missing" -eq 1 ]; then
  ./mvnw -q -DskipTests package
fi

mkdir -p logs
port_of() {
  case "$1" in client) echo 8081 ;; account) echo 8082 ;; deposit) echo 8083 ;; credit) echo 8084 ;; atm) echo 8085 ;; esac
}

for name in "${services[@]}"; do
  nohup java -jar "$name-service/target/$name-service-1.0.0.jar" > "logs/$name.log" 2>&1 &
  echo $! > "logs/$name.pid"
done

for name in "${services[@]}"; do
  port=$(port_of "$name")
  for _ in $(seq 1 90); do
    curl -s -o /dev/null "http://localhost:$port/" && break
    sleep 1
  done
  echo "$name-service: http://localhost:$port/"
done
