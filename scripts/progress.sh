#!/usr/bin/env bash
# Живое табло переноса на Java. Запуск: scripts/progress.sh [секунды между обновлениями]
exec python3 "$(dirname "$(readlink -f "$0")")/progress.py" "$@"
