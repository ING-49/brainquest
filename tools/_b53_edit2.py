# -*- coding: utf-8 -*-
"""批53 编辑第二段：GAMEOVER 入场动画 + 新纪录脉动 + LEVELUP 数值对比 + 稀有流光"""
import io

p2 = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()

# C1) GAMEOVER 入场动画（GameDialog 外包 graphicsLayer）
old = '''        // ---------- Paused（批24 统一弹窗） ----------'''
# 先找 GAMEOVER overlay 的实际开头
idx = s2.find('SurvivorGame.Phase.GAMEOVER')
# GAMEOVER 块：if (hud.phase == SurvivorGame.Phase.GAMEOVER) { ... } — 定位它的 Box 行
import re
m = re.search(r'if \(hud\.phase == SurvivorGame\.Phase\.GAMEOVER\) \{\n(            Box\([^\n]*\n)', s2)
assert m, 'gameover block not found'
old_block = m.group(0)
new_block = '''if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
            // 批53：结算卡入场动画
            val goIn = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(goIn) {
                goIn.snapTo(0f)
                goIn.animateTo(1f, androidx.compose.animation.core.tween(220))
            }
''' + m.group(1).replace('Box(', 'Box(Modifier.graphicsLayer { alpha = goIn.value; val sc = 0.9f + 0.1f * goIn.value; scaleX = sc; scaleY = sc }).')
s2 = s2.replace(old_block, new_block)
print('gameover entrance ok')

# C2) 新纪录行金色脉动
old2 = '''                        Text("🎉 新纪录！", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GameUi.Gold)'''
if old2 in s2:
    m2 = re.search(r'val newPulse = remember \{ infiniteTransition', s2)
    if 'newPulse' not in s2:
        s2 = s2.replace('''        if (hud.phase == SurvivorGame.Phase.GAMEOVER) {''',
'''        if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
            val recPulse = rememberInfiniteTransition(label = "recPulse")
            val recA = recPulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(600, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse), label = "recA")''', 1)
        s2 = s2.replace(old2, '''                        Text("🎉 新纪录！", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GameUi.Gold.copy(alpha = recA.value))   // 批53：金色脉动''')
        print('record pulse ok')
else:
    # 备选：查实际新纪录行写法
    print('record line anchor differs; check manually')

# C3) LEVELUP 数值对比（desc 下方 当前→升级后）
old3 = '''                                Text(up.desc, style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim)'''
new3 = '''                                Text(up.desc, style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim)
                                // 批53：数值对比（当前 → 升级后，从实际数值计算）
                                val curTxt = when (up.id) {
                                    "atk", "atk2" -> "伤害 ${"%.0f".format(game.attackDmg)}"
                                    "aspd" -> "间隔 ${"%.2f".format(game.attackInterval)}s"
                                    "spd" -> "移速 ${"%.0f".format(game.moveSpeed)}"
                                    "hp", "hp2" -> "生命 ${"%.0f".format(game.maxHp)}"
                                    else -> "拾取 ${"%.0f".format(game.pickupRange)}"
                                }
                                val aftTxt = when (up.id) {
                                    "atk" -> "伤害 ${"%.0f".format(game.attackDmg * 1.25f)}"
                                    "atk2" -> "伤害 ${"%.0f".format(game.attackDmg * 1.5f)}"
                                    "aspd" -> "间隔 ${"%.2f".format(game.attackInterval * 0.85f)}s"
                                    "spd" -> "移速 ${"%.0f".format(game.moveSpeed * 1.10f)}"
                                    "hp" -> "生命 ${"%.0f".format(game.maxHp + 25f)}"
                                    "hp2" -> "生命 ${"%.0f".format(game.maxHp + 60f)}"
                                    else -> "拾取 ${"%.0f".format(game.pickupRange * 1.30f)}"
                                }
                                Text(
                                    "$curTxt → $aftTxt",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GameUi.Xp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )'''
assert old3 in s2, 'c3'
s2 = s2.replace(old3, new3)
print('levelup compare ok')

# C4) 稀有卡流光（✦ 稀有强化 alpha 脉动）
old4 = '''                                        "✦ 稀有强化",
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (up.rare) Color(0xFFFFB74D) else GameUi.Gold,'''
if old4 in s2:
    s2 = s2.replace(old4, '''                                        "✦ 稀有强化",
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (up.rare) GameUi.Gold.copy(alpha = 0.55f + 0.45f * sin(game.elapsed * 5f).let { kotlin.math.abs(it) }) else GameUi.Gold,''')
    print('rare shimmer ok')
else:
    # 实际写法可能是多行 color 分支
    m4 = re.search(r'Text\(\s*"✦ 稀有强化",\s*fontSize = 10\.sp, fontWeight = FontWeight\.Bold,\s*color = if \(up\.rare\) ([^)]+)\) else ([^,\n]+),', s2)
    print('rare line pattern:', bool(m4))
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('phase2 done')
