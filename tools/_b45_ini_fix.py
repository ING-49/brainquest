# -*- coding: utf-8 -*-
"""批45 迁移：AVD ini 路径改到 D 盘"""
import io

for ini in [
    r'D:\Software\Android-Studio\avd-home\BQ2.ini',
    r'D:\Software\Android-Studio\avd-home\CarMonitor.ini',
]:
    s = io.open(ini, encoding='utf-8').read()
    old = r'D:\Software\Android-Studio\avd-home'
    new = r'D:\Software\Android-Studio\avd-home'
    if old in s:
        s = s.replace(old, new)
        io.open(ini, 'w', encoding='utf-8', newline='').write(s)
        print(ini, 'patched')
    else:
        print(ini, 'no old path found (already patched?) ->', [l for l in s.splitlines() if 'path=' in l])
