# -*- coding: utf-8 -*-
"""
本地占位图生成器。

为什么需要它：
- 参考截图里的游戏原画 / 商品实拍 / 门店照片都有版权，不能直接用；
- 需求明确要求「不要大量使用网络图片」「设计成与截图风格一致的本地占位图」。

所以这里用程序生成一套风格统一的渐变 + 几何图形占位图：
- 和截图一致的蓝紫主色系；
- 同一目录下色调协调，不会出现风格突兀的图片；
- 纯本地生成，可重复执行（结果由文件名哈希决定，是确定性的）。

用法：
    python scripts/gen_placeholders.py
输出目录：
    src/static/images/
"""

import hashlib
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "static", "images")
SS = 3  # 超采样倍数，先画大图再缩小，得到平滑边缘


# --------------------------------------------------------------------------
# 调色板
# --------------------------------------------------------------------------

# 品牌主色系（与 uni.scss 保持一致）
BRAND = [
    (142, 123, 255),  # #8E7BFF
    (91, 91, 214),    # #5B5BD6
    (124, 108, 240),  # #7C6CF0
    (167, 139, 250),  # #A78BFA
]

# 首页顶部粉紫
HOME = [(240, 213, 248), (230, 220, 250), (214, 200, 246)]

# 活动橙 / 礼品红
WARM = [(255, 138, 76), (255, 92, 92), (247, 179, 60)]

# 电竞深色（游戏页 / 社区视频封面）
DARK = [(43, 58, 92), (29, 37, 64), (19, 26, 46)]

# 商品浅色底
LIGHT = [(245, 247, 251), (238, 241, 250), (250, 248, 252)]


def seed_of(name):
    """由文件名得到稳定的随机种子，保证每次生成结果一致。"""
    return int(hashlib.md5(name.encode("utf-8")).hexdigest()[:8], 16)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient(size, colors, angle=135, extra_stops=None):
    """生成线性渐变底图。colors 为 2~3 个颜色。"""
    w, h = size
    img = Image.new("RGB", (w, h))
    px = img.load()
    rad = math.radians(angle)
    dx, dy = math.cos(rad), math.sin(rad)
    denom = abs(dx) * w + abs(dy) * h or 1
    stops = extra_stops or colors
    n = len(stops) - 1
    for y in range(h):
        for x in range(w):
            t = (x * dx + y * dy) / denom
            t = min(1.0, max(0.0, (t + 1) / 2 if False else t))
            seg = min(n - 1, int(t * n)) if n > 0 else 0
            local = (t * n - seg) if n > 0 else 0
            c = lerp(stops[seg], stops[min(seg + 1, n)], local)
            px[x, y] = c
    return img


def add_glow(img, center, radius, color, alpha=90):
    """叠加径向光晕。"""
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    cx, cy = center
    steps = 26
    for i in range(steps, 0, -1):
        r = radius * i / steps
        a = int(alpha * (1 - i / steps) ** 1.6)
        md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=a)
    mask = mask.filter(ImageFilter.GaussianBlur(radius / 5))
    layer = Image.new("RGB", (w, h), tuple(color))
    img.paste(layer, (0, 0), mask)
    return img


def add_blobs(img, count, palette, seed, alpha=70, rmin=0.10, rmax=0.34):
    """叠加半透明圆形色块。"""
    rnd = random.Random(seed)
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    for _ in range(count):
        r = int(min(w, h) * rnd.uniform(rmin, rmax))
        cx = rnd.randint(-r // 2, w + r // 2)
        cy = rnd.randint(-r // 2, h + r // 2)
        md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rnd.randint(alpha // 2, alpha))
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) / 22))
    layer = Image.new("RGB", (w, h), tuple(rnd.choice(palette)))
    img.paste(layer, (0, 0), mask)
    return img


