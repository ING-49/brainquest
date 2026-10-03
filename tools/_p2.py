# -*- coding: utf-8 -*-
"""补丁 2：大厅改版（移除加成/重排）（跑完即删）"""
import io
p = 'app/src/main/java/com/brainquest/game/game/dungeon/DungeonScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1) 大厅移除加成按钮组
old = '''                    // 永久升级（局外成长）
                    val perks = player.dungeonPerks
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("hp", "❤️", 8), Triple("atk", "⚔️", 1), Triple("spd", "👟", 4)).forEach { (id, icon, gain) ->
                            val n = perks[id] ?: 0
                            val cost = vm.dungeonPerkCost(id)
                            OutlinedButton(onClick = { vm.buyDungeonPerk(id) }, modifier = Modifier.height(52.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$icon+$gain Lv.$n", style = MaterialTheme.typography.labelSmall, color = Color.White)
    
                                }
                            }
                        }
                    }
                    // 模式卡'''
new = '''                    // 模式卡'''
assert s.count(old) == 1, 'lobby perks'
s = s.replace(old, new)

# 2) 职业选择页移除加成按钮
old = '''                // 🧬 永久升级（局外成长，花费大厅同款金币）
                val perks = player.dungeonPerks
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("hp", "❤️", 8), Triple("atk", "⚔️", 1), Triple("spd", "👟", 4)).forEach { (id, icon, gain) ->
                        val n = perks[id] ?: 0
                        val cost = vm.dungeonPerkCost(id)
                        OutlinedButton(onClick = { vm.buyDungeonPerk(id) }, modifier = Modifier.height(56.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$icon+$gain Lv.$n", style = MaterialTheme.typography.labelSmall, color = Color.White)

                            }
                        }
                    }
                }'''
assert s.count(old) == 1, 'cs perks'
s = s.replace(old, '')

# 3) 大厅标题改横幅式
old = '''                    Text("🏰 地牢幸存者", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GamePalette.UI_GOLD)'''
new = '''                    Text(
                        "🏰 地牢幸存者",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GamePalette.UI_GOLD,
                        modifier = Modifier.padding(top = 10.dp),
                    )'''
assert s.count(old) == 1, 'title'
s = s.replace(old, new)

# 4) 纪录从右上移到大厅底部
old = '''                Column(
                    Modifier.weight(1.1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "🏆 最高纪录：${player.bestScores["dungeon_floor"] ?: 0} 层 · 最高击杀 ${player.bestScores["dungeon_kills"] ?: 0}",
                        style = MaterialTheme.typography.labelMedium,
                        color = GamePalette.UI_GOLD,
                    )'''
new = '''                Column(
                    Modifier.weight(1.1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {'''
assert s.count(old) == 1, 'record top'
s = s.replace(old, new)

old = '''                    OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth(0.9f).padding(top = 6.dp).height(40.dp)) { Text("返回") }
                }
            }
        }'''
new = '''                    OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth(0.9f).padding(top = 6.dp).height(40.dp)) { Text("返回") }
                    Text(
                        "🏆 最高纪录 ${player.bestScores["dungeon_floor"] ?: 0} 层 · 最高击杀 ${player.bestScores["dungeon_kills"] ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF78909C),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }'''
assert s.count(old) == 1, 'record bottom'
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8').write(s)
print('p2 ok')
