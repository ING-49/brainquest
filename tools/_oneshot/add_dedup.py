"""题目去重：QuestionBank.pickExcluding + BattleState 已用跟踪与错题低概率重出"""

# ---------- 1. QuestionBank.pickExcluding ----------
src = open('app/src/main/java/com/brainquest/game/data/question/QuestionBank.kt', encoding='utf-8').read()
old = '''    /** 打乱选项顺序并重映射答案下标（判断题/选项不足时跳过） */'''
new = '''    /**
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

    /** 打乱选项顺序并重映射答案下标（判断题/选项不足时跳过） */'''
assert old in src
src = src.replace(old, new, 1)
open('app/src/main/java/com/brainquest/game/data/question/QuestionBank.kt', 'w', encoding='utf-8').write(src)
print('1 pickExcluding OK')

# ---------- 2. BattleState 去重 + 错题重出 ----------
src = open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleState.kt', encoding='utf-8').read()
old2 = '''    /** 进入下一题 */
    fun nextQuestion() {
        questionIndex++
        eliminated.clear()
        question = bank.pick(subject, questionDifficulty)
    }'''
new2 = '''    // 已出题跟踪（去重）与错题重出队列
    private val usedIds = mutableSetOf<String>()
    private val usedTexts = mutableSetOf<String>()
    private val reAskedIds = mutableSetOf<String>()   // 重出过的题不再重出
    private var reAskPending: Question? = null        // 答错的题，低概率再出一次

    /** 进入下一题 */
    fun nextQuestion() {
        questionIndex++
        eliminated.clear()
        // 小概率插入刚才答错的题（最多重出一次）
        val pending = reAskPending
        if (pending != null && rng.nextInt(100) < 20) {
            reAskPending = null
            question = pending
            return
        }
        // 正常出题：排除已用
        var q = bank.pickExcluding(subject, questionDifficulty, usedIds, usedTexts, rng)
        if (q == null) q = bank.pick(subject, questionDifficulty)  // 池子耗尽才允许重复
        question = q
        q?.let { usedIds.add(it.id); usedTexts.add(it.question) }
    }'''
assert old2 in src
src = src.replace(old2, new2, 1)

old3 = '''        } else {
            combo = 0
            mistakes++
            val atk = enemyAttack()
            playerHp = (playerHp - atk).coerceAtLeast(0)
            -atk
        }
    }'''
new3 = '''        } else {
            combo = 0
            mistakes++
            val atk = enemyAttack()
            playerHp = (playerHp - atk).coerceAtLeast(0)
            // 答错的题 25% 概率安排重出一次（已重出过的不重复安排）
            if (reAskPending == null && q.id !in reAskedIds && rng.nextInt(100) < 25) {
                reAskPending = q
                reAskedIds.add(q.id)
            }
            -atk
        }
    }'''
assert old3 in src
src = src.replace(old3, new3, 1)
open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleState.kt', 'w', encoding='utf-8').write(src)
print('2 battle dedup OK')

# ---------- 3. init 时把第一题记入已用 ----------
src = open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleState.kt', encoding='utf-8').read()
old4 = '''        enemyHp = enemy.maxHp
        nextQuestion()
    }'''
new4 = '''        enemyHp = enemy.maxHp
        nextQuestion()
        question?.let { usedIds.add(it.id); usedTexts.add(it.question) }
    }'''
assert old4 in src
src = src.replace(old4, new4, 1)
open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleState.kt', 'w', encoding='utf-8').write(src)
print('3 init tracking OK')
