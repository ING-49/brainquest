# -*- coding: utf-8 -*-
"""批47 第二阶段：图鉴档案详情页 + 击杀展示 + 退出合并修复"""
import io

p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 详情状态（挂 showIndex 后）
old = '''    var showIndex by remember { mutableStateOf(false) }'''
new = '''    var showIndex by remember { mutableStateOf(false) }
    var codexDetail by remember { mutableStateOf<String?>(null) }   // 批47：图鉴详情页当前选中 key'''
assert old in s, 's1'
s = s.replace(old, new)

# 2) 三处 exitToLobby 退出合并（中途退出不再丢图鉴记录）
s = s.replace('''onClick = { rewarded = false; game.exitToLobby() }''',
'''onClick = {
                            vm.mergeDungeonSeen(game.seenMonsters.toList())
                            vm.mergeMonsterKills(game.monsterKills.toMap())   // 批47：中途退出也合并
                            rewarded = false; game.exitToLobby()
                        }''')
old2 = '''                    onClick = { confirmExit = false; game.exitToLobby() },'''
new2 = '''                    onClick = {
                        vm.mergeDungeonSeen(game.seenMonsters.toList())
                        vm.mergeMonsterKills(game.monsterKills.toMap())   // 批47：中途退出也合并
                        confirmExit = false; game.exitToLobby()
                    },'''
assert old2 in s, 's2'
s = s.replace(old2, new2)

# 3) 结算 LaunchedEffect 也合并击杀数
old3 = '''            vm.mergeDungeonSeen(game.seenMonsters.toList())   // 图鉴合并（批20-D）'''
new3 = '''            vm.mergeDungeonSeen(game.seenMonsters.toList())   // 图鉴合并（批20-D）
            vm.mergeMonsterKills(game.monsterKills.toMap())   // 批47：击杀数合并'''
assert old3 in s, 's3'
s = s.replace(old3, new3)

# 4) 大厅芯片计数 11→12
old4 = '''            val seenCount = player.seenMonsters.count { !it.startsWith("QUIZ_") }.coerceAtMost(11)
            val seenTotal = 11   // 硬编码'''
new4 = '''            val seenCount = player.seenMonsters.count { !it.startsWith("QUIZ_") }.coerceAtMost(12)
            val seenTotal = 12   // 批47：+泥沼傀儡'''
assert old4 in s, 's4'
s = s.replace(old4, new4)

