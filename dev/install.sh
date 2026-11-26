#!/usr/bin/env bash
# Один раз: ставит команду dev (ссылка в ~/.local/bin), чтобы она работала из любой консоли.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
chmod +x "$HERE/dev.sh"
mkdir -p "$HOME/.local/bin"
ln -sf "$HERE/dev.sh" "$HOME/.local/bin/dev"
case ":$PATH:" in
  *":$HOME/.local/bin:"*) echo 'Готово. Набери: dev' ;;
  *) echo 'Готово. Добавь в ~/.bashrc строку: export PATH="$HOME/.local/bin:$PATH", открой новую консоль и набери: dev' ;;
esac
