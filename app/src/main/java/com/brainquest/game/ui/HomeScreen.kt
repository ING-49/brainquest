package com.brainquest.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.Items
import com.brainquest.game.data.levelForXp
import com.brainquest.game.ui.CoinText
import com.brainquest.game.ui.BqProgressBar
import com.brainquest.game.ui.CoinIcon
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType

/**
 * 大厅主页（批25 布局重构）：
 * - 顶部用户卡（压缩，去掉重复的 Lv 昵称）
 * - 签到 / 每日挑战 两张整卡可点的日常卡并排
 * - 每日任务紧凑三行
 * - 游戏中心：统一 2 列 GameTile 网格 + 联机对战全宽横条（替代旧版「4 张网格卡 + 3 张全宽大卡」的割裂布局）
 * - 功能入口三等宽按钮（错题本/商店/成就）
 * - 道具 / 最佳成绩 紧凑收尾
 */
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var checkInReward by remember { mutableStateOf(0) }

    val checkedIn = player.lastCheckIn == vm.today()
    val dailyDone = vm.isDailyDone()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- 顶部用户卡 ----------
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBadge(player.avatar, size = 48)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("你好，${player.nickname}！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Lv.${levelForXp(player.xp)} · 连续挑战 ${player.dailyStreak} 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CoinText(player.coins, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { nav.navigate(com.brainquest.game.ui.Routes.SHOP) })
                }
                XpBar(player.xp, Modifier.padding(top = 10.dp))
            }
        }

        // ---------- 日常：签到 + 每日挑战（两张并排整卡可点） ----------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DailyMini(
                icon = "📅",
                title = "每日签到",
                sub = if (checkedIn) "今日已签 · 明天再来" else "点击签到领金币",
                done = checkedIn,
                onClick = { if (!checkedIn) { checkInReward = vm.checkIn(); vm.toast("✅ 签到成功，奖励已到账") } },
                modifier = Modifier.weight(1f),
            )
            DailyMini(
                icon = "🎯",
                title = "每日挑战",
                sub = if (dailyDone) "今日已完成" else "10 道混合题冲奖励",
                done = dailyDone,
                highlight = !dailyDone,
                onClick = { nav.navigate(Routes.DAILY) },
                modifier = Modifier.weight(1f),
            )
        }

        // ---------- 每日任务（紧凑三行） ----------
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("📋 每日任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                com.brainquest.game.data.DailyTasks.all.forEach { def ->
                    val progress = (player.dailyTaskProgress[def.id] ?: 0).coerceAtMost(def.goal)
                    val done = progress >= def.goal
                    val claimed = def.id in player.dailyTaskClaimed
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(def.emoji, style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(def.desc, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text("$progress/${def.goal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            BqProgressBar(
                                progress = progress.toFloat() / def.goal,
                                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                                height = 5.dp,
                                color = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            )
                        }
                        when {
                            claimed -> Text("✅", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp))
                            else -> Button(
                                onClick = {
                                    if (vm.claimDailyTask(def.id) > 0) Sfx.play(context, player.soundOn, player.hapticsOn, SfxType.COIN)
                                },
                                enabled = done,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.padding(start = 10.dp).height(30.dp),
                            ) {
                                CoinText(def.reward, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // ---------- 游戏中心：统一 2 列网格 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎮 游戏中心", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("6 款玩法 · 1 款联机", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameTile("⚔️", "速算英雄", "答题战斗闯关", { nav.navigate(Routes.SUBJECTS) }, modifier = Modifier.weight(1f))
            GameTile("🧱", "华容道", "滑动移块救曹操", { nav.navigate(Routes.KLOTSKI) }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameTile("⚫", "五子棋", "挑战电脑棋力", { nav.navigate(Routes.GOMOKU) }, modifier = Modifier.weight(1f))
            GameTile("🐍", "贪吃蛇", "滑动吃豆变长", { nav.navigate(Routes.SNAKE) }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameTile(
                "🏰", "地牢幸存者", "职业装备 · 四模式",
                { nav.navigate(Routes.DUNGEON) },
                accent = Color(0xFF7C4DFF), badge = "新", modifier = Modifier.weight(1f),
            )
            GameTile("🧟", "迷你幸存者", "弹幕求生三选一", { nav.navigate(Routes.SURVIVOR) }, accent = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
        }
        // 联机对战：全宽横条
        FeatureBanner(
            icon = "🌐", name = "联机对战", desc = "快速匹配 / 好友房间 · 与真人同台竞技",
            onClick = { nav.navigate(Routes.PK) },
        )

        // ---------- 功能入口 ----------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FuncChip("📖", "错题本", player.wrongBook.size.takeIf { it > 0 }?.toString(), { nav.navigate(Routes.WRONGBOOK) }, modifier = Modifier.weight(1f))
            FuncChip("🛒", "商店", null, { nav.navigate(Routes.SHOP) }, modifier = Modifier.weight(1f))
            FuncChip("🏅", "成就", player.achievements.size.takeIf { it > 0 }?.toString(), { nav.navigate(Routes.ACHIEVEMENTS) }, modifier = Modifier.weight(1f))
        }

        // ---------- 我的道具（紧凑） ----------
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("🎒 我的道具", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Items.all.forEach { def ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(def.emoji, style = MaterialTheme.typography.titleMedium)
                            Text("×${player.items[def.id] ?: 0}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(def.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---------- 最佳成绩（紧凑） ----------
        if (player.bestScores.isNotEmpty()) {
            val hrdTime = player.bestScores.entries.filter { it.key.startsWith("hrd_") && it.key.endsWith("_t") }
            val hrdSteps = player.bestScores.entries.filter { it.key.startsWith("hrd_") && !it.key.endsWith("_t") }
            val normal = player.bestScores.entries.filter { !it.key.startsWith("hrd_") }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("🏆 最佳成绩", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    hrdSteps.sortedBy { it.value }.take(2).forEach { (k, v) ->
                        BestRow("华容道·${k.removePrefix("hrd_")}（最少步数）", v)
                    }
                    hrdTime.sortedBy { it.value }.take(2).forEach { (k, v) ->
                        BestRow("华容道·${k.removePrefix("hrd_").removeSuffix("_t")}（最快用时 s）", v)
                    }
                    normal.sortedByDescending { it.value }.take(5).forEach { (k, v) ->
                        BestRow(bestName(k), v)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 日常小卡（签到/每日挑战）：整卡可点，右上角完成态 */
@Composable
private fun DailyMini(
    icon: String,
    title: String,
    sub: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(74.dp)
            .clip(shape)
            .background(
                when {
                    highlight && !done -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)))
                    else -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.surfaceContainerLow))
                },
            )
            .border(1.dp, if (highlight && !done) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (done) {
                Text("✓", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
        }
    }
}

/** 游戏入口磁贴：图标磁贴 + 名称/描述（+强调色/角标），全部入口统一形态 */
@Composable
private fun GameTile(
    icon: String,
    name: String,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(66.dp)
            .clip(shape)
            .background(
                if (accent != null) Brush.horizontalGradient(listOf(accent.copy(alpha = 0.14f), MaterialTheme.colorScheme.surfaceContainerLow))
                else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.surfaceContainerLow)),
            )
            .border(1.dp, accent?.copy(alpha = 0.5f) ?: Color.Transparent, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(11.dp))
                    .background(
                        if (accent != null) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                contentAlignment = Alignment.Center,
            ) { Text(icon, style = MaterialTheme.typography.headlineSmall) }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (badge != null) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp))
                        .background(accent ?: MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** 全宽功能横条（联机对战等强调入口） */
@Composable
private fun FeatureBanner(
    icon: String,
    name: String,
    desc: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerLow),
                ),
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 功能入口等宽按钮（错题本/商店/成就） */
@Composable
private fun FuncChip(icon: String, label: String, count: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(icon, style = MaterialTheme.typography.titleSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 5.dp),
        )
        if (count != null) {
            Text(
                " $count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 最佳成绩单行 */
@Composable
private fun BestRow(name: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Text("$value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/** bestScores 键 → 中文名 */
private fun bestName(k: String): String = when (k) {
    "gomoku_wins" -> "五子棋·总胜场"
    "gomoku_best_streak" -> "五子棋·最佳连胜"
    "snake_best" -> "贪吃蛇·最高分"
    "survivor_best" -> "迷你幸存者·最长生存（秒）"
    "survivor_kills" -> "迷你幸存者·单局击杀"
    "dungeon_floor" -> "地牢幸存者·最高层数"
    "dungeon_kills" -> "地牢幸存者·单局击杀"
    "arena_wave" -> "地牢角斗场·最高波数"
    else -> k
}
