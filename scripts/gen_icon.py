#!/usr/bin/env python3
"""PureProbe App 图标生成器：蓝紫渐变圆角方块 + 白盾 + 渐变对勾
输出 Android 各密度 mipmap PNG（方形 ic_launcher + 圆形 ic_launcher_round）。
复跑: python3 scripts/gen_icon.py
"""
from PIL import Image, ImageDraw
import os, math

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/res")
SS = 2  # 超采样倍数（抗锯齿）
BASE = 1024
C = BASE * SS

BLUE = (59, 130, 246)   # #3b82f6
PURPLE = (139, 92, 246) # #8b5cf6
WHITE_TOP = (255, 255, 255)
WHITE_BOT = (214, 226, 250)

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def v_gradient(size, top, bottom):
    img = Image.new("RGB", size)
    px = img.load()
    h = size[1]
    for y in range(h):
        c = lerp(top, bottom, y / (h - 1))
        for x in range(size[0]):
            px[x, y] = c
    return img

def bezier(p0, p1, p2, p3, n=48):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t*t*p2[0] + t**3*p3[0]
        y = (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t*t*p2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts

def build_base():
    """1024*SS 画布：渐变圆角方 + 盾 + 对勾，返回 RGBA"""
    # 背景：圆角方形，蓝→紫对角渐变
    bg = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    grad = Image.new("RGBA", (C, C))
    gp = grad.load()
    for y in range(C):
        for_step = y / (C - 1)
        for x in range(0, C, C):  # 逐行填充（行内同色，取对角混合）
            pass
        row_c = lerp(BLUE, PURPLE, (y / (C - 1)) * 0.85)
        # 行内再叠一点水平分量
        for x in range(C):
            t = 0.75 * (y / (C - 1)) + 0.25 * (x / (C - 1))
            gp[x, y] = lerp(BLUE, PURPLE, t) + (255,)
    mask = Image.new("L", (C, C), 0)
    md = ImageDraw.Draw(mask)
    r = int(C * 0.223)  # Android 圆角比例
    md.rounded_rectangle([0, 0, C - 1, C - 1], radius=r, fill=255)
    bg.paste(grad, (0, 0), mask)

    d = ImageDraw.Draw(bg)
    # 盾牌轮廓（超采样坐标）
    s = SS
    top_y, tip_y = 258 * s, 838 * s
    lx, rx = 302 * s, 722 * s
    cx = 512 * s
    shield = []
    shield += bezier((lx, top_y), (512 * s, 196 * s), (512 * s, 196 * s), (rx, top_y))  # 顶边微拱
    shield += bezier((rx, top_y), (rx, 470 * s), (646 * s, 700 * s), (cx, tip_y))       # 右缘
    shield += bezier((cx, tip_y), (378 * s, 700 * s), (lx, 470 * s), (lx, top_y))       # 左缘
    # 盾面（白渐变）
    shield_img = v_gradient((C, C), WHITE_TOP, WHITE_BOT).convert("RGBA")
    smask = Image.new("L", (C, C), 0)
    ImageDraw.Draw(smask).polygon(shield, fill=255)
    bg.paste(shield_img, (0, 0), smask)
    # 盾底淡阴影
    d = ImageDraw.Draw(bg)
    d.polygon([(p[0], p[1] + 10 * s) for p in shield], fill=(30, 20, 80, 40))

    # 对勾（渐变描边：分段画粗线 + 圆头）
    def seg(p1, p2, c1, c2, w):
        n = 60
        for i in range(n):
            t0, t1 = i / n, (i + 1) / n
            a = (p1[0] + (p2[0]-p1[0])*t0, p1[1] + (p2[1]-p1[1])*t0)
            b = (p1[0] + (p2[0]-p1[0])*t1, p1[1] + (p2[1]-p1[1])*t1)
            d.line([a, b], fill=lerp(c1, c2, t0), width=w)
            d.ellipse([a[0]-w/2, a[1]-w/2, a[0]+w/2, a[1]+w/2], fill=lerp(c1, c2, t0))
        d.ellipse([b[0]-w/2, b[1]-w/2, b[0]+w/2, b[1]+w/2], fill=c2)
    W = 62 * s
    seg((392*s, 536*s), (474*s, 622*s), BLUE, lerp(BLUE, PURPLE, 0.4), W)
    seg((474*s, 622*s), (652*s, 428*s), lerp(BLUE, PURPLE, 0.4), PURPLE, W)
    return bg

def circle_version(img):
    """圆形裁剪版（透明四角）"""
    C2 = img.size[0]
    mask = Image.new("L", (C2, C2), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, C2-1, C2-1], fill=255)
    out = Image.new("RGBA", (C2, C2), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

def main():
    base = build_base()
    round_ic = circle_version(base)
    dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, px in dens.items():
        dirp = os.path.join(OUT, "mipmap-" + d)
        os.makedirs(dirp, exist_ok=True)
        base.resize((px*SS*SS//SS*SS//SS if False else px*2, px*2), Image.LANCZOS).resize((px, px), Image.LANCZOS).save(
            os.path.join(dirp, "ic_launcher.png"))
        round_ic.resize((px*2, px*2), Image.LANCZOS).resize((px, px), Image.LANCZOS).save(
            os.path.join(dirp, "ic_launcher_round.png"))
        print(f"mipmap-{d}: {px}px OK")
    # 预览大图
    prev = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "preview")
    os.makedirs(prev, exist_ok=True)
    base.resize((512, 512), Image.LANCZOS).save(os.path.join(prev, "icon-preview.png"))
    print("预览: preview/icon-preview.png")

if __name__ == "__main__":
    main()