# 5) 图鉴整块替换（收集层 + 卡片可点 + 详情面板）
start_anchor = '''        // ---------- 📖 怪物图鉴（批24：场景背景 + 4 列收藏卡） ----------'''
end_anchor = '''        // ---------- 🎲/🧠 事件房（批24：统一弹窗 + 主题色） ----------'''
i1 = s.find(start_anchor)
i2 = s.find(end_anchor)
assert i1 > 0 and i2 > i1, f'codex block bounds: {i1},{i2}'
new_block = '''        // ---------- 📖 怪物图鉴（批24 收集层 + 批47 档案详情：属性/攻击特点/行动习惯/击杀数） ----------
        if (showIndex) {
            val seenCount = codexEntries.count { e ->
                player.seenMonsters.contains(e.key) || player.seenMonsters.contains(e.key.removePrefix("BOSS_"))
            }
            Box(Modifier.fillMaxSize()) {
                GameBackdrop(accent = Color(0xFFBA68C8))
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton("←", onClick = { showIndex = false }, size = 38.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                "📖 怪物图鉴",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = GamePalette.UI_GOLD,
                            )
                            Text(
                                "已遭遇 ${seenCount} / ${codexEntries.size} · 点击卡片查看档案",
                                fontSize = 10.sp,
                                color = GameUi.TextDim,
                            )
                        }
                        HudXpBar(
                            seenCount.toFloat() / codexEntries.size,
                            Modifier.width(140.dp),
                            height = 6.dp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        codexEntries.chunked(4).forEach { rowEntries ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowEntries.forEach { e ->
                                    val seen = player.seenMonsters.contains(e.key) || player.seenMonsters.contains(e.key.removePrefix("BOSS_"))
                                    GamePanel(
                                        Modifier.weight(1f)
                                            .clickable(enabled = seen) { codexDetail = e.key },
                                        accent = if (seen) Color(0xFFBA68C8) else null,
                                        contentPadding = 8.dp,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
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
                                            }
                                            Text(
                                                if (seen) e.name else "？？？",
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                color = Color.White, maxLines = 1,
                                            )
                                            Text(
                                                if (seen) "已收录" else "未遭遇",
                                                fontSize = 9.sp,
                                                color = if (seen) Color(0xFFBA68C8) else GameUi.TextFaint,
                                            )
                                        }
                                    }
                                }
                                repeat(4 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // ---------- 批47：图鉴详情面板（属性/攻击特点/行动习惯/出没/击杀数） ----------
        codexDetail?.let { dKey ->
            val entry = codexEntries.firstOrNull { it.key == dKey }
            if (entry != null) {
                val seen = player.seenMonsters.contains(entry.key) || player.seenMonsters.contains(entry.key.removePrefix("BOSS_"))
                val kills = player.monsterKills[entry.key] ?: 0
                Box(Modifier.fillMaxSize().background(Color(0xE6070A14))) {
                    Column(
                        Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundIconButton("←", onClick = { codexDetail = null }, size = 38.dp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(
                                    if (seen) entry.name else "？？？",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = GamePalette.UI_GOLD,
                                )
                                Text(
                                    if (seen) "已收录 · 累计击杀 ${kills}" else "档案加密 · 未遭遇",
                                    fontSize = 10.sp, color = GameUi.TextDim,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(120.dp)) {
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
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (seen) {
                            Text("属性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GamePalette.UI_GOLD)
                            CodexStatRow("血量", entry.hpBar, Color(0xFFE53935))
                            CodexStatRow("速度", entry.spdBar, Color(0xFF4FC3F7))
                            CodexStatRow("威胁", entry.threatBar, Color(0xFFFFB74D))
                            Spacer(Modifier.height(10.dp))
                            Text("⚔️ 攻击特点", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GamePalette.UI_GOLD)
                            Text(entry.attack, style = MaterialTheme.typography.bodySmall, color = GameUi.Text, modifier = Modifier.padding(top = 3.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("🧠 行动习惯", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GamePalette.UI_GOLD)
                            Text(entry.behavior, style = MaterialTheme.typography.bodySmall, color = GameUi.Text, modifier = Modifier.padding(top = 3.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("🗺️ 出没", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GamePalette.UI_GOLD)
                            Text(entry.haunt, style = MaterialTheme.typography.bodySmall, color = GameUi.Text, modifier = Modifier.padding(top = 3.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("💀 累计击杀 ${kills}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GamePalette.UI_COIN)
                        } else {
                            Text("该怪物的档案尚未解锁——进入地牢将其击杀，档案会自动记录。", style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim)
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }

'''
s = s[:i1] + new_block + s[i2:]

