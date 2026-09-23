"""v1.6.0 改造一：艾宾浩斯错题复习（数据模型 + ViewModel + UI）"""

# ---------- 1. WrongEntry 增加复习字段 ----------
src = open('app/src/main/java/com/brainquest/game/data/PlayerState.kt', encoding='utf-8').read()
old = '''@Serializable
data class WrongEntry(
    val question: com.brainquest.game.data.question.Question,
    val chosen: Int, // -1 表示超时未答
    val time: Long,
    val mastered: Boolean = false,
)'''
new = '''@Serializable
data class WrongEntry(
    val question: com.brainquest.game.data.question.Question,
    val chosen: Int, // -1 表示超时未答
    val time: Long,
    val mastered: Boolean = false,
    val stage: Int = 0,        // 艾宾浩斯复习阶段 0~5（0=刚答错，5=已掌握）
    val nextReviewAt: Long = 0, // 下次到期复习时间戳（0=立即到期）
)'''
assert old in src
src = src.replace(old, new, 1)

# AppViewModel 位置插入艾宾浩斯计算（放 PlayerState.kt 末尾作为扩展）
old = '''fun xpForLevel(lvl: Int): Int = 50 * (lvl - 1) * lvl'''
new = '''/** 艾宾浩斯间隔（毫秒）：阶段 1→+1天 2→+2天 3→+4天 4→+7天 5→+15天 */
fun reviewIntervalMs(stage: Int): Long = when (stage) {
    1 -> 1L * 24 * 3600 * 1000
    2 -> 2L * 24 * 3600 * 1000
    3 -> 4L * 24 * 3600 * 1000
    4 -> 7L * 24 * 3600 * 1000
    else -> 15L * 24 * 3600 * 1000
}

fun xpForLevel(lvl: Int): Int = 50 * (lvl - 1) * lvl'''
assert old in src
src = src.replace(old, new, 1)
open('app/src/main/java/com/brainquest/game/data/PlayerState.kt', 'w', encoding='utf-8').write(src)
print('1 WrongEntry OK')

# ---------- 2. AppViewModel：到期查询 + 复习结算 ----------
src = open('app/src/main/java/com/brainquest/game/AppViewModel.kt', encoding='utf-8').read()
old = '''    /** 错题本标记已掌握（复习时答对调用） */
    fun markWrongMastered(questionId: String) = commit { state ->
        state.copy(wrongBook = state.wrongBook.map {
            if (it.question.id == questionId) it.copy(mastered = true) else it
        })
    }'''
new = '''    /** 错题本标记已掌握（复习时答对调用） */
    fun markWrongMastered(questionId: String) = commit { state ->
        state.copy(wrongBook = state.wrongBook.map {
            if (it.question.id == questionId) it.copy(mastered = true) else it
        })
    }

    /** 今日到期错题（未掌握 且 到期时间已到；stage0 立即到期） */
    fun dueReviewQuestions(): List<com.brainquest.game.data.question.Question> {
        val now = System.currentTimeMillis()
        return _player.value.wrongBook
            .filter { !it.mastered && (it.nextReviewAt <= now || it.stage == 0) }
            .map { it.question }
    }

    /** 复习结算：答对推进阶段（下一次到期），答错回退重练 */
    fun reviewAnswered(questionId: String, correct: Boolean) {
        val now = System.currentTimeMillis()
        commit { state ->
            state.copy(wrongBook = state.wrongBook.map { entry ->
                if (entry.question.id != questionId) entry else when {
                    correct -> {
                        val ns = (entry.stage + 1).coerceAtMost(5)
                        entry.copy(stage = ns, nextReviewAt = now + com.brainquest.game.data.reviewIntervalMs(ns))
                    }
                    else -> entry.copy(stage = 0, nextReviewAt = now + 60_000L) // 1 分钟后再练
                }
            })
        }
        if (correct) {
            _events.tryEmit("📅 复习通过，进入下一轮间隔")
        } else {
            _events.tryEmit("🔁 答错了，稍后再练一次")
        }
    }'''
assert old in src
src = src.replace(old, new, 1)
open('app/src/main/java/com/brainquest/game/AppViewModel.kt', 'w', encoding='utf-8').write(src)
print('2 viewmodel OK')

# ---------- 3. 错题本页：今日待复习区块 ----------
src = open('app/src/main/java/com/brainquest/game/ui/meta/WrongBookScreen.kt', encoding='utf-8').read()
old = '''        val unmastered = player.wrongBook.count { !it.mastered }
        Button(
            onClick = { reviewing = true },
            enabled = unmastered > 0,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) { Text(if (unmastered > 0) "开始复习（$unmastered 道待掌握）" else "全部已掌握，太棒了！") }'''
new = '''        val unmastered = player.wrongBook.count { !it.mastered }
        val dueQuestions = remember(player.wrongBook) { vm.dueReviewQuestions() }

        // 📅 艾宾浩斯今日待复习
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("📅 今日待复习（艾宾浩斯）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "按遗忘曲线安排：答错当天 → 1天后 → 2天 → 4天 → 7天 → 15天，复习通过进入下一轮",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Button(
                    onClick = { reviewing = true; reviewDue = true },
                    enabled = dueQuestions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text(if (dueQuestions.isNotEmpty()) "开始今日复习（${dueQuestions.size} 题到期）" else "今日无到期复习 ✓") }
            }
        }

        Button(
            onClick = { reviewing = true },
            enabled = unmastered > 0,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) { Text(if (unmastered > 0) "全部错题过一遍（$unmastered 道）" else "全部已掌握，太棒了！") }'''
assert old in src
src = src.replace(old, new, 1)

# 复习模式按来源切换：reviewDue=true 用到期题，否则全部未掌握
old = '''    if (reviewing) {
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
    }'''
new = '''    var reviewDue by remember { mutableStateOf(false) }
    if (reviewing) {
        val questions = if (reviewDue) vm.dueReviewQuestions().take(15)
        else player.wrongBook.filter { !it.mastered }.map { it.question }.take(10)
        QuizRunner(
            questions = questions,
            title = if (reviewDue) "📅 今日复习" else "📖 错题复习",
            subtitle = if (reviewDue) "答对推进下一轮间隔，答错重来" else "答对即标记掌握",
            soundOn = player.soundOn,
            hapticsOn = player.hapticsOn,
            onBack = { nav.popBackStack() },
            onAnswered = { q, chosen, correct ->
                vm.recordAnswer(q, chosen)
                if (correct) vm.markWrongMastered(q.id)
                vm.reviewAnswered(q.id, correct)
            },
            onFinish = { _, _ -> reviewing = false; reviewDue = false },
        )
        return
    }'''
assert old in src
src = src.replace(old, new, 1)

# reviewing 声明前移（var reviewing 在函数开头）
old = '''    var reviewing by remember { mutableStateOf(false) }'''
new = '''    var reviewing by remember { mutableStateOf(false) }
    var reviewDue by remember { mutableStateOf(false) }'''
if src.count(old) == 1:
    src = src.replace(old, new, 1)
# 若 reviewDue 已在上面用过（前移过一次），去重
if src.count('var reviewDue by remember') > 1:
    src = src.replace('    var reviewDue by remember { mutableStateOf(false) }\n    if (reviewing) {', '    if (reviewing) {', 1)
open('app/src/main/java/com/brainquest/game/ui/meta/WrongBookScreen.kt', 'w', encoding='utf-8').write(src)
print('3 wrongbook UI OK')
