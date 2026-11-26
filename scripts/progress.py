#!/usr/bin/env python3
"""Живое табло переноса на Java: python3 scripts/progress.py [секунды]. Выход: Ctrl+C."""
import json, os, subprocess, sys, time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
BASE_COMMIT = "944fc12d"  # последний коммит до переноса: всё ещё на Kotlin
CACHE = os.path.join(ROOT, "scripts", ".progress-baseline.json")
SKIP = {"build", ".git", ".gradle", "node_modules"}
MODULES = [
    "modules/common", "modules/spi", "modules/api", "modules/features/config-api",
    "modules/features/currency-api", "modules/features/localization-api",
    "modules/features/config-runtime", "modules/features/currency-runtime",
    "modules/updates", "platforms/velocity", "platforms/bungee", "platforms/bukkit", "modules/core",
]


def module_of(path):
    for m in sorted(MODULES, key=len, reverse=True):
        if path.startswith(m + "/"):
            return m
    return None


def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], capture_output=True, text=True).stdout


def baseline():
    if os.path.exists(CACHE):
        return json.load(open(CACHE))
    data = {m: [0, 0] for m in MODULES}
    for path in git("ls-tree", "-r", "--name-only", BASE_COMMIT).splitlines():
        m = module_of(path)
        if m and path.endswith(".kt"):
            text = git("show", f"{BASE_COMMIT}:{path}")
            data[m][0] += 1
            data[m][1] += text.count("\n") + 1
    json.dump(data, open(CACHE, "w"))
    return data


def scan():
    kt = {m: [0, 0] for m in MODULES}
    jv = {m: [0, 0] for m in MODULES}
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP]
        rel = os.path.relpath(base, ROOT).replace(os.sep, "/")
        m = module_of(rel + "/x")
        if not m:
            continue
        for f in files:
            kind = kt if f.endswith(".kt") else jv if f.endswith(".java") else None
            if kind is None:
                continue
            try:
                n = open(os.path.join(base, f), encoding="utf-8", errors="ignore").read().count("\n") + 1
            except OSError:
                continue
            kind[m][0] += 1
            kind[m][1] += n
    return kt, jv


import re

W = 92
ANSI = re.compile(r"\033\[[0-9;]*m")
RESET = "\033[0m"
DIM = "\033[38;5;245m"
TEXT = "\033[38;5;252m"
ACCENT = "\033[38;5;81m"
OK = "\033[38;5;78m"
WARN = "\033[38;5;117m"
FLASH = ["\033[1;38;5;171m"]  # фиолетовый: в обычной раскладке не используется
BLINKS = 3
BLINK_SECONDS = 0.35


def visible(text):
    return len(ANSI.sub("", text))


def row(text="", edge=ACCENT):
    pad = max(0, W - 4 - visible(text))
    return f"{edge}│{RESET} {text}{' ' * pad} {edge}│{RESET}"


def rule(left, right, label=""):
    inner = W - 2
    if label:
        text = f" {label} "
        return f"{ACCENT}{left}─{RESET}{TEXT}{text}{ACCENT}{'─' * (inner - 1 - len(text))}{right}{RESET}"
    return f"{ACCENT}{left}{'─' * inner}{right}{RESET}"


def level(done, total):
    ratio = 1.0 if total == 0 else max(0.0, min(1.0, done / total))
    code = 78 if ratio >= 1 else 114 if ratio >= 0.75 else 75 if ratio >= 0.5 else 221 if ratio >= 0.25 else 203
    return f"\033[1;38;5;{code}m"


def bar(done, total, width, color=None):
    """Сплошная шкала: заполненная часть цветом процента, пустая тёмной дорожкой."""
    ratio = 1.0 if total == 0 else max(0.0, min(1.0, done / total))
    filled = int(round(ratio * width))
    code = re.search(r"38;5;(\d+)", color or level(done, total)).group(1)
    return f"\033[48;5;{code}m{' ' * filled}\033[48;5;238m{' ' * (width - filled)}{RESET}"


def tint(key, flashes, now, normal):
    start = flashes.get(key)
    if start is None:
        return normal
    phase = int((now - start) / BLINK_SECONDS)
    if phase >= BLINKS * 2:
        flashes.pop(key, None)
        return normal
    return FLASH[0] if phase % 2 == 0 else normal


COLS = [("Модуль", 36, "l"), ("Прогресс", 20, "l"), ("Готово", 7, "r"), ("Файлов", 7, "r"), ("Строк", 7, "r"), ("Статус", 8, "l")]


def cell(text, width, align):
    pad = max(0, width - visible(text))
    return " " + (text + " " * pad if align == "l" else " " * pad + text) + " "


def table_line(cells, edge=ACCENT):
    parts = [cell(c, w, a) for c, (_, w, a) in zip(cells, COLS)]
    return f"{edge}│{RESET}" + f"{edge}│{RESET}".join(parts) + f"{edge}│{RESET}"


def table_rule(left, mid, right):
    return ACCENT + left + mid.join("─" * (w + 2) for _, w, _ in COLS) + right + RESET


