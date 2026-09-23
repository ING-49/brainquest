"""实现填空题：生成器 fill 变体 + BattleState answerFill/时限 + 题库过滤"""

# ---------- 1. MathGenerator ----------
src = open('app/src/main/java/com/brainquest/game/data/question/MathGenerator.kt', encoding='utf-8').read()
old1 = '''        return Question(
            id = "gen_m_${System.nanoTime()}_${rng.nextInt(9999)}",
            subject = Subjects.MATH,
            difficulty = d,
            question = if (text.endsWith("？") || text.endsWith("?")) text else "$text = ?",
            options = buildOptions(ans, rng),
            answer = 0,
            explanation = "$text = $ans",
            tags = listOf("口算"),
        ).withShuffledAnswer(ans, rng)
    }'''
new1 = '''        val qText = if (text.endsWith("？") || text.endsWith("?")) text else "$text = ?"
        if (rng.nextInt(3) == 0 && d <= 3) {  // 低难度口算 1/3 概率出填空题
            return Question(
                id = "gen_mf_${System.nanoTime()}_${rng.nextInt(9999)}",
                subject = Subjects.MATH,
                difficulty = d,
                type = "fill",
                question = qText,
                options = listOf(ans.toString()),
                answer = 0,
                explanation = "$text = $ans",
                tags = listOf("口算", "填空"),
            )
        }
        return Question(
            id = "gen_m_${System.nanoTime()}_${rng.nextInt(9999)}",
            subject = Subjects.MATH,
            difficulty = d,
            question = qText,
            options = buildOptions(ans, rng),
            answer = 0,
            explanation = "$text = $ans",
            tags = listOf("口算"),
        ).withShuffledAnswer(ans, rng)
    }'''
assert old1 in src, "MathGenerator 结构不匹配"
src = src.replace(old1, new1, 1)
open('app/src/main/java/com/brainquest/game/data/question/MathGenerator.kt', 'w', encoding='utf-8').write(src)
print('1 generator fill OK')

# ---------- 2. BattleState ----------
src = open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleState.kt', encoding='utf-8').read()
old2 = '''    /** 超时按答错处理 */
    fun timeout(): Int = answer(-1)'''
new2 = '''    /** 超时按答错处理 */
    fun timeout(): Int = answer(-1)

    /** 填空题作答：文本归一化后与答案比较 */
    fun answerFill(input: String): Int {
        val q = question ?: return 0
        val expected = q.options.firstOrNull()?.trim() ?: return 0
        val ok = normalizeNum(input) == normalizeNum(expected)
        return answer(if (ok) q.answer else -1)
    }

    /** 每题时限：填空题多给 10 秒 */
    fun timeLimitFor(q: Question?): Int =
        if (q?.type == "fill") timeLimitSec + 10 else timeLimitSec

    private fun normalizeNum(s: String): String =
        s.trim().replace("，", "").replace(",", "").removeSuffix("。").let {
            it.toDoubleOrNull()?.let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString() } ?: it
        }'''
assert old2 in src, "BattleState timeout 不匹配"
src = src.replace(old2, new2, 1)
open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleState.kt', 'w', encoding='utf-8').write(src)
print('2 battle state OK')

# ---------- 3. QuestionBank 每日/组卷过滤填空 ----------
src = open('app/src/main/java/com/brainquest/game/data/question/QuestionBank.kt', encoding='utf-8').read()
c1 = 'result.none { it.id == q.id && it.question == q.question }) result.add(q)'
n1 = 'q.type != "fill" && result.none { it.id == q.id && it.question == q.question }) result.add(q)'
assert src.count(c1) == 2, f"预期 2 处，实际 {src.count(c1)}"
src = src.replace(c1, n1, 2)
open('app/src/main/java/com/brainquest/game/data/question/QuestionBank.kt', 'w', encoding='utf-8').write(src)
print('3 bank filters OK')