# 6) 文件级：CodexEntry 数据 + 12 条目 + 详情属性行组件（追加到文件末尾）
s += '''

/** 批47：怪物图鉴档案条目（属性三围 + 攻击特点 + 行动习惯 + 出没） */
private data class CodexEntry(
    val sprite: String,
    val name: String,
    val key: String,
    val hpBar: Float,
    val spdBar: Float,
    val threatBar: Float,
    val attack: String,
    val behavior: String,
    val haunt: String,
)

/** 批47：12 条怪物档案（数据源自 CombatEngine 各 kind AI 与刷怪规则） */
private val codexEntries = listOf(
    CodexEntry("swampy", "沼泽史莱姆", "SLIME", 0.35f, 0.45f, 0.30f,
        "弹跳接近后压扁蓄力 0.4s，随即 3.4 倍速短距突进（冷却 3s）",
        "周期性弹跳前进，蓄力时身体压扁——看到压扁立刻横向闪避",
        "全楼层 · 战斗房"),
    CodexEntry("skelet", "骷髅兵", "SKELETON", 0.40f, 0.55f, 0.40f,
        "贴近至近距离横扫（冷却 1.6s）",
        "稳步推进，抬臂 0.3s 后出招；节奏快，是前期的主力纠缠者",
        "全楼层 · 战斗/精英房"),
    CodexEntry("skelet", "骷髅弓手", "BONE_ARCHER", 0.30f, 0.62f, 0.45f,
        "保持 380~560 距离，前摇 0.45s 锁向后射出单发快箭——侧移可躲",
        "远程风筝：贴太近会拉开距离，箭出手前方向已锁定",
        "第 2 层起 · 战斗/第二波"),
    CodexEntry("imp", "小恶魔蝠", "BAT", 0.30f, 0.78f, 0.40f,
        "蓄力 0.35s 后 4 倍速俯冲突袭（冷却 2.8s）",
        "正弦绕飞接近，轨迹飘忽；俯冲前会短暂悬停",
        "全楼层 · 战斗房"),
    CodexEntry("necromancer", "邪教术士", "CASTER", 0.35f, 0.50f, 0.55f,
        "保持 260~420 距离，三连扇形弹幕（冷却 2.4s）",
        "始终与玩家拉开距离；弹幕呈扇形——侧移或贴脸打断蓄力",
        "第 2 层起 · 战斗/第二波"),
    CodexEntry("ogre", "巨型盾卫", "SHIELD_GUARD", 0.85f, 0.38f, 0.70f,
        "重型盾击（冷却 2.4s），伤害 1.35 倍",
        "正面格挡：来自正面的伤害仅 25% 生效——绕到背后攻击是唯一正解",
        "第 3 层起 · 战斗房"),
    CodexEntry("wogol", "自爆犬魔", "BOOM_SLIME", 0.25f, 0.68f, 0.60f,
        "贴脸后 0.6s 起爆（闪红预警），爆炸伤害 1.6 倍并自灭",
        "1.35 倍速冲刺贴近；看到红色脉冲立刻拉开距离或抢先击杀",
        "第 2 层起 · 战斗/第二波"),
    CodexEntry("muddy", "泥沼傀儡", "MUDDY_GOLEM", 0.75f, 0.30f, 0.50f,
        "无主动攻击，接触伤害；特殊能力「吞弹」——吸收玩家子弹并回复 2 生命",
        "缓慢坚定地逼近——远程打它是在喂它，用近战或绕后解决",
        "第 2 层起 · 战斗/第二波"),
    CodexEntry("big_demon", "魔王 · 大恶魔", "BOSS_big_demon", 1.0f, 0.50f, 1.0f,
        "层机制轮换：预警冲刺+召唤史莱姆 / 8 向弹幕环+瞬移贴脸",
        "血量低于 30% 进入狂暴（1.5 倍速），竞技场相机拉远聚焦",
        "每层末 · BOSS 房"),
    CodexEntry("chort", "魔王 · 短角恶魔", "BOSS_chort", 1.0f, 0.50f, 1.0f,
        "层机制轮换：护盾 15%+反伤 15% / 召唤蝠群+旋转弹幕",
        "狂暴期弹幕密度翻倍——护盾期先破盾再输出",
        "每层末 · BOSS 房"),
    CodexEntry("big_zombie", "魔王 · 巨尸", "BOSS_big_zombie", 1.0f, 0.45f, 1.0f,
        "层机制轮换（四套机制按层数循环）",
        "巨大体型弹幕覆盖面广——贴边走位躲环",
        "每层末 · BOSS 房"),
    CodexEntry("ogre", "魔王 · 巨魔", "BOSS_ogre", 1.0f, 0.45f, 1.0f,
        "第 5 层狂暴期追加 12 向全屏弹幕",
        "最终考验：全部机制同时在线",
        "第 5 层末 · BOSS 房"),
)

/** 批47：图鉴详情属性行 */
@Composable
private fun CodexStatRow(label: String, frac: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 11.sp, color = GameUi.TextDim, modifier = Modifier.width(40.dp))
        BqProgressBar(frac, Modifier.weight(1f), height = 7.dp, color = color)
    }
}
'''
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('codex page ok')
