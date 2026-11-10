#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_contributors_db.py —— 生成 / 追加贡献者云数据库（contributors.db）

表结构与 LineageParts 完全一致：
    metadata(id, name, username, filter, commits, x, y, w, h, r, fs)
    info(key, value)   ->  orig_size = 画布边长, date = 生成时间(ms)

坐标约定（和 App 里的绘制逻辑一一对应）：
    x, y 是文字的“基线点”；r = 0 横排，-1 逆时针 90°，1 顺时针 90°
    w, h 是旋转之后的包围盒宽高

三种用法：
    # 1) 只用自己的名单（最常用，什么都不用传）
    python3 tools/build_contributors_db.py --standalone --bump-version

    # 2) 换一份名单 / 换个输出位置
    python3 tools/build_contributors_db.py -i tools/other.json -o tools/other.db

    # 3) 在现有全库上追加自己的人
    python3 tools/build_contributors_db.py -b tools/base.db --bump-version

    # 附加：出预览图 / 看库内容
    ... --preview tools/preview.png
    python3 tools/build_contributors_db.py --inspect

目录约定：
    tools/contributors.json               名单，改这个
    tools/contributors.db                 库，脚本产出，唯一真相源
    app/src/main/assets/contributors.db   打包副本，脚本写完会自动同步
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import sqlite3
import sys
import time

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # 预览图可选，缺 Pillow 也能写库
    Image = ImageDraw = ImageFont = None

SCHEMA = """
CREATE TABLE android_metadata (locale TEXT);
CREATE TABLE metadata (
    id NUMBER, name TEXT, username TEXT, filter TEXT,
    commits NUMBER, x NUMBER, y NUMBER, w NUMBER, h NUMBER, r NUMBER, fs REAL
);
CREATE TABLE info (key TEXT, value TEXT);
CREATE INDEX metadata_idx_1 on metadata (name);
CREATE INDEX metadata_idx_2 on metadata (filter);
CREATE INDEX metadata_idx_3 on metadata (x, y, w, h);
CREATE INDEX metadata_idx_4 on metadata (commits);
"""

DEFAULT_SIZE = 2056
MARGIN_RATIO = 0.012
TEXT_WIDTH_FUDGE = 1.08      # DejaVu 比 Roboto 略宽，留余量
CELL = 128                   # 碰撞网格边长

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "C:/Windows/Fonts/arial.ttf",
]


# --------------------------------------------------------------------------- #
# 字体与度量
# --------------------------------------------------------------------------- #

def find_font() -> str | None:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return path
    return None


_FONT_PATH = find_font()
_FONT_CACHE: dict[int, object] = {}


def say(msg: str) -> None:
    """进度写 stderr 并立即刷新：管道/重定向下也能看到，不会像卡死"""
    print(f"[i] {msg}", file=sys.stderr, flush=True)


def font_for(size: float):
    if not _FONT_PATH or ImageFont is None:
        return None
    px = max(1, int(round(size)))
    if px not in _FONT_CACHE:
        _FONT_CACHE[px] = ImageFont.truetype(_FONT_PATH, px)
    return _FONT_CACHE[px]


def measure(name: str, size: float) -> tuple[float, float]:
    """(文本宽, 文本高)，只用于布局，不需要与 Android 完全一致"""
    font = font_for(size)
    if font is None:
        return len(name) * size * 0.56 * TEXT_WIDTH_FUDGE, size * 0.98
    width = font.getlength(name) if hasattr(font, "getlength") else font.getsize(name)[0]
    return max(width * TEXT_WIDTH_FUDGE, size * 0.55), size * 0.98


# --------------------------------------------------------------------------- #
# 碰撞网格
# --------------------------------------------------------------------------- #

