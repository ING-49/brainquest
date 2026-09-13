package com.brainquest.game.data.question

import kotlin.random.Random

/** 数学口算与逻辑推理程序化生成器：题目无限、难度 1~5 自适应 */
object MathGenerator {

    fun generate(subject: String, difficulty: Int, rng: Random = Random.Default): Question {
        return if (subject == Subjects.LOGIC) logic(difficulty, rng) else arithmetic(difficulty, rng)
    }

    // ---------- 口算 ----------

    private fun arithmetic(d: Int, rng: Random): Question {
        val (text, ans) = when (d.coerceIn(1, 5)) {
            1 -> level1(rng)
            2 -> level2(rng)
            3 -> level3(rng)
            4 -> level4(rng)
            else -> level5(rng)
        }
        val qText = if (text.endsWith("？") || text.endsWith("?")) text else "$text = ?"
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
    }

    private fun level1(rng: Random): Pair<String, Int> = when (rng.nextInt(3)) {
        0 -> { val a = rng.nextInt(2, 20); val b = rng.nextInt(1, 20); "$a + $b" to (a + b) }
        1 -> { val a = rng.nextInt(10, 30); val b = rng.nextInt(1, a); "$a − $b" to (a - b) }
        else -> { val a = rng.nextInt(2, 10); val b = rng.nextInt(2, 10); "$a × $b" to (a * b) }
    }

    private fun level2(rng: Random): Pair<String, Int> = when (rng.nextInt(3)) {
        0 -> { val a = rng.nextInt(2, 12); val b = rng.nextInt(2, 10); val c = rng.nextInt(1, 30); "$a × $b + $c" to (a * b + c) }
        1 -> { val b = rng.nextInt(2, 10); val q = rng.nextInt(2, 12); val a = b * q; "$a ÷ $b" to q }
        else -> { val a = rng.nextInt(20, 80); val b = rng.nextInt(10, 50); val c = rng.nextInt(10, 40); "$a + $b − $c" to (a + b - c) }
    }

    private fun level3(rng: Random): Pair<String, Int> = when (rng.nextInt(3)) {
        0 -> { val a = rng.nextInt(2, 12); val b = rng.nextInt(1, 12); val c = rng.nextInt(2, 9); "($a + $b) × $c" to ((a + b) * c) }
        1 -> { val a = rng.nextInt(3, 12); val b = rng.nextInt(3, 10); val c = rng.nextInt(2, 9); val d = rng.nextInt(2, 9); "$a × $b − $c × $d" to (a * b - c * d) }
        else -> { val x = rng.nextInt(3, 20); val a = rng.nextInt(2, 30); val b = rng.nextInt(20, 80); "x + $a = $b，x = ?" to (b - a) }
    }

    private fun level4(rng: Random): Pair<String, Int> = when (rng.nextInt(3)) {
        0 -> { val a = rng.nextInt(6, 16); val b = rng.nextInt(2, 15); "($a − $b) × ${b + 1}" to ((a - b) * (b + 1)) }
        1 -> { val x = rng.nextInt(3, 15); val a = rng.nextInt(3, 15); val b = rng.nextInt(2, 40); "x × $a + $b = ${x * a + b}，x = ?" to x }
        else -> { val a = rng.nextInt(4, 14); "$a²" to (a * a) }
    }

    private fun level5(rng: Random): Pair<String, Int> = when (rng.nextInt(4)) {
        0 -> { val a = rng.nextInt(5, 16); val b = rng.nextInt(3, 12); "$a² − $b²" to (a * a - b * b) }
        1 -> { val n = rng.nextInt(1, 9); "2^$n" to (1 shl n) }
        2 -> { val s = rng.nextInt(4, 16); "√${s * s}" to s }
        else -> { // 百分数
            val pct = listOf(10, 20, 25, 50, 75).random(rng); val base = rng.nextInt(2, 20) * 20; "$pct% of $base" to (base * pct / 100)
        }
    }

    // ---------- 找规律 ----------

    private fun logic(d: Int, rng: Random): Question {
        val (seq, ans, explain) = when (d.coerceIn(1, 5)) {
            1 -> { // 等差
                val a = rng.nextInt(1, 10); val step = rng.nextInt(2, 6)
                val s = List(4) { a + it * step }; Triple(s, a + 4 * step, "等差数列，公差 $step")
            }
            2 -> { // 等比
                val a = rng.nextInt(1, 4); val r = rng.nextInt(2, 4)
                val s = List(4) { a * (1 shl (it * r)) }; Triple(s, a * (1 shl (4 * r)), "等比数列，公比 ${1 shl r}")
            }
            3 -> { // 隔项等差
                val a = rng.nextInt(2, 15); val p = rng.nextInt(2, 5); val q = rng.nextInt(1, 4)
                val s = listOf(a, a + p, a + p + q, a + 2 * p + q); Triple(s, a + 2 * p + 2 * q, "隔项看：奇数位差 $p，偶数位差 $q")
            }
            4 -> { // 平方/立方数列
                val base = rng.nextInt(1, 6)
                val s = List(4) { (it + base) * (it + base) }; Triple(s, (base + 4) * (base + 4), "完全平方数列：${base}² 起步")
            }
            else -> { // 二阶等差（差为等差）
                val a = rng.nextInt(1, 8); val d1 = rng.nextInt(2, 5)
                val s = mutableListOf(a); var cur = a; var gap = d1
                repeat(3) { cur += gap; gap += 1; s.add(cur) }
                Triple(s, cur + gap, "相邻差是 $d1, ${d1 + 1}, ${d1 + 2}, ${d1 + 3}…（二阶等差）")
            }
        }
        val seqText = seq.joinToString("，")
        val full = ans
        return Question(
            id = "gen_l_${System.nanoTime()}_${rng.nextInt(9999)}",
            subject = Subjects.LOGIC,
            difficulty = d,
            question = "找规律：$seqText，下一项是？",
            options = buildOptions(full, rng, spread = (full * 0.2f).toInt().coerceAtLeast(2)),
            answer = 0,
            explanation = "规律：$explain，下一项为 $full",
            tags = listOf("找规律"),
        ).withShuffledAnswer(full, rng)
    }

    // ---------- 选项工具 ----------

    /** 生成 4 个选项并把正确答案下标打乱 */
    private fun buildOptions(ans: Int, rng: Random, spread: Int = 3): List<String> {
        val pool = mutableSetOf<Int>()
        var delta = 1
        while (pool.size < 6 && delta < 50) {
            pool.add(ans + delta); pool.add(ans - delta)
            delta += if (rng.nextBoolean()) spread else 1
        }
        if (ans > 10) { pool.add(ans * 2); pool.add(ans / 2) }
        val wrongs = pool.filter { it != ans && it >= 0 }.shuffled(rng).take(3).toMutableList()
        var pad = 1
        while (wrongs.size < 3) { wrongs.add(ans + 100 * pad); pad++ }
        val all = (wrongs + ans).map { it.toString() }
        val shuffled = all.shuffled(rng)
        return shuffled
    }

    private fun Question.withShuffledAnswer(ans: Int, rng: Random): Question {
        val idx = options.indexOf(ans.toString())
        return copy(answer = idx)
    }
}