def render(base, kt, jv, flashes, now):
    b_files = sum(v[0] for v in base.values()); b_lines = sum(v[1] for v in base.values())
    k_files = sum(v[0] for v in kt.values()); k_lines = sum(v[1] for v in kt.values())
    j_files = sum(v[0] for v in jv.values()); j_lines = sum(v[1] for v in jv.values())
    d_files = b_files - k_files; d_lines = b_lines - k_lines
    ready = sum(1 for m in MODULES if kt[m][0] == 0 and base.get(m, [0, 0])[0] > 0)
    pct = lambda d, t: f"{(100.0 if t == 0 else d * 100 / t):.1f}%"
    num = lambda n: f"{n:,}".replace(",", " ")
    width = sum(w + 3 for _, w, _ in COLS) + 1
    tf = tint("total-files", flashes, now, level(d_files, b_files))
    tl = tint("total-lines", flashes, now, level(d_lines, b_lines))

    def total(label, done, size, left_files, left_lines, tone):
        return table_line([f"{TEXT}{label}{RESET}", bar(done, size, 20, tone), f"{tone}{pct(done, size)}{RESET}",
                           f"{WARN}{num(left_files)}{RESET}", f"{WARN}{num(left_lines)}{RESET}", ""])

    title = f" Перенос pnLibrary с Kotlin на Java "
    stamp = f" {time.strftime('%H:%M:%S')} "
    fill = width - 2 - len(title) - len(stamp)
    out = ["", f"{ACCENT}╭{RESET}{TEXT}{title}{ACCENT}{'─' * fill}{DIM}{stamp}{ACCENT}╮{RESET}"]
    inner = width - 4
    boxed = lambda text: f"{ACCENT}│{RESET} {text}{' ' * max(0, inner - visible(text))} {ACCENT}│{RESET}"
    big = tint("total-lines", flashes, now, level(d_lines, b_lines))
    out.append(boxed(""))
    out.append(boxed(f"{TEXT}Готово всего   {big}{pct(d_lines, b_lines)}{RESET}   {DIM}(по строкам кода, это главный показатель){RESET}"))
    out.append(boxed(bar(d_lines, b_lines, inner)))
    out.append(boxed(f"{TEXT}Написано {big}{num(d_lines)}{TEXT} строк{DIM}   ·   {TEXT}осталось {WARN}{num(k_lines)}{TEXT} строк{DIM}   ·   всего было {num(b_lines)}{RESET}"))
    out.append(boxed(""))
    out.append(table_rule("├", "┬", "┤").replace("├", ACCENT + "├"))
    out.append(table_line([f"{DIM}{name}{RESET}" for name, _, _ in COLS]))
    out.append(table_rule("├", "┼", "┤"))
    out.append(total("ВСЕГО · по файлам", d_files, b_files, k_files, k_lines, tf))
    out.append(total("ВСЕГО · по строкам", d_lines, b_lines, k_files, k_lines, tl))
    out.append(table_rule("├", "┼", "┤"))
    for m in MODULES:
        bf, bl = base.get(m, [0, 0]); kf, kl = kt[m]
        done = kf == 0 and bf > 0
        tone = tint(m, flashes, now, level(bf - kf, bf))
        status = f"{OK}✔ готово{RESET}" if done else f"{DIM}в работе{RESET}"
        out.append(table_line([f"{TEXT}{m}{RESET}", bar(bf - kf, bf, 20, tone), f"{tone}{pct(bf - kf, bf)}{RESET}",
                               f"{WARN if kf else DIM}{num(kf)}{RESET}", f"{WARN if kl else DIM}{num(kl)}{RESET}", status]))
    out.append(table_rule("├", "┴", "┤"))
    note = f"Модулей готово {OK}{ready}{DIM} из {len(MODULES)}  ·  Java в проекте: {TEXT}{num(j_files)}{DIM} файлов, {TEXT}{num(j_lines)}{DIM} строк"
    out.append(f"{ACCENT}│{RESET} {DIM}{note}{RESET}{' ' * (width - 4 - visible(note))} {ACCENT}│{RESET}")
    hint = "Файлов и Строк = сколько ещё осталось на Kotlin  ·  обновляется само  ·  выход Ctrl+C"
    out.append(f"{ACCENT}│{RESET} {DIM}{hint}{RESET}{' ' * (width - 4 - len(hint))} {ACCENT}│{RESET}")
    out.append(f"{ACCENT}╰{'─' * (width - 2)}╯{RESET}")
    return "\n".join(out)


def mark_changes(prev, kt, prev_java, jv, flashes, now):
    """Мигает строка модуля, у которого изменился Kotlin или Java, и общие итоги."""
    if prev is None:
        return
    for m in MODULES:
        if kt[m] != prev[m] or jv[m] != prev_java[m]:
            flashes[m] = now
            flashes["total-files"] = now
            flashes["total-lines"] = now


def main():
    delay = float(sys.argv[1]) if len(sys.argv) > 1 else 3.0
    base = baseline()
    sys.stdout.write("\033[?25l\033[2J")
    prev = None
    flashes = {}
    kt, jv = scan()
    last_scan = time.time()
    try:
        while True:
            now = time.time()
            if now - last_scan >= delay:
                prev, prev_java = kt, jv
                kt, jv = scan()
                mark_changes(prev, kt, prev_java, jv, flashes, now)
                last_scan = now
            frame = render(base, kt, jv, flashes, now)
            sys.stdout.write("\033[H" + frame.replace("\n", "\033[K\n") + "\033[K\033[J")
            sys.stdout.flush()
            time.sleep(0.15)
    except KeyboardInterrupt:
        pass
    finally:
        sys.stdout.write("\033[?25h\n")


if __name__ == "__main__":
    main()
