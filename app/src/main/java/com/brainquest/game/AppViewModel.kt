package com.brainquest.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brainquest.game.data.AchievementDef
import com.brainquest.game.data.Achievements
import com.brainquest.game.data.DailyResult
import com.brainquest.game.data.Items
import com.brainquest.game.data.PlayerState
import com.brainquest.game.data.SaveStore
import com.brainquest.game.data.WrongEntry
import com.brainquest.game.data.levelForXp
import com.brainquest.game.data.question.Question
import com.brainquest.game.data.question.QuestionBank
import com.brainquest.game.data.subjectKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全局 App ViewModel：持有玩家状态、题库，并统一结算成长数值与成就 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val bank = QuestionBank(app)

    private val store = SaveStore(app)

    private val _player = MutableStateFlow(PlayerState())
    val player = _player.asStateFlow()

    /** 成就解锁 / 升级等提示事件（一次性 toast 用） */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** UI 侧直接发一条 toast（导出成功等） */
    fun toast(msg: String) = _events.tryEmit(msg)

    init {
        com.brainquest.game.data.GenRulesConfig.load(getApplication())
        bank.reload()
        viewModelScope.launch {
            _player.value = store.load()
            ensureIdentity()
            // 上线默认发一点新手资源
            checkNewcomer()
        }
    }

    /** 身份码：首次启动生成、永久固定不可改（云存档归属校验用） */
    private fun ensureIdentity() {
        if (_player.value.identity.isBlank()) {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val id = "QX" + (1..10).map { chars.random() }.joinToString("")
            commit { it.copy(identity = id) }
        }
    }

    val identity: String get() = _player.value.identity

    private suspend fun checkNewcomer() {
        if (_player.value.lastCheckIn.isEmpty()) {
            commit { it.copy(items = it.items + (Items.ITEM_HINT to 3) + (Items.ITEM_SKIP to 1) + (Items.ITEM_REVIVE to 1)) }
        }
    }

    private fun commit(transform: (PlayerState) -> PlayerState) {
        val next = transform(_player.value)
        _player.value = next
        viewModelScope.launch { store.save(next) }
        checkAchievements(next)
    }

    // ---------- 成长数值 ----------

    fun addCoins(amount: Int) {
        if (amount == 0) return
        commit { it.copy(coins = (it.coins + amount).coerceAtLeast(0)) }
    }

    fun addXp(amount: Int) {
        val before = levelForXp(_player.value.xp)
        commit { it.copy(xp = it.xp + amount) }
        val after = levelForXp(_player.value.xp)
        if (after > before) {
            _events.tryEmit("🎉 升级！Lv.$after")
            addCoins(after * 20)
        }
    }

    fun spendCoins(amount: Int): Boolean {
        if (_player.value.coins < amount) return false
        addCoins(-amount)
        return true
    }

    fun useItem(id: String): Boolean {
        val count = _player.value.items[id] ?: 0
        if (count <= 0) return false
        commit { it.copy(items = it.items + (id to count - 1)) }
        return true
    }

    fun addItem(id: String, count: Int = 1) {
        commit { it.copy(items = it.items + (id to (it.items[id] ?: 0) + count)) }
    }

    fun rename(nickname: String) = commit { it.copy(nickname = nickname.take(12).ifBlank { "小勇者" }) }

    fun setTheme(id: String) = commit { it.copy(activeTheme = id) }

    fun setAvatar(emoji: String) = commit { it.copy(avatar = emoji) }

    fun setSettings(url: String? = null, sound: Boolean? = null, haptics: Boolean? = null, hard: Boolean? = null, pkServer: String? = null, lastGoodSource: String? = null, cloudCode: String? = null) = commit {
        it.copy(
            updateServerUrl = url ?: it.updateServerUrl,
            soundOn = sound ?: it.soundOn,
            hapticsOn = haptics ?: it.hapticsOn,
            hardMode = hard ?: it.hardMode,
            pkServerUrl = pkServer ?: it.pkServerUrl,
            lastGoodSource = lastGoodSource ?: it.lastGoodSource,
            cloudCode = cloudCode ?: it.cloudCode,
        )
    }

    // ---------- 云存档 ----------

    private val saveJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 导出当前存档 JSON（明文，仅作加密前的原料） */
    fun exportSaveJson(): String = saveJson.encodeToString(PlayerState.serializer(), _player.value)

    /** 导出加密云存档（口令 PBKDF2→AES-GCM 信封，服务器只见密文） */
    fun encryptSaveJson(password: String): String =
        com.brainquest.game.util.SaveCrypto.encrypt(exportSaveJson(), password)

    /** 导入云存档（覆盖本地），成功返回 true。
     *  身份码永不随导入改变（v1.6.12 修复：旧实现会把本机身份码覆盖成存档里的或空，
     *  导致之后上传归属校验失败——身份码只属于本机，与存档内容无关）。 */
    fun importSaveJson(text: String): Boolean = runCatching {
        val state = saveJson.decodeFromString(PlayerState.serializer(), text)
            .copy(identity = _player.value.identity)
        _player.value = state
        viewModelScope.launch { store.save(state) }
    }.isSuccess

    /**
     * 导入云端信封存档：新格式用口令解密后导入，口令错返回 false；
     * 旧版明文存档（无 fmt 字段）免口令直接导入，老用户无损。
     */
    fun importCloudSave(envelope: String, password: String): Boolean {
        if (!com.brainquest.game.util.SaveCrypto.isEnvelope(envelope)) return importSaveJson(envelope)
        val plain = com.brainquest.game.util.SaveCrypto.decrypt(envelope, password) ?: return false
        return importSaveJson(plain)
    }

    /** 生成 10 位云存档码（BQ + 8 位大写字母数字，去除易混字符；旧 8 位码仍可用） */
    fun generateCloudCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "BQ" + (1..8).map { chars.random() }.joinToString("")
    }

    // ---------- 答题结算 ----------

    fun recordAnswer(question: Question, chosen: Int) {
        val correct = chosen == question.answer
        commit { state ->
            val wrongBook = if (!correct) {
                val entry = WrongEntry(question, chosen, System.currentTimeMillis())
                val others = state.wrongBook.filter { it.question.id != question.id }
                (listOf(entry) + others).take(120)
            } else {
                state.wrongBook
            }
            val s = state.copy(
                totalCorrect = state.totalCorrect + if (correct) 1 else 0,
                totalWrong = state.totalWrong + if (!correct) 1 else 0,
                wrongBook = wrongBook,
            )
            if (correct) taskBump(withDailyTasks(s), "solve10") else withDailyTasks(s)
        }
    }

    // ---------- 每日任务（日期变化自动重置；键与目标见 data/DailyTasks） ----------

    private fun todayStr(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun withDailyTasks(state: PlayerState): PlayerState =
        if (state.dailyTaskDate == todayStr()) state
        else state.copy(dailyTaskDate = todayStr(), dailyTaskProgress = emptyMap(), dailyTaskClaimed = emptyList())

    /** 推进任务进度（不超过目标值；done=直接置满） */
    private fun taskBump(state: PlayerState, id: String, done: Boolean = false): PlayerState {
        val goal = com.brainquest.game.data.DailyTasks.byId(id)?.goal ?: return state
        val cur = state.dailyTaskProgress[id] ?: 0
        val v = if (done) goal else (cur + 1).coerceAtMost(goal)
        if (v == cur) return state
        return state.copy(dailyTaskProgress = state.dailyTaskProgress + (id to v))
    }

    /** 华容道完成一局（胜利结算处调用） */
    fun completeDailyKlotski() = commit { withDailyTasks(taskBump(withDailyTasks(it), "klotski1", done = true)) }

    /** 领取任务奖励；成功返回奖励值，未达目标/已领取返回 0 */
    fun claimDailyTask(id: String): Int {
        val def = com.brainquest.game.data.DailyTasks.byId(id) ?: return 0
        var granted = 0
        commit { state ->
            val s = withDailyTasks(state)
            val done = (s.dailyTaskProgress[id] ?: 0) >= def.goal
            if (!done || id in s.dailyTaskClaimed) s
            else {
                granted = def.reward
                s.copy(dailyTaskClaimed = s.dailyTaskClaimed + id, coins = s.coins + def.reward)
            }
        }
        if (granted > 0) _events.tryEmit("✅ 任务完成「${def.desc}」 +$granted 金币")
        return granted
    }

    /** 错题本标记已掌握（复习时答对调用） */
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
            val s = withDailyTasks(state)
            val nb = s.wrongBook.map { entry ->
                if (entry.question.id != questionId) entry else when {
                    correct -> {
                        val ns = (entry.stage + 1).coerceAtMost(5)
                        entry.copy(stage = ns, nextReviewAt = now + com.brainquest.game.data.reviewIntervalMs(ns))
                    }
                    else -> entry.copy(stage = 0, nextReviewAt = now + 60_000L) // 1 分钟后再练
                }
            }
            if (correct) taskBump(s.copy(wrongBook = nb), "review3") else s.copy(wrongBook = nb)
        }
        if (correct) {
            _events.tryEmit("📅 复习通过，进入下一轮间隔")
        } else {
            _events.tryEmit("🔁 答错了，稍后再练一次")
        }
    }

    /** 关卡通关结算 */
    fun finishLevel(subject: String, level: Int, stars: Int, coins: Int, xp: Int) {
        val key = "${subjectKey(subject)}_$level"
        commit { state ->
            state.copy(
                levelStars = state.levelStars + (key to maxOf(stars, state.levelStars[key] ?: 0)),
            )
        }
        addCoins(coins)
        addXp(xp)
    }

    /** 记录小游戏最佳成绩，返回是否破纪录 */
    fun reportBest(key: String, score: Int): Boolean {
        val old = _player.value.bestScores[key] ?: 0
        if (score > old) {
            commit { it.copy(bestScores = it.bestScores + (key to score)) }
            return true
        }
        return false
    }

    /**
     * 迷你幸存者结算：最高生存秒/单局击杀入 bestScores，累计击杀累加，并按战绩发金币经验。
     * 返回是否刷新最高生存纪录。
     */
    fun addSurvivorResult(timeSec: Int, kills: Int): Boolean {
        val newBest = reportBest("survivor_best", timeSec)
        reportBest("survivor_kills", kills)
        commit { it.copy(survivorTotalKills = it.survivorTotalKills + kills) }
        if (kills > 0 || timeSec > 0) {
            addCoins(minOf(kills / 3, 50))
            addXp(minOf(timeSec / 4 + kills / 6, 80))
        }
        return newBest
    }

    /** 地牢幸存者结算：最高层数入档，金币（局内拾取 + 层数奖励）与经验入账；won=仅通关 5 层算胜场 */
    fun addDungeonResult(floor: Int, kills: Int, timeSec: Int, runCoins: Int, won: Boolean): Boolean {
        val newBest = reportBest("dungeon_floor", floor)
        reportBest("dungeon_kills", kills)
        commit { it.copy(dungeonWins = it.dungeonWins + if (won) 1 else 0, totalDungeonKills = it.totalDungeonKills + kills) }
        addCoins(runCoins + floor * 10)
        addXp(kills / 3 + floor * 8)
        return newBest
    }

    /** 地牢永久升级：花费金币买一级，返回是否成功 */
    fun buyDungeonPerk(id: String): Boolean {
        val cost = dungeonPerkCost(id)
        if (_player.value.coins < cost) return false
        commit { it.copy(coins = it.coins - cost, dungeonPerks = it.dungeonPerks + (id to (it.dungeonPerks[id] ?: 0) + 1)) }
        return true
    }

    fun dungeonPerkCost(id: String): Int {
        val n = _player.value.dungeonPerks[id] ?: 0
        return when (id) {
            "hp" -> 30 + n * 25
            "atk" -> 40 + n * 35
            "spd" -> 30 + n * 30
            "crit" -> 35 + n * 30
            "skillcd" -> 40 + n * 35
            "gold" -> 30 + n * 25
            else -> 9999
        }
    }

    /** 反向指标记录（越小越好，如最少步数）：首次记录或刷新更低值，返回是否破纪录 */
    fun reportBestLow(key: String, score: Int): Boolean {
        val old = _player.value.bestScores[key]
        if (old == null || (old > 0 && score < old) || old == 0) {
            commit { it.copy(bestScores = it.bestScores + (key to score)) }
            return true
        }
        return false
    }

    // ---------- 每日 ----------

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    fun yesterday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400_000L))

    fun isDailyDone(): Boolean = _player.value.dailyResults.containsKey(today())

    /** 每日挑战结算：连续天数 + 奖励 */
    fun finishDaily(correct: Int, total: Int): Int {
        val coins = 30 + correct * 8
        val today = today()
        val streak = if (_player.value.dailyResults.containsKey(yesterday())) _player.value.dailyStreak + 1 else 1
        commit {
            it.copy(
                dailyResults = it.dailyResults + (today to DailyResult(correct, total, coins)),
                dailyStreak = streak,
            )
        }
        addCoins(coins)
        addXp(20 + correct * 5)
        return coins
    }

    fun checkIn(): Int {
        val today = today()
        if (_player.value.lastCheckIn == today) return 0
        val streak = if (_player.value.lastCheckIn == yesterday()) _player.value.streakDays + 1 else 1
        val reward = 20 + minOf(streak, 7) * 5
        commit { it.copy(lastCheckIn = today, streakDays = streak) }
        addCoins(reward)
        return reward
    }

    // ---------- 商店 ----------

    fun buyItem(id: String): Boolean {
        val def = Items.byId(id)
        if (!spendCoins(def.price)) {
            _events.tryEmit("金币不足，还差 ${def.price - _player.value.coins}")
            return false
        }
        addItem(id)
        _events.tryEmit("✅ 已购入 ${def.name}（持有 ${_player.value.items[def.id] ?: 1}）")
        return true
    }

    fun buyTheme(id: String, price: Int): Boolean {
        if (_player.value.ownedThemes.contains(id)) return false
        if (!spendCoins(price)) {
            _events.tryEmit("金币不足，还差 ${price - _player.value.coins}")
            return false
        }
        commit { it.copy(ownedThemes = it.ownedThemes + id) }
        _events.tryEmit("✅ 主题已购入，点击卡片即可启用")
        return true
    }

    fun buyAvatar(emoji: String, price: Int, label: String = emoji): Boolean {
        if (_player.value.ownedAvatars.contains(emoji)) return false
        if (!spendCoins(price)) {
            _events.tryEmit("金币不足，还差 ${price - _player.value.coins}")
            return false
        }
        commit { it.copy(ownedAvatars = it.ownedAvatars + emoji) }
        _events.tryEmit("✅ 已购入头像「$label」并装备")
        return true
    }

    fun unlockAchievementManually(def: AchievementDef) = commit {
        it.copy(achievements = it.achievements + (def.id to System.currentTimeMillis()))
    }

    // ---------- 成就 ----------

    private fun checkAchievements(state: PlayerState) {
        val newly = Achievements.all.filter { def ->
            !state.achievements.containsKey(def.id) && def.check(state)
        }
        if (newly.isEmpty()) return
        commit { s ->
            // 成就限定头像：clear_30 → 金冠(kawaii_8)、snake_30 → 夜影(kawaii_9)，解锁即赠
            val giftAvatars = newly.mapNotNull { def ->
                when (def.id) {
                    "clear_30" -> "kawaii_8" to "金冠"
                    "snake_30" -> "kawaii_9" to "夜影"
                    else -> null
                }
            }.filter { (id, _) -> id !in s.ownedAvatars }
            for ((_, label) in giftAvatars) {
                _events.tryEmit("🎨 解锁限定头像「$label」，去「我的 → 换装」查看")
            }
            s.copy(
                achievements = s.achievements + newly.associate { it.id to System.currentTimeMillis() },
                ownedAvatars = s.ownedAvatars + giftAvatars.map { it.first },
                coins = s.coins + newly.sumOf { it.reward },
            )
        }
        newly.forEach { _events.tryEmit("${it.icon} 成就达成：${it.name} +${it.reward}金币") }
    }

    // ---------- 内容包（热更新后） ----------

    fun recordPkResult(win: Boolean, draw: Boolean = false) = commit {
        if (draw) it
        else if (win) it.copy(pkWins = it.pkWins + 1)
        else it.copy(pkLosses = it.pkLosses + 1)
    }

    fun setContentVersions(versions: Map<String, Int>) {
        commit { it.copy(contentVersions = versions) }
        com.brainquest.game.data.GenRulesConfig.load(getApplication())  // 出题参数热更
        bank.reload()
    }
}
