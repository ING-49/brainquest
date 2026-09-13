package com.brainquest.game.ui.meta

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.QuizRunner
import com.brainquest.game.ui.starText

/** 每日挑战：每天 10 道混合题，答完结算连续天数。题目范围随设置中的「大学考研模式」变化 */
@Composable
fun DailyScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    var started by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val modeText = if (player.hardMode) "🎓 考研模式 · 大学高难" else "🌱 入门模式 · 简单基础"

    if (!started && result == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeader(
                "🎯 每日挑战",
                onBack = { nav.popBackStack() },
                subtitle = "$modeText · 连续 ${player.dailyStreak} 天 · 今日${if (vm.isDailyDone()) "已完成" else "待挑战"}",
            )
            androidx.compose.material3.Card(
                modifier = Modifier.padding(top = 16.dp),
                onClick = { started = true },
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("开始今天的 10 道混合题", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    Text(
                        if (player.hardMode) "🎓 考研模式：约六成历年真题 + 大学科目高难题（高数/线代/概率/高频/通信）"
                        else "🌱 入门模式：数学口算 / 逻辑 / 英语 / 科学 / 编程（难度 2）",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "可在「设置 → 大学考研模式」切换难度范围",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (player.hardMode) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { nav.navigate(com.brainquest.game.ui.Routes.PAPERS) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("📄 真题试卷（历年真题精选，含解析）") }
                    }
                }
            }
        }
        return
    }

    if (result != null) {
        val (correct, total) = result!!
        AlertDialog(
            onDismissRequest = {},
            title = { Text("🎯 挑战完成！") },
            text = { Text("答对 $correct / $total ${starText(if (correct >= 9) 3 else if (correct >= 6) 2 else 1)}") },
            confirmButton = {
                Button(onClick = { nav.popBackStack() }) { Text("领奖返回") }
            },
        )
        return
    }

    val questions = remember(player.hardMode) { vm.bank.pickDaily(10, player.hardMode) }
    QuizRunner(
        questions = questions,
        title = "🎯 每日挑战",
        subtitle = modeText,
        soundOn = player.soundOn,
        hapticsOn = player.hapticsOn,
        onBack = { nav.popBackStack() },
        onAnswered = { q, chosen, _ -> vm.recordAnswer(q, chosen) },
        onFinish = { correct, total ->
            vm.finishDaily(correct, total)
            result = correct to total
        },
    )
}
