package com.brainquest.game.game.quizbattle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.brainquest.game.AppViewModel
import com.brainquest.game.ui.Routes
import com.brainquest.game.data.Items
import com.brainquest.game.data.question.Subjects
import com.brainquest.game.ui.CoinText
import com.brainquest.game.ui.PageHeader
import com.brainquest.game.util.Sfx
import com.brainquest.game.util.SfxType
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.brainquest.game.ui.BqProgressBar
import com.brainquest.game.ui.coinColor
import com.brainquest.game.ui.pressScale
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
    var confirmExit by remember { mutableStateOf(false) }   // 批23-A 返回确认
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

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFBF2), Color(0xFFF4F6FB))))
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        PageHeader(
            "${Subjects.emoji(subject)} ${subject} · ${if (battle.isBoss) "BOSS战" else "第${level}关"}${if (player.hardMode) " · 🎓考研" else ""}",
            onBack = { if (resultState != 0) nav.popBackStack() else confirmExit = true },   // 批23-A：对局中返回需确认
            subtitle = "限时 ${battle.timeLimitSec}s / 题 · 答对出招 答错挨打",
        )
        }
        if (confirmExit) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("退出本局？") },
                text = { Text("当前答题进度与当局收益将丢失") },
                confirmButton = {
                    Button(onClick = { confirmExit = false; nav.popBackStack() }) { Text("退出") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmExit = false }) { Text("继续答题") }
                },
            )
        }

        // 批57：幕布上缘柔化——暗带与暖白页头之间加过渡渐变（消除硬切，保留全宽出血）
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .background(Brush.verticalGradient(listOf(Color(0x001C2434), Color(0xFF1C2434))))
        )
        // 批41：敌方阵营暗带（战斗氛围，全宽出血）——品类参照：回合制战斗的"敌方区域"
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2434), Color(0xFF121826))))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp)
                        .background(
                            Brush.radialGradient(
                                0f to Color(0x55E53935),
                                0.7f to Color(0x22E53935),
                                1f to Color(0x00E53935),
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // 批57：脚下椭圆地面阴影（敌人从"悬浮"落地——接地感）
                    EnemyGroundShadow(Modifier.align(Alignment.BottomCenter))
                    Text(
                        battle.enemy.emoji,
                        fontSize = 34.sp,
                        modifier = Modifier.graphicsLayer {
                            translationX = enemyShake.value * if (enemyShake.value < 0.5f) -14f else 14f
                        },
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(battle.enemy.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        if (battle.combo >= 2) {
                            LaunchedEffect(battle.combo) {
                                comboScale.snapTo(1.5f)
                                comboScale.animateTo(1f, tween(180))
                            }
                            Text(
                                "🔥 连击 x${battle.combo}",
                                color = Color(0xFFFFB74D),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = comboScale.value
                                    scaleY = comboScale.value
                                },
                            )
                        }
                    }
                    SegmentedHpBar(
                        frac = battle.enemyHp.toFloat() / battle.enemyHpMax,
                        flash = hpFlash.value,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Text(
                        "HP ${battle.enemyHp} / ${battle.enemyHpMax}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x99FFFFFF),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        // 批57：幕布下缘柔化
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF121826), Color(0x00121826))))
        )

        // 批57：我方状态卡化（与敌方幕布视觉对等：敌方=暗色战场，我方=亮色营地卡）
        Card(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Text("🧑‍⚔️", fontSize = 13.sp) }
                Text(
                    player.nickname,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
                BqProgressBar(
                    progress = battle.playerHp / 100f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    color = Color(0xFF43A047),
                    height = 10.dp,
                )
                Text("❤️ ${battle.playerHp}", style = MaterialTheme.typography.labelLarge)
                // 批33：金币撤出战斗页（金币与战斗决策无关=噪声，见设计说明 §4）
            }
        }

        // 伤害飘字
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(36.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn() + slideInVertically { -it } +
                    scaleIn(initialScale = 0.6f, animationSpec = tween(150)),   // 批57：弹出感
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .weight(1f, fill = false)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
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

        // 道具栏（批57：按压反馈与全站一致）
        val hintInter = remember { MutableInteractionSource() }
        val skipInter = remember { MutableInteractionSource() }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (battle.useHint() && vm.useItem(Items.ITEM_HINT)) {   // 批21 修复：先判定成功再扣道具（原白扣）
                        Sfx.play(context, sfxOn, hapticOn, SfxType.CLICK)
                    }
                },
                enabled = !answered && resultState == 0 && (player.items[Items.ITEM_HINT] ?: 0) > 0 && battle.canUseHint(),
                interactionSource = hintInter,
                modifier = Modifier.pressScale(hintInter, 0.96f),
            ) { Text("💡提示 ${player.items[Items.ITEM_HINT] ?: 0}") }
            OutlinedButton(
                onClick = {
                    if (vm.useItem(Items.ITEM_SKIP)) {
                        Sfx.play(context, sfxOn, hapticOn, SfxType.CLICK)
                        battle.nextQuestion()
                    }
                },
                enabled = !answered && resultState == 0 && (player.items[Items.ITEM_SKIP] ?: 0) > 0,
                interactionSource = skipInter,
                modifier = Modifier.pressScale(skipInter, 0.96f),
            ) { Text("🌀跳过 ${player.items[Items.ITEM_SKIP] ?: 0}") }
        }
    }

    // 胜负结算（批57：自定义 overlay 演出卡——峰终定律，战斗体验的终点时刻决定记忆）
    when (resultState) {
        1 -> {
            val stars = battle.stars()
            val coinsReward = battle.coins()
            val xpReward = battle.xp()
            LaunchedEffect(Unit) {
                Sfx.play(context, sfxOn, hapticOn, SfxType.WIN)
            }
            // 入场（批53 GAMEOVER 同款 goIn：淡入+微缩放）
            val goIn = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                goIn.snapTo(0f); goIn.animateTo(1f, tween(220))
            }
            // 星星逐个弹出（延迟递增 + 弹性 overshoot）
            val starAnims = List(3) { remember { Animatable(0f) } }
            LaunchedEffect(Unit) {
                starAnims.forEachIndexed { i, a ->
                    launch {
                        delay(280L + i * 130L)
                        a.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
            }
            // 奖励数字滚动（星星之后）
            val coinAnim = remember { Animatable(0f) }
            val xpAnim = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(680L)
                launch { coinAnim.animateTo(coinsReward.toFloat(), tween(600)) }
                launch { xpAnim.animateTo(xpReward.toFloat(), tween(600)) }
            }
            // 满星金色脉动（批53 recPulse 同款）
            val perfect = stars >= 3
            val recPulse = rememberInfiniteTransition(label = "recPulse")
            val recA = recPulse.animateFloat(
                0.45f, 1f,
                infiniteRepeatable(tween(600, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
                label = "recA",
            )
            BackHandler(enabled = true) {
                vm.finishLevel(subject, level, stars, coinsReward, xpReward)
                nav.popBackStack()
            }
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .graphicsLayer {
                            alpha = 0.3f + 0.7f * goIn.value
                            val sc = 0.9f + 0.1f * goIn.value
                            scaleX = sc; scaleY = sc
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (battle.isBoss) "🏆 BOSS 击败！" else "🎉 通关成功！",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        // 星星行（逐个弹出；实心金 / 空心灰）
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            repeat(3) { i ->
                                val filled = i < stars
                                Text(
                                    if (filled) "★" else "☆",
                                    fontSize = 40.sp,
                                    color = if (filled) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.graphicsLayer {
                                        val s = starAnims[i].value
                                        scaleX = s; scaleY = s
                                        alpha = s.coerceIn(0f, 1f)
                                    },
                                )
                            }
                        }
                        if (perfect) {
                            Text(
                                "✨ 完美通关！",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300).copy(alpha = recA.value),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Text(
                            "答对 ${battle.correctCount} / ${battle.answered} · 最高连击 x${battle.maxCombo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        // 奖励行：CoinText（金币图标代码绘制，非 emoji）+ 数字滚动
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            CoinText(
                                coinAnim.value.roundToInt(),
                                style = MaterialTheme.typography.titleMedium,
                                color = coinColor,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("⭐", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "+${xpAnim.value.roundToInt()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (level < BattleState.LEVEL_COUNT) {
                            Button(
                                onClick = {
                                    vm.finishLevel(subject, level, stars, coinsReward, xpReward)
                                    nav.popBackStack()
                                    nav.navigate(Routes.battle(com.brainquest.game.data.subjectKey(subject), level + 1))
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                            ) { Text("下一关") }
                        } else {
                            Button(
                                onClick = {
                                    vm.finishLevel(subject, level, stars, coinsReward, xpReward)
                                    nav.popBackStack()
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                            ) { Text("领取奖励") }
                        }
                        TextButton(
                            onClick = {
                                vm.finishLevel(subject, level, stars, coinsReward, xpReward)
                                nav.popBackStack()
                            },
                            modifier = Modifier.padding(top = 2.dp),
                        ) { Text("返回") }
                    }
                }
            }
        }
        2 -> {
            LaunchedEffect(Unit) { Sfx.play(context, sfxOn, hapticOn, SfxType.LOSE) }
            val goIn = remember { Animatable(0f) }
            LaunchedEffect(Unit) { goIn.snapTo(0f); goIn.animateTo(1f, tween(220)) }
            BackHandler(enabled = true) { nav.popBackStack() }
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .graphicsLayer {
                            alpha = 0.3f + 0.7f * goIn.value
                            val sc = 0.9f + 0.1f * goIn.value
                            scaleX = sc; scaleY = sc
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("💀 战败了…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "别灰心，看看解析，恢复一下再战！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        battle.question?.explanation?.let { exp ->
                            Card(
                                Modifier.fillMaxWidth().padding(top = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Text(
                                    "💡 $exp",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                )
                            }
                        }
                        if ((player.items[Items.ITEM_REVIVE] ?: 0) > 0 && !revivedOnce) {
                            Button(
                                onClick = {
                                    if (vm.useItem(Items.ITEM_REVIVE)) {
                                        battle.revive()
                                        battle.nextQuestion()
                                        revivedOnce = true
                                        resultState = 0
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            ) { Text("🧪 使用复活药水") }
                        } else {
                            Button(
                                onClick = {
                                    nav.popBackStack()
                                    nav.navigate(Routes.battle(com.brainquest.game.data.subjectKey(subject), level))
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            ) { Text("重新挑战") }
                        }
                        TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 2.dp)) { Text("撤退") }
                    }
                }
            }
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

/** 批57：敌人脚下椭圆地面阴影（radialGradient 在宽扁盒内形成椭圆软斑，半径=半宽） */
@Composable
private fun EnemyGroundShadow(modifier: Modifier = Modifier) {
    val r = with(LocalDensity.current) { 22.dp.toPx() }
    Box(
        modifier
            .size(width = 44.dp, height = 12.dp)
            .background(Brush.radialGradient(listOf(Color(0x4D000000), Color(0x00000000)), radius = r))
    )
}

/** 批41：浅色分段血条（敌方暗带上用；5 段，分段思路参考 GameUi.HudHpBar） */
@Composable
private fun SegmentedHpBar(frac: Float, flash: Float, modifier: Modifier = Modifier) {
    val segments = 5
    Box(
        modifier
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x33000000))
    ) {
        Row(Modifier.fillMaxHeight().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(segments) { i ->
                val filled = i < (frac * segments).coerceIn(0f, segments.toFloat())
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (filled) androidx.compose.ui.graphics.lerp(Color(0xFFE53935), Color.White, flash * 0.8f)
                            else Color(0x22FFFFFF)
                        )
                )
            }
        }
    }
}
