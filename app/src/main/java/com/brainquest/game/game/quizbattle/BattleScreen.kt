package com.brainquest.game.game.quizbattle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.Routes
import com.brainquest.game.data.Items
import com.brainquest.game.data.question.Subjects
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.ui.starText
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.brainquest.game.ui.BqProgressBar
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition

@Composable
fun BattleScreen(vm: AppViewModel, nav: NavHostController, subject: String, level: Int) {
    val player by vm.player.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val battle = remember(subject, level, player.hardMode) { BattleState(subject, level, vm.bank, hardMode = player.hardMode) }

    var answered by remember { mutableStateOf(false) }
    var chosen by remember { mutableIntStateOf(-1) }
    var fillInput by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var lastDamage by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(battle.timeLimitSec) }
    var resultState by remember { mutableIntStateOf(0) } // 0战斗中 1胜 2负
    var revivedOnce by remember { mutableStateOf(false) }
    var shake by remember { mutableFloatStateOf(0f) }
    val enemyShake = remember { Animatable(0f) }
    val hpFlash = remember { Animatable(0f) }        // 敌方血条受击闪白
    var prevEnemyHp by remember { mutableIntStateOf(battle.enemyHp) }
    val comboScale = remember { Animatable(1f) }     // 连击文字弹跳
    val urgentTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "urgent")
    val urgentAlpha by urgentTransition.animateFloat(
        0.4f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(400), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "urgentAlpha",
    )

    val sfxOn = player.soundOn
    val hapticOn = player.hapticsOn

    fun onTimeout() {
        if (answered || resultState != 0) return
        answered = true // 同步置位，防重复触发
        scope.launch {
            handleAnswer(vm, battle, context, sfxOn, hapticOn, -1, onDone = { dmg, fb ->
                lastDamage = dmg; feedback = fb
            }, onEnd = { rs -> resultState = rs }, onNextQuestion = { answered = false; chosen = -1 })
        }
    }

    // 计时器：questionIndex 变化 = 新题，重置全部状态
    LaunchedEffect(battle.questionIndex) {
        answered = false
        chosen = -1
        feedback = null
        fillInput = ""
        timeLeft = battle.timeLimitFor(battle.question)
        while (timeLeft > 0 && !answered && resultState == 0) {
            delay(1000)
            timeLeft--
        }
        if (timeLeft <= 0 && !answered && resultState == 0) {
            onTimeout()
        }
    }

    suspend fun shakeEnemy() {
        enemyShake.snapTo(0f)
        enemyShake.animateTo(1f, tween(120))
        enemyShake.animateTo(0f, tween(200))
        if (battle.enemyHp < prevEnemyHp) {
            hpFlash.snapTo(1f)
            hpFlash.animateTo(0f, tween(320))
        }
        prevEnemyHp = battle.enemyHp
    }

    fun doFill(input: String) {
        if (answered || resultState != 0 || input.isBlank()) return
        answered = true
        chosen = 0
        scope.launch {
            handleAnswer(vm, battle, context, sfxOn, hapticOn, index = -100, fillInput = input, onDone = { dmg, fb ->
                lastDamage = dmg; feedback = fb
                if (dmg > 0) shakeEnemy()
            }, onEnd = { rs -> resultState = rs }, onNextQuestion = { answered = false; chosen = -1 })
        }
    }

    fun doAnswer(index: Int) {
        if (answered || resultState != 0) return
        answered = true // 同步置位，防连点
        chosen = index
        scope.launch {
            handleAnswer(vm, battle, context, sfxOn, hapticOn, index, onDone = { dmg, fb ->
                lastDamage = dmg; feedback = fb
                if (dmg > 0) {
                    shakeEnemy()
                }
            }, onEnd = { rs -> resultState = rs }, onNextQuestion = { answered = false; chosen = -1 })
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(
            "${Subjects.emoji(subject)} ${subject} · ${if (battle.isBoss) "BOSS战" else "第${level}关"}${if (player.hardMode) " · 🎓考研" else ""}",
            onBack = { nav.popBackStack() },
            subtitle = "限时 ${battle.timeLimitSec}s / 题 · 答对出招 答错挨打",
        )

        // 敌人面板
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    battle.enemy.emoji,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = enemyShake.value * if (enemyShake.value < 0.5f) -14f else 14f
                            alpha = 1f
                        }
                        .size(width = 56.dp, height = 56.dp),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(battle.enemy.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (battle.combo >= 2) {
                            LaunchedEffect(battle.combo) {
                                comboScale.snapTo(1.5f)
                                comboScale.animateTo(1f, tween(180))
                            }
                            Text(
                                "🔥 连击 x${battle.combo}",
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = comboScale.value
                                    scaleY = comboScale.value
                                },
                            )
                        }
                    }
                    BqProgressBar(
                        progress = battle.enemyHp.toFloat() / battle.enemyHpMax,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = androidx.compose.ui.graphics.lerp(Color(0xFFE53935), Color.White, hpFlash.value * 0.8f),
                        height = 10.dp,
                    )
                    Text(
                        "HP ${battle.enemyHp} / ${battle.enemyHpMax}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 我方状态
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🧑‍⚔️${player.nickname}", style = MaterialTheme.typography.labelLarge)
            BqProgressBar(
                progress = battle.playerHp / 100f,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                color = Color(0xFF43A047),
                height = 10.dp,
            )
            Text("❤️ ${battle.playerHp}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(8.dp))
            com.brainquest.game.ui.CoinText(player.coins, style = MaterialTheme.typography.labelLarge)
        }

        // 伤害飘字
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut(),
            ) {
                Text(
                    feedback ?: "",
                    color = if (lastDamage > 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 题目卡（内容可滚动：长题干/解析不虚）
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Q${battle.answered + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val urgent = timeLeft <= 5
                    Text(
                        "⏳ ${timeLeft}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (urgent) Color(0xFFC62828).copy(alpha = urgentAlpha) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BqProgressBar(
                    progress = timeLeft.toFloat() / battle.timeLimitSec,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .graphicsLayer { alpha = if (timeLeft <= 5) urgentAlpha else 1f },
                    color = if (timeLeft <= 5) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                    height = 4.dp,
                )
                val isFormula = battle.question?.let { com.brainquest.game.ui.isFormulaSubject(it.subject) } ?: false
                com.brainquest.game.ui.FormulaText(
                    battle.question?.question ?: "",
                    formula = isFormula,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                val q = battle.question
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
                } else if (q != null) {
                    // 统一选项视图：正确绿框、错选红框、解析随选项展示
                    com.brainquest.game.ui.QuizOptionList(
                        options = q.options,
                        answer = q.answer,
                        chosen = if (answered) chosen else -1,
                        revealed = answered,
                        onChoose = { doAnswer(it) },
                        eliminated = battle.eliminated.toSet(),
                        explanation = q.explanation,
                        formula = isFormula,
                    )
                }
            }
        }

        // 道具栏
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (vm.useItem(Items.ITEM_HINT) && battle.useHint()) {
                        Sfx.play(context, sfxOn, hapticOn, SfxType.CLICK)
                    }
                },
                enabled = !answered && resultState == 0 && (player.items[Items.ITEM_HINT] ?: 0) > 0,
            ) { Text("💡提示 ${player.items[Items.ITEM_HINT] ?: 0}") }
            OutlinedButton(
                onClick = {
                    if (vm.useItem(Items.ITEM_SKIP)) {
                        Sfx.play(context, sfxOn, hapticOn, SfxType.CLICK)
                        battle.nextQuestion()
                    }
                },
                enabled = !answered && resultState == 0 && (player.items[Items.ITEM_SKIP] ?: 0) > 0,
            ) { Text("🌀跳过 ${player.items[Items.ITEM_SKIP] ?: 0}") }
        }
    }

    // 胜负结算
    when (resultState) {
        1 -> {
            val stars = battle.stars()
            LaunchedEffect(Unit) {
                Sfx.play(context, sfxOn, hapticOn, SfxType.WIN)
            }
            AlertDialog(
                onDismissRequest = {},
                title = { Text(if (battle.isBoss) "🏆 BOSS 击败！" else "🎉 通关成功！") },
                text = {
                    Column {
                        Text("评价：${starText(stars)}", fontSize = 22.sp)
                        Text("答对 ${battle.correctCount} / ${battle.answered} · 最高连击 x${battle.maxCombo}")
                        Text("💰 金币 +${battle.coins()}  ⭐ 经验 +${battle.xp()}")
                    }
                },
                confirmButton = {
                    if (level < BattleState.LEVEL_COUNT) {
                        Button(onClick = {
                            vm.finishLevel(subject, level, stars, battle.coins(), battle.xp())
                            nav.popBackStack()
                            nav.navigate(Routes.battle(com.brainquest.game.data.subjectKey(subject), level + 1))
                        }) { Text("下一关") }
                    } else {
                        Button(onClick = {
                            vm.finishLevel(subject, level, stars, battle.coins(), battle.xp())
                            nav.popBackStack()
                        }) { Text("领取奖励") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        vm.finishLevel(subject, level, stars, battle.coins(), battle.xp())
                        nav.popBackStack()
                    }) { Text("返回") }
                },
            )
        }
        2 -> {
            LaunchedEffect(Unit) { Sfx.play(context, sfxOn, hapticOn, SfxType.LOSE) }
            AlertDialog(
                onDismissRequest = {},
                title = { Text("💀 战败了…") },
                text = { Text("别灰心，看看${battle.question?.explanation ?: ""}，恢复一下再战！") },
                confirmButton = {
                    if ((player.items[Items.ITEM_REVIVE] ?: 0) > 0 && !revivedOnce) {
                        Button(onClick = {
                            if (vm.useItem(Items.ITEM_REVIVE)) {
                                battle.revive()
                                battle.nextQuestion()
                                revivedOnce = true
                                resultState = 0
                            }
                        }) { Text("🧪 使用复活药水") }
                    } else {
                        Button(onClick = {
                            nav.popBackStack()
                            nav.navigate(Routes.battle(com.brainquest.game.data.subjectKey(subject), level))
                        }) { Text("重新挑战") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { nav.popBackStack() }) { Text("撤退") }
                },
            )
        }
        3 -> { /* 退出中 */ }
    }
}

