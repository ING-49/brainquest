# -*- coding: utf-8 -*-
"""
生成「学海星槎」项目 logo。

图形按 app/src/main/res/drawable/ic_launcher_foreground.xml 的原始坐标
（108x108 viewport）1:1 重建，保证与 App 启动图标是同一个品牌标记：
  品牌紫底 #6750A4 + 白色毕业帽（帽顶菱形 + 帽檐 #E8DDF5）+ 金色灯泡/流苏 #FFD54F

输出（1024 画布按 4 倍超采样绘制后 LANCZOS 缩小，得到平滑边缘）：
  项目logo.png            方形圆角，透明背景
  项目logo_白底.jpg        方形白底（部分报名系统不接受透明通道时用）
  项目logo_横版带名.png     横版：图形 + 「学海星槎」+ 副题
"""
import os
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PURPLE = (0x67, 0x50, 0xA4, 255)   # ic_launcher_background
WHITE = (0xFF, 0xFF, 0xFF, 255)
LILAC = (0xE8, 0xDD, 0xF5, 255)    # 帽檐
GOLD = (0xFF, 0xD5, 0x4F, 255)     # 灯泡与流苏

SS = 4                              # 超采样倍数
NAME = "学海星槎"
TAGLINE = "游戏化智能学习与对战平台"

# 图形在 108x108 viewport 中的外接框（含描边）
EMB_BBOX = (30.0, 77.5, 32.0, 64.0)  # x0, x1, y0, y1
EMB_CX = (EMB_BBOX[0] + EMB_BBOX[1]) / 2.0
EMB_CY = (EMB_BBOX[2] + EMB_BBOX[3]) / 2.0
EMB_W = EMB_BBOX[1] - EMB_BBOX[0]
EMB_H = EMB_BBOX[3] - EMB_BBOX[2]


def font_file(bold=False):
    for p in (r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
              r"C:\Windows\Fonts\simhei.ttf", r"C:\Windows\Fonts\simsun.ttc"):
        if os.path.exists(p):
            return p
    raise RuntimeError("未找到可用中文字体")


def load_font(px, bold=False):
    try:
        return ImageFont.truetype(font_file(bold), px)
    except Exception:
        return ImageFont.truetype(font_file(bold), px, index=0)


def bezier(p0, c1, c2, p1, n=80):
    pts = []
    for i in range(n + 1):
        t = i / float(n)
        mt = 1.0 - t
        x = mt**3 * p0[0] + 3 * mt * mt * t * c1[0] + 3 * mt * t * t * c2[0] + t**3 * p1[0]
        y = mt**3 * p0[1] + 3 * mt * mt * t * c1[1] + 3 * mt * t * t * c2[1] + t**3 * p1[1]
        pts.append((x, y))
    return pts


