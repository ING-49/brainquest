package com.brainquest.game.data

import android.content.Context
import android.util.Log
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
        val packText = findPackedRules(File(context.filesDir, "content/packs"))
        val text = if (packText != null) {
            Log.i("GenRules", "命中热更 gen_rules（version=${current.version} → 应用热更版）")
            packText
        } else {
            runCatching {
                context.assets.open("config/gen_rules.json").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return
        }
        runCatching { current = json.decodeFromString<GenRules>(text) }
    }

    /**
     * 在 content/packs/ 下递归找 gen_rules.json（UpdateManager 把包解到 content/packs/<包id>/ 子目录，
     * 故不能只查平铺路径）；多个包都带时取 version 最高的。
     */
    private fun findPackedRules(dir: File): String? {
        if (!dir.isDirectory) return null
        var best: Pair<Int, String>? = null
        dir.walkTopDown().filter { it.isFile && it.name == "gen_rules.json" }.forEach { f ->
            runCatching {
                val v = json.parseToJsonElement(f.readText()).jsonObjectVersion()
                if (best == null || v > best!!.first) best = v to f.readText()
            }
        }
        return best?.second
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectVersion(): Int =
        (this as? kotlinx.serialization.json.JsonObject)
            ?.get("version")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toIntOrNull() } ?: 0
}
