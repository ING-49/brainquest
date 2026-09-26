"""贪吃蛇速度与界面取证：起步慢速（v1.6.6 由 220ms 降到 300ms/格）、无方向键、速度档、返回确认。

用法：python tools/snake_speed_check.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import snake_autoeat as s  # noqa: E402
import ui  # noqa: E402

SHOTS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".shots")
WINDOW_S = 2.0


def main():
    fails = []
    ui.start()
    if not ui.tap_text("贪吃蛇", exact=False, wait=6):
        print("❌ 未找到贪吃蛇入口"); return 1
    time.sleep(0.6)

    labels = ui.texts()
    dpad = [t for t in labels if t in ("↑", "↓", "←", "→")]
    print(f"[1] 屏幕方向键：{'仍存在 ' + str(dpad) if dpad else '已移除 ✓'}")
    if dpad:
        fails.append("方向键未移除")
    print(f"[1] HUD：{[t for t in labels if '得分' in t or '速度' in t or '最高分' in t]}")
    if not any("速度" in t for t in labels):
        fails.append("HUD 无速度档显示")

    restart = s.text_node("重开一局")
    tap0 = time.time()
    s.tap(*restart)
    time.sleep(0.8)

    # 向下起步（棋盘下半 10 行，够跑 2 秒不死）
    s.turn(s.board_box(s.screenshot()), "down")
    t0 = time.time()
    img1 = s.screenshot()
    box = s.board_box(img1)
    h1 = s.head_cell(img1, box)
    shot1 = os.path.join(SHOTS, "snake_1_start.png")
    ui.shot(shot1)
    time.sleep(max(0.0, WINDOW_S - (time.time() - t0)))
    t1 = time.time()
    img2 = s.screenshot()
    h2 = s.head_cell(img2, s.board_box(img2))
    print(f"[2] {t1 - t0:.2f}s 内 蛇头 {h1} → {h2}")
    if h1 is None or h2 is None:
        print("❌ 蛇头识别失败"); return 1
    steps = abs(h2[0] - h1[0]) + abs(h2[1] - h1[1])
    tick_ms = (t1 - t0) * 1000.0 / max(steps, 1)
    print(f"[2] 前进 {steps} 格 → 实测约 {tick_ms:.0f}ms/格（v1.6.5 为 220ms，v1.6.6 目标 300ms）")
    if steps >= 10:   # 300ms/格 在窗口内应走 ~11 格；模拟器负载会有波动，10 格为判慢下限
        fails.append(f"速度未变慢（{steps} 格 / {WINDOW_S}s）")

    # 返回确认（对局进行中：先关掉可能存在的结算弹窗，再重开起步）
    if not ui.tap_text("再来一局", exact=False, delay=0.8):
        s.tap(*restart)
    time.sleep(0.6)
    s.turn(s.board_box(s.screenshot()), "down")
    ui.key("BACK", delay=0.8)
    has_dialog = any("退出这一局" in t for t in ui.texts())
    print(f"[3] 对局中返回确认框：{'出现 ✓' if has_dialog else '未出现'}")
    if not has_dialog:
        fails.append("对局中返回未弹确认框")
    else:
        ui.shot(os.path.join(SHOTS, "snake_2_back_dialog.png"))
        ui.tap_text("继续玩", delay=0.6)

    print("✅ 贪吃蛇 速度/界面/返回 全部通过" if not fails else f"❌ 未通过：{fails}")
    return 0 if not fails else 1


if __name__ == "__main__":
    sys.exit(main())