def add_bands(img, count, palette, seed, alpha=48):
    """叠加斜向色带，让画面不单调。"""
    rnd = random.Random(seed + 7)
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    for _ in range(count):
        x0 = rnd.randint(-w // 3, w)
        width = rnd.randint(int(w * 0.12), int(w * 0.42))
        md.polygon(
            [(x0, h), (x0 + width, h), (x0 + width + int(h * 0.35), 0), (x0 + int(h * 0.35), 0)],
            fill=rnd.randint(alpha // 2, alpha),
        )
    mask = mask.filter(ImageFilter.GaussianBlur(w / 40))
    layer = Image.new("RGB", (w, h), tuple(rnd.choice(palette)))
    img.paste(layer, (0, 0), mask)
    return img


def add_grain(img, seed, strength=6):
    """轻微噪点，避免大块渐变出现色带。"""
    rnd = random.Random(seed + 99)
    w, h = img.size
    small = Image.new("L", (max(2, w // 3), max(2, h // 3)))
    small.putdata([rnd.randint(128 - strength, 128 + strength) for _ in range(small.width * small.height)])
    noise = small.resize((w, h), Image.BILINEAR).convert("RGB")
    return Image.blend(img, noise, 0.045)


def rounded(img, radius_ratio=0.14):
    """圆角遮罩。"""
    w, h = img.size
    r = int(min(w, h) * radius_ratio)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=r, fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(img.convert("RGB"), (0, 0), mask)
    return out


def save(img, rel, quality=82):
    """
    统一输出 JPEG。

    微信小程序主包上限 2MB，渐变类图片用 PNG 会浪费 4~5 倍体积
    （实测 69 张 PNG 占 2.5MB，转 JPEG 后约 0.7MB）。
    """
    path = os.path.join(BASE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if rel.lower().endswith((".jpg", ".jpeg")):
        # 圆角外的透明区域必须合成到白底，否则 JPEG 会出现黑角
        if img.mode == "RGBA":
            bg = Image.new("RGB", img.size, (255, 255, 255))
            bg.paste(img, (0, 0), img)
            img = bg
        img.convert("RGB").save(path, "JPEG", quality=quality, optimize=True, progressive=True)
    else:
        img.save(path, "PNG", optimize=True)
    print("  ->", rel, img.size)


# --------------------------------------------------------------------------
# 各类图片
# --------------------------------------------------------------------------

def gen_banner(name, w=750, h=340, kind="brand"):
    seed = seed_of(name)
    big = (w * SS, h * SS)
    if kind == "treasure":
        img = gradient(big, [(199, 125, 255), (155, 93, 229), (124, 77, 220)], angle=115)
        img = add_glow(img, (int(big[0] * 0.24), int(big[1] * 0.46)), int(big[0] * 0.34), (255, 236, 160), 130)
        img = add_glow(img, (int(big[0] * 0.82), int(big[1] * 0.32)), int(big[0] * 0.24), (255, 160, 220), 90)
        img = add_blobs(img, 9, [(255, 220, 130), (255, 150, 210), (180, 150, 255)], seed, alpha=95)
    elif kind == "newcomer":
        img = gradient(big, [(255, 168, 92), (255, 118, 118), (232, 96, 176)], angle=120)
        img = add_glow(img, (int(big[0] * 0.78), int(big[1] * 0.5)), int(big[0] * 0.3), (255, 244, 190), 120)
        img = add_blobs(img, 8, [(255, 230, 150), (255, 190, 210)], seed, alpha=80)
    elif kind == "hotel":
        img = gradient(big, [(88, 128, 232), (91, 91, 214), (108, 78, 210)], angle=120)
        img = add_glow(img, (int(big[0] * 0.7), int(big[1] * 0.42)), int(big[0] * 0.3), (150, 220, 255), 110)
        img = add_blobs(img, 7, [(130, 200, 255), (170, 160, 255)], seed, alpha=76)
    else:
        img = gradient(big, BRAND, angle=125)
        img = add_glow(img, (int(big[0] * 0.75), int(big[1] * 0.4)), int(big[0] * 0.3), (255, 255, 255), 80)
        img = add_blobs(img, 8, BRAND, seed, alpha=80)
    img = add_bands(img, 3, [(255, 255, 255)], seed, alpha=30)
    img = add_grain(img, seed)
    img = img.resize((w, h), Image.LANCZOS)
    save(rounded(img, 0.10), name)


def gen_gift_banner(name, w=750, h=260):
    """我的页面「添加好友领 328 元大礼包」横幅。"""
    seed = seed_of(name)
    big = (w * SS, h * SS)
    img = gradient(big, [(255, 196, 92), (255, 140, 80), (250, 108, 108)], angle=0)
    img = add_glow(img, (int(big[0] * 0.74), int(big[1] * 0.5)), int(big[0] * 0.3), (255, 250, 210), 140)
    img = add_blobs(img, 10, [(255, 236, 170), (255, 120, 120), (255, 218, 120)], seed, alpha=100)
    img = add_bands(img, 4, [(255, 255, 255)], seed, alpha=34)
    img = add_grain(img, seed)
    img = img.resize((w, h), Image.LANCZOS)
    save(rounded(img, 0.15), name)


def _draw_bottle(d, big, accent, bw, bh, bx, by):
    """瓶装/罐装：圆角柱体 + 瓶盖 + 标签带。"""
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=int(bw * 0.22), fill=accent + (255,))
    d.rounded_rectangle(
        [bx + int(bw * 0.12), by + int(bh * 0.10), bx + int(bw * 0.30), by + int(bh * 0.82)],
        radius=int(bw * 0.08), fill=(255, 255, 255, 92),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.26), by - int(bh * 0.13), bx + int(bw * 0.74), by + int(bh * 0.05)],
        radius=int(bw * 0.07), fill=lerp(accent, (255, 255, 255), 0.15) + (255,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.06), by + int(bh * 0.40), bx + int(bw * 0.94), by + int(bh * 0.66)],
        radius=int(bw * 0.06), fill=(255, 255, 255, 225),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.14), by + int(bh * 0.48), bx + int(bw * 0.86), by + int(bh * 0.52)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.25) + (200,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.14), by + int(bh * 0.56), bx + int(bw * 0.62), by + int(bh * 0.60)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.10) + (160,),
    )


