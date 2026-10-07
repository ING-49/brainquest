# -*- coding: utf-8 -*-
"""地牢幸存者音效合成器：numpy 生成短 WAV 入 app/src/main/res/raw/（零外部素材）。
用法：python tools/gen_dungeon_sfx.py   （重跑会覆盖同名 wav）"""
import numpy as np, wave, os, shutil

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')

def env(n, a=0.005, r=0.15):
    """attack/release 包络（a: attack 秒, r: release 比例）"""
    e = np.ones(n)
    an = max(1, int(a * SR))
    e[:an] = np.linspace(0, 1, an)
    rn = max(1, int(n * r))
    e[-rn:] *= np.linspace(1, 0, rn)
    return e

def tone(freq, dur, kind='sine', detune=0.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    if kind == 'sine':
        s = np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t)
    elif kind == 'square':
        s = np.sign(np.sin(2 * np.pi * freq * t)) * 0.5
    else:  # saw
        s = 2 * ((freq * t) % 1) - 1
    if detune:
        s += np.sin(2 * np.pi * (freq + detune) * t)
    return s * env(n)

def noise(dur, lp=0.3):
    n = int(dur * SR)
    x = np.random.uniform(-1, 1, n)
    # 一阶低通
    for _ in range(2):
        x = np.convolve(x, [lp, 1 - lp], 'same')
    return x * env(n, a=0.001, r=0.6)

def sweep(f0, f1, dur, kind='sine'):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = np.linspace(f0, f1, n)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph)
    if kind == 'rich':
        s += 0.4 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph)
    return s * env(n)

def seq(*parts):
    """顺序拼接（numpy 数组序列）"""
    return np.concatenate(parts)

def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out

def save(name, sig, gain=0.8):
    sig = sig / (np.abs(sig).max() + 1e-9) * gain
    pcm = (sig * 32767).astype(np.int16)
    path = os.path.join(OUT, name + '.wav')
    with wave.open(path, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(name + '.wav', len(pcm) / SR, 's')

os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(42)

# 命中：短噪声 + 低频闷响
save('dg_hit', mix(noise(0.06, 0.15), tone(150, 0.07) * 0.9), 0.7)
# 暴击：更大闷响 + 金属高音 ping
save('dg_crit', mix(noise(0.09, 0.12), tone(110, 0.12), tone(1250, 0.1) * 0.35), 0.85)
# 射击：快速上扫气声
save('dg_shoot', mix(sweep(600, 1400, 0.07), noise(0.05, 0.4) * 0.4), 0.55)
# 受伤：下扫 + 噪声
save('dg_hurt', mix(sweep(400, 120, 0.16, 'rich'), noise(0.12, 0.2) * 0.5), 0.8)
# 拾取：两连升调
save('dg_pickup', seq(tone(880, 0.05) * 0.7, tone(1320, 0.07)), 0.55)
# 升级：三连上行琶音
save('dg_levelup', seq(tone(523, 0.11), tone(659, 0.11), mix(tone(784, 0.22), tone(1046, 0.2) * 0.5)), 0.75)
# 技能：快速上扫闪音
save('dg_skill', mix(sweep(300, 1800, 0.2, 'rich'), noise(0.15, 0.5) * 0.25), 0.7)
# Boss 吼：低频颤 + 噪吼
growl = tone(70, 0.5) * (1 + 0.3 * np.sin(2 * np.pi * 11 * np.arange(int(0.5 * SR)) / SR))
save('dg_boss', mix(growl, noise(0.45, 0.1) * 0.6, sweep(200, 60, 0.5) * 0.5), 0.85)
# 胜利：四音号角
save('dg_victory', seq(tone(523, 0.14), tone(659, 0.14), tone(784, 0.14), mix(tone(1046, 0.34), tone(784, 0.3) * 0.4)), 0.8)
# 失败：下行三音
save('dg_lose', seq(tone(392, 0.2), tone(311, 0.2), mix(tone(233, 0.42), tone(220, 0.4) * 0.6)), 0.75)
# 空挥 whoosh：下扫气声（出手成功提示，批6）
save('dg_whoosh', mix(sweep(300, 90, 0.18), noise(0.15, 0.3) * 0.5), 0.5)

# ---------- 批11 BGM 三态循环（8s @22050 单声道，首尾淡出入接缝） ----------
BGM_SR, BGM_DUR = SR, 8.0
BN = int(BGM_DUR * BGM_SR)
bt = np.arange(BN) / BGM_SR
benv = np.ones(BN)   # 首尾淡出入（接缝轻）
fade = int(0.15 * BGM_SR)
benv[:fade] = np.linspace(0, 1, fade)
benv[-fade:] = np.linspace(1, 0, fade)

def note(freq, start, dur, amp=1.0, kind='sine'):
    """在 BGM 时基上放一个音"""
    s = tone(freq, dur, kind)
    i0 = int(start * BGM_SR)
    n = min(len(s), BN - i0)
    seg = np.zeros(BN)
    if n > 0: seg[i0:i0 + n] += s[:n] * amp
    return seg

# explore：柔和氛围垫（Am-F-C-G 长音 + 低音根），音量最低
pad = np.zeros(BN)
for bar, chord in enumerate([[110, 220, 261.6], [87.3, 174.6, 220], [130.8, 261.6, 329.6], [98, 196, 246.9]]):
    for f in chord:
        pad += note(f, bar * 2.0, 2.1, 0.16) * (1 + 0.15 * np.sin(2 * np.pi * 0.25 * bt))
save('dg_bgm_explore', pad * benv, 0.5)

# combat：140BPM（拍长 0.4286s）——底鼓 + 军鼓噪声 + 贝斯琶音 A2/A2/C3/E3
beat = 60 / 140
cb = np.zeros(BN)
k = int(beat * BGM_SR)
bi = 0
while bi < BN:
    cb += note(55, bi / BGM_SR, 0.12, 0.9)          # 底鼓（低音 thump）
    sn = noise(0.08, 0.5)                            # 军鼓：短噪声（每 2 拍 1 次）
    n = min(len(sn), BN - bi - k // 2)
    if n > 0: cb[bi + k // 2: bi + k // 2 + n] += sn[:n] * 0.35
    bi += k
arp = np.zeros(BN)
bass_line = [110, 110, 130.8, 164.8] * 14
for idx, f in enumerate(bass_line):
    arp += note(f, idx * beat / 2, beat / 2 * 0.9, 0.22, kind='saw')
save('dg_bgm_combat', (cb * 0.7 + arp) * benv, 0.5)

# boss：低音驱动 + 三全音点缀（压迫感）
bb = np.zeros(BN)
bass_line2 = [82.4, 82.4, 82.4, 116.5] * 14   # E2 与 A#3 三全音张力
for idx, f in enumerate(bass_line2):
    bb += note(f, idx * beat / 2, beat / 2 * 0.95, 0.3, kind='square')
bb += note(466.2, 0, 8.0, 0.05)   # A#3 高垫（持续不协和）
bb += note(233.1, 0, 8.0, 0.06)
save('dg_bgm_boss', bb * benv, 0.5)
print('done ->', os.path.abspath(OUT))
