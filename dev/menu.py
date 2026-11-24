#!/usr/bin/env python3
"""Консольное меню рабочего окружения pnFolder для Linux: то же, что menu.ps1 на Windows, и чуть больше.
Запуск: dev  (или ./dev.sh, или python3 menu.py).

Управление: стрелки или цифры выбирают пункт, Enter открывает, Esc / Backspace / стрелка влево возвращают назад,
мышь (клик и колесо), «/» поиск по длинному списку, T задачи, ? справка.
Сборки идут в фоне: проценты по прошлым запускам, живая панель, лог, отмена.
Рядом с меню играет случайная анимация из точек (бублик, куб, спираль, звёзды, кривая); цвета каждый раз новые.
Без анимации: DEV_NO_ART=1. Нужен только Python 3 (есть в Ubuntu); терминал с 256 цветами."""
import base64
import curses
import datetime
import glob
import json
import locale
import math
import os
import random
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

os.environ.setdefault('ESCDELAY', '25')

HERE = Path(__file__).resolve().parent
LIB = HERE.parent                      # репозиторий pnLibrary, эта папка лежит в нём
ROOT = LIB.parent                      # рядом с ним лежат репозитории плагинов
DEV_SH = HERE / 'dev.sh'
PLUGINS = ['pnClans', 'pnMarket', 'pnCases', 'pnChat', 'pnSouls', 'pnRtp', 'pnTrades', 'pnBans', 'pnSeller',
           'pnRelics', 'pnRelog', 'pnCaptcha', 'pnAuth']
LOCAL_VERSION = '0.1.0-dev.local'
TEST_BUILD = HERE / 'test-build'
CONFIG = HERE / 'config.json'
LOG_DIR = Path(tempfile.gettempdir()) / 'pnfolder-dev'
M2_API = Path.home() / '.m2/repository/io/github/pnfolder/pnlibrary-api' / LOCAL_VERSION
CRUMB_SEP = '  »  '

# Платформы jar библиотеки: название, задача Gradle, маска файла.
LIBRARY_PLATFORMS = [
    {'name': 'Bukkit / Paper / Spigot', 'task': 'shadowBukkit', 'mask': 'pnLibrary-*-bukkit-java8.jar'},
    {'name': 'BungeeCord', 'task': 'shadowBungee', 'mask': 'pnLibrary-*-bungeecord-java8.jar'},
    {'name': 'Velocity', 'task': 'shadowVelocity', 'mask': 'pnLibrary-*-velocity-java17.jar'},
    {'name': 'Универсальный (все платформы)', 'task': 'shadowUniversal', 'mask': 'pnLibrary-*-universal.jar'},
]

# Необязательные модули библиотеки, которые подключаются отдельной зависимостью.
OPTIONAL_MODULES = [
    {'id': 'pnlibrary-common', 'title': 'Общие классы (common)', 'note': 'уже входит в api, нужен только если подключаешь отдельно', 'bukkit': False},
    {'id': 'pnlibrary-config-api', 'title': 'Конфиги (config-api)', 'note': 'уже входит в api, нужен только если подключаешь отдельно', 'bukkit': False},
    {'id': 'pnlibrary-minecraft-localization-api', 'title': 'Локализация, API (localization-api)', 'note': 'уже входит в api, нужен только если подключаешь отдельно', 'bukkit': False},
    {'id': 'pnlibrary-currency-api', 'title': 'Валюты и экономика', 'note': 'API валют, баланс, перевод (все платформы)', 'bukkit': False},
    {'id': 'pnlibrary-minecraft-localization', 'title': 'Локализация для Bukkit', 'note': 'сообщения по языку игрока, файлы lang', 'bukkit': True},
]


# ---------- настройки, Java, git ----------

def get_config():
    try:
        return json.loads(CONFIG.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        return {'ServerPlugins': ''}


def save_config(config):
    CONFIG.write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding='utf-8')


def get_server_plugins():
    """Папка plugins тестового сервера: из настроек меню, а если не задана, из привычного места."""
    configured = get_config().get('ServerPlugins') or ''
    if configured and Path(configured).is_dir():
        return configured
    for guess in (Path.home() / 'Downloads/Server/Paper-Standalone/plugins', Path.home() / 'Server/plugins'):
        if guess.is_dir():
            return str(guess)
    return ''


def java_version(home):
    try:
        out = subprocess.run([f'{home}/bin/java', '-version'], capture_output=True, text=True, timeout=10).stderr
    except (OSError, subprocess.SubprocessError):
        return None
    m = re.search(r'version "([\d.]+)', out)
    return m.group(1) if m else None


def find_jdk21():
    """Java 21 нужна всем плагинам: pnMarket не собирается на более новой."""
    current = os.environ.get('JAVA_HOME')
    if current and (java_version(current) or '').startswith('21'):
        return current
    candidates = (sorted(glob.glob('/usr/lib/jvm/*21*')) + sorted(glob.glob(str(Path.home() / '.jdks/*21*')))
                  + sorted(glob.glob('/opt/*21*')) + sorted(glob.glob(str(Path.home() / '.sdkman/candidates/java/21*'))))
    for c in candidates:
        if os.access(f'{c}/bin/java', os.X_OK):
            return c
    return None


JDK21 = find_jdk21()
JDK21_VERSION = java_version(JDK21) if JDK21 else None


def task_env():
    env = dict(os.environ)
    if JDK21:
        env['JAVA_HOME'] = JDK21
    env['PN_LOCAL_VERSION'] = LOCAL_VERSION
    return env


def git(args, cwd=LIB):
    r = subprocess.run(['git', '-C', str(cwd), *args], capture_output=True, text=True, errors='replace')
    return [l for l in (r.stdout + r.stderr).splitlines()]


def branch_of(path):
    return ''.join(git(['branch', '--show-current'], path)).strip()


def changes_of(path):
    return len([l for l in git(['status', '--short'], path) if l.strip()])


INFO = {}


def update_info():
    built = M2_API.exists()
    INFO.update(
        branch=branch_of(LIB), changes=changes_of(LIB), built=built,
        build=('собрана ' + datetime.datetime.fromtimestamp(M2_API.stat().st_mtime).strftime('%d.%m %H:%M')) if built else 'не собрана',
        guard=(LIB / '.git/hooks/pre-push').exists())


# ---------- фоновые задачи ----------

TASKS = []
_next_id = [1]


def task_stat(label):
    return int(get_config().get('Stats', {}).get(label, 0) or 0)


def save_task_stat(label, count):
    if count < 3:
        return
    config = get_config()
    config.setdefault('Stats', {})[label] = count
    save_config(config)


def dev_spec(command, *more):
    return {'cmd': ['bash', str(DEV_SH), command, *more], 'cwd': str(ROOT)}


def gradle_spec(directory, args):
    return {'cmd': ['bash', '-c', 'chmod +x ./gradlew 2>/dev/null; exec ./gradlew "$@" --console=plain', 'gradle', *args],
            'cwd': str(directory)}


def line_color(line):
    if re.search(r'(?i)\berror\b|FAILED|BUILD FAILED|ОШИБКА|Exception|^e: ', line):
        return 'red'
    if re.search(r'(?i)\bwarning\b|^w: ', line):
        return 'yellow'
    if 'BUILD SUCCESSFUL' in line:
        return 'green'
    if line.startswith('> Task '):
        return 'darkcyan'
    return 'gray'


