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
print('done ->', os.path.abspath(OUT))
