#!/usr/bin/env bash
# Рабочее окружение pnFolder для Linux (Ubuntu): то же, что dev.ps1 на Windows.
# Правим библиотеку и сразу видим, что ломается в плагинах. Релизов нет.
#
#   ./dev.sh                       меню (menu.py; простое по номерам: DEV_SIMPLE=1 ./dev.sh)
#   ./dev.sh status                ветки и несохранённые правки всех репозиториев
#   ./dev.sh lib-check             тесты и проверка публичного API библиотеки
#   ./dev.sh lib-publish           собрать библиотеку из рабочей папки в локальный Maven (0.1.0-dev.local)
#   ./dev.sh plugins [имена|all]   собрать плагины против локальной библиотеки
#   ./dev.sh api-diff [тег]        что изменилось в публичном API по сравнению с релизом (по умолчанию v0.1.0-dev.6)
#   ./dev.sh api-map               обновить карту API (dev/API-MAP.md)
#   ./dev.sh guard                 запретить push в main/master и теги во всех репозиториях
#   ./dev.sh unguard               снять запрет
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB="$(dirname "$HERE")"        # репозиторий pnLibrary, эта папка лежит в нём
ROOT="$(dirname "$LIB")"        # рядом с ним лежат репозитории плагинов
LOCAL_VERSION='0.1.0-dev.local'
INIT="$HERE/local.init.gradle"
PLUGINS=(pnClans pnMarket pnCases pnChat pnSouls pnRtp pnTrades pnBans pnSeller pnRelics pnRelog pnCaptcha pnAuth)

# Java 21 нужна всем плагинам: pnMarket не собирается на более новой.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21'; then
  for candidate in /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/temurin-21-jdk-amd64 /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/*21* \
                   "$HOME"/.jdks/*21* "$HOME"/.sdkman/candidates/java/21* /opt/*21*; do
    if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
  done
fi
export PN_LOCAL_VERSION="$LOCAL_VERSION"

gradle_run() { # папка, аргументы gradle
  local dir="$1"; shift
  (cd "$dir" && chmod +x ./gradlew && ./gradlew "$@" --console=plain)
}

repos() { printf '%s\n' pnLibrary "${PLUGINS[@]}"; }

cmd_status() {
  local name dir
  while read -r name; do
    dir="$ROOT/$name"
    [ -d "$dir/.git" ] || continue
    printf '%-10s %-28s изменений: %s\n' "$name" "$(git -C "$dir" branch --show-current)" "$(git -C "$dir" status --short | wc -l)"
  done < <(repos)
}

cmd_lib_check() { gradle_run "$LIB" build --no-daemon; }

cmd_lib_publish() {
  gradle_run "$LIB" publishToMavenLocal -x test --no-build-cache "-Pversion=$LOCAL_VERSION"
  echo "Библиотека собрана как $LOCAL_VERSION. Теперь: ./dev.sh plugins all"
}

cmd_plugins() {
  local names=("$@") failed=() name
  if [ ${#names[@]} -eq 0 ] || [[ " ${names[*]} " == *" all "* ]]; then names=("${PLUGINS[@]}"); fi
  for name in "${names[@]}"; do
    echo "=== $name ==="
    gradle_run "$ROOT/$name" build -x test -I "$INIT" || failed+=("$name")
  done
  if [ ${#failed[@]} -gt 0 ]; then echo "Сломались: ${failed[*]}"; return 1; fi
  echo 'Все выбранные плагины собираются против локальной библиотеки.'
}

cmd_api_diff() {
  local tag="${1:-v0.1.0-dev.6}"
  git -C "$LIB" diff --stat "$tag" -- '*.api'
  git -C "$LIB" diff "$tag" -- '*.api'
}

cmd_api_map() {
  local base="$LIB/modules/api/src/main/java/org/pnfolder/pnlibrary/api" out="$HERE/API-MAP.md" dir name files types sample
  {
    echo '# Карта публичного API pnLibrary'
    echo
    echo "Сгенерировано $(date '+%Y-%m-%d %H:%M') из modules/api. Обновить: ./dev.sh api-map"
    echo
    echo '| Пакет | Файлов | Типов | Файлы |'
    echo '|---|---|---|---|'
    for dir in "$base"/*/; do
      name="$(basename "$dir")"
      files="$(find "$dir" -name '*.java' | wc -l)"
      types="$( (grep -rhE '^\s*(public |protected |abstract |final |static )*(class|interface|enum) ' --include='*.java' "$dir" || true) | wc -l)"
      sample="$(find "$dir" -name '*.java' | sort | head -4 | xargs -r -n1 basename | sed 's/\.java$//' | paste -sd, - | sed 's/,/, /g')"
      echo "| api.$name | $files | $types | $sample |"
    done
  } > "$out"
  echo "Готово: $out"
}

