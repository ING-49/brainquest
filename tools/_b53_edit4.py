# -*- coding: utf-8 -*-
"""批53 编辑第三段 v2：GAMEOVER 入场+新纪录脉动 + LEVELUP 数值对比+稀有流光"""
import io

p2 = 'app/src/main/java/com/brainquest/game/game/survivor/SurvivorScreen.kt'
s2 = io.open(p2, encoding='utf-8').read()

# C1) GAMEOVER 入场动画（goIn + recPulse 声明 + Box 包裹）
old1 = '''        if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
            GameDialog(title = "💀 游戏结束", accent = GameUi.Hp, width = 480.dp) {'''
new1 = '''        if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
            // 批53：结算卡入场动画 + 新纪录脉动
            val goIn = remember { androidx.compose.animation.core.Animatable(0f) }
            val recPulse = rememberInfiniteTransition(label = "recPulse")
            val recA = recPulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(600, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse), label = "recA")
            androidx.compose.runtime.LaunchedEffect(hud.phase) {
                if (hud.phase == SurvivorGame.Phase.GAMEOVER) {
                    goIn.snapTo(0f)
                    goIn.animateTo(1f, androidx.compose.animation.core.tween(220))
                }
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = 0.3f + 0.7f * goIn.value
                    val sc = 0.9f + 0.1f * goIn.value
                    scaleX = sc; scaleY = sc
                },
                contentAlignment = Alignment.Center,
            ) {
            GameDialog(title = "💀 游戏结束", accent = GameUi.Hp, width = 480.dp) {'''
assert old1 in s2, 'c1'
s2 = s2.replace(old1, new1)

# C1b) GAMEOVER 块收尾补 Box 闭合（if 块的 } 前）
old1b = '''        // ---------- Paused（批24 统一弹窗） ----------
        if (hud.phase == SurvivorGame.Phase.PAUSED) {'''
new1b = '''            }
        }

        // ---------- Paused（批24 统一弹窗） ----------
        if (hud.phase == SurvivorGame.Phase.PAUSED) {'''
assert old1b in s2, 'c1b'
s2 = s2.replace(old1b, new1b)

# C2) 新纪录脉动（实际锚点）
old2 = '''                        Text(
                            "🎉 新纪录！",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GameUi.Gold,
                        )'''
new2 = '''                        Text(
                            "🎉 新纪录！",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GameUi.Gold.copy(alpha = recA.value),   // 批53：金色脉动
                        )'''
assert old2 in s2, 'c2'
s2 = s2.replace(old2, new2)

# C3) LEVELUP 数值对比（desc 后）
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

# C4) 稀有卡流光
old4 = '''                                        color = if (up.rare) Color(0xFFFFB74D) else GameUi.Gold,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )   // 批45：稀有卡金标'''
new4 = '''                                        color = if (up.rare) GameUi.Gold.copy(alpha = 0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(game.elapsed * 5f))) else GameUi.Gold,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )   // 批45：稀有卡金标；批53：流光'''
assert old4 in s2, 'c4'
s2 = s2.replace(old4, new4)

io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('phase2 all ok')
