"""BattleScreen 接入填空题 UI 与每题时限"""

src = open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleScreen.kt', encoding='utf-8').read()

# 1. 计时按题型
old = "        timeLeft = battle.timeLimitSec"
new = "        timeLeft = battle.timeLimitFor(battle.question)"
assert src.count(old) == 1
src = src.replace(old, new, 1)

# 2. 填空输入状态（加在 chosen 声明后）
old = "    var chosen by remember { mutableIntStateOf(-1) }"
new = "    var chosen by remember { mutableIntStateOf(-1) }\n    var fillInput by remember { mutableStateOf(\"\") }"
assert src.count(old) == 1
src = src.replace(old, new, 1)

# 3. 计时器重置时清空输入
old = "        answered = false\n        chosen = -1\n        feedback = null\n        timeLeft = battle.timeLimitFor(battle.question)"
new = "        answered = false\n        chosen = -1\n        feedback = null\n        fillInput = \"\"\n        timeLeft = battle.timeLimitFor(battle.question)"
assert src.count(old) == 1
src = src.replace(old, new, 1)

# 4. 题目卡：fill 分支 + 原选项分支
old = '''                val q = battle.question
                q?.options?.forEachIndexed { i, opt ->'''
new = '''                val q = battle.question
                if (q?.type == "fill") {
                    // 填空题：键入答案（时限已 +10s）
                    OutlinedTextField(
                        value = fillInput,
                        onValueChange = { fillInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !answered,
                        label = { Text("输入答案后提交") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = { Text("= ?") },
                    )
                    Button(
                        onClick = { doFill(fillInput) },
                        enabled = !answered && fillInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text("提交答案") }
                } else q?.options?.forEachIndexed { i, opt ->'''
assert src.count(old) == 1
src = src.replace(old, new, 1)

# 5. doFill 函数（放在 doAnswer 后）
old = '''    fun doAnswer(index: Int) {'''
new = '''    fun doFill(input: String) {
        if (answered || resultState != 0 || input.isBlank()) return
        answered = true
        chosen = 0
        scope.launch {
            handleAnswer(vm, battle, context, sfxOn, hapticOn, index = -100, fillInput = input, onDone = { dmg, fb ->
                lastDamage = dmg; feedback = fb
                if (dmg > 0) shakeEnemy()
            }, onEnd = { rs -> resultState = rs })
        }
    }

    fun doAnswer(index: Int) {'''
assert src.count(old) == 1
src = src.replace(old, new, 1)

# 6. handleAnswer 支持 fill
old = '''private suspend fun handleAnswer(
    vm: AppViewModel,
    battle: BattleState,
    context: android.content.Context,
    sfxOn: Boolean,
    hapticOn: Boolean,
    index: Int,
    onDone: suspend (Int, String) -> Unit,
    onEnd: (Int) -> Unit,
) {
    val q = battle.question ?: return
    val dmg = battle.answer(index)
    vm.recordAnswer(q, index)'''
new = '''private suspend fun handleAnswer(
    vm: AppViewModel,
    battle: BattleState,
    context: android.content.Context,
    sfxOn: Boolean,
    hapticOn: Boolean,
    index: Int,
    onDone: suspend (Int, String) -> Unit,
    onEnd: (Int) -> Unit,
    fillInput: String? = null,
) {
    val q = battle.question ?: return
    val dmg = if (fillInput != null) battle.answerFill(fillInput) else battle.answer(index)
    vm.recordAnswer(q, if (fillInput != null) (if (dmg > 0) q.answer else -1) else index)'''
assert src.count(old) == 1
src = src.replace(old, new, 1)

# 7. imports：键盘类型与文本框
old = "import androidx.compose.foundation.background"
new = "import androidx.compose.foundation.background\nimport androidx.compose.foundation.text.KeyboardOptions"
assert src.count(old) == 1
src = src.replace(old, new, 1)
old = "import androidx.compose.material3.Text\n"
new = "import androidx.compose.material3.Text\nimport androidx.compose.material3.OutlinedTextField\n"
assert src.count(old) == 1
src = src.replace(old, new, 1)
old = "import androidx.compose.ui.platform.LocalContext"
new = "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.text.input.KeyboardType"
assert src.count(old) == 1
src = src.replace(old, new, 1)

open('app/src/main/java/com/brainquest/game/game/quizbattle/BattleScreen.kt', 'w', encoding='utf-8').write(src)
print("BattleScreen fill UI OK")