# Запрет push в main/master и в теги: хук pre-push в каждом репозитории.
write_hook() {
  cat > "$1" <<'HOOK'
#!/bin/sh
# pnFolder dev: релизов пока нет. Запрещены push в main/master и любые теги.
while read local_ref local_sha remote_ref remote_sha; do
  case "$remote_ref" in
    refs/heads/main|refs/heads/master|refs/tags/*) echo "pnFolder dev: push в $remote_ref запрещён (dev.sh unguard снимает запрет)"; exit 1;;
  esac
done
exit 0
HOOK
  chmod +x "$1"
}

cmd_guard() {
  local name dir
  while read -r name; do
    dir="$ROOT/$name"
    [ -d "$dir/.git" ] || continue
    write_hook "$dir/.git/hooks/pre-push"
    echo "защита включена: $name"
  done < <(repos)
}

cmd_unguard() {
  local name hook
  while read -r name; do
    hook="$ROOT/$name/.git/hooks/pre-push"
    if [ -f "$hook" ]; then rm -f "$hook"; echo "защита снята: $name"; fi
  done < <(repos)
}

cmd_menu_simple() {
  local choice
  while true; do
    echo
    echo "pnFolder dev   библиотека: $(git -C "$LIB" branch --show-current), изменений: $(git -C "$LIB" status --short | wc -l)"
    echo ' 1) Статус всех репозиториев'
    echo ' 2) Проверка библиотеки (тесты, стиль, API)'
    echo ' 3) Собрать библиотеку локально'
    echo ' 4) Собрать все плагины против неё'
    echo ' 5) Что изменилось в API'
    echo ' 6) Обновить карту API'
    echo ' 7) Включить защиту от push в main и тегов'
    echo ' 8) Снять защиту'
    echo ' 0) Выход'
    read -r -p 'Пункт: ' choice || return 0
    case "$choice" in
      1) cmd_status ;;
      2) cmd_lib_check || true ;;
      3) cmd_lib_publish || true ;;
      4) cmd_plugins all || true ;;
      5) cmd_api_diff ;;
      6) cmd_api_map ;;
      7) cmd_guard ;;
      8) cmd_unguard ;;
      0|q|'') return 0 ;;
      *) echo 'Нет такого пункта.' ;;
    esac
  done
}

cmd_menu() {
  # Красивое меню (menu.py: рамки, стрелки, фоновые сборки с процентами) нужно терминалу с цветами и Python 3.
  # Иначе откроется простое меню по номерам. Принудительно простое: DEV_SIMPLE=1 ./dev.sh
  if [ -z "${DEV_SIMPLE:-}" ] && [ -t 0 ] && [ -t 1 ] && command -v python3 >/dev/null 2>&1 && [ -f "$HERE/menu.py" ]; then
    exec python3 "$HERE/menu.py"
  fi
  cmd_menu_simple
}

command="${1:-menu}"
if [ $# -gt 0 ]; then shift; fi
case "$command" in
  menu) cmd_menu ;;
  menu-simple) cmd_menu_simple ;;
  status) cmd_status ;;
  lib-check) cmd_lib_check ;;
  lib-publish) cmd_lib_publish ;;
  plugins) cmd_plugins "$@" ;;
  api-diff) cmd_api_diff "$@" ;;
  api-map) cmd_api_map ;;
  guard) cmd_guard ;;
  unguard) cmd_unguard ;;
  *) sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' ;;
esac
