package com.brainquest.game.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 出题规则参数（可热更：内容包里放 gen_rules.json 即可覆盖默认手感） */
@Serializable
data class GenRules(
    val version: Int = 1,
    val fillChance: Int = 33,          // 填空题出现概率 %
    val fillMaxDifficulty: Int = 3,    // 填空题最高难度
    val baseDamage: Int = 24,          // 基础伤害
    val perLevelDamage: Int = 4,       // 每关卡级伤害
    val comboDamage: Int = 6,          // 每连击伤害
    val fillTimeBonus: Int = 10,       // 填空题加时（秒）
)

/** 出题规则配置：assets 默认 + 热更包覆盖 */
object GenRulesConfig {
    @Volatile
    var current: GenRules = GenRules()
        private set

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context) {
        val packFile = File(context.filesDir, "content/packs/gen_rules.json")
        val text = if (packFile.isFile) {
            packFile.readText()
        } else {
            runCatching {
                context.assets.open("config/gen_rules.json").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return
        }
        runCatching { current = json.decodeFromString<GenRules>(text) }
    }
}