def draw_emblem(draw, origin, draw_scale):
    """origin: 图形中心在画布上的坐标；draw_scale: 每 viewport 单位对应像素数。"""
    s = draw_scale
    ox, oy = origin

    def T(pt):
        return (ox + (pt[0] - EMB_CX) * s, oy + (pt[1] - EMB_CY) * s)

    def path(pts):
        return [T(p) for p in pts]

    def bar(x0, x1, y0, y1, color):
        """轴对齐着色矩形，用于替代 draw.line（圆头端点在高倍下会起毛刺）。"""
        draw.polygon(path([(x0, y0), (x1, y0), (x1, y1), (x0, y1)]), fill=color)

    # 1) 帽顶菱形
    draw.polygon(path([(54, 32), (78, 44), (54, 56), (30, 44)]), fill=WHITE)

    # 2) 帽檐底座（左竖边 → 底部圆角贝塞尔 → 右竖边 → 回到帽顶下沿）
    base = [(42, 50), (42, 60)]
    base += bezier((42, 60), (42, 64), (66, 64), (66, 60))
    base += [(66, 50), (54, 56)]
    draw.polygon(path(base), fill=LILAC)

    # 3) 流苏：竖直垂线 + 末端圆球
    bar(74.9, 77.1, 46.0, 58.0, GOLD)
    cx, cy = T((73.2, 57.5))
    r = 3.2 * s
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GOLD)

    # 5) 帽顶上的灯泡（智慧）
    bulb = [(54, 38)]
    bulb += bezier((54, 38), (50.7, 38), (48, 40.7), (48, 44))
    bulb += bezier((48, 44), (48, 46.2), (49.2, 47.6), (50.2, 48.7))
    bulb += bezier((50.2, 48.7), (50.8, 49.4), (51, 49.8), (51, 50.5))
    bulb.append((57, 50.5))
    bulb += bezier((57, 50.5), (57, 49.8), (57.2, 49.4), (57.8, 48.7))
    bulb += bezier((57.8, 48.7), (58.8, 47.6), (60, 46.2), (60, 44))
    bulb += bezier((60, 44), (60, 40.7), (57.3, 38), (54, 38))
    draw.polygon(path(bulb), fill=GOLD)

    # 6) 灯泡底座两道细线
    bar(52.0, 56.0, 51.4, 52.6, PURPLE)
    bar(52.5, 55.5, 53.4, 54.6, PURPLE)


def square_logo(target_px, canvas_px=1024, radius=200, bg=PURPLE):
    """方形 logo，返回 RGBA 图。图形占画布宽度约 58%。"""
    big = canvas_px * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=radius * SS, fill=bg)
    s = (canvas_px * 0.62 / EMB_W) * SS
    draw_emblem(draw, (big / 2.0, big / 2.0), s)
    return img.resize((target_px, target_px), Image.LANCZOS)


def wordmark_logo():
    """横版：左侧紫底徽章（白色帽体需有底色才可见）+ 右侧「学海星槎」与副题。"""
    W, H, K = 1800, 600, 2
    img = Image.new("RGBA", (W * K, H * K), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    bx, by, bs, br = 60, 90, 420, 92
    draw.rounded_rectangle([bx * K, by * K, (bx + bs) * K, (by + bs) * K],
                           radius=br * K, fill=PURPLE)
    s = (bs * 0.62 / EMB_W) * K
    draw_emblem(draw, ((bx + bs / 2.0) * K, (by + bs / 2.0) * K), s)

    f_title = load_font(200 * K, bold=True)
    f_tag = load_font(52 * K)
    tx = 560 * K

    bt = draw.textbbox((0, 0), NAME, font=f_title)
    bg = draw.textbbox((0, 0), TAGLINE, font=f_tag)
    ht, hg = bt[3] - bt[1], bg[3] - bg[1]
    gap = 30 * K
    top = 300 * K - (ht + gap + hg) / 2.0

    draw.text((tx, top - bt[1]), NAME, font=f_title, fill=PURPLE)
    draw.text((tx + 4 * K, top + ht + gap - bg[1]), TAGLINE, font=f_tag,
              fill=(0x6B, 0x6B, 0x6B, 255))

    return img.resize((W, H), Image.LANCZOS)


def main():
    sq = square_logo(1024)
    p1 = os.path.join(OUT_DIR, "项目logo.png")
    sq.save(p1)

    flat = Image.new("RGB", sq.size, (255, 255, 255))
    flat.paste(sq, mask=sq.split()[3])
    p2 = os.path.join(OUT_DIR, "项目logo_白底.jpg")
    flat.save(p2, quality=95)

    p3 = os.path.join(OUT_DIR, "项目logo_横版带名.png")
    wordmark_logo().save(p3)

    for p in (p1, p2, p3):
        im = Image.open(p)
        print("%-28s %s  %dx%d  %.1f KB" % (
            os.path.basename(p), im.mode, im.size[0], im.size[1], os.path.getsize(p) / 1024.0))


if __name__ == "__main__":
    main()
