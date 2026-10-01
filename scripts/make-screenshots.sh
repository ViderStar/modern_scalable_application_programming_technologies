#!/usr/bin/env bash
# Пересъёмка всех иллюстраций отчётов: сервисы поднимаются с чистой базой, сценарии проходит Chrome без окна.
# Требуются собранные jar-файлы (./mvnw -DskipTests package), Chrome и uv.
set -euo pipefail
cd "$(dirname "$0")/.."

scripts/stop-all.sh
# ЛР1–ЛР2: стартовый капитал фонда развития — 1 млн BYN, как требует задание второй работы;
# демо-карты отключены, чтобы в отчёте по счетам были только счета сценария
BANK_FUND_CAPITAL_BYN=1000000 BANK_DEMO_ENABLED=false scripts/run-all.sh
uv run --with selenium scripts/screenshots.py lab1 lab2
scripts/stop-all.sh

# ЛР3: капитал по умолчанию — 100 млрд BYN
BANK_DEMO_ENABLED=false scripts/run-all.sh
uv run --with selenium scripts/screenshots.py lab3
scripts/stop-all.sh

# ЛР4: обычный запуск — с демо-картами для банкомата
scripts/run-all.sh
uv run --with selenium scripts/screenshots.py lab4
scripts/stop-all.sh