def _draw_can(d, big, accent, bw, bh, bx, by):
    """易拉罐：更矮更宽 + 顶部银色拉环。"""
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=int(bw * 0.16), fill=accent + (255,))
    d.rounded_rectangle(
        [bx + int(bw * 0.06), by + int(bh * 0.03), bx + int(bw * 0.94), by + int(bh * 0.14)],
        radius=int(bw * 0.08), fill=(228, 230, 238, 255),
    )
    d.ellipse(
        [bx + int(bw * 0.34), by + int(bh * 0.055), bx + int(bw * 0.66), by + int(bh * 0.115)],
        fill=(190, 194, 206, 255),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.08), by + int(bh * 0.30), bx + int(bw * 0.92), by + int(bh * 0.72)],
        radius=int(bw * 0.05), fill=(255, 255, 255, 220),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.18), by + int(bh * 0.42), bx + int(bw * 0.82), by + int(bh * 0.47)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.28) + (200,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.18), by + int(bh * 0.54), bx + int(bw * 0.64), by + int(bh * 0.58)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.10) + (150,),
    )


def _draw_box(d, big, accent, bw, bh, bx, by):
    """盒装/袋装：方盒 + 折角，适合零食、泡面桶、自热饭。"""
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=int(bw * 0.10), fill=accent + (255,))
    # 顶盖
    d.polygon(
        [(bx, by + int(bh * 0.10)), (bx + int(bw * 0.5), by - int(bh * 0.05)),
         (bx + bw, by + int(bh * 0.10)), (bx + int(bw * 0.5), by + int(bh * 0.24))],
        fill=lerp(accent, (255, 255, 255), 0.24) + (255,),
    )
    # 正面标签
    d.rounded_rectangle(
        [bx + int(bw * 0.14), by + int(bh * 0.36), bx + int(bw * 0.86), by + int(bh * 0.78)],
        radius=int(bw * 0.05), fill=(255, 255, 255, 226),
    )
    d.ellipse(
        [bx + int(bw * 0.24), by + int(bh * 0.44), bx + int(bw * 0.52), by + int(bh * 0.60)],
        fill=lerp(accent, (255, 255, 255), 0.35) + (255,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.58), by + int(bh * 0.46), bx + int(bw * 0.78), by + int(bh * 0.50)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.3) + (200,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.58), by + int(bh * 0.55), bx + int(bw * 0.72), by + int(bh * 0.585)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.12) + (150,),
    )


def _draw_card(d, big, accent, bw, bh, bx, by):
    """虚拟商品：卡片/券。"""
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=int(bw * 0.12), fill=accent + (255,))
    d.rounded_rectangle(
        [bx + int(bw * 0.10), by + int(bh * 0.20), bx + int(bw * 0.90), by + int(bh * 0.52)],
        radius=int(bw * 0.06), fill=(255, 255, 255, 232),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.18), by + int(bh * 0.30), bx + int(bw * 0.60), by + int(bh * 0.35)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.28) + (210,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.18), by + int(bh * 0.40), bx + int(bw * 0.78), by + int(bh * 0.44)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.12) + (170,),
    )
    # 磁条
    for i in range(4):
        d.rounded_rectangle(
            [bx + int(bw * 0.10), by + int(bh * (0.62 + i * 0.075)),
             bx + int(bw * (0.90 - i * 0.12)), by + int(bh * (0.66 + i * 0.075))],
            radius=3, fill=(255, 255, 255, 150),
        )


