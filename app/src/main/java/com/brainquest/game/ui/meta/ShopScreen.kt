package com.brainquest.game.ui.meta

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.Items
import com.brainquest.game.ui.CoinIcon
import com.brainquest.game.ui.CoinText
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.components.KawaiiAvatar
import com.brainquest.game.ui.theme.AppThemes

private const val KAWAII_PRICE = 120

private val KAWAII_NAMES = listOf("樱粉", "蓝蓝", "香芋", "抹茶", "奶黄", "薄荷", "草莓", "奶咖")

private data class PendingPurchase(val kind: String, val id: String, val label: String, val price: Int)

/** 商店：道具 / 主题 / Q 版头像。购买先弹确认框，成功走 vm.events toast + 金币动效，已拥有走「使用中」徽标。 */
@Composable
fun ShopScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    var pending by remember { mutableStateOf<PendingPurchase?>(null) }
    var coinFlash by remember { mutableStateOf<Int?>(null) }
    var coinFlashKey by remember { mutableIntStateOf(0) }

    fun onConfirm(p: PendingPurchase) {
        val ok = when (p.kind) {
            "item" -> vm.buyItem(p.id)
            "theme" -> vm.buyTheme(p.id, p.price).also { if (it) vm.setTheme(p.id) }
            "avatar" -> vm.buyAvatar(p.id, p.price, p.label).also { if (it) vm.setAvatar(p.id) }
            else -> false
        }
        if (ok) {
            coinFlash = -p.price
            coinFlashKey++
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PageHeader("🛒 商店", onBack = { nav.popBackStack() }, subtitle = "购置道具与装扮")

            // 金币余额：购买后数字上滑刷新，扣款浮字上飘渐隐
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box {
                    AnimatedContent(
                        targetState = player.coins,
                        transitionSpec = {
                            (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                        },
                        label = "coins",
                    ) { c ->
                        CoinText(c, style = MaterialTheme.typography.titleMedium)
                    }
                    coinFlash?.let { delta ->
                        key(coinFlashKey) { FloatingCoinDelta(delta) { coinFlash = null } }
                    }
                }
            }

            // ---------- 道具 ----------
            Text("🧰 道具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
            Items.all.forEach { def ->
                val shortage = player.coins < def.price
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(def.emoji, style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("${def.name}（持有 ${player.items[def.id] ?: 0}）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(def.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (shortage) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    CoinIcon(12.dp)
                                    Text("金币不足，还差 ${def.price - player.coins}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { pending = PendingPurchase("item", def.id, def.name, def.price) },
                            enabled = !shortage,
                        ) {
                            CoinText(def.price, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // ---------- 主题（2 列网格，色卡预览） ----------
            Text("🎨 主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            AppThemes.all.chunked(2).forEach { rowThemes ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowThemes.forEach { theme ->
                        val owned = player.ownedThemes.contains(theme.id)
                        val active = player.activeTheme == theme.id
                        Card(
                            onClick = {
                                if (owned) vm.setTheme(theme.id)
                                else pending = PendingPurchase("theme", theme.id, theme.name, theme.price)
                            },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .background(theme.seed, CircleShape)
                                        .background(Color.White.copy(alpha = 0.25f), CircleShape),
                                )
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(theme.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    when {
                                        active -> Text("✅ 使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        owned -> Text("点击启用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        else -> CoinText(theme.price, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                    if (rowThemes.size == 1) Box(Modifier.weight(1f))
                }
            }

            // ---------- Q 版头像（kawaii_0 体验款免费，其余 120） ----------
            Text("👤 Q 版头像", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            Text(
                "8 款马卡龙 Q 版，购买后在「我的 → 换装」里随时切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KAWAII_NAMES.indices.chunked(4).forEach { rowIds ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowIds.forEach { v ->
                        val id = "kawaii_$v"
                        val owned = player.ownedAvatars.contains(id) || v == 0   // kawaii_0 体验款
                        val active = player.avatar == id
                        Card(
                            onClick = {
                                if (owned) vm.setAvatar(id)
                                else pending = PendingPurchase("avatar", id, KAWAII_NAMES[v], KAWAII_PRICE)
                            },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(
                                Modifier.padding(8.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                KawaiiAvatar(variant = v, size = 44.dp)
                                Text(KAWAII_NAMES[v], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                when {
                                    active -> Text("使用中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    owned -> Text("免费", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else -> CoinText(KAWAII_PRICE, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal)
                                }
                            }
                        }
                    }
                    repeat(4 - rowIds.size) { Box(Modifier.weight(1f)) }
                }
            }
        }

        pending?.let { p ->
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text("确认购买") },
                text = {
                    Column {
                        Text("「${p.label}」", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("价格：")
                            CoinText(p.price)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("余额：")
                            CoinText(
                                player.coins,
                                color = if (player.coins >= p.price) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                        }
                        if (player.coins < p.price) {
                            Text("金币不足，还差 ${p.price - player.coins}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirm(p); pending = null },
                        enabled = player.coins >= p.price,
                    ) { Text("确认购买") }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
            )
        }
    }
}

/** 扣款浮字：从金币余额处上浮渐隐 */
@Composable
private fun FloatingCoinDelta(delta: Int, onDone: () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        onDone()
    }
    val a = anim.value
    Row(
        Modifier
            .offset(y = (-36 * a).dp)
            .alpha((1f - a).coerceIn(0f, 1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        CoinIcon(13.dp)
        Text("$delta", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
    }
}