class Task:
    def __init__(self, title, specs, after=None, context=None):
        self.id = _next_id[0]
        _next_id[0] += 1
        self.title, self.queue, self.index = title, list(specs), 0
        self.proc, self.reader = None, None
        self.status, self.exit_code = 'running', 0            # running | done | failed | cancelled
        self.start, self.end, self.seen = time.time(), None, False
        self.tail, self.task_lines, self.warns, self.errors, self.current = [], 0, 0, 0, ''
        self.expected = task_stat(title)
        self.after, self.context, self.report, self.files = after, context or {}, [], []
        self.lock = threading.Lock()
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        self.log_path = LOG_DIR / f'{self.id}.log'
        self.log = open(self.log_path, 'w', encoding='utf-8')
        TASKS.append(self)
        self._start_spec()

    def _start_spec(self):
        spec = self.queue[self.index]
        self._add('$ ' + ' '.join(spec['cmd'][-3:] if spec['cmd'][0] == 'bash' and spec['cmd'][1] == '-c' else spec['cmd']))
        self.proc = subprocess.Popen(spec['cmd'], cwd=spec['cwd'], env=task_env(), stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, text=True, errors='replace',
                                     bufsize=1, start_new_session=True)
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def _read(self):
        for line in self.proc.stdout:
            self._add(line.rstrip('\n'))

    def _add(self, line):
        with self.lock:
            self.tail.append(line)
            if len(self.tail) > 20000:
                del self.tail[:5000]
            if line.startswith('> Task '):
                self.task_lines += 1
                self.current = line[2:]
            color = line_color(line)
            if color == 'yellow':
                self.warns += 1
            elif color == 'red':
                self.errors += 1
            try:
                self.log.write(line + '\n')
                self.log.flush()
            except ValueError:
                pass

    def update(self):
        """Возвращает True, если задача сменила состояние."""
        if self.status != 'running' or self.proc.poll() is None or self.reader.is_alive():
            return False
        code = self.proc.returncode
        if code != 0:
            self.status, self.exit_code, self.end = 'failed', code, time.time()
            return True
        self.index += 1
        if self.index < len(self.queue):
            self._start_spec()
            return True
        self.status, self.end = 'done', time.time()
        save_task_stat(self.title, self.task_lines)
        if self.after:
            try:
                self.report = list(self.after(self) or [])
            except Exception as e:  # noqa: BLE001
                self.report = ['Ошибка после сборки: ' + str(e)]
        update_info()
        return True

    def stop(self):
        if self.status != 'running':
            return
        try:
            os.killpg(os.getpgid(self.proc.pid), signal.SIGTERM)
        except (OSError, ProcessLookupError):
            pass
        self.status, self.end = 'cancelled', time.time()

    def seconds(self):
        return int((self.end or time.time()) - self.start)

    def elapsed(self):
        s = self.seconds()
        return f'{s // 60:02d}:{s % 60:02d}'

    def percent(self):
        if self.status == 'done':
            return 100
        if self.expected <= 0:
            return None
        return min(99, self.task_lines * 100 // self.expected)

    def eta(self):
        p = self.percent()
        if self.status != 'running' or not p:
            return None
        left = int(self.seconds() * (100 - p) / p)
        return f'{left // 60:02d}:{left % 60:02d}'

    def lines(self):
        with self.lock:
            return list(self.tail)


def update_tasks():
    return any([t.update() for t in list(TASKS)])


def running_tasks():
    return [t for t in TASKS if t.status == 'running']


# ---------- анимации из точек (как в стартовом экране терминала) ----------

WHITE = (255, 255, 255)
BASE = (30, 30, 46)
BRAILLE = ((0x01, 0x02, 0x04, 0x40), (0x08, 0x10, 0x20, 0x80))
PALETTES = [((203, 166, 247), (245, 194, 231)), ((137, 180, 250), (148, 226, 213)), ((250, 179, 135), (245, 194, 231)),
            ((166, 227, 161), (148, 226, 213)), ((243, 139, 168), (250, 179, 135)), ((137, 180, 250), (203, 166, 247))]
ART_W, ART_H = 16, 8


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def rgb256(c):
    r, g, b = (int(max(0, min(255, v))) for v in c)
    if abs(r - g) < 10 and abs(g - b) < 10:                  # серая шкала точнее
        v = (r + g + b) // 3
        if v < 4:
            return 16
        if v > 246:
            return 231
        return 232 + min(23, (v - 8) // 10)
    q = lambda v: min(5, (v + 25) // 51)  # noqa: E731
    return 16 + 36 * q(r) + 6 * q(g) + q(b)


class Canvas:
    """Холст 16x8 символов из брайлевых точек (по 2x4 точки в символе)."""

    def __init__(self, cols=ART_W, rows=ART_H):
        self.cols, self.rows = cols, rows
        self.W, self.H = cols * 2, rows * 4
        self.bits = [0] * (cols * rows)
        self.col = [None] * (cols * rows)
        self.pr = [-1e9] * (cols * rows)

    def pset(self, x, y, c, pri=0.0):
        x, y = int(x), int(y)
        if 0 <= x < self.W and 0 <= y < self.H:
            i = (y >> 2) * self.cols + (x >> 1)
            self.bits[i] |= BRAILLE[x & 1][y & 3]
            if pri >= self.pr[i]:
                self.pr[i], self.col[i] = pri, c

    def line(self, x0, y0, x1, y1, c, pri=0.0):
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        for _ in range(200):
            self.pset(x0, y0, c, pri)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err, x0 = err + dy, x0 + sx
            if e2 <= dx:
                err, y0 = err + dx, y0 + sy

    def cells(self):
        """Строки холста: списки (текст, цвет256 или None)."""
        out = []
        for ry in range(self.rows):
            runs = []
            for rx in range(self.cols):
                i = ry * self.cols + rx
                ch, c = (chr(0x2800 + self.bits[i]), rgb256(self.col[i])) if self.bits[i] else (' ', None)
                if runs and runs[-1][1] == c:
                    runs[-1] = (runs[-1][0] + ch, c)
                else:
                    runs.append((ch, c))
            out.append(runs)
        return out


def make_grad(acc):
    stops = [lerp(BASE, acc[0], 0.25), lerp(BASE, acc[0], 0.65), acc[0], acc[1], WHITE]

    def g(t):
        f = max(0.0, min(0.9999, t)) * (len(stops) - 1)
        i = int(f)
        return lerp(stops[i], stops[i + 1], f - i)
    return [g(i / 63) for i in range(64)]


R1, R2, K2 = 1.0, 2.0, 5.0
TT = [(math.cos(i * 0.13), math.sin(i * 0.13)) for i in range(49)]
PP = [(math.cos(j * 0.065), math.sin(j * 0.065)) for j in range(98)]


def anim_torus(t, dt, st, acc):
    cv = Canvas()
    W, H = cv.W, cv.H
    grad = st.setdefault('grad', make_grad(acc))
    K1, cx, cy = W * 0.62, W / 2, H / 2
    A, B = t * 0.9 + 0.7, t * 0.55
    sA, cA, sB, cB = math.sin(A), math.cos(A), math.sin(B), math.cos(B)
    zb = {}
    for cT, sT in TT:
        cxx, cyy = R2 + R1 * cT, R1 * sT
        for cP, sP in PP:
            x = cxx * (cB * cP + sA * sB * sP) - cyy * cA * sB
            y = cxx * (sB * cP - sA * cB * sP) + cyy * cA * cB
            z = K2 + cA * cxx * sP + cyy * sA
            ooz = 1 / z
            xp, yp = int(cx + K1 * ooz * x), int(cy - K1 * ooz * y)
            if 0 <= xp < W and 0 <= yp < H and ooz > zb.get((xp, yp), 0):
                L = cP * cT * sB - cA * cT * sP - sA * sT + cB * (cA * sT - cT * sA * sP)
                zb[(xp, yp)] = ooz
                cv.pset(xp, yp, grad[int((L + 1.414) / 2.828 * 63)], ooz)
    return cv.cells()


CUBE_V = [(x, y, z) for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]
CUBE_E = [(a, b) for a in range(8) for b in range(a + 1, 8) if sum(1 for k in range(3) if CUBE_V[a][k] != CUBE_V[b][k]) == 1]


def anim_cube(t, dt, st, acc):
    cv = Canvas()
    ay, ax, az = t * 0.9, t * 0.6, t * 0.35
    pts = []
    for x, y, z in CUBE_V:
        x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
        y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
        x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
        s = 12 / (1 - z * 0.12)
        pts.append((16 + x * s, 16 + y * s, z))
    for a, b in CUBE_E:
        d = (pts[a][2] + pts[b][2]) / 4 + 0.5
        cv.line(pts[a][0], pts[a][1], pts[b][0], pts[b][1], lerp(acc[0], acc[1], d), d)
    for p in pts:
        cv.pset(p[0], p[1], WHITE, 5)
        cv.pset(p[0] + 1, p[1], WHITE, 5)
    return cv.cells()


def anim_helix(t, dt, st, acc):
    cv = Canvas()
    for y in range(1, 31):
        ph = y * 0.33 + t * 3.0
        for k in (0, 1):
            s, z = math.sin(ph + k * math.pi), math.cos(ph + k * math.pi)
            x, c = 16 + 11 * s, lerp(acc[k], WHITE, (z + 1) / 2 * 0.7)
            cv.pset(x, y, c, z)
            cv.pset(x + 1, y, c, z)
        if y % 4 == 0:
            cv.line(16 + 11 * math.sin(ph), y, 16 + 11 * math.sin(ph + math.pi), y, lerp((69, 71, 90), acc[0], 0.5), -2)
    return cv.cells()


def anim_stars(t, dt, st, acc):
    cv = Canvas()
    stars = st.get('stars')
    if stars is None:
        stars = st['stars'] = [[random.uniform(-1, 1), random.uniform(-1, 1), random.uniform(0.1, 1.0)] for _ in range(70)]
    for s in stars:
        s[2] -= dt * 0.45
        if s[2] <= 0.06:
            s[0], s[1], s[2] = random.uniform(-1, 1), random.uniform(-1, 1), 1.0
        x0, y0 = 16 + s[0] / s[2] * 9, 16 + s[1] / s[2] * 9
        z2 = s[2] + 0.07
        b = 1 - s[2]
        cv.line(16 + s[0] / z2 * 9, 16 + s[1] / z2 * 9, x0, y0, lerp(acc[0], WHITE, b), b)
    return cv.cells()


def anim_lissajous(t, dt, st, acc):
    cv = Canvas()
    for k in range(90):
        tt = t * 1.4 - k * 0.025
        x, y, f = 16 + 14 * math.sin(3 * tt + t * 0.3), 16 + 14 * math.sin(2 * tt), 1 - k / 90
        cv.pset(x, y, WHITE if f > 0.97 else lerp(BASE, lerp(acc[0], acc[1], 1 - f), f ** 0.5), f)
        if f > 0.8:
            cv.pset(x + 1, y, WHITE, f)
    return cv.cells()


ANIMS = {'torus': anim_torus, 'cube': anim_cube, 'helix': anim_helix, 'stars': anim_stars, 'lissajous': anim_lissajous}


class ArtPlayer:
    """Играет одну анимацию, раз в ~16 секунд меняет её и цвета."""

    def __init__(self):
        forced = os.environ.get('DEV_ANIM')
        self.forced = forced if forced in ANIMS else None
        self.name = self.forced or random.choice(list(ANIMS))
        self.acc, self.state = random.choice(PALETTES), {}
        self.t0 = self.last = self.switched = time.time()

    def frame(self):
        now = time.time()
        if not self.forced and now - self.switched > 16:
            self.name = random.choice([n for n in ANIMS if n != self.name])
            self.acc, self.state, self.switched = random.choice(PALETTES), {}, now
        dt, self.last = min(0.1, now - self.last), now
        return ANIMS[self.name](now - self.t0, dt, self.state, self.acc)


# ---------- цвета и рисование ----------

THEME256 = {'black': 235, 'red': 211, 'green': 151, 'yellow': 223, 'blue': 111, 'magenta': 183, 'cyan': 183,
            'gray': 146, 'darkgray': 243, 'white': 231, 'darkcyan': 104, 'darkyellow': 180, 'darkblue': 60,
            'darkred': 167, 'teal': 116, 'peach': 216, 'pink': 218, 'mauve': 183, 'dim': 238, 'edge': 61}
BASIC16 = {'black': 0, 'red': 1, 'green': 2, 'yellow': 3, 'blue': 4, 'magenta': 5, 'cyan': 6, 'gray': 7,
           'darkgray': 8, 'white': 15, 'darkcyan': 6, 'darkyellow': 3, 'darkblue': 4, 'darkred': 1}
BRIGHT = {'red': 9, 'green': 10, 'yellow': 11, 'cyan': 14}
_pairs = {}


def _pair(f, b):
    key = (f, b)
    if key not in _pairs:
        _pairs[key] = len(_pairs) + 1
        curses.init_pair(_pairs[key], f, b)
    return curses.color_pair(_pairs[key])


def attr(fg='gray', bg=None, bold=False):
    if not curses.has_colors():
        return curses.A_BOLD if bold else 0
    n = curses.COLORS
    if n >= 256:
        f = THEME256.get(fg, 146)
        b = -1 if bg is None else THEME256.get(bg, 235)
        return _pair(f, b) | (curses.A_BOLD if bold else 0)
    f = BASIC16.get(fg, 7)
    if n >= 16 and fg in BRIGHT:
        f = BRIGHT[fg]
    elif n < 16 and fg in ('darkgray', 'white'):
        f, bold = 7, bold or fg == 'white'
    b = -1 if bg is None else BASIC16.get(bg, 0)
    return _pair(f, b) | (curses.A_BOLD if bold else 0)


def attr_rgb(index):
    return _pair(index, -1)


class UI:
    def __init__(self, scr):
        self.scr = scr
        self.detached = False
        self.memory = {}
        self.pending = None
        self.frame = 0
        self.last_crumb = ''
        self.last_task = None
        self.art = ArtPlayer()
        self.art_off = bool(os.environ.get('DEV_NO_ART'))

    def size(self):
        return self.scr.getmaxyx()

    def width(self):
        return max(40, min(self.size()[1] - 1, 124))

    def art_ok(self, width, h):
        return (not self.art_off) and width >= 88 and h >= 26 and curses.COLORS >= 256

    def put(self, y, x, text, a=0):
        h, w = self.size()
        if y < 0 or y >= h or x >= w:
            return
        try:
            self.scr.addstr(y, x, text[:max(0, w - x - 1)], a)
        except curses.error:
            pass

    def draw_art(self, y, x, cells):
        for i, runs in enumerate(cells):
            cx = x
            for text, c in runs:
                self.put(y + i, cx, text, 0 if c is None else attr_rgb(c))
                cx += len(text)

    def rule(self, y, kind, width):
        left, right = {'top': ('╭', '╮'), 'mid': ('├', '┤'), 'bottom': ('╰', '╯')}[kind]
        self.put(y, 0, left + '─' * (width - 2) + right, attr('edge'))

    def row(self, y, segments, width, bg=None):
        """Строка рамки: сегменты (текст, цвет[, цвет фона]) внутри │ ... │, добивается пробелами."""
        edge = attr('edge')
        self.put(y, 0, '│', edge)
        self.put(y, width - 1, '│', edge)
        self.put(y, 1, ' ' * (width - 2), attr('gray', bg) if bg else 0)
        x = 2
        for seg in segments:
            text, color = seg[0], seg[1]
            seg_bg = seg[2] if len(seg) > 2 else bg
            room = width - 2 - x
            if room <= 0:
                break
            self.put(y, x, text[:room], attr(color, seg_bg, bold=color == 'white' and not seg_bg))
            x += len(text)
        return y + 1

    # -- ввод --
    def key(self, timeout=100):
        if self.pending is not None:
            k, self.pending = self.pending, None
            return k
        self.scr.timeout(timeout)
        try:
            k = self.scr.get_wch()
        except curses.error:
            return None
        if isinstance(k, str):
            if k in ('\n', '\r'):
                return 'ENTER'
            if k == '\x1b':
                return 'ESC'
            if k in ('\x7f', '\b'):
                return 'BACK'
            return k
        if k == curses.KEY_MOUSE:
            try:
                _, mx, my, _, state = curses.getmouse()
            except curses.error:
                return None
            if state & getattr(curses, 'BUTTON4_PRESSED', 0x10000):
                return 'UP'
            if state & getattr(curses, 'BUTTON5_PRESSED', 0x200000):
                return 'DOWN'
            if state & (curses.BUTTON1_CLICKED | curses.BUTTON1_PRESSED):
                return ('CLICK', my, mx)
            return None
        return {curses.KEY_UP: 'UP', curses.KEY_DOWN: 'DOWN', curses.KEY_LEFT: 'LEFT', curses.KEY_RIGHT: 'RIGHT',
                curses.KEY_ENTER: 'ENTER', curses.KEY_BACKSPACE: 'BACK', curses.KEY_PPAGE: 'PGUP',
                curses.KEY_NPAGE: 'PGDN', curses.KEY_HOME: 'HOME', curses.KEY_END: 'END',
                curses.KEY_RESIZE: 'RESIZE'}.get(k)

    def drain_arrows(self):
        """Удержание стрелки копит очередь нажатий: лишние повторы выбрасываются."""
        while True:
            self.scr.timeout(0)
            try:
                k = self.scr.get_wch()
            except curses.error:
                return
            if k in (curses.KEY_UP, curses.KEY_DOWN):
                continue
            self.pending = {curses.KEY_LEFT: 'LEFT', curses.KEY_RIGHT: 'RIGHT'}.get(k, k if isinstance(k, str) else None)
            return

    # -- шапка --
    def task_segments(self):
        running = running_tasks()
        unseen = [t for t in TASKS if t.status != 'running' and not t.seen]
        if not running and not unseen:
            return None
        spin = '⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'[self.frame % 10]
        segs = [('задачи  ', 'darkgray')]
        for t in running[:2]:
            p = t.percent()
            segs.append((f'{spin} {t.title} {"..." if p is None else str(p) + "%"} {t.elapsed()}   ', 'yellow'))
        if len(running) > 2:
            segs.append((f'+{len(running) - 2}   ', 'yellow'))
        for t in unseen[:2]:
            segs.append({'done': (f'✔ {t.title}   ', 'green'), 'cancelled': (f'○ {t.title}   ', 'darkgray')}
                        .get(t.status, (f'✖ {t.title}   ', 'red')))
        segs.append(('T  открыть', 'cyan'))
        return segs

    def draw_top(self, crumb, width):
        y = 0
        self.rule(y, 'top', width)
        y += 1
        y = self.row(y, [(' pnFolder ', 'black', 'cyan'), ('  рабочее окружение, релизов нет', 'darkgray')], width)
        clock = datetime.datetime.now().strftime('%H:%M:%S')
        self.put(y - 1, width - 2 - len(clock) - 1, clock, attr('darkgray'))
        ch = INFO.get('changes', 0)
        y = self.row(y, [('ветка ', 'darkgray'), (INFO.get('branch', ''), 'green'), ('   изменений ', 'darkgray'),
                         (str(ch), 'yellow' if ch else 'green')], width)
        guard = ('защита вкл', 'green') if INFO.get('guard') else ('защита выкл', 'yellow')
        java = (f'java {JDK21_VERSION}', 'green') if JDK21 else ('JDK 21 не найдена', 'red')
        y = self.row(y, [('сборка ', 'darkgray'), (INFO.get('build', ''), 'green' if INFO.get('built') else 'darkgray'),
                         ('   ', 'darkgray'), guard, ('   ', 'darkgray'), java], width)
        segs = self.task_segments()
        if segs:
            y = self.row(y, segs, width)
        self.rule(y, 'mid', width)
        y += 1
        parts, crumb_segs = crumb.split(CRUMB_SEP), []
        for i, part in enumerate(parts):
            if i:
                crumb_segs.append((' › ', 'darkgray'))
            crumb_segs.append((part, 'white' if i == len(parts) - 1 else 'darkcyan'))
        return self.row(y, crumb_segs, width)

    # -- меню --
    def select(self, crumb, items, hint='', start=0, live=None):
        """Меню на весь экран. items: строки «Название|пояснение». Возвращает индекс или -1 («назад»)."""
        self.last_crumb = crumb
        pos = start if start > 0 else self.memory.get(crumb, 0)
        offset, flt, typing = 0, '', False
        while True:
            update_tasks()
            self.frame += 1
            its = live() if live else items
            titles = [i.split('|', 1)[0] for i in its]
            details = [i.split('|', 1)[1] if '|' in i else '' for i in its]
            idxs = [i for i in range(len(titles)) if not flt or flt.lower() in (titles[i] + ' ' + details[i]).lower()]
            n = len(idxs)
            pos = max(0, min(pos, n - 1)) if n else 0
            width, (h, _) = self.width(), self.size()
            art_on = self.art_ok(width, h)
            visible = max(5, h - 17)
            if pos < offset:
                offset = pos
            if pos >= offset + visible:
                offset = pos - visible + 1
            if n <= visible:
                offset = 0
            last = min(n, offset + visible)
            name_w = max([len(titles[i]) for i in idxs] or [1])
            spacer = [(' ' * (ART_W + 3), 'gray')] if art_on else []
            self.scr.erase()
            y = self.draw_top(crumb, width)
            body, click_rows = [[('', 'gray')]], {}
            if offset > 0:
                body.append(spacer + [(f'    ▲ ещё {offset} выше', 'darkgray')])
            for k in range(offset, last):
                i = idxs[k]
                num = f'{k + 1} ' if k < 9 else '  '
                if k == pos:
                    body.append(spacer + [('► ', 'black', 'cyan'), (num, 'black', 'cyan'), (' ', 'black', 'cyan'),
                                          (titles[i].ljust(name_w), 'black', 'cyan'), (f'   {details[i]}', 'darkblue', 'cyan'),
                                          (' ' * 200, 'black', 'cyan')])
                else:
                    body.append(spacer + [('  ', 'gray'), (num, 'darkgray'), (' ', 'gray'), (titles[i].ljust(name_w), 'white'),
                                          (f'   {details[i]}', 'darkgray')])
                click_rows[y + len(body) - 1] = k
            if not n:
                body.append(spacer + [('  ничего не найдено', 'darkyellow')])
            if last < n:
                body.append(spacer + [(f'    ▼ ещё {n - last} ниже', 'darkgray')])
            while art_on and len(body) < ART_H + 1:
                body.append([('', 'gray')])
            body.append([('', 'gray')])
            if typing:
                body.append([('поиск: ', 'darkyellow'), (flt, 'white'), ('█', 'cyan')])
            elif flt:
                body.append([('фильтр: ', 'darkyellow'), (flt, 'white'), ('   Esc сбросить', 'darkgray')])
            elif hint:
                body.append([(hint, 'darkyellow')])
            art_y = y + 1
            for segs in body:
                y = self.row(y, segs, width)
            if art_on:
                self.draw_art(art_y, 3, self.art.frame())
            self.rule(y, 'mid', width)
            y += 1
            keys = [('↑↓', 'cyan'), (' выбор   ', 'darkgray'), ('Enter', 'cyan'), (' открыть   ', 'darkgray'),
                    ('Esc', 'cyan'), (' назад   ', 'darkgray'), ('1-9', 'cyan'), (' быстро   ', 'darkgray')]
            if len(titles) > 7:
                keys += [('/', 'cyan'), (' поиск   ', 'darkgray')]
            keys += [('T', 'cyan'), (' задачи   ', 'darkgray'), ('?', 'cyan'), (' справка', 'darkgray'),
                     (f'   {pos + 1 if n else 0}/{n}', 'darkgray')]
            y = self.row(y, keys, width)
            self.rule(y, 'bottom', width)
            self.scr.refresh()

            k = self.key(60 if (art_on or running_tasks()) else 120)
            if k is None or k == 'RESIZE':
                continue
            if k in ('UP', 'DOWN'):
                self.drain_arrows()
            if typing:
                if k == 'ENTER':
                    typing = False
                elif k == 'ESC':
                    typing, flt = False, ''
                elif k == 'BACK':
                    flt = flt[:-1]
                elif isinstance(k, str) and len(k) == 1 and k.isprintable():
                    flt += k
                    pos = 0
                continue
            if isinstance(k, tuple):                                   # клик мышью
                row = click_rows.get(k[1])
                if row is not None and n:
                    self.memory[crumb] = row
                    return idxs[row]
                continue
            if k == 'UP':
                pos = pos - 1 if pos > 0 else max(0, n - 1)
            elif k == 'DOWN':
                pos = pos + 1 if pos < n - 1 else 0
            elif k == 'PGUP':
                pos = max(0, pos - visible)
            elif k == 'PGDN':
                pos = min(max(0, n - 1), pos + visible)
            elif k == 'HOME':
                pos = 0
            elif k == 'END':
                pos = max(0, n - 1)
            elif k in ('ENTER', 'RIGHT') and n:
                self.memory[crumb] = pos
                return idxs[pos]
            elif k in ('ESC', 'BACK', 'LEFT'):
                if flt and k != 'LEFT':
                    flt, pos = '', 0
                else:
                    return -1
            elif k == '/' and len(titles) > 7:
                typing = True
            elif k == '?':
                show_help(self)
            elif isinstance(k, str) and k in 'tTеЕ':
                menu_tasks(self)
            elif isinstance(k, str) and k in '123456789' and int(k) - 1 < n:
                self.memory[crumb] = int(k) - 1
                return idxs[int(k) - 1]

    def text_screen(self, crumb, lines, hint='Esc или Enter  назад   ↑↓ PgUp PgDn  прокрутка'):
        action = Action(self, crumb)
        for l in lines:
            action.say(*(l if isinstance(l, tuple) else (l, 'gray')))
        action.wait(hint)

    def splash(self):
        """Короткая заставка при запуске; любая клавиша пропускает."""
        if self.art_off or curses.COLORS < 256:
            return
        end, total = time.time() + 1.4, 1.4
        while time.time() < end:
            h, w = self.size()
            if w < ART_W + 10 or h < ART_H + 6:
                return
            self.scr.erase()
            y0, x0 = h // 2 - 6, (w - ART_W) // 2
            self.draw_art(y0, x0, self.art.frame())
            title = 'pnFolder dev'
            self.put(y0 + ART_H + 1, (w - len(title)) // 2, title, attr('cyan', bold=True))
            done = 1 - (end - time.time()) / total
            bar = '━' * int(done * 24) + '╌' * (24 - int(done * 24))
            self.put(y0 + ART_H + 3, (w - 24) // 2, bar, attr('mauve'))
            self.put(y0 + ART_H + 4, (w - 24) // 2, 'загружаю меню…'.center(24), attr('darkgray'))
            self.scr.refresh()
            if self.key(40) is not None:
                return


class Action:
    """Экран действия: шапка, под ней вывод; вопросы и подтверждения внизу."""

    def __init__(self, ui, crumb):
        self.ui, self.crumb, self.lines, self.top = ui, crumb, [], None

    def say(self, text='', color='gray'):
        self.lines.append((text, color))

    def say_segs(self, segs):
        self.lines.append(list(segs))

    def say_all(self, lines):
        for l in lines:
            self.say(l)

    def render(self, prompt=None, hint=None):
        ui = self.ui
        ui.scr.erase()
        h, _ = ui.size()
        width = ui.width()
        y = ui.draw_top(self.crumb, width)
        ui.rule(y, 'bottom', width)
        body_y, avail = y + 2, max(3, h - y - 5)
        wrapped = []
        for item in self.lines:
            if isinstance(item, list):
                wrapped.append(item)
                continue
            text, color = item
            chunks = [text[i:i + width - 6] for i in range(0, max(1, len(text)), width - 6)] or ['']
            wrapped.extend((c, color) for c in chunks)
        start = max(0, len(wrapped) - avail) if self.top is None else max(0, min(self.top, max(0, len(wrapped) - avail)))
        for i, item in enumerate(wrapped[start:start + avail]):
            if isinstance(item, list):
                x = 2
                for text, color in item:
                    ui.put(body_y + i, x, text, attr(color))
                    x += len(text)
            else:
                ui.put(body_y + i, 2, item[0], attr(item[1]))
        if prompt:
            ui.put(h - 2, 2, prompt, attr('yellow'))
        elif hint:
            ui.put(h - 2, 2, hint, attr('darkgray'))
        ui.scr.refresh()
        return len(wrapped), avail

    def ask(self, question):
        buf = ''
        while True:
            self.render(prompt=f'{question}: {buf}█')
            k = self.ui.key(200)
            if k is None or k == 'RESIZE' or isinstance(k, tuple):
                continue
            if k == 'ENTER':
                return buf.strip()
            if k == 'ESC':
                return ''
            if k == 'BACK':
                buf = buf[:-1]
            elif isinstance(k, str) and len(k) == 1 and k.isprintable():
                buf += k

    def confirm(self, question):
        while True:
            self.render(prompt=f'{question} (д/н) ')
            k = self.ui.key(200)
            if isinstance(k, str) and len(k) == 1:
                return k.lower() in ('д', 'y', 'l')
            if k in ('ESC', 'ENTER'):
                return False

    def wait(self, hint='Нажми любую клавишу, чтобы вернуться...   ↑↓ PgUp PgDn  прокрутка'):
        while True:
            total, avail = self.render(hint=hint)
            k = self.ui.key(200)
            if k is None or k == 'RESIZE':
                continue
            cur = self.top if self.top is not None else max(0, total - avail)
            if k == 'UP':
                self.top = max(0, cur - 1)
            elif k == 'DOWN':
                self.top = min(max(0, total - avail), cur + 1)
            elif k == 'PGUP':
                self.top = max(0, cur - avail)
            elif k == 'PGDN':
                self.top = min(max(0, total - avail), cur + avail)
            else:
                update_info()
                return


# ---------- экран задачи: живая панель ----------

def draw_bar(percent, cells):
    filled = cells * percent // 100
    return '█' * filled + '░' * (cells - filled)


def watch_task(ui, task, crumb=''):
    crumb = crumb or ui.last_crumb
    while True:
        update_tasks()
        ui.frame += 1
        width, (h, _) = ui.width(), ui.size()
        art_on = ui.art_ok(width, h) and h >= 30
        spacer = [(' ' * (ART_W + 3), 'gray')] if art_on else []
        spin = '⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'[ui.frame % 10]
        state, color = {'running': (spin, 'cyan'), 'done': ('✔', 'green'), 'failed': ('✖', 'red'),
                        'cancelled': ('○', 'darkgray')}[task.status]
        ui.scr.erase()
        y = ui.draw_top(crumb, width)
        art_y = y + 1
        y = ui.row(y, [('', 'gray')], width)
        y = ui.row(y, spacer + [(f'{state} ', color), (task.title, 'white'), (f'   {task.elapsed()}', 'darkgray')], width)
        p, bar_w = task.percent(), max(10, width - 14 - (ART_W + 3 if art_on else 0))
        if p is not None:
            y = ui.row(y, spacer + [(draw_bar(p, bar_w) + ' ', color), (f'{p}%', 'white')], width)
        else:
            cells, spot = [' '] * bar_w, (ui.frame * 2) % bar_w
            for k in range(8):
                cells[(spot + k) % bar_w] = '█'
            y = ui.row(y, spacer + [(''.join(cells) + ' ', color), (f'задач: {task.task_lines}', 'darkgray')], width)
        step = f'шаг {min(task.index + 1, len(task.queue))} из {len(task.queue)}   ' if len(task.queue) > 1 else ''
        if p is None and task.status == 'running':
            step += 'проценты появятся после первого запуска'
        y = ui.row(y, spacer + [(step, 'darkgray')], width)
        y = ui.row(y, spacer + [('сейчас  ', 'darkgray'), (task.current or '…', 'darkcyan')], width)
        y = ui.row(y, spacer + [('задач ', 'darkgray'), (str(task.task_lines), 'white'), ('   предупреждений ', 'darkgray'),
                                (str(task.warns), 'yellow' if task.warns else 'darkgray'), ('   ошибок ', 'darkgray'),
                                (str(task.errors), 'red' if task.errors else 'darkgray')], width)
        eta = task.eta()
        y = ui.row(y, spacer + ([('осталось примерно ', 'darkgray'), (eta, 'white')] if eta else [('', 'gray')]), width)
        while art_on and y < art_y + ART_H:
            y = ui.row(y, [('', 'gray')], width)
        if art_on:
            ui.draw_art(art_y, 3, ui.art.frame())
        ui.rule(y, 'mid', width)
        y += 1
        shown = max(5, min(14, h - y - 5))
        lines = task.lines()[-shown:]
        for i in range(shown):
            y = ui.row(y, [(lines[i] if i < len(lines) else '', line_color(lines[i]) if i < len(lines) else 'gray')], width)
        ui.rule(y, 'mid', width)
        y += 1
        if task.status == 'running':
            y = ui.row(y, [('Esc', 'cyan'), (' в фон   ', 'darkgray'), ('L', 'cyan'), (' весь лог   ', 'darkgray'),
                           ('C', 'cyan'), (' отменить', 'darkgray')], width)
        else:
            y = ui.row(y, [('Enter', 'cyan'), (' закрыть   ', 'darkgray'), ('L', 'cyan'), (' весь лог с прокруткой', 'darkgray')], width)
        ui.rule(y, 'bottom', width)
        ui.scr.refresh()
        k = ui.key(60 if art_on else 120)
        if k is None or k == 'RESIZE' or isinstance(k, tuple):
            continue
        if isinstance(k, str) and k in 'lLдД':
            show_log(ui, task)
            continue
        if task.status == 'running':
            if k in ('ESC', 'LEFT', 'BACK'):
                ui.detached = True
                return
            if isinstance(k, str) and k in 'cCсС':
                if Action(ui, crumb).confirm('Отменить задачу?'):
                    task.stop()
        elif k in ('ENTER', 'ESC', 'BACK', 'LEFT'):
            break
    task.seen = True
    show_task_result(ui, task, crumb)


def show_task_result(ui, task, crumb):
    a = Action(ui, crumb)
    if task.status == 'done':
        a.say(f'✔ Готово: {task.title} за {task.elapsed()}.', 'green')
        for l in task.report:
            a.say(l)
    elif task.status == 'cancelled':
        a.say('○ Задача отменена.', 'darkgray')
    else:
        a.say(f'✖ Ошибка: {task.title} (код {task.exit_code}). Последние строки лога:', 'red')
        for l in task.lines()[-14:]:
            a.say(l, line_color(l))
        a.say('')
        a.say(f'Полный лог: {task.log_path}', 'darkgray')
    a.wait()


def show_log(ui, task):
    crumb = CRUMB_SEP.join(['Главное меню', 'Задачи', task.title, 'лог'])
    top, follow = 0, task.status == 'running'
    while True:
        update_tasks()
        h, _ = ui.size()
        width, lines = ui.width(), task.lines()
        visible = max(5, h - 12)
        if follow:
            top = max(0, len(lines) - visible)
        top = max(0, min(top, max(0, len(lines) - visible)))
        ui.scr.erase()
        y = ui.draw_top(crumb, width)
        y = ui.row(y, [('', 'gray')], width)
        for i in range(visible):
            j = top + i
            y = ui.row(y, [(lines[j] if j < len(lines) else '', line_color(lines[j]) if j < len(lines) else 'gray')], width)
        ui.rule(y, 'mid', width)
        y += 1
        y = ui.row(y, [('↑↓ PgUp PgDn', 'cyan'), (' прокрутка   ', 'darkgray'), ('Home/End', 'cyan'), (' в начало/конец   ', 'darkgray'),
                       ('Esc', 'cyan'), (' назад', 'darkgray'), (f'   {top + 1}-{min(len(lines), top + visible)} из {len(lines)}', 'darkgray')], width)
        ui.rule(y, 'bottom', width)
        ui.scr.refresh()
        k = ui.key(150)
        if k is None or k == 'RESIZE' or isinstance(k, tuple):
            continue
        if k in ('ESC', 'BACK', 'LEFT', 'ENTER'):
            return
        follow = False
        if k == 'UP':
            top -= 1
        elif k == 'DOWN':
            top += 1
        elif k == 'PGUP':
            top -= visible
        elif k == 'PGDN':
            top += visible
        elif k == 'HOME':
            top = 0
        elif k == 'END':
            follow = True


def step(ui, label, specs, after=None, context=None, crumb=''):
    """Запускает сборку в фоне и показывает её ход. True при успехе; если ушли в меню, ui.detached = True."""
    task = Task(label, specs, after, context)
    ui.last_task = task
    watch_task(ui, task, crumb)
    return task.status == 'done'


def menu_tasks(ui):
    crumb = CRUMB_SEP.join(['Главное меню', 'Задачи'])
    while True:
        def items():
            if not TASKS:
                return ['Задач пока нет|собери что-нибудь: они появятся здесь']
            out = []
            for t in reversed(TASKS):
                icon = {'running': '⠶', 'done': '✔', 'cancelled': '○'}.get(t.status, '✖')
                p = t.percent()
                out.append(f'{icon} {t.title}|{t.elapsed()}' + (f'   {p}%' if p is not None and t.status == 'running' else ''))
            return out
        pick = ui.select(crumb, items(), 'Enter открывает задачу: ход, лог, отмена.', 0, items)
        if pick < 0 or not TASKS:
            return
        watch_task(ui, list(reversed(TASKS))[pick], crumb)
        ui.detached = False


# ---------- действия ----------

def run_sync(args, cwd=ROOT):
    r = subprocess.run(args, cwd=str(cwd), capture_output=True, text=True, errors='replace', env=task_env())
    return (r.stdout + r.stderr).splitlines(), r.returncode


def run_dev(command, *more):
    return run_sync(['bash', str(DEV_SH), command, *more])[0]


def select_plugin(ui, crumb):
    labels = ['Все плагины|подряд, по одному'] + [f'{p}|ветка {branch_of(ROOT / p)}' for p in PLUGINS]
    pick = ui.select(crumb, labels)
    if pick < 0:
        return None
    return ['all'] if pick == 0 else [PLUGINS[pick - 1]]


def connect_entries(key, extra):
    entries = [('pnlibrary-api', 'compileOnly'), (f'pnlibrary-{key}-api', 'compileOnly'), (f'pnlibrary-bootstrap-{key}', 'implementation')]
    return entries + [(i, 'compileOnly') for i in extra]


def connect_text(fmt, entries):
    v = LOCAL_VERSION
    if fmt == 'kotlin':
        return ['repositories { mavenLocal() }', 'dependencies {'] + [f'    {s}("io.github.pnfolder:{i}:{v}")' for i, s in entries] + ['}']
    if fmt == 'groovy':
        return ['repositories { mavenLocal() }', 'dependencies {'] + [f"    {s} 'io.github.pnfolder:{i}:{v}'" for i, s in entries] + ['}']
    return [f'<dependency><groupId>io.github.pnfolder</groupId><artifactId>{i}</artifactId><version>{v}</version>'
            + ('<scope>provided</scope>' if s == 'compileOnly' else '') + '</dependency>' for i, s in entries]


def show_connect(a, key, extra=()):
    entries = connect_entries(key, list(extra))
    a.say('Сначала собери библиотеку «для плагинов». Она лежит в локальном Maven, поэтому нужен mavenLocal().', 'darkgray')
    for title, fmt in (('Gradle (Kotlin, build.gradle.kts)', 'kotlin'), ('Gradle (Groovy, build.gradle)', 'groovy'),
                       ('Maven (pom.xml, в <dependencies>; репозиторий ~/.m2 подхватывается сам)', 'maven')):
        a.say()
        a.say(title, 'white')
        for l in connect_text(fmt, entries):
            a.say(l, 'cyan')
    a.say()
    a.say('common, config-api и localization-api уже входят в pnlibrary-api: галочки нужны, только если хочешь указать их явно.', 'darkgray')
    a.say('Без правки build-файлов плагина: пункт «Проверить плагины» подставляет эту версию сам.', 'darkgray')


def copy_to_clipboard(text):
    for cmd in (['wl-copy'], ['xclip', '-selection', 'clipboard'], ['xsel', '--clipboard', '--input']):
        if shutil.which(cmd[0]):
            try:
                subprocess.run(cmd, input=text, text=True, timeout=5, check=True)
                return True
            except (OSError, subprocess.SubprocessError):
                continue
    try:  # запасной путь: терминал сам кладёт текст в буфер (OSC 52)
        sys.stdout.write('\033]52;c;' + base64.b64encode(text.encode()).decode() + '\a')
        sys.stdout.flush()
        return True
    except OSError:
        return False


def newest(directory, mask, skip=('full',)):
    files = [f for f in Path(directory).glob(mask) if not any(s in f.name for s in skip)]
    return max(files, key=lambda f: f.stat().st_mtime) if files else None


def after_library_jar(task):
    TEST_BUILD.mkdir(parents=True, exist_ok=True)
    platform = task.context['platform']
    jar = newest(LIB / 'distribution/build/libs', platform['mask'])
    if not jar:
        return ['Jar не найден после сборки.']
    for old in TEST_BUILD.glob(platform['mask'].replace('.jar', '-local.jar')):
        old.unlink()
    target = TEST_BUILD / (jar.stem + '-local.jar')
    shutil.copy2(jar, target)
    task.files = [target]
    return [f'Готово: {target}  ({target.stat().st_size / 1048576:.1f} МБ)']


def after_plugin_jars(task):
    after_library_jar(task)
    files = list(task.files)
    plugin_dir = ROOT / task.context['plugin'] / 'build/libs'
    candidates = [f for f in plugin_dir.glob('*.jar') if not re.search(r'plain|sources|javadoc', f.name)]
    if candidates:
        jar = max(candidates, key=lambda f: f.stat().st_mtime)
        shutil.copy2(jar, TEST_BUILD / jar.name)
        files.append(TEST_BUILD / jar.name)
    task.files = files
    return [f'Файлы в {TEST_BUILD}'] + [f'· {f.name}  ({f.stat().st_size / 1048576:.1f} МБ)' for f in files]


def build_library_jar(ui, platform, crumb):
    ok = step(ui, f'jar библиотеки: {platform["name"]}', [gradle_spec(LIB, [f':distribution:{platform["task"]}', '--offline'])],
              after_library_jar, {'platform': platform}, crumb)
    return ui.last_task.files[0] if ok and ui.last_task.files else None


def build_jars(ui, plugin, crumb):
    platform = LIBRARY_PLATFORMS[0]
    gtask = 'shadowJar' if (ROOT / plugin / 'build.gradle.kts').exists() else 'build'
    specs = [gradle_spec(LIB, [f':distribution:{platform["task"]}', '--offline']),
             gradle_spec(ROOT / plugin, [gtask, '-x', 'test', '-I', str(HERE / 'local.init.gradle')])]
    ok = step(ui, f'jar плагина {plugin}', specs, after_plugin_jars, {'platform': platform, 'plugin': plugin}, crumb)
    return list(ui.last_task.files) if ok and ui.last_task.files else None


def deploy_jars(a, files):
    folder = get_server_plugins()
    if not folder:
        a.say('Папка сервера не задана. Укажи её в пункте «Папка plugins».', 'yellow')
        return
    a.say(f'Ставлю в: {folder}', 'white')
    a.say()
    old = []
    for f in files:
        prefix = Path(f).name.split('-')[0]
        old += [o for o in Path(folder).glob(f'{prefix}-*.jar')
                if o.name != Path(f).name and re.match(rf'{re.escape(prefix)}-\d', o.name) and 'updater' not in o.name]
    if old:
        a.say('Уже лежат другие версии:', 'yellow')
        for o in old:
            a.say(f'· {o.name}')
        if a.confirm('Убрать их в папку _old-pnfolder (не удаляя)? Иначе будут дубли.'):
            backup = Path(folder) / '_old-pnfolder' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
            backup.mkdir(parents=True, exist_ok=True)
            for o in old:
                try:
                    shutil.move(str(o), str(backup / o.name))
                    a.say(f'убрано: {o.name}')
                except OSError as e:
                    a.say(f'не удалось убрать {o.name}: {e}. Останови сервер и повтори.', 'red')
    for f in files:
        try:
            shutil.copy2(f, folder)
            a.say(f'положено: {Path(f).name}', 'green')
        except OSError as e:
            a.say(f'не удалось положить {Path(f).name}: {e}. Останови сервер и повтори.', 'red')
    a.say()
    a.say('Теперь запусти сервер. В консоли должны появиться строки Enabling pnLibrary и Enabling твоего плагина.', 'cyan')


# ---------- меню ----------

def menu_branch(ui):
    crumb = CRUMB_SEP.join(['Главное меню', 'Ветка и изменения'])
    while True:
        pick = ui.select(crumb, [
            'Что изменилось|список файлов и размер правок',
            'Сохранить у себя|локальный коммит, на GitHub не уходит',
            'Отменить последний коммит|правки останутся в файлах',
            'Переключить ветку|между ветками этого репозитория',
            'Новая ветка|создать от текущей и перейти в неё',
            'История|последние 20 коммитов',
            'Спрятать правки|stash: убрать на потом, не теряя',
            'Вернуть спрятанные|достать последний stash',
            'Выбросить все правки|без возврата, с подтверждением',
        ], 'Коммиты остаются у тебя. На GitHub уходит только то, что отправишь сам.')
        if pick < 0:
            return
        a = Action(ui, crumb + CRUMB_SEP + 'действие')
        if pick == 0:
            if changes_of(LIB) == 0:
                a.say('Изменений нет, всё сохранено.', 'green')
            else:
                a.say_all(git(['status', '--short']))
                a.say()
                a.say_all(git(['--no-pager', 'diff', '--stat']))
        elif pick == 1:
            if changes_of(LIB) == 0:
                a.say('Нечего сохранять.', 'yellow')
            else:
                a.say_all(git(['status', '--short']))
                msg = a.ask('Описание коммита (пусто = отмена)')
                if msg:
                    git(['add', '-A'])
                    a.say_all(git(['commit', '-q', '-m', msg]))
                    a.say('Сохранено у тебя локально.', 'green')
        elif pick == 2:
            a.say_all(git(['--no-pager', 'log', '--oneline', '-1']))
            if a.confirm('Отменить этот коммит, оставив правки в файлах?'):
                a.say_all(git(['reset', '--soft', 'HEAD~1']))
                a.say('Готово.', 'green')
        elif pick == 3:
            branches = [b for b in git(['branch', '--format=%(refname:short)']) if b.strip()]
            choice = ui.select(crumb + CRUMB_SEP + 'ветки', branches)
            if choice < 0:
                continue
            a = Action(ui, crumb + CRUMB_SEP + 'переключение')
            if changes_of(LIB) > 0:
                a.say('Есть несохранённые правки: сначала сохрани их или спрячь.', 'yellow')
            else:
                a.say_all(git(['switch', branches[choice]]))
        elif pick == 4:
            name = a.ask('Имя новой ветки, например dev/api-events (пусто = отмена)')
            if name:
                a.say_all(git(['switch', '-c', name]))
        elif pick == 5:
            a.say_all(git(['--no-pager', 'log', '--oneline', '--decorate', '-20']))
        elif pick == 6:
            a.say_all(git(['stash', 'push', '-u', '-m', 'dev-menu']))
        elif pick == 7:
            a.say_all(git(['stash', 'pop']))
        elif pick == 8:
            a.say_all(git(['status', '--short']))
            if a.confirm('Выбросить ВСЕ несохранённые правки? Вернуть их будет нельзя.') and a.confirm('Точно выбросить?'):
                git(['reset', '--hard'])
                git(['clean', '-fd'])
                a.say('Правки выброшены.', 'yellow')
        a.wait()


def menu_connect(ui, crumb):
    platforms = [('Bukkit / Paper / Spigot', 'bukkit'), ('BungeeCord', 'bungee'), ('Velocity', 'velocity')]
    pick = ui.select(crumb + CRUMB_SEP + 'платформа', [f'{n}|строки для Gradle и Maven' for n, _ in platforms])
    if pick < 0:
        return
    name, key = platforms[pick]
    modules = [m for m in OPTIONAL_MODULES if not m['bukkit'] or key == 'bukkit']
    selected, position = set(), 0
    screen = crumb + CRUMB_SEP + name
    while True:
        items = [f'{"[x]" if m["id"] in selected else "[ ]"} {m["title"]}|{m["note"]}' for m in modules]
        items.append('Показать строки подключения|с выбранными модулями')
        choice = ui.select(screen + CRUMB_SEP + 'модули', items,
                           'Основа (api, платформа, bootstrap) подключается всегда. Enter отмечает модуль.', position)
        if choice < 0:
            return
        if choice < len(modules):
            selected ^= {modules[choice]['id']}
            position = choice
            continue
        break
    a = Action(ui, screen + CRUMB_SEP + 'строки')
    extra = [m['id'] for m in modules if m['id'] in selected]
    show_connect(a, key, extra)
    if a.confirm('Скопировать Gradle (Kotlin) в буфер обмена?'):
        ok = copy_to_clipboard('\n'.join(connect_text('kotlin', connect_entries(key, extra))))
        a.say('Скопировано.' if ok else 'Буфер обмена недоступен: скопируй строки выше вручную.', 'green' if ok else 'yellow')
    a.wait()


def menu_library_jar(ui, crumb):
    pick = ui.select(crumb + CRUMB_SEP + 'платформа', [f'{p["name"]}|{p["task"]}' for p in LIBRARY_PLATFORMS])
    if pick < 0:
        return
    platform, sub = LIBRARY_PLATFORMS[pick], crumb + CRUMB_SEP + LIBRARY_PLATFORMS[pick]['name']
    file = build_library_jar(ui, platform, sub)
    if ui.detached:
        ui.detached = False
        return
    if file:
        a = Action(ui, sub)
        a.say(f'Собрано: {file}', 'green')
        if a.confirm('Положить на тестовый сервер прямо сейчас?'):
            deploy_jars(a, [file])
        a.wait()


def menu_jars(ui, crumb):
    target = select_plugin(ui, crumb + CRUMB_SEP + 'плагин')
    if not target or target[0] == 'all':
        return
    sub = crumb + CRUMB_SEP + 'сборка ' + target[0]
    files = build_jars(ui, target[0], sub)
    if ui.detached:
        ui.detached = False
        return
    if files:
        a = Action(ui, sub)
        a.say('Собрано: ' + ', '.join(f.name for f in files), 'green')
        if a.confirm('Положить их на тестовый сервер прямо сейчас?'):
            deploy_jars(a, files)
        a.wait()


def menu_build(ui):
    crumb = CRUMB_SEP.join(['Главное меню', 'Собрать и проверить'])
    while True:
        pick = ui.select(crumb, [
            'Собрать для плагинов|библиотека в локальный Maven, НЕ jar для сервера',
            'Проверить плагины|сломались ли они от твоих правок API',
            'Быстрые тесты|только модуль core',
            'Полная проверка библиотеки|тесты, стиль, проверка API',
            'Привести стиль в порядок|spotlessApply',
            'Собрать jar библиотеки|pnLibrary для plugins сервера, любая платформа',
            'Собрать jar плагина|плагин вместе с библиотекой для сервера',
            'Как подключить в плагин|строки для Gradle и Maven',
        ], 'Два разных пути: плагины (компиляция) и сервер (jar в plugins). Подробности: Сервер » Как подключить.')
        if pick < 0:
            return
        ui.detached = False
        sub = crumb + CRUMB_SEP + 'выполняется'
        if pick == 0:
            if step(ui, 'Библиотека из твоих правок', [dev_spec('lib-publish')], crumb=sub):
                a = Action(ui, sub)
                a.say('Что дальше:', 'white')
                a.say('· «Проверить плагины»: узнать, не сломали ли правки плагины.')
                a.say('· «Собрать и поставить» на сервер: проверить в игре.')
                a.say(f'· Свой плагин можно собрать вручную: версия библиотеки {LOCAL_VERSION}, репозиторий mavenLocal().')
                if a.confirm('Показать строки подключения для Gradle и Maven?'):
                    show_connect(a, 'bukkit')
                a.wait()
        elif pick == 1:
            target = select_plugin(ui, crumb + CRUMB_SEP + 'какие плагины')
            if not target:
                continue
            sub = crumb + CRUMB_SEP + 'проверка плагинов'
            if not M2_API.exists() and not step(ui, 'Библиотека из твоих правок', [dev_spec('lib-publish')], crumb=sub):
                ui.detached = False
                continue
            step(ui, 'Проверка плагинов', [dev_spec('plugins', *target)], crumb=sub)
        elif pick == 2:
            step(ui, 'Тесты модуля core', [gradle_spec(LIB, [':modules:core:test', '--offline'])], crumb=sub)
        elif pick == 3:
            step(ui, 'Полная проверка библиотеки', [dev_spec('lib-check')], crumb=sub)
        elif pick == 4:
            step(ui, 'Стиль кода (spotless)', [gradle_spec(LIB, ['spotlessApply', '--offline'])], crumb=sub)
        elif pick == 5:
            menu_library_jar(ui, crumb + CRUMB_SEP + 'jar библиотеки')
        elif pick == 6:
            menu_jars(ui, crumb + CRUMB_SEP + 'jar плагина')
        elif pick == 7:
            menu_connect(ui, crumb)
        ui.detached = False


def set_server_folder(a):
    a.say('Сейчас: ' + (get_server_plugins() or 'не задана'))
    path = a.ask('Путь к папке plugins или к папке сервера (пусто = отмена)').strip('"\' ')
    if not path:
        return
    path = os.path.expanduser(path)
    if os.path.isdir(os.path.join(path, 'plugins')):
        path = os.path.join(path, 'plugins')
    if not os.path.isdir(path):
        a.say('Такой папки нет.', 'red')
        return
    config = get_config()
    config['ServerPlugins'] = path
    save_config(config)
    a.say(f'Запомнил: {path}', 'green')


def show_server_jars(a):
    folder = get_server_plugins()
    if not folder:
        a.say('Папка сервера не задана: пункт «Папка plugins».', 'yellow')
        return
    a.say(f'Папка: {folder}', 'white')
    jars = sorted(Path(folder).glob('pn*.jar'))
    if not jars:
        a.say('pnFolder-плагинов там нет.', 'yellow')
        return
    for j in jars:
        st = j.stat()
        a.say(f'· {j.name:<60} {st.st_size / 1048576:.1f} МБ   {datetime.datetime.fromtimestamp(st.st_mtime):%d.%m %H:%M}')
    if len([j for j in jars if j.name.startswith('pnLibrary-') and 'updater' not in j.name]) > 1:
        a.say()
        a.say('Внимание: pnLibrary лежит в нескольких версиях, оставь одну.', 'yellow')


def show_howto(a):
    folder = get_server_plugins() or '(папка не задана: пункт «Папка plugins»)'
    for text, color in [
        ('Два разных результата: не путай', 'white'), ('', 'gray'),
        ('А. «Собрать для плагинов»: библиотека кладётся в локальный Maven (папка ~/.m2 на компьютере).', 'gray'),
        ('   Это НЕ файл для сервера. Он нужен, чтобы плагины компилировались против твоих правок', 'gray'),
        ('   и ты видел, что сломалось. На сервер это не ставится.', 'gray'),
        ('Б. «Собрать jar для сервера»: получаются настоящие jar-файлы (pnLibrary + плагин).', 'gray'),
        ('   Их кладут в plugins сервера, чтобы проверить в игре.', 'gray'), ('', 'gray'),
        ('Как правильно подключить на сервер (путь Б)', 'white'), ('', 'gray'),
        ('1. Собери: «Сервер» → «Собрать и поставить плагин» (или «…библиотеку», если нужна только она).', 'gray'),
        (f'   Меню положит в {TEST_BUILD} две вещи: pnLibrary (…-local.jar) и jar плагина.', 'gray'),
        ('2. Останови сервер, если он держит старые jar.', 'gray'),
        (f'3. В {folder} должен остаться ОДИН pnLibrary-*.jar. Старые меню само уберёт в _old-pnfolder.', 'gray'),
        ('4. Положи оба файла: меню делает это само, когда соглашаешься «Положить на сервер».', 'gray'),
        ('5. Запусти сервер. В логе ищи:', 'gray'),
        ('     [pnLibrary] Enabling pnLibrary v…       библиотека включилась', 'cyan'),
        ('     [<плагин>] Enabling <плагин> v…         плагин включился без LinkageError', 'cyan'),
        ('6. Команда /pn в игре или консоли показывает версию библиотеки и подключённые плагины.', 'gray'), ('', 'gray'),
        ('Если плагин пишет про устаревшую pnLibrary:', 'yellow'),
        ('   на сервере остался старый pnLibrary-jar. Проверь «Что лежит на сервере».', 'gray'), ('', 'gray'),
        (f'Для своего плагина без меню: в build.gradle версия библиотеки {LOCAL_VERSION} и mavenLocal() в repositories.', 'darkgray'),
    ]:
        a.say(text, color)


def menu_server(ui):
    crumb = CRUMB_SEP.join(['Главное меню', 'Сервер'])
    while True:
        pick = ui.select(crumb, [
            f'Папка plugins|{get_server_plugins() or "не задана"}',
            'Собрать и поставить плагин|библиотека из правок и выбранный плагин',
            'Собрать и поставить библиотеку|только pnLibrary, любая платформа',
            'Поставить уже собранное|всё из папки test-build',
            'Что лежит на сервере|pnFolder-jar в plugins, дубли',
            'Как подключить на сервер|пошаговая инструкция',
        ], 'Старые версии не удаляются, а переносятся в _old-pnfolder.')
        if pick < 0:
            return
        if pick in (1, 2):
            (menu_jars if pick == 1 else menu_library_jar)(ui, crumb + CRUMB_SEP + 'jar')
            continue
        a = Action(ui, crumb + CRUMB_SEP + 'результат')
        if pick == 0:
            set_server_folder(a)
        elif pick == 3:
            files = sorted(TEST_BUILD.glob('*.jar')) if TEST_BUILD.exists() else []
            if files:
                deploy_jars(a, files)
            else:
                a.say('В test-build пока пусто: сначала «Собрать и поставить».', 'yellow')
        elif pick == 4:
            show_server_jars(a)
        elif pick == 5:
            show_howto(a)
        a.wait()


def menu_api(ui):
    crumb = CRUMB_SEP.join(['Главное меню', 'API библиотеки'])
    while True:
        pick = ui.select(crumb, [
            'Карта API|пакеты и количество типов',
            'Что изменилось|публичное API по сравнению с релизом dev.6',
            'Принять изменения API|apiDump: обновить эталон',
            'Проверить API|apiCheck: совпадает ли с эталоном',
        ], 'После правок API: «Принять изменения», затем «Проверить плагины» в сборке.')
        if pick < 0:
            return
        sub = crumb + CRUMB_SEP + 'результат'
        if pick in (2, 3):
            ui.detached = False
            step(ui, 'Принять изменения API' if pick == 2 else 'Проверить API',
                 [gradle_spec(LIB, ['apiDump' if pick == 2 else 'apiCheck', '--offline'])], crumb=sub)
            ui.detached = False
            continue
        a = Action(ui, sub)
        if pick == 0:
            a.say_all(run_dev('api-map'))
            try:
                a.say_all((HERE / 'API-MAP.md').read_text(encoding='utf-8').splitlines()[4:])
            except OSError:
                pass
        else:
            a.say_all(run_dev('api-diff'))
        a.wait()


def ru_age(text):
    """«2 hours ago» -> «2 ч назад» (git пишет возраст коммита по-английски)."""
    units = {'second': 'сек', 'minute': 'мин', 'hour': 'ч', 'day': 'дн', 'week': 'нед', 'month': 'мес', 'year': 'г'}
    m = re.match(r'(\d+) (\w+?)s? ago', text)
    return f'{m.group(1)} {units.get(m.group(2), m.group(2))} назад' if m else text


def repo_row(name):
    d = ROOT / name
    if not (d / '.git').exists():
        return None
    ab = ''.join(git(['rev-list', '--left-right', '--count', '@{u}...HEAD'], d)).split()
    behind, ahead = (int(ab[0]), int(ab[1])) if len(ab) == 2 and all(x.isdigit() for x in ab) else (0, 0)
    return {'name': name, 'branch': branch_of(d), 'changes': changes_of(d), 'ahead': ahead, 'behind': behind,
            'age': ru_age(''.join(git(['log', '-1', '--format=%cr'], d)).strip())}


def show_repos(ui, crumb):
    a = Action(ui, crumb)
    a.say('Читаю репозитории…', 'darkgray')
    a.render()
    a.lines = []
    a.say_segs([(f'{"репозиторий":<13}', 'darkgray'), (f'{"ветка":<30}', 'darkgray'), (f'{"правки":<8}', 'darkgray'),
                (f'{"отправить":<11}', 'darkgray'), (f'{"забрать":<9}', 'darkgray'), ('последний коммит', 'darkgray')])
    for name in ['pnLibrary'] + PLUGINS:
        r = repo_row(name)
        if not r:
            a.say_segs([(f'{name:<13}', 'darkgray'), ('нет репозитория рядом', 'darkgray')])
            continue
        a.say_segs([(f'{name:<13}', 'white'), (f'{r["branch"]:<30}', 'green' if r['branch'] not in ("main", "master") else 'gray'),
                    (f'{r["changes"] or "чисто":<8}', 'yellow' if r['changes'] else 'green'),
                    (f'{"↑ " + str(r["ahead"]) if r["ahead"] else "·":<11}', 'peach' if r['ahead'] else 'darkgray'),
                    (f'{"↓ " + str(r["behind"]) if r["behind"] else "·":<9}', 'teal' if r['behind'] else 'darkgray'),
                    (r['age'], 'darkgray')])
    a.say()
    a.say('↑ есть коммиты, которых нет на GitHub   ↓ на GitHub есть новое, чего у тебя нет   зелёная ветка = рабочая (не main)', 'darkgray')
    a.wait()


def open_in_intellij(a):
    for name in ('idea', 'intellij-idea-ultimate', 'intellij-idea-community', 'intellij-idea', 'idea.sh'):
        exe = shutil.which(name) or (f'/snap/bin/{name}' if os.path.exists(f'/snap/bin/{name}') else None)
        if exe:
            subprocess.Popen([exe, str(LIB)], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                             start_new_session=True)
            a.say('IntelliJ открывается.', 'green')
            return
    a.say(f'IntelliJ не найден. Открой папку вручную: File → Open → {LIB}', 'yellow')
    subprocess.Popen(['xdg-open', str(LIB)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)


def menu_other(ui):
    crumb = CRUMB_SEP.join(['Главное меню', 'Прочее'])
    while True:
        pick = ui.select(crumb, [
            'Открыть в IntelliJ|библиотека pnLibrary',
            'Открыть папку проекта|в файловом менеджере',
            'Все репозитории|ветки и изменения плагинов',
            'Отправить ветку на GitHub|не main, с подтверждением',
            'Включить защиту|нельзя пушить в main и теги',
            'Снять защиту|разрешить push в main',
            'Инструкция|как работать с окружением',
            'Анимация|сменить картинку и цвета прямо сейчас',
        ])
        if pick < 0:
            return
        if pick == 2:
            show_repos(ui, crumb + CRUMB_SEP + 'результат')
            continue
        if pick == 7:
            ui.art.forced = None
            ui.art.switched = 0
            continue
        a = Action(ui, crumb + CRUMB_SEP + 'результат')
        if pick == 0:
            open_in_intellij(a)
        elif pick == 1:
            subprocess.Popen(['xdg-open', str(ROOT)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
            a.say('Открыто.', 'green')
        elif pick == 3:
            branch = branch_of(LIB)
            if branch in ('main', 'master'):
                a.say('Нельзя: это основная ветка.', 'red')
            elif a.confirm(f'Отправить ветку {branch} на GitHub?'):
                a.say_all(git(['push', '-u', 'origin', branch]))
        elif pick == 4:
            a.say_all(run_dev('guard'))
        elif pick == 5:
            if a.confirm('Снять защиту? После этого push в main возможен.'):
                a.say_all(run_dev('unguard'))
        elif pick == 6:
            try:
                a.say_all((HERE / 'README.md').read_text(encoding='utf-8').splitlines())
            except OSError:
                a.say('README.md не найден.', 'yellow')
        a.wait()


def show_help(ui):
    ui.text_screen(ui.last_crumb or 'Справка', [
        ('Клавиши', 'white'), ('', 'gray'),
        ('  ↑ ↓ PgUp PgDn Home End   выбор пункта (колесо мыши тоже)', 'gray'),
        ('  Enter или →              открыть пункт (клик мышью тоже)', 'gray'),
        ('  Esc, Backspace или ←     назад; в главном меню закрыть', 'gray'),
        ('  1 … 9                    быстрый выбор по номеру', 'gray'),
        ('  /                        поиск в длинном списке (Enter оставить фильтр, Esc сбросить)', 'gray'),
        ('  T                        список фоновых задач', 'gray'),
        ('  ?                        эта справка', 'gray'), ('', 'gray'),
        ('Сборка идёт в фоне', 'white'), ('', 'gray'),
        ('  Esc в окне сборки        спрятать её в фон, она продолжит работать; ход виден в шапке', 'gray'),
        ('  L                        весь лог с прокруткой;  C  отменить', 'gray'), ('', 'gray'),
        ('Настройки окружения (переменные)', 'white'), ('', 'gray'),
        ('  DEV_NO_ART=1             без анимации (если слабый терминал)', 'gray'),
        ('  DEV_ANIM=torus|cube|helix|stars|lissajous   выбрать анимацию', 'gray'),
        ('  DEV_SIMPLE=1 ./dev.sh    простое меню по номерам', 'gray'),
    ])


def main_menu(ui):
    update_info()
    ui.splash()
    while True:
        def items():
            n = len(running_tasks())
            note = f'идёт сейчас: {n}' if n else 'сборки в фоне: вернуться, посмотреть, отменить'
            return ['Ветка и изменения|смотреть, сохранять, переключать ветки',
                    'Собрать и проверить|библиотека, плагины, jar для сервера',
                    'Сервер|поставить сборку на тестовый сервер, инструкция',
                    'API библиотеки|карта, что изменилось, проверка',
                    'Репозитории|ветки, правки и отставание всех плагинов',
                    'Прочее|IntelliJ, отправка на GitHub, защита, анимация',
                    f'Задачи|{note}']
        pick = ui.select('Главное меню', items(), 'Esc закрывает меню.', 0, items)
        if pick == -1:
            running = running_tasks()
            if running:
                if not Action(ui, 'Главное меню').confirm(f'Ещё идут задачи ({len(running)}). Остановить их и выйти?'):
                    continue
                for t in running:
                    t.stop()
            return
        if pick == 4:
            show_repos(ui, CRUMB_SEP.join(['Главное меню', 'Репозитории']))
        else:
            {0: menu_branch, 1: menu_build, 2: menu_server, 3: menu_api, 5: menu_other, 6: menu_tasks}[pick](ui)


def run(scr):
    curses.curs_set(0)
    if curses.has_colors():
        curses.start_color()
        curses.use_default_colors()
    scr.keypad(True)
    try:
        curses.mousemask(curses.ALL_MOUSE_EVENTS)
        curses.mouseinterval(0)
    except curses.error:
        pass
    main_menu(UI(scr))


if __name__ == '__main__':
    locale.setlocale(locale.LC_ALL, '')
    try:
        curses.wrapper(run)
    except KeyboardInterrupt:
        pass