def _draw_combo(d, big, accent, bw, bh, bx, by):
    """套餐：餐盒 + 旁边一杯饮料，读起来像「主食 + 饮料」。"""
    # 主餐盒
    d.rounded_rectangle(
        [bx, by + int(bh * 0.18), bx + int(bw * 0.66), by + bh],
        radius=int(bw * 0.09), fill=accent + (255,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.05), by + int(bh * 0.10), bx + int(bw * 0.61), by + int(bh * 0.24)],
        radius=int(bw * 0.05), fill=lerp(accent, (255, 255, 255), 0.35) + (255,),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.10), by + int(bh * 0.42), bx + int(bw * 0.56), by + int(bh * 0.78)],
        radius=int(bw * 0.04), fill=(255, 255, 255, 224),
    )
    d.rounded_rectangle(
        [bx + int(bw * 0.18), by + int(bh * 0.52), bx + int(bw * 0.48), by + int(bh * 0.56)],
        radius=3, fill=lerp(accent, (0, 0, 0), 0.28) + (200,),
    )
    # 饮料杯
    cx = bx + int(bw * 0.72)
    cw = int(bw * 0.28)
    d.polygon(
        [(cx, by + int(bh * 0.34)), (cx + cw, by + int(bh * 0.34)),
         (cx + int(cw * 0.84), by + bh), (cx + int(cw * 0.16), by + bh)],
        fill=lerp(accent, (255, 255, 255), 0.18) + (255,),
    )
    d.rounded_rectangle(
        [cx - int(cw * 0.08), by + int(bh * 0.28), cx + cw + int(cw * 0.08), by + int(bh * 0.37)],
        radius=int(cw * 0.10), fill=(240, 242, 248, 255),
    )
    d.line(
        [(cx + int(cw * 0.62), by + int(bh * 0.40)), (cx + int(cw * 0.32), by + int(bh * 0.86))],
        fill=lerp(accent, (0, 0, 0), 0.18) + (170,), width=max(2, int(cw * 0.09)),
    )


# 文件名 → 造型，避免所有商品长得一模一样
SHAPE_BY_KEY = {
    'cola': 'can', 'redbull': 'can', 'dongpeng': 'bottle',
    'water': 'bottle', 'tea': 'bottle',
    'chips': 'box', 'oreo': 'box', 'pie': 'box', 'beef': 'box',
    'noodle1': 'box', 'noodle2': 'box', 'rice': 'box',
    'combo1': 'combo', 'combo2': 'combo', 'combo3': 'combo',
    'vnet': 'card', 'vtime': 'card', 'vnight': 'card',
}

SHAPE_FN = {
    'bottle': _draw_bottle,
    'can': _draw_can,
    'box': _draw_box,
    'card': _draw_card,
    'combo': _draw_combo,
}


