"""修复 PkBattleScreen：事件流解耦 + 函数声明顺序 + height import"""
src = open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', encoding='utf-8').read()

# 1. client 改为事件流
old = '''    val client = remember { PkClient { event ->'''
new = '''    val pkEvents = remember { kotlinx.coroutines.flow.MutableSharedFlow<PkEvent>(extraBufferCapacity = 32) }
    val client = remember { PkClient { pkEvents.tryEmit(it) } }

    LaunchedEffect(Unit) {
        pkEvents.collect { event ->'''
assert old in src
src = src.replace(old, new, 1)

# 2. 事件处理体需要缩进调整——把 `when (event) {` 后的原有处理保持，但要把原闭合补齐
# 原：val client = remember { PkClient { event -> ... } } 的闭合是 "    } }"
# 现在 collect 块的闭合需要 "} }"→"}\n    }" 处理：找到原事件处理结束的位置
old2 = '''            is PkEvent.Error -> status = event.msg
        }
    } }'''
new2 = '''            is PkEvent.Error -> status = event.msg
        }
        }
    }'''
assert old2 in src
src = src.replace(old2, new2, 1)

# 3. 把 submit/beginQuestion/nextOrFinish 移到 LaunchedEffect 计时器之前
old3 = '''    // 计时器
    LaunchedEffect(phase, qIndex) {'''
new3 = '''    fun beginQuestion() {
        qStartAt = System.currentTimeMillis()
    }

    fun submit(idx: Int) {
        val q = questions.getOrNull(qIndex) ?: return
        val correct = idx == q.answer
        val spent = System.currentTimeMillis() - qStartAt
        totalTime += spent
        if (correct) myCorrect++
        answered = true
        chosen = idx
        Sfx.play(context, player.soundOn, if (correct) SfxType.CORRECT else SfxType.WRONG)
        vm.recordAnswer(q, idx)
        client.sendAnswer(qIndex, correct, spent)
    }

    fun nextOrFinish() {
        if (qIndex + 1 >= questions.size) {
            client.sendFinish(myCorrect, totalTime)
            status = "已完成，等待对手…"
        } else {
            qIndex++
        }
    }

    // 计时器
    LaunchedEffect(phase, qIndex) {'''
assert old3 in src
src = src.replace(old3, new3, 1)

# 4. 删除后面重复定义的两个函数
old4 = '''    fun beginQuestion() {
        qStartAt = System.currentTimeMillis()
    }

    fun submit(idx: Int) {
        val q = questions.getOrNull(qIndex) ?: return
        val correct = idx == q.answer
        val spent = System.currentTimeMillis() - qStartAt
        totalTime += spent
        if (correct) myCorrect++
        answered = true
        chosen = idx
        Sfx.play(context, player.soundOn, if (correct) SfxType.CORRECT else SfxType.WRONG)
        vm.recordAnswer(q, idx)
        client.sendAnswer(qIndex, correct, spent)
    }

    fun nextOrFinish() {
        if (qIndex + 1 >= questions.size) {
            client.sendFinish(myCorrect, totalTime)
            status = "已完成，等待对手…"
        } else {
            qIndex++
        }
    }

    // ---------- 界面 ----------'''
new4 = '''    // ---------- 界面 ----------'''
assert old4 in src
src = src.replace(old4, new4, 1)

# 5. height import
old5 = 'import androidx.compose.foundation.layout.fillMaxWidth'
new5 = 'import androidx.compose.foundation.layout.fillMaxWidth\nimport androidx.compose.foundation.layout.height'
assert src.count(old5) == 1
src = src.replace(old5, new5, 1)

open('app/src/main/java/com/brainquest/game/game/pk/PkBattleScreen.kt', 'w', encoding='utf-8').write(src)
print("restructured")
