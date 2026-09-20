package com.brainquest.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brainquest.game.data.question.Question
import com.brainquest.game.data.question.Subjects
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType

/**
 * 通用答题流：逐题作答、即时判分并展示解析。
 * onAnswered 每题回调一次；全部答完回调 onFinish(答对数, 总数)。
 */
@Composable
fun QuizRunner(
    questions: List<Question>,
    title: String,
    subtitle: String,
    soundOn: Boolean,
    hapticsOn: Boolean,
    onBack: () -> Unit,
    onAnswered: (Question, Int, Boolean) -> Unit,
    onFinish: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }

    val q = questions.getOrNull(index)
    if (q == null) {
        onFinish(correctCount, questions.size)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(title, onBack = onBack, subtitle = subtitle)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1} / ${questions.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { (index + 1).toFloat() / questions.size },
                modifier = Modifier.weight(1f).padding(start = 10.dp).fillMaxWidth().padding(end = 8.dp).weight(1f, fill = false),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${Subjects.emoji(q.subject)} ${q.subject} · 难度${q.difficulty}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    q.question,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                QuizOptionList(
                    options = q.options,
                    answer = q.answer,
                    chosen = chosen ?: -1,
                    onChoose = { i ->
                        chosen = i
                        val correct = i == q.answer
                        if (correct) correctCount++
                        Sfx.play(context, soundOn, if (correct) SfxType.CORRECT else SfxType.WRONG)
                        onAnswered(q, i, correct)
                    },
                    explanation = q.explanation,
                )

                if (chosen != null) {
                    Button(
                        onClick = {
                            index++
                            chosen = null
                            if (index >= questions.size) onFinish(correctCount, questions.size)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) {
                        Text(if (index + 1 >= questions.size) "完成" else "下一题 →")
                    }
                }
            }
        }
    }
}
