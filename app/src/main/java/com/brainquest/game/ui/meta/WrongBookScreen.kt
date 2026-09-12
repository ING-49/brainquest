package com.brainquest.game.ui.meta

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.QuizRunner

/** 错题本：查看错题 + 复习模式（复习答对自动标记已掌握） */
@Composable
fun WrongBookScreen(vm: AppViewModel, nav: NavHostController) {
    val player by vm.player.collectAsState()
    var reviewing by remember { mutableStateOf(false) }

    if (reviewing) {
        val questions = player.wrongBook.filter { !it.mastered }.map { it.question }.take(10)
        QuizRunner(
            questions = questions,
            title = "📖 错题复习",
            subtitle = "答对即标记掌握",
            soundOn = player.soundOn,
            hapticsOn = player.hapticsOn,
            onBack = { nav.popBackStack() },
            onAnswered = { q, chosen, correct ->
                vm.recordAnswer(q, chosen)
                if (correct) vm.markWrongMastered(q.id)
            },
            onFinish = { _, _ -> reviewing = false },
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(
            "📖 错题本",
            onBack = { nav.popBackStack() },
            subtitle = "共 ${player.wrongBook.size} 道 · 未掌握 ${player.wrongBook.count { !it.mastered }} 道",
        )

        val unmastered = player.wrongBook.count { !it.mastered }
        Button(
            onClick = { reviewing = true },
            enabled = unmastered > 0,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) { Text(if (unmastered > 0) "开始复习（$unmastered 道待掌握）" else "全部已掌握，太棒了！") }

        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(player.wrongBook.size) { i ->
                val entry = player.wrongBook[i]
                val q = entry.question
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (entry.mastered) MaterialTheme.colorScheme.surfaceContainerLowest
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${com.brainquest.game.data.question.Subjects.emoji(q.subject)} ${q.subject}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (entry.mastered) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("✅ 已掌握", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        Text(q.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            "你的答案：${if (entry.chosen < 0) "超时" else "${'A' + entry.chosen}. ${q.options.getOrNull(entry.chosen) ?: "-"}"}" +
                                " ｜ 正确：${'A' + q.answer}. ${q.options.getOrNull(q.answer) ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (q.explanation.isNotBlank()) {
                            Text("💡 ${q.explanation}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