class Grid:
    def __init__(self, size: int):
        self.size = size
        self.cells: dict[tuple[int, int], list[tuple[float, float, float, float]]] = {}

    def _keys(self, l: float, t: float, r: float, b: float):
        for gx in range(int(l // CELL), int(r // CELL) + 1):
            for gy in range(int(t // CELL), int(b // CELL) + 1):
                yield gx, gy

    def fits(self, box: tuple[float, float, float, float], gap: float = 2.0) -> bool:
        l, t, r, b = box
        if l < 0 or t < 0 or r > self.size or b > self.size:
            return False
        for key in self._keys(l - gap, t - gap, r + gap, b + gap):
            for other in self.cells.get(key, ()):
                if not (r + gap <= other[0] or l - gap >= other[2] or
                        b + gap <= other[1] or t - gap >= other[3]):
                    return False
        return True

    def add(self, box: tuple[float, float, float, float]):
        for key in self._keys(*box):
            self.cells.setdefault(key, []).append(box)


# --------------------------------------------------------------------------- #
# 布局
# --------------------------------------------------------------------------- #

def half_sizes(text_w: float, text_h: float, rotation: int) -> tuple[float, float]:
    if rotation == 0:
        return text_w / 2.0, text_h / 2.0
    return text_h / 2.0, text_w / 2.0


def center_from_baseline(x: float, y: float, text_w: float, text_h: float,
                         rotation: int) -> tuple[float, float]:
    """
    由基线点算包围盒中心，必须与 App 的 Canvas 变换一致。

    App 里旋转分支是「先 translate 再 rotate(pivot)」，所以文字并不以 (x, y) 为中心：
        r = -1: translate(h, w-h) + rotate(-90) -> 盒子 [x, x+th] x [y-th, y+tw-th]
        r =  +1: translate(h/2, -h) + rotate(+90) -> 盒子 [x+th/2, x+3th/2] x [y-th, y+tw-th]
    """
    if rotation == 0:
        return x + text_w / 2.0, y - text_h / 2.0
    if rotation == -1:
        return x + text_h / 2.0, y + text_w / 2.0 - text_h
    return x + text_h, y + text_w / 2.0 - text_h


def baseline_from_center(cx: float, cy: float, text_w: float, text_h: float,
                         rotation: int) -> tuple[float, float]:
    if rotation == 0:
        return cx - text_w / 2.0, cy + text_h / 2.0
    if rotation == -1:
        return cx - text_h / 2.0, cy - text_w / 2.0 + text_h
    return cx - text_h, cy - text_w / 2.0 + text_h


def spiral_slot(grid: Grid, cx: float, cy: float, half_w: float, half_h: float,
                max_radius: float, seed: float,
                region: tuple[float, float, float, float] | None = None,
                gap: float = 2.0) -> tuple[float, float] | None:
    """阿基米德螺旋：从 (cx, cy) 向外找第一个能放下的位置"""
    # 半径按「角度的相对增量」增长：每转一步就外扩约 0.45 个字高，
    # 保证相邻名字之间留出真实间距，而不是全部堆在圆心附近。
    step = max(6.0, min(half_h, half_w * 0.55) * 1.8)
    theta = seed
    while True:
        radius = step * (theta - seed)
        if radius > max_radius:
            return None
        angle = theta * 2.0
        px = cx + radius * math.cos(angle)
        py = cy + radius * math.sin(angle)
        theta += 0.25
        if region and not (region[0] + half_w <= px <= region[2] - half_w and
                           region[1] + half_h <= py <= region[3] - half_h):
            continue
        if grid.fits((px - half_w, py - half_h, px + half_w, py + half_h), gap=gap):
            return px, py


def border_anchor(index: int, size: float, border: float) -> tuple[float, float]:
    """新增名字在「加边框」模式下分别去上、下、左、右四条边的中点"""
    half = border / 2.0
    return [
        (size / 2.0, half),
        (size / 2.0, size - half),
        (half, size / 2.0),
        (size - half, size / 2.0),
    ][index % 4]


def border_region(index: int, size: float, border: float):
    slot = index % 4
    if slot == 0:
        return 0.0, 0.0, size, border
    if slot == 1:
        return 0.0, size - border, size, size
    if slot == 2:
        return 0.0, border, border, size - border
    return size - border, border, size, size - border


def border_rotation(index: int) -> int:
    """上下横排，左逆时针、右顺时针，读起来方向对称"""
    return [0, 0, -1, 1][index % 4]


def rotation_for(name: str) -> int:
    """按名字哈希确定性地分配旋转，保证每次构建结果一致"""
    digest = hashlib.md5(name.encode("utf-8")).digest()
    return (0, -1, 1)[digest[0] % 3]


def font_size_for(commits: int, max_commits: int, size: int,
                  has_base: bool) -> float:
    if has_base:
        fmin, fmax = size * 0.0065, size * 0.0105
    elif size <= 1400:
        # 只有自己人时，字号占画布的比例可以更大，画面才不会显得空
        fmin, fmax = size * 0.028, size * 0.062
    else:
        fmin, fmax = size * 0.030, size * 0.075
    if max_commits <= 0:
        return fmin
    ratio = math.log1p(max(0, commits)) / math.log1p(max_commits)
    return fmin + (fmax - fmin) * (ratio ** 0.75)


# --------------------------------------------------------------------------- #
# 数据库
# --------------------------------------------------------------------------- #

def read_rows(path: str) -> list[dict]:
    db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        cur = db.execute(
            "select id, name, username, filter, commits, x, y, w, h, r, fs from metadata"
        )
        return [{
            "id": r[0], "name": r[1], "username": r[2], "filter": r[3],
            "commits": r[4] or 0, "x": r[5], "y": r[6], "w": r[7], "h": r[8],
            "r": r[9], "fs": r[10],
        } for r in cur]
    finally:
        db.close()


def read_info(path: str) -> dict:
    try:
        db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        try:
            return {k: v for k, v in db.execute("select key, value from info")}
        except sqlite3.Error:
            return {}
        finally:
            db.close()
    except sqlite3.Error:
        return {}


def box_of(row: dict) -> tuple[float, float, float, float]:
    """从已有行的基线点还原轴对齐包围盒（w/h 是旋转之后的尺寸）

    旋转后 w 已是字高、h 已是字宽：横向与逆时针都以 x 为左端，
    只有顺时针（r=1）的文字向 x 左侧生长。
    """
    x, y, w, h, r = row["x"], row["y"], row["w"], row["h"], row["r"] or 0
    if r == 1:
        return x - w, y - h, x, y
    return x, y - h, x + w, y


def recenter(rows: list[dict], size: float) -> None:
    """把整块内容平移到画布正中：螺旋只朝一边长，不校正会全挤在一个角"""
    boxes = [box_of(r) for r in rows if r.get("name")]
    if not boxes:
        return
    left = min(b[0] for b in boxes)
    right = max(b[2] for b in boxes)
    top = min(b[1] for b in boxes)
    bottom = max(b[3] for b in boxes)
    dx = size / 2.0 - (left + right) / 2.0
    dy = size / 2.0 - (top + bottom) / 2.0
    if abs(dx) < 0.5 and abs(dy) < 0.5:
        return
    for row in rows:
        row["x"] = round(row["x"] + dx, 3)
        row["y"] = round(row["y"] + dy, 3)


def fit_canvas(rows: list[dict], margin_ratio: float = 0.14) -> int:
    """
    把画布裁到刚好包住内容再留一圈边。

    只有自己几个人时，沿用 2056/2376 那种大画布会让整屏有七成是空白；
    裁过之后 fit-to-view 正好铺满，视觉上才像一张图。
    """
    boxes = [box_of(r) for r in rows if r.get("name")]
    if not boxes:
        return 0
    left = min(b[0] for b in boxes)
    right = max(b[2] for b in boxes)
    top = min(b[1] for b in boxes)
    bottom = max(b[3] for b in boxes)
    side = max(right - left, bottom - top)
    if side <= 0:
        return 0
    margin = side * margin_ratio
    size = int(math.ceil(side + margin * 2))
    dx = size / 2.0 - (left + right) / 2.0
    dy = size / 2.0 - (top + bottom) / 2.0
    for row in rows:
        row["x"] = round(row["x"] + dx, 3)
        row["y"] = round(row["y"] + dy, 3)
    return size


def write_db(path: str, rows: list[dict], size: int, extra_info: dict | None = None):
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    try:
        db.executescript(SCHEMA)
        db.execute("insert into android_metadata(locale) values ('en_US')")
        db.executemany(
            "insert into metadata(id,name,username,filter,commits,x,y,w,h,r,fs) "
            "values (:id,:name,:username,:filter,:commits,:x,:y,:w,:h,:r,:fs)",
            rows,
        )
        info = {"date": str(int(time.time() * 1000)), "orig_size": str(size)}
        if extra_info:
            info.update({k: v for k, v in extra_info.items()
                         if k not in ("date", "orig_size")})
        db.executemany("insert into info(key,value) values (?,?)", list(info.items()))
        db.commit()
    finally:
        db.close()


# --------------------------------------------------------------------------- #
# 预览图
# --------------------------------------------------------------------------- #

def draw_preview(rows: list[dict], size: int, path: str, highlight: set[str]):
    if Image is None:
        print("！未安装 Pillow，跳过预览图")
        return
    scale = min(1.0, 2048.0 / size)
    side = int(size * scale)
    image = Image.new("RGB", (side, side), (250, 250, 252))
    draw = ImageDraw.Draw(image)

    for row in sorted(rows, key=lambda r: r["fs"] or 0):
        name = row["name"]
        if not name:
            continue
        fs = float(row["fs"] or 10) * scale
        font = font_for(fs)
        if font is None:
            continue
        color = (25, 100, 210) if name in highlight else (40, 40, 46)
        rotation = row["r"] or 0
        if rotation == 0:
            draw.text((row["x"] * scale, row["y"] * scale), name, font=font,
                      fill=color, anchor="ls")
            continue

        # 旋转文字：按字形实际包围盒建图层，旋转后以包围盒中心贴回，位置才准
        l, t, r, b = font.getbbox(name)
        glyph_w, glyph_h = max(1, int(r - l)), max(1, int(b - t))
        layer = Image.new("RGBA", (glyph_w, glyph_h), (0, 0, 0, 0))
        ImageDraw.Draw(layer).text((-l, -t), name, font=font, fill=color + (255,))
        layer = layer.rotate(90 if rotation == -1 else -90, expand=True)

        left, top, right, bottom = box_of(row)
        cx = (left + right) / 2 * scale
        cy = (top + bottom) / 2 * scale
        image.paste(layer, (int(cx - layer.width / 2), int(cy - layer.height / 2)), layer)

    image.save(path)
    print(f"预览图已保存：{path}")


# --------------------------------------------------------------------------- #
# 主流程
# --------------------------------------------------------------------------- #

def load_contributors(path: str) -> list[dict]:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict):
        data = data.get("contributors", [])
    result = []
    for i, item in enumerate(data):
        name = (item.get("name") or "").strip()
        if not name:
            print(f"！第 {i + 1} 条缺少 name，已跳过")
            continue
        result.append({
            "name": name,
            "username": (item.get("username") or None),
            "commits": int(item.get("commits") or 0),
            "x": item.get("x"), "y": item.get("y"),
            "r": item.get("r"), "fs": item.get("fs"),
        })
    return result


def bump_db_version(kt_path: str) -> None:
    if not os.path.exists(kt_path):
        print(f"！未找到 {kt_path}，跳过版本号自增")
        return
    with open(kt_path, encoding="utf-8") as f:
        source = f.read()
    match = re.search(r"DB_VERSION\s*=\s*(\d+)", source)
    if not match:
        print("！没找到 DB_VERSION，跳过")
        return
    old, new = int(match.group(1)), int(match.group(1)) + 1
    with open(kt_path, "w", encoding="utf-8") as f:
        f.write(source.replace(match.group(0), f"DB_VERSION = {new}", 1))
    print(f"DB_VERSION: {old} -> {new}")


def inspect(path: str):
    rows = read_rows(path)
    info = read_info(path)
    people = [r for r in rows if r["id"] > 0]
    print(f"文件：{path}")
    print(f"总行数：{len(rows)}（其中贡献者 {len(people)} 人）")
    print(f"orig_size：{info.get('orig_size')}，date：{info.get('date')}")
    print("提交数前 10：")
    for r in sorted(people, key=lambda r: r["commits"] or 0, reverse=True)[:10]:
        print(f"  {r['commits']:>6}  {r['name']}  @{r['username'] or '-'}")


def pick_size(count: int) -> int:
    if count <= 8:
        return 1200
    if count <= 20:
        return 1200
    if count <= 60:
        return 1600
    return DEFAULT_SIZE


def find_project_root(start: str) -> str | None:
    """向上找含 app/src/main 的项目根，脚本放哪都能定位资源"""
    path = os.path.abspath(start)
    while True:
        if os.path.isdir(os.path.join(path, "app", "src", "main")):
            return path
        parent = os.path.dirname(path)
        if parent == path:
            return None
        path = parent


def main():
    started = time.time()
    parser = argparse.ArgumentParser(description="生成 / 追加贡献者云数据库")
    parser.add_argument("-i", "--input", help="贡献者名单 JSON（默认 tools/contributors.json）")
    parser.add_argument("-b", "--base", help="现有 contributors.db（合并模式）")
    parser.add_argument("-o", "--out", help="输出 db 路径（默认 tools/contributors.db）")
    parser.add_argument("--standalone", action="store_true", help="不读 base，完全自建")
    parser.add_argument("--rebuild", action="store_true",
                        help="把 base 也重新布局（会丢一部分小字，不推荐）")
    parser.add_argument("--no-border", action="store_true",
                        help="不扩大画布，直接往原图的空隙里塞")
    parser.add_argument("--size", type=int, default=0, help="画布边长")
    parser.add_argument("--preview", help="输出预览 PNG")
    parser.add_argument("--inspect", metavar="DB", nargs="?", const="__default__",
                        help="查看某个库的内容，省略参数则看 tools/contributors.db")
    parser.add_argument("--bump-version", action="store_true",
                        help="把 ContributorsDb.kt 的 DB_VERSION 加一")
    parser.add_argument("--no-sync", action="store_true",
                        help="不自动把库同步到 app/src/main/assets")
    parser.add_argument("--no-rotate", action="store_true", help="全部横排")
    args = parser.parse_args()

    # 名单与库统一放在 tools/ 下，assets 里那份只是交给打包的副本
    root = find_project_root(os.getcwd()) or find_project_root(os.path.dirname(os.path.abspath(__file__)))
    here = os.path.dirname(os.path.abspath(__file__))
    default_json = os.path.join(here, "contributors.json")
    default_db = os.path.join(here, "contributors.db")

    if args.inspect is not None:
        inspect(default_db if args.inspect == "__default__" else args.inspect)
        return

    if not args.input:
        args.input = default_json
    if not args.out:
        args.out = default_db

    if not os.path.exists(args.input):
        parser.error(f"名单不存在：{args.input}（放到 tools/contributors.json 或改用 -i 指定）")

    # 先把环境亮出来：手机终端里最常见的就是没有 Pillow 或路径不对，
    # 这两行能让人立刻看出是「在跑」还是「根本没进来」。
    say(f"Python {sys.version.split()[0]}, Pillow={'可用' if Image is not None else '不可用（不影响出库）'}")
    say(f"读取名单：{args.input}")

    new_items = load_contributors(args.input)
    if not new_items:
        print("名单为空，什么都没做")
        return
    say(f"名单 {len(new_items)} 人，开始布局…")

    base_rows: list[dict] = []
    base_info: dict = {}
    merge = bool(args.base) and not args.standalone
    if merge:
        if not os.path.exists(args.base):
            parser.error(f"base 不存在：{args.base}")
        base_rows = read_rows(args.base)
        base_info = read_info(args.base)

    size = (args.size
            or int(base_info.get("orig_size") or 0)
            or pick_size(len(new_items)))
    highlight = {item["name"] for item in new_items}

    # 合并模式默认给原图加一圈边：base 整体平移、布局一点不动，
    # 新名字放进这圈空白里，不会挤掉原来的任何一个人
    border = 0
    if merge and not args.rebuild and not args.no_border:
        border = int(max(size * 0.078, 160))
        for row in base_rows:
            row["x"] = (row["x"] or 0) + border
            row["y"] = (row["y"] or 0) + border
        size += border * 2

    # 待布局清单：--rebuild 时把 base 的人也丢进来重排，否则 base 坐标不动
    pending: list[dict] = []
    if merge and args.rebuild:
        for row in base_rows:
            if not row["name"]:
                continue
            pending.append({
                "kind": "base",
                "id": row["id"],
                "name": row["name"],
                "username": row["username"],
                "filter": row["filter"],
                "commits": row["commits"],
                "fs": float(row["fs"] or 10),
                "rotation": row["r"] or 0,
                "manual": None,
            })
        base_rows = []

    max_commits = max([i["commits"] for i in new_items] + [0])
    for item in new_items:
        if item["fs"]:
            fs = float(item["fs"])
        elif merge:
            # 合并时用自己的名字比原库最大的字略大，缩放到全图后仍能一眼找到
            fs = max(font_size_for(item["commits"], max_commits, size, True),
                     size * 0.0105)
        else:
            fs = font_size_for(item["commits"], max_commits, size, False)
        pending.append({
            "kind": "new",
            "id": None,
            "name": item["name"],
            "username": item["username"],
            "filter": "|".join(filter(None, [item["name"], item["username"]])),
            "commits": item["commits"],
            "fs": fs,
            "rotation": item["r"],
            "manual": (item["x"], item["y"]) if item["x"] is not None and item["y"] is not None else None,
        })

    grid = Grid(size)
    margin = size * MARGIN_RATIO

    # 保留原布局时，先把 base 的盒子标成占用
    if merge and not args.rebuild:
        for row in base_rows:
            if row["w"] and row["h"]:
                grid.add(box_of(row))

    # 大字先摆：从中心开始，小字最后填缝
    pending.sort(key=lambda p: p["fs"], reverse=True)

    placed: list[dict] = []
    failed: list[str] = []
    next_id = max([r["id"] for r in base_rows] + [0]) + 1

    for index, item in enumerate(pending):
        name = item["name"]
        fs = item["fs"]
        text_w, text_h = measure(name, fs)

        # 新增名字放在四条边的空白带里：上下横排、左右竖排
        in_border = border > 0 and item["kind"] == "new" and not item["manual"]

        rotation = item["rotation"]
        if rotation is None:
            if in_border and not args.no_rotate:
                rotation = border_rotation(index)
            elif args.standalone and not args.no_rotate and len(pending) <= 12 \
                    and index % 2 == 1 and len(name) <= 12:
                # 只有自己人时，隔一个竖排，画面才有层次，也不容易排成一列
                rotation = -1 if index % 4 == 1 else 1
            elif args.no_rotate or (item["kind"] == "new" and fs >= size * 0.03):
                rotation = 0
            else:
                rotation = rotation_for(name)

        half_w, half_h = half_sizes(text_w, text_h, rotation)
        gap = max(3.0, fs * 0.4)

        if item["manual"]:
            x, y = float(item["manual"][0]), float(item["manual"][1])
            cx, cy = center_from_baseline(x, y, text_w, text_h, rotation)
            grid.add((cx - half_w, cy - half_h, cx + half_w, cy + half_h))
        else:
            slot = None
            if in_border:
                ax, ay = border_anchor(index, size, border)
                slot = spiral_slot(grid, ax, ay, half_w, half_h,
                                   max_radius=border * 1.8 + max(half_w, half_h),
                                   seed=index * 1.1,
                                   region=border_region(index, size, border),
                                   gap=gap)
            if slot is None:
                slot = spiral_slot(grid, size / 2, size / 2, half_w, half_h,
                                   max_radius=max(0.0, size / 2 - margin - max(half_w, half_h)),
                                   seed=index * 0.37, gap=gap)
            if slot is None:
                failed.append(name)
                continue
            cx, cy = slot
            x, y = baseline_from_center(cx, cy, text_w, text_h, rotation)
            grid.add((cx - half_w, cy - half_h, cx + half_w, cy + half_h))

        placed.append({
            "id": item["id"] if item["id"] is not None else next_id,
            "name": name,
            "username": item["username"],
            "filter": item["filter"] or "|".join(filter(None, [name, item["username"]])),
            "commits": item["commits"],
            "x": round(x, 3), "y": round(y, 3),
            "w": round(text_h if rotation else text_w, 3),
            "h": round(text_w if rotation else text_h, 3),
            "r": rotation,
            "fs": round(fs, 3),
        })
        if item["id"] is None:
            next_id += 1

    if failed:
        print(f"！放不下（可加 --size 或调小 fs）：{', '.join(failed[:10])}"
              + (" …" if len(failed) > 10 else ""))

    rows = base_rows + placed
    if args.standalone:
        fitted = fit_canvas(rows)
        if fitted:
            size = fitted
    say(f"布局完成，画布 {size}px，写入 {args.out}")
    write_db(args.out, rows, size, base_info if merge else None)

    new_count = sum(1 for r in placed if r["name"] in highlight)
    print(f"已写入 {args.out}：{len(rows)} 行，画布 {size}px，新增 {new_count} 个")
    if merge and args.rebuild:
        print("（base 已重新布局：大字号先占中心，小字号填外圈）")
    elif merge and border:
        print(f"（原图整体平移 +{border}px，画布扩到 {size}px，原有布局未改动）")

    if args.preview:
        draw_preview(rows, size, args.preview, highlight)

    if args.bump_version:
        if root:
            kt = os.path.join(root, "app", "src", "main", "java", "com", "android", "ty",
                              "systemservices", "compose", "db", "ContributorsDb.kt")
        else:
            kt = ""
        bump_db_version(kt)

    if not args.no_sync and root:
        target = os.path.join(root, "app", "src", "main", "assets", "contributors.db")
        if os.path.abspath(target) != os.path.abspath(args.out):
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(args.out, target)
            say(f"已同步到 {os.path.relpath(target, root)}（打包用）")

    say(f"完成，用时 {time.time() - started:.2f}s")


if __name__ == "__main__":
    sys.exit(main())
