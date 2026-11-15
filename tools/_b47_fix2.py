# -*- coding: utf-8 -*-
"""批47 修复：收集卡 Canvas 包裹 + 详情立绘 dstSize 版 + imports"""
import io

p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s = io.open(p, encoding='utf-8').read()

# imports
for imp, anchor in [
    ('import kotlin.math.min', 'import androidx.compose.ui.graphics.Color'),
    ('import androidx.compose.ui.graphics.FilterQuality', 'import androidx.compose.ui.graphics.Color'),
    ('import androidx.compose.ui.unit.IntOffset', 'import androidx.compose.ui.unit.dp'),
    ('import androidx.compose.ui.graphics.IntSize', 'import androidx.compose.ui.unit.IntOffset'),
]:
    if imp not in s:
        if anchor in s:
            s = s.replace(anchor, anchor + '\n' + imp, 1)
        else:
            s = s.replace('import androidx.compose.ui.draw.clip', 'import androidx.compose.ui.draw.clip\n' + imp, 1)

# 收集卡片：漏包 Canvas 的错误块 → Canvas 版
old = '''                                            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                                if (seen && SpriteAnims.ready) {
                                                    val frames = SpriteAnims.frames(e.sprite, run = false)
                                                    frames.firstOrNull()?.let { bmp ->
                                                        val sc = min(46f / bmp.width, 46f / bmp.height)
                                                        withTransform({ scale(sc, sc, pivot = Offset.Center) }) {
                                                            drawImage(bmp, filterQuality = FilterQuality.None)
                                                        }
                                                    }
                                                } else {
                                                    drawRect(Color(0xFF0A0D14), Offset(4f, 4f), Size(38f, 38f))
                                                    drawCircle(Color(0x33FFFFFF), 7f, Offset(size.center), style = Stroke(1.5f))
                                                }
                                            }'''
new = '''                                            Canvas(Modifier.size(46.dp)) {
                                                if (seen && SpriteAnims.ready) {
                                                    val frames = SpriteAnims.frames(e.sprite, run = false)
                                                    frames.firstOrNull()?.let { bmp ->
                                                        val sc = min(46f / bmp.width, 46f / bmp.height)
                                                        drawImage(
                                                            bmp,
                                                            srcOffset = IntOffset.Zero,
                                                            srcSize = IntSize(bmp.width, bmp.height),
                                                            dstOffset = IntOffset(((size.width - bmp.width * sc) / 2f).toInt(), ((size.height - bmp.height * sc) / 2f).toInt()),
                                                            dstSize = IntSize((bmp.width * sc).toInt(), (bmp.height * sc).toInt()),
                                                            filterQuality = FilterQuality.None,
                                                        )
                                                    }
                                                } else {
                                                    drawRect(Color(0xFF0A0D14), Offset(4f, 4f), Size(38f, 38f))
                                                    drawCircle(Color(0x33FFFFFF), 7f, center, style = Stroke(1.5f))
                                                }
                                            }'''
assert old in s, 'card canvas'
s = s.replace(old, new)

# 详情立绘：同样 dstSize 版（去掉 Offset.Center 依赖）
old2 = '''                            Canvas(Modifier.size(120.dp)) {
                                if (seen && SpriteAnims.ready) {
                                    val frames = SpriteAnims.frames(entry.sprite, run = false)
                                    frames.firstOrNull()?.let { bmp ->
                                        val sc = min(size.width / bmp.width, size.height / bmp.height)
                                        withTransform({ scale(sc, sc, pivot = Offset(size.width / 2f, size.height / 2f)) }) {
                                            drawImage(bmp, filterQuality = FilterQuality.None)
                                        }
                                    }
                                } else {
                                    drawRect(Color(0xFF0A0D14), Offset(10f, 10f), Size(size.width - 20f, size.height - 20f))
                                    drawCircle(Color(0x22FFFFFF), 24f, Offset(size.center), style = Stroke(2f))
                                }
                            }'''
new2 = '''                            Canvas(Modifier.size(120.dp)) {
                                if (seen && SpriteAnims.ready) {
                                    val frames = SpriteAnims.frames(entry.sprite, run = false)
                                    frames.firstOrNull()?.let { bmp ->
                                        val sc = min(size.width / bmp.width, size.height / bmp.height)
                                        drawImage(
                                            bmp,
                                            srcOffset = IntOffset.Zero,
                                            srcSize = IntSize(bmp.width, bmp.height),
                                            dstOffset = IntOffset(((size.width - bmp.width * sc) / 2f).toInt(), ((size.height - bmp.height * sc) / 2f).toInt()),
                                            dstSize = IntSize((bmp.width * sc).toInt(), (bmp.height * sc).toInt()),
                                            filterQuality = FilterQuality.None,
                                        )
                                    }
                                } else {
                                    drawRect(Color(0xFF0A0D14), Offset(10f, 10f), Size(size.width - 20f, size.height - 20f))
                                    drawCircle(Color(0x22FFFFFF), 24f, center, style = Stroke(2f))
                                }
                            }'''
assert old2 in s, 'detail canvas'
s = s.replace(old2, new2)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('fixed')
