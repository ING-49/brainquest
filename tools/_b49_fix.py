# -*- coding: utf-8 -*-
"""批49 收尾：codexTab 状态 + Tab 行 + 人物卡 + 网格门控 + 清理注释重叠"""
import io

p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 清理 codexDetail 行的重叠注释 + 加 codexTab
old = '''    var codexDetail by remember { mutableStateOf<String?>(null) }   // 批47：图鉴详情页当前选中 key      // 📖 怪物图鉴（批20-D）'''
new = '''    var codexDetail by remember { mutableStateOf<String?>(null) }   // 批47：图鉴详情页当前选中 key
    var codexTab by remember { mutableIntStateOf(0) }   // 批49：0=怪物 1=人物'''
if old in s:
    s = s.replace(old, new)
    print('state ok')
elif 'var codexTab by remember' in s:
    print('state already')
else:
    raise AssertionError('codexDetail line not found')

# 2) Tab 行（图鉴头部 Spacer 之后）
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
                    if (codexTab == 0) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        codexEntries.chunked(4).forEach { rowEntries ->'''
if '批49：图鉴分类 Tab' in s:
    print('tabs already')
else:
    assert old2 in s, 'tabs anchor'
    s = s.replace(old2, new2)
    print('tabs added')

# 3) 网格收尾后追加人物卡（else 分支）
old3 = '''                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // ---------- 批47：图鉴详情面板'''
new3 = '''                        Spacer(Modifier.height(6.dp))
                    }
                    }
                    else {
                        // ---------- 批49：人物图鉴（三职业立绘+属性+使用局数） ----------
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            Text("👤 选择职业开始冒险，使用局数随每次「开始冒险」累计", fontSize = 10.sp, color = GameUi.TextDim)
                            Spacer(Modifier.height(6.dp))
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
                    }
                }
            }
        }

        // ---------- 批47：图鉴详情面板'''
if '批49：人物图鉴（三职业立绘' in s:
    print('person cards already')
else:
    assert old3 in s, 'person anchor'
    s = s.replace(old3, new3)
    print('person cards added')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('done')
