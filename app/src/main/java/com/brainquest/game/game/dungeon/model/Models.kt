package com.brainquest.game.game.dungeon.model

/**
 * 地牢幸存者：共享数据模型（纯 Kotlin，零 Compose 依赖）。
 * 世界布局：房间放在网格 (gx,gy) 上，房间 interiors 之间用走廊（门）连接。
 */

enum class Dir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);
}

enum class RoomType {
    START, BATTLE, ELITE, CHEST, SHOP, BOSS;

    /** 小地图与大房间中央的标识字 */
    val label: String get() = when (this) {
        START -> "起"
        BATTLE -> "战"
        ELITE -> "英"
        CHEST -> "箱"
        SHOP -> "店"
        BOSS -> "王"
    }
}

class Room(
    val id: Int,
    val gx: Int,
    val gy: Int,
    var type: RoomType,   // 生成器先生成后定型（Boss 房取最远点），故 var
) {
    /** 房间实际尺寸：生成器定型房型后按类型赋值（Boss 大 / 宝箱·商店紧凑），几何全走 per-room */
    var w = 2000f
    var h = 1300f

    var visited = false          // 玩家进过
    var populated = false        // 敌人已布置（预刷新幂等）
    var secondWave = false       // 怪海第二波已触发（战斗房首波清空后概率补波）
    var cleared = type == RoomType.START  // 清怪开门；起点房常开
    val discovered: Boolean get() = visited || neighbors.values.any { it.visited }

    /** 相邻房间（由生成器填好，键为方向） */
    val neighbors = HashMap<Dir, Room>(4)

    override fun toString() = "Room($gx,$gy,$type)"
}

/** 职业定义（MVP 3 个） */
data class ClassDef(
    val id: String,
    val name: String,
    val desc: String,
    val bodyColor: Long,      // 模型主色
    val accentColor: Long,    // 帽子/武器配色
    val maxHp: Int,
    val speed: Float,         // 逻辑像素/秒
    val attack: Int,
    val attackInterval: Float,
    val weaponName: String,
    val passiveName: String,
    val skillName: String,    // 主动技能名（engine 用 skillId 实装）
    val skillId: String,      // 职业专属主动技能 id（开局自带，见 CombatEngine.useSkill）
) {
    companion object {
        val ALL = listOf(
            ClassDef(
                "knight", "⚔️ 剑士", "高血近战，稳扎稳打",
                0xFFFF8A50, 0xFFB45309, 130, 200f, 10, 0.85f,
                weaponName = "挥砍", passiveName = "格挡：受击伤害 −15%", skillName = "旋风斩", skillId = "whirlwind",
            ),
            ClassDef(
                "mage", "🔥 法师", "低血高伤，火球远轰",
                0xFFB388FF, 0xFF6A1B9A, 85, 210f, 13, 1.0f,
                weaponName = "火球", passiveName = "法力回复：击杀回 1 血", skillName = "暴风雪", skillId = "blizzard",
            ),
            ClassDef(
                "ranger", "🏹 游侠", "高移速高攻速，风筝流",
                0xFF7EE38A, 0xFF1B5E20, 95, 245f, 7, 0.55f,
                weaponName = "连射", passiveName = "闪避：20% 概率免伤", skillName = "箭雨", skillId = "arrowrain",
            ),
        )

        fun byId(id: String) = ALL.first { it.id == id }
    }
}

/** 敌人种类（DUMMY=木桩/Boss 底座） */
enum class EnemyKind { DUMMY, SLIME, SKELETON, BAT, CASTER, BONE_ARCHER, SHIELD_GUARD, BOOM_SLIME }

/** 精英词缀（阶段 5 实装） */
enum class EliteAffix { RAGE, SHIELD, SPLIT }
