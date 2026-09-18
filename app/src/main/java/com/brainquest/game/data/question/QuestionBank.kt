package com.brainquest.game.data.question

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 题库仓库：assets 内置题库 + 已下载热更包（filesDir/content/packs/<packId> 下的 json 文件）合并。
 * 每个文件是一个 SubjectBankFile JSON。
 */
class QuestionBank(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var curated: Map<String, List<Question>> = emptyMap()

    val packsDir: File get() = File(context.filesDir, "content/packs")

    /** 重新加载内置题库 + 全部已下载内容包（热更应用后调用） */
    fun reload() {
        val map = mutableMapOf<String, MutableList<Question>>()
        for (subject in Subjects.curated) {
            val file = subjectAssetFile(subject) ?: continue
            runCatching {
                val text = context.assets.open(file).bufferedReader().use { it.readText() }
                val parsed = json.decodeFromString<SubjectBankFile>(text)
                map.getOrPut(parsed.subject) { mutableListOf() }.addAll(parsed.questions)
            }
        }
        // 热更新下载的内容包
        val packs = packsDir
        if (packs.isDirectory) {
            packs.listFiles()?.filter { it.isDirectory }?.forEach { pack ->
                pack.listFiles()?.filter { it.extension == "json" }?.forEach { f ->
                    runCatching {
                        val parsed = json.decodeFromString<SubjectBankFile>(f.readText())
                        map.getOrPut(parsed.subject) { mutableListOf() }.addAll(parsed.questions)
                    }
                }
            }
        }
        // 去重（按 id，热更包优先：后加入的先放前面再按 id 去重时保留最新）
        curated = map.mapValues { (_, list) ->
            list.reversed().distinctBy { it.id }.reversed()
        }
    }

    private fun subjectAssetFile(subject: String): String? = when (subject) {
        Subjects.ENGLISH -> "questions/english.json"
        Subjects.SCIENCE -> "questions/science.json"
        Subjects.CODING -> "questions/coding.json"
        Subjects.ADV_MATH -> "questions/advmath.json"
        Subjects.LIN_ALG -> "questions/linalg.json"
        Subjects.PROBABILITY -> "questions/probability.json"
        Subjects.RF_CIRCUITS -> "questions/rf_circuits.json"
        Subjects.COMMUNICATION -> "questions/communication.json"
        else -> null
    }

    fun curatedFor(subject: String): List<Question> = curated[subject] ?: emptyList()

    fun countFor(subject: String): Int {
        if (subject == Subjects.MATH || subject == Subjects.LOGIC) return Int.MAX_VALUE
        return curatedFor(subject).size
    }

    /** 取一道题：数学生成，其余从题库随机（按难度，找不到就放宽到最近难度）。题库题出题时随机打乱选项防背位置 */
    fun pick(subject: String, difficulty: Int, rng: kotlin.random.Random = kotlin.random.Random.Default): Question? {
        if (subject == Subjects.MATH || subject == Subjects.LOGIC) {
            return MathGenerator.generate(subject, difficulty, rng)
        }
        val pool = curatedFor(subject)
        if (pool.isEmpty()) return null
        val exact = pool.filter { it.difficulty == difficulty }
        val base = if (exact.isNotEmpty()) exact.random(rng)
        else pool.minByOrNull { kotlin.math.abs(it.difficulty - difficulty) * 100 + rng.nextInt(100) }
        return base?.let(::shuffleOptions)
    }

    /**
     * 取一道"未用过"的题：题库题按 id 排除；生成题按题干文本去重重试。
     * 池子耗尽返回 null（调用方回退普通抽题）。
     */
    fun pickExcluding(
        subject: String,
        difficulty: Int,
        excludeIds: Set<String>,
        excludeTexts: Set<String>,
        rng: kotlin.random.Random = kotlin.random.Random.Default,
    ): Question? {
        if (subject == Subjects.MATH || subject == Subjects.LOGIC) {
            repeat(8) {
                val q = MathGenerator.generate(subject, difficulty, rng)
                if (q.question !in excludeTexts) return q
            }
            return null
        }
        val pool = curatedFor(subject).filter { it.id !in excludeIds }
        if (pool.isEmpty()) return null
        val exact = pool.filter { it.difficulty == difficulty }
        val base = if (exact.isNotEmpty()) exact.random(rng)
        else pool.minByOrNull { kotlin.math.abs(it.difficulty - difficulty) * 100 + rng.nextInt(100) }
        return base?.let(::shuffleOptions)
    }

    /** 打乱选项顺序并重映射答案下标（判断题/选项不足时跳过） */
    private fun shuffleOptions(q: Question, rng: kotlin.random.Random = kotlin.random.Random.Default): Question {
        if (q.type != "single" || q.options.size < 2) return q
        val correct = q.options[q.answer]
        val shuffled = q.options.shuffled(rng)
        return q.copy(options = shuffled, answer = shuffled.indexOf(correct))
    }

    fun pickMany(subject: String, difficulty: Int, count: Int, exclude: Set<String> = emptySet()): List<Question> {
        if (subject == Subjects.MATH || subject == Subjects.LOGIC) {
            return List(count) { MathGenerator.generate(subject, difficulty) }
        }
        var pool = curatedFor(subject).filter { it.id !in exclude }
        if (pool.size < count) pool = curatedFor(subject)
        if (pool.isEmpty()) return emptyList()
        return pool.shuffled().take(count).map(::shuffleOptions)
    }

    /**
     * 每日挑战组卷：
     * - 入门模式（hardMode=false）：基础科目（数学口算/逻辑/英语/科学/编程），难度 2，简单入门
     * - 考研模式（hardMode=true） ：约 6 成历年真题 + 4 成大学科目高难题（高数/线代/概率/高频/通信）
     */
    fun pickDaily(count: Int, hardMode: Boolean, rng: kotlin.random.Random = kotlin.random.Random.Default): List<Question> {
        if (!hardMode) {
            val subjects = listOf(Subjects.MATH, Subjects.LOGIC, Subjects.ENGLISH, Subjects.SCIENCE, Subjects.CODING)
            val result = mutableListOf<Question>()
            val shuffled = subjects.shuffled(rng)
            var i = 0
            while (result.size < count && i < count * 4) {
                val subject = shuffled[result.size % shuffled.size]
                val q = pick(subject, 2, rng)
                if (q != null && q.type != "fill" && result.none { it.id == q.id && it.question == q.question }) result.add(q)
                i++
            }
            return result
        }
        // 考研模式：真题池优先（约六成），不足部分用大学科目难度 4 补足
        val uniSubjects = listOf(
            Subjects.ADV_MATH, Subjects.LIN_ALG, Subjects.PROBABILITY,
            Subjects.RF_CIRCUITS, Subjects.COMMUNICATION,
        )
        val zhentiPool = Subjects.all.flatMap { curatedFor(it) }
            .filter { "真题" in it.tags }
            .distinctBy { it.id }
            .shuffled(rng)
        val result = mutableListOf<Question>()
        zhentiPool.take(minOf(count * 6 / 10, zhentiPool.size)).forEach { result.add(it) }
        var i = 0
        while (result.size < count && i < count * 4) {
            val subject = uniSubjects[result.size % uniSubjects.size]
            val q = pick(subject, 4, rng)
            if (q != null && q.type != "fill" && result.none { it.id == q.id && it.question == q.question }) result.add(q)
            i++
        }
        return result.shuffled(rng)
    }
}