def gen_product(name, w=300, h=300):
    """商品图：浅底 + 主体造型 + 标签，按品类换造型。"""
    seed = seed_of(name)
    rnd = random.Random(seed)
    big = (w * SS, h * SS)
    img = gradient(big, LIGHT, angle=125)

    accent = rnd.choice(BRAND + WARM + [(60, 190, 170), (80, 170, 240)])
    img = add_glow(img, (int(big[0] * 0.5), int(big[1] * 0.62)), int(big[0] * 0.36), accent, 70)

    key = os.path.splitext(os.path.basename(name))[0]
    shape = SHAPE_BY_KEY.get(key, 'bottle')
    draw = SHAPE_FN[shape]

    bw = int(big[0] * 0.44)
    bh = int(big[1] * (0.50 if shape in ('combo', 'card', 'can') else 0.54))
    bx = (big[0] - bw) // 2
    by = int(big[1] * 0.36)

    # 投影
    shadow = Image.new("RGBA", big, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [bx + 6, by + 14, bx + bw + 6, by + bh + 14],
        radius=int(bw * 0.18), fill=(90, 90, 140, 70),
    )
    img.paste(Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB"), (0, 0))

    draw(ImageDraw.Draw(img, "RGBA"), big, accent, bw, bh, bx, by)

    img = add_grain(img, seed, 4)
    img = img.resize((w, h), Image.LANCZOS)
    save(rounded(img, 0.14), name)


def gen_game_thumb(name, w=200, h=200):
    """游戏任务缩略图：高饱和渐变 + 几何切面，读起来像游戏立绘。"""
    seed = seed_of(name)
    rnd = random.Random(seed)
    big = (w * SS, h * SS)
    base = rnd.choice([
        [(70, 90, 180), (40, 50, 110), (24, 30, 70)],
        [(180, 70, 90), (120, 40, 70), (60, 24, 46)],
        [(190, 140, 60), (140, 96, 40), (74, 50, 24)],
        [(60, 160, 150), (36, 108, 110), (20, 60, 70)],
        [(130, 90, 200), (86, 56, 150), (44, 30, 88)],
    ])
    img = gradient(big, base, angle=rnd.choice([100, 125, 145, 70]))
    img = add_glow(img, (int(big[0] * rnd.uniform(0.3, 0.7)), int(big[1] * rnd.uniform(0.3, 0.6))),
                   int(big[0] * 0.42), rnd.choice(WARM + BRAND), 120)
    img = add_blobs(img, 5, base + WARM, seed, alpha=120, rmin=0.12, rmax=0.4)
    img = add_bands(img, 3, [(255, 255, 255)], seed, alpha=42)
    img = add_grain(img, seed, 7)
    img = img.resize((w, h), Image.LANCZOS)
    save(rounded(img, 0.16), name)


def gen_community_photo(name, w=750, h=440, dark=True):
    """社区配图 / 视频封面。"""
    seed = seed_of(name)
    rnd = random.Random(seed)
    big = (w * SS, h * SS)
    stops = DARK if dark else rnd.choice([BRAND, WARM])
    img = gradient(big, stops, angle=rnd.choice([110, 130, 160]))
    img = add_glow(img, (int(big[0] * rnd.uniform(0.35, 0.65)), int(big[1] * rnd.uniform(0.3, 0.6))),
                   int(big[0] * 0.34), rnd.choice([(90, 130, 220), (200, 90, 140), (240, 180, 90), (110, 200, 200)]), 100)
    img = add_blobs(img, 7, [(60, 80, 150), (140, 80, 170), (40, 60, 120)], seed, alpha=110)
    img = add_bands(img, 4, [(255, 255, 255), (120, 160, 255)], seed, alpha=34)
    # 底部压暗，方便叠文字
    d = ImageDraw.Draw(img, "RGBA")
    grad_h = int(big[1] * 0.45)
    for i in range(grad_h):
        a = int(150 * (i / grad_h) ** 1.5)
        d.line([(0, big[1] - grad_h + i), (big[0], big[1] - grad_h + i)], fill=(0, 0, 0, a))
    img = add_grain(img, seed, 6)
    img = img.resize((w, h), Image.LANCZOS)
    save(rounded(img, 0.05), name)


def gen_avatar(name, size=96):
    """
    头像：渐变底 + 简笔人形。

    刻意输出「方图」而不是圆图 —— 页面统一用 CSS `border-radius: 50%` 裁圆，
    这样图片本身不需要透明通道，可以直接存 JPEG（省 4~5 倍体积）。
    """
    seed = seed_of(name)
    rnd = random.Random(seed)
    big = size * SS
    img = gradient((big, big), rnd.choice([BRAND, WARM, [(70, 170, 200), (40, 110, 180)],
                                           [(240, 140, 90), (210, 90, 120)]]), angle=125)
    d = ImageDraw.Draw(img, "RGBA")
    # 头部
    hr = int(big * 0.17)
    cx, cy = big // 2, int(big * 0.40)
    d.ellipse([cx - hr, cy - hr, cx + hr, cy + hr], fill=(255, 255, 255, 215))
    # 肩膀
    bw, bh = int(big * 0.56), int(big * 0.30)
    d.rounded_rectangle([cx - bw // 2, int(big * 0.62), cx + bw // 2, int(big * 0.62) + bh],
                        radius=int(bw * 0.42), fill=(255, 255, 255, 215))
    img = img.resize((size, size), Image.LANCZOS)
    save(img, name)


def gen_room(name, w=400, h=300):
    """酒店房型图。"""
    seed = seed_of(name)
    big = (w * SS, h * SS)
    img = gradient(big, [(250, 246, 255), (232, 228, 250), (214, 210, 244)], angle=120)
    d = ImageDraw.Draw(img, "RGBA")
    accent = (91, 91, 214)
    # 床
    d.rounded_rectangle([int(big[0] * 0.14), int(big[1] * 0.46), int(big[0] * 0.86), int(big[1] * 0.82)],
                        radius=int(big[0] * 0.05), fill=(255, 255, 255, 245))
    d.rounded_rectangle([int(big[0] * 0.14), int(big[1] * 0.40), int(big[0] * 0.40), int(big[1] * 0.52)],
                        radius=int(big[0] * 0.03), fill=(255, 255, 255, 255))
    d.rounded_rectangle([int(big[0] * 0.60), int(big[1] * 0.40), int(big[0] * 0.86), int(big[1] * 0.52)],
                        radius=int(big[0] * 0.03), fill=(255, 255, 255, 255))
    # 显示器
    d.rounded_rectangle([int(big[0] * 0.20), int(big[1] * 0.14), int(big[0] * 0.46), int(big[1] * 0.36)],
                        radius=int(big[0] * 0.02), fill=accent + (240,))
    d.rounded_rectangle([int(big[0] * 0.56), int(big[1] * 0.14), int(big[0] * 0.82), int(big[1] * 0.36)],
                        radius=int(big[0] * 0.02), fill=accent + (200,))
    img = add_glow(img, (int(big[0] * 0.5), int(big[1] * 0.5)), int(big[0] * 0.4), (167, 139, 250), 60)
    img = add_grain(img, seed, 4)
    img = img.resize((w, h), Image.LANCZOS)
    save(rounded(img, 0.10), name)


def gen_store(name, w=400, h=300):
    """门店图。"""
    seed = seed_of(name)
    big = (w * SS, h * SS)
    img = gradient(big, [(58, 44, 96), (36, 30, 66), (22, 20, 44)], angle=125)
    img = add_glow(img, (int(big[0] * 0.5), int(big[1] * 0.55)), int(big[0] * 0.42), (167, 139, 250), 110)
    d = ImageDraw.Draw(img, "RGBA")
    # 一排显示器剪影
    for i in range(4):
        x = int(big[0] * (0.08 + i * 0.23))
        d.rounded_rectangle([x, int(big[1] * 0.38), x + int(big[0] * 0.17), int(big[1] * 0.66)],
                            radius=int(big[0] * 0.015), fill=(140, 170, 255, 150))
    img = add_blobs(img, 5, [(120, 100, 220)], seed, alpha=90)
    img = add_grain(img, seed, 6)
    img = img.resize((w, h), Image.LANCZOS)
    save(rounded(img, 0.10), name)


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------

def main():
    print("生成占位图到", os.path.abspath(BASE))

    # 首页 Banner（显示宽度约 702rpx ≈ 337pt，600px 足够且省体积）
    gen_banner("banner/treasure.jpg", w=600, h=218, kind="treasure")
    gen_banner("banner/newcomer.jpg", w=600, h=218, kind="newcomer")
    gen_banner("banner/hotel.jpg", w=600, h=218, kind="hotel")
    gen_gift_banner("banner/gift-328.jpg", w=600, h=172)

    # 商品（卡片里约 150rpx ≈ 72pt）
    for n in ["cola", "redbull", "water", "tea", "dongpeng", "chips", "oreo", "pie",
              "beef", "noodle1", "noodle2", "rice", "combo1", "combo2", "combo3",
              "vnet", "vtime", "vnight"]:
        gen_product(f"product/{n}.jpg", w=240, h=240)

    # 游戏任务缩略图 4 宫格 × 6 个任务（显示 56rpx ≈ 27pt）
    for t in range(1, 7):
        for s in "abcd":
            gen_game_thumb(f"game/task{t}-{s}.jpg", w=160, h=160)

    # 社区配图
    gen_community_photo("community/post1-cover.jpg", w=640, h=376, dark=True)
    for i in range(1, 8):
        gen_community_photo(f"community/post2-{i}.jpg", w=280, h=318, dark=i % 3 != 2)
    gen_community_photo("community/post3-1.jpg", w=640, h=358, dark=False)
    for i in range(1, 3):
        gen_community_photo(f"community/post4-{i}.jpg", w=340, h=304, dark=i == 2)

    # 头像（显示 60rpx ≈ 29pt，CSS 用 border-radius 裁圆，所以出方图即可）
    for n in ["official", "official2", "u1", "u2", "u3", "u4", "u5", "default"]:
        gen_avatar(f"avatar/{n}.jpg", size=96)

    # 酒店 / 门店
    for i in range(1, 5):
        gen_room(f"hotel/room{i}.jpg", w=320, h=240)
    for i in range(1, 4):
        gen_store(f"store/store-{i}.jpg", w=320, h=240)

    print("完成")


if __name__ == "__main__":
    main()
