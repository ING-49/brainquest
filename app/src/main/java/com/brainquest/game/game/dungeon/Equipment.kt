package com.brainquest.game.game.dungeon

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 装备系统（纯 Kotlin）：6 槽位 / 4 品质 / 词条随机 / 评分与自动穿戴。
 * 词条集：攻击、生命、移速、暴击、拾取、元素（攻速只来自升级三选一，避免区间乘法回退复杂度）。
 * 所有数值随层数与品质缩放；穿戴对引擎属性走"增量应用"（见 DungeonGame.applyBonus）。
 */
object Equipment {

    enum class Slot(val label: String) {
        WEAPON("武器"), HELMET("头盔"), ARMOR("护甲"), BOOTS("鞋子"), TRINKET1("饰品Ⅰ"), TRINKET2("饰品Ⅱ");
    }

    enum class Rarity(val label: String, val color: Long, val mult: Float) {
        COMMON("普通", 0xFFECEFF1, 1.0f),
        RARE("稀有", 0xFF64B5F6, 1.3f),
        EPIC("史诗", 0xFFBA68C8, 1.7f),
        LEGENDARY("传说", 0xFFFFB74D, 2.2f);
    }

    /** 词条种类 */
    enum class Affix(val label: String) {
        ATK("攻击"), HP("生命"), SPD("移速"), CRIT("暴击"), PICKUP("拾取"), ELEM("元素");
    }

    /** 一件装备：槽位 + 品质 + 词条（affix → 数值） */
    data class Item(
        val slot: Slot,
        val rarity: Rarity,
        val affixes: Map<Affix, Int>,
        val floor: Int,
    ) {
        val name: String get() = "${rarity.label}${slot.label}"
        val rarityColorLong: Long get() = rarity.color   // 渲染层转 Compose Color，本模块零 Compose 依赖

        /** 评分：用于自动穿戴比较 */
        val score: Int
            get() = ((affixes[Affix.ATK] ?: 0) * 3 + (affixes[Affix.HP] ?: 0) +
                    (affixes[Affix.SPD] ?: 0) / 4 + (affixes[Affix.CRIT] ?: 0) * 4 +
                    (affixes[Affix.PICKUP] ?: 0) / 3 + (affixes[Affix.ELEM] ?: 0))

        fun describe(): String = affixes.entries.joinToString(" · ") {
            when (it.key) {
                Affix.ATK -> "攻击 +${it.value}"
                Affix.HP -> "生命 +${it.value}"
                Affix.SPD -> "移速 +${it.value}"
                Affix.CRIT -> "暴击 +${it.value}%"
                Affix.PICKUP -> "拾取 +${it.value}%"
                Affix.ELEM -> "元素 +${it.value}%"
            }
        }
    }

    /** 六槽穿戴集合（聚合属性增量用） */
    class Bonus {
        var atk = 0; var hp = 0; var spd = 0
        var crit = 0; var pickup = 0; var elem = 0

        fun fromItem(it: Item) {
            atk += it.affixes[Affix.ATK] ?: 0
            hp += it.affixes[Affix.HP] ?: 0
            spd += it.affixes[Affix.SPD] ?: 0
            crit += it.affixes[Affix.CRIT] ?: 0
            pickup += it.affixes[Affix.PICKUP] ?: 0
            elem += it.affixes[Affix.ELEM] ?: 0
        }
    }

    /** 聚合六槽的总增量 */
    fun aggregate(slots: Map<Slot, Item>): Bonus {
        val b = Bonus()
        slots.values.forEach { b.fromItem(it) }
        return b
    }

    /** 随机生成一件装备（floor 从 1 起） */
    fun generate(floor: Int, rng: Random, forceRarity: Rarity? = null): Item {
        val rarity = forceRarity ?: run {
            val roll = rng.nextFloat()
            val legChance = 0.03f + 0.02f * (floor - 1)
            val epicChance = 0.10f + 0.03f * (floor - 1)
            when {
                roll < legChance -> Rarity.LEGENDARY
                roll < legChance + epicChance -> Rarity.EPIC
                roll < legChance + epicChance + 0.25f -> Rarity.RARE
                else -> Rarity.COMMON
            }
        }
        val slot = Slot.entries.random(rng)
        val count = 1 + rarity.ordinal                                  // 白1 词条 … 橙4 词条
        val pool = Affix.entries.shuffled(rng).take(count)
        val affixes = HashMap<Affix, Int>(count)
        for (a in pool) {
            affixes[a] = when (a) {
                Affix.ATK -> (2 + floor) * rarity.mult.roundToInt().coerceAtLeast(1)
                Affix.HP -> ((6 + 3 * floor) * rarity.mult).roundToInt()
                Affix.SPD -> ((8 + 2 * floor) * rarity.mult).roundToInt()
                Affix.CRIT -> (2 * rarity.mult).roundToInt().coerceAtLeast(1)
                Affix.PICKUP -> (6 * rarity.mult).roundToInt()
                Affix.ELEM -> (4 * rarity.mult).roundToInt()
            }
        }
        return Item(slot, rarity, affixes, floor)
    }

    /** Boss/精英的保底品质 */
    fun generateElite(floor: Int, rng: Random): Item = generate(floor, rng, Rarity.EPIC)
    fun generateBoss(floor: Int, rng: Random): Item = generate(floor, rng, Rarity.LEGENDARY)
}
