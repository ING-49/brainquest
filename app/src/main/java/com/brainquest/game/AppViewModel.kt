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

    init {
        com.brainquest.game.data.GenRulesConfig.load(getApplication())
        bank.reload()
        viewModelScope.launch {
            _player.value = store.load()
            // 上线默认发一点新手资源
            checkNewcomer()
        }
    }

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

    fun setSettings(url: String? = null, sound: Boolean? = null, haptics: Boolean? = null, hard: Boolean? = null, pkServer: String? = null) = commit {
        it.copy(
            updateServerUrl = url ?: it.updateServerUrl,
            soundOn = sound ?: it.soundOn,
            hapticsOn = haptics ?: it.hapticsOn,
            hardMode = hard ?: it.hardMode,
            pkServerUrl = pkServer ?: it.pkServerUrl,
        )
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
            state.copy(
                totalCorrect = state.totalCorrect + if (correct) 1 else 0,
                totalWrong = state.totalWrong + if (!correct) 1 else 0,
                wrongBook = wrongBook,
            )
        }
    }

    /** 错题本标记已掌握（复习时答对调用） */
    fun markWrongMastered(questionId: String) = commit { state ->
        state.copy(wrongBook = state.wrongBook.map {
            if (it.question.id == questionId) it.copy(mastered = true) else it
        })
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
        if (!spendCoins(def.price)) return false
        addItem(id)
        return true
    }

    fun buyTheme(id: String, price: Int): Boolean {
        if (_player.value.ownedThemes.contains(id)) return false
        if (!spendCoins(price)) return false
        commit { it.copy(ownedThemes = it.ownedThemes + id) }
        return true
    }

    fun buyAvatar(emoji: String, price: Int): Boolean {
        if (_player.value.ownedAvatars.contains(emoji)) return false
        if (!spendCoins(price)) return false
        commit { it.copy(ownedAvatars = it.ownedAvatars + emoji) }
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
            s.copy(
                achievements = s.achievements + newly.associate { it.id to System.currentTimeMillis() },
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
