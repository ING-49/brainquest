package com.brainquest.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val GREEN = Color(0xFF2E7D32)
private val GREEN_BG = Color(0xFFC8E6C9)
private val RED = Color(0xFFC62828)
private val RED_BG = Color(0xFFFFCDD2)

/**
 * 统一选项答题视图（闯关 / 每日挑战 / 错题复习 / 联机对战通用）：
 * 答完正确项标绿框 ✔，选错的项额外标红框 ✘，答案解析显示在最后一个选项下方。
 *
 * @param chosen    已选下标，-1 表示未作答
 * @param revealed  是否已揭晓答案（超时未选也为 true，此时只标绿正确项）
 * @param eliminated 被道具排除的选项（揭晓前隐藏）
 */
@Composable
fun QuizOptionList(
    options: List<String>,
    answer: Int,
    chosen: Int,
    onChoose: (Int) -> Unit,
    revealed: Boolean = chosen >= 0,
    enabled: Boolean = true,
    eliminated: Set<Int> = emptySet(),
    explanation: String = "",
) {
    options.forEachIndexed { i, opt ->
        if (i !in eliminated || revealed) {
            val isCorrect = revealed && i == answer
            val isWrongPick = revealed && i == chosen && chosen != answer
            val accent = when {
                isCorrect -> GREEN
                isWrongPick -> RED
                else -> Color.Unspecified
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isCorrect -> GREEN_BG
                        isWrongPick -> RED_BG
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
                border = when {
                    isCorrect -> BorderStroke(2.dp, GREEN)
                    isWrongPick -> BorderStroke(2.dp, RED)
                    else -> null
                },
                enabled = enabled && !revealed,
                onClick = { onChoose(i) },
            ) {
                Row(Modifier.padding(12.dp)) {
                    Text(
                        "${'A' + i}. ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    Text(
                        opt,
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCorrect) Text("✔", color = GREEN, fontWeight = FontWeight.Bold)
                    else if (isWrongPick) Text("✘", color = RED, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (revealed && explanation.isNotBlank()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(Color(0xFFFFF8E1), RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Text("💡 $explanation", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
