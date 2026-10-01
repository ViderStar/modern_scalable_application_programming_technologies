#!/usr/bin/env bash
# Пересъёмка всех иллюстраций отчётов: сервисы поднимаются с чистой базой, сценарии проходит Chrome без окна.
# Требуются собранные jar-файлы (./mvnw -DskipTests package), Chrome и uv.
set -euo pipefail
cd "$(dirname "$0")/.."

scripts/stop-all.sh
# ЛР1–ЛР2: стартовый капитал фонда развития — 1 млн BYN, как требует задание второй работы
BANK_FUND_CAPITAL_BYN=1000000 scripts/run-all.sh
uv run --with selenium scripts/screenshots.py lab1 lab2
scripts/stop-all.sh

# ЛР3–ЛР4: капитал по умолчанию — 100 млрд BYN
scripts/run-all.sh
uv run --with selenium scripts/screenshots.py lab3 lab4
scripts/stop-all.sh
