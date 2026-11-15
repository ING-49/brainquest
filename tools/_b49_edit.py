# -*- coding: utf-8 -*-
"""批49 编辑：人物图鉴（图鉴页 Tab + drawPortrait 立绘卡 + 使用局数）"""
import io

# ============ A. PlayerState：classRuns ============
p = 'app/src/main/java/com/brainquest/game/data/PlayerState.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    val survivorSeen: List<String> = emptyList(), // 📐 数学空间已遭遇的怪种类（批48）'''
new = '''    val survivorSeen: List<String> = emptyList(), // 📐 数学空间已遭遇的怪种类（批48）
    val classRuns: Map<String, Int> = emptyMap(), // 👤 各职业使用局数（批49：人物图鉴）'''
assert old in s, 'a'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('PlayerState ok')

# ============ B. AppViewModel：recordClassRun ============
p = 'app/src/main/java/com/brainquest/game/AppViewModel.kt'
s = io.open(p, encoding='utf-8').read()
old = '''    /** 图鉴：标记幸存者已遭遇的怪种类（批48） */'''
new = '''    /** 👤 记录职业使用局数（批49：人物图鉴） */
    fun recordClassRun(classId: String) = commit {
        it.copy(classRuns = it.classRuns + (classId to (it.classRuns[classId] ?: 0) + 1))
    }

    /** 图鉴：标记幸存者已遭遇的怪种类（批48） */'''
assert old in s, 'b'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('AppViewModel ok')

# ============ C. DungeonScreen：开始冒险记录局数 ============
p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s = io.open(p, encoding='utf-8').read()
old = '''                        GameActionButton(
                            "▶  开始冒险",
                            onClick = {
                                game.pendingPerks = player.dungeonPerks
                                game.startFromLobby()
                            },'''
new = '''                        GameActionButton(
                            "▶  开始冒险",
                            onClick = {
                                vm.recordClassRun(hud.lobbyCls)   // 批49：人物图鉴使用局数
                                game.pendingPerks = player.dungeonPerks
                                game.startFromLobby()
                            },'''
assert old in s, 'c'
s = s.replace(old, new)

# D. 图鉴页：Tab + 人物卡
old2 = '''                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        codexEntries.chunked(4).forEach { rowEntries ->'''
new2 = '''                    Spacer(Modifier.height(8.dp))
                    // 批49：图鉴分类 Tab
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("🧟 怪物", "👤 人物").forEachIndexed { ti, label ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (codexTab == ti) GamePalette.UI_GOLD.copy(alpha = 0.25f) else Color(0x14FFFFFF))
                                    .border(1.2.dp, if (codexTab == ti) GamePalette.UI_GOLD else Color(0x1FFFFFFF), RoundedCornerShape(999.dp))
                                    .clickable { codexTab = ti }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (codexTab == ti) GamePalette.UI_GOLD else GameUi.Text)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (codexTab == 1) {
                        // ---------- 批49：人物图鉴（三职业立绘+属性+使用局数） ----------
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            ClassDef.ALL.forEach { cls ->
                                val runs = player.classRuns[cls.id] ?: 0
                                GamePanel(Modifier.fillMaxWidth().padding(vertical = 6.dp), accent = Color(cls.accentColor), contentPadding = 14.dp) {
                                    Row {
                                        Canvas(Modifier.size(110.dp)) {
                                            EntityRenderer.drawPortrait(this, cls, size.width / 2f, size.height / 2f, 0.55f, animT.floatValue)
                                        }
                                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                            Text(cls.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(cls.desc, style = MaterialTheme.typography.bodySmall, color = GameUi.TextDim)
                                            Text(
                                                "❤${cls.maxHp}  ⚔${cls.attack}  👟${cls.speed.toInt()}  ⚡${cls.attackInterval}s",
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                color = GamePalette.UI_GOLD, modifier = Modifier.padding(top = 5.dp),
                                            )
                                            Text(
                                                "被动:${cls.passiveName} · 技能:${cls.skillName}",
                                                fontSize = 10.sp, color = GameUi.Text, modifier = Modifier.padding(top = 4.dp),
                                            )
                                            Text(
                                                "🏭 累计使用 ${runs} 局",
                                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                color = GamePalette.UI_COIN, modifier = Modifier.padding(top = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        codexEntries.chunked(4).forEach { rowEntries ->'''
assert old2 in s, 'd'
s = s.replace(old2, new2)

# E. codexTab 状态（挂 codexDetail 后）
old3 = '''    var codexDetail by remember { mutableStateOf<String?>(null) }   // 批47：图鉴详情页当前选中 key'''
new3 = '''    var codexDetail by remember { mutableStateOf<String?>(null) }   // 批47：图鉴详情页当前选中 key
    var codexTab by remember { mutableIntStateOf(0) }   // 批49：0=怪物 1=人物'''
assert old3 in s, 'e'
s = s.replace(old3, new3)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('DungeonScreen ok')