private suspend fun handleAnswer(
    vm: AppViewModel,
    battle: BattleState,
    context: android.content.Context,
    sfxOn: Boolean,
    hapticOn: Boolean,
    index: Int,
    onDone: suspend (Int, String) -> Unit,
    onEnd: (Int) -> Unit,
    onNextQuestion: () -> Unit = {},
    fillInput: String? = null,
) {
    val q = battle.question ?: return
    val dmg = if (fillInput != null) battle.answerFill(fillInput) else battle.answer(index)
    vm.recordAnswer(q, if (fillInput != null) (if (dmg > 0) q.answer else -1) else index)
    if (dmg > 0) {
        Sfx.play(context, sfxOn, hapticOn, SfxType.CORRECT)
        onDone(dmg, "⚔️ 造成 $dmg 伤害！")
    } else {
        Sfx.play(context, sfxOn, hapticOn, SfxType.WRONG)
        onDone(dmg, "🛡️ 受到 ${-dmg} 伤害")
    }
    delay(900)
    when {
        battle.victory -> onEnd(1)
        battle.defeated -> onEnd(2)
        else -> {
            // 复位必须与 nextQuestion() 同帧提交：若等 LaunchedEffect(questionIndex) 再复位，
            // 新题会先以"已揭晓"状态画出一帧，表现为短暂闪出正确答案
            onNextQuestion()
            battle.nextQuestion()
        }
    }
}
