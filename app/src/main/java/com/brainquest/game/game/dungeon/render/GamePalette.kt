package com.brainquest.game.game.dungeon.render

import androidx.compose.ui.graphics.Color

/**
 * 地牢幸存者全局色板：所有绘制只能从这里取色（硬约束，禁止随手 new Color）。
 * 风格：暗黑地牢 + 微光霓虹——底色深、主体高饱和、光源来自角色与技能。
 * 每层地牢色调由 [floorPalette] 派生，保持同一明度关系。
 */
object GamePalette {

    // ---------- 基础环境 ----------
    val BG_DEEP = Color(0xFF0B0E14)
    val BG_FLOOR = Color(0xFF1A1F2E)
    val BG_WALL = Color(0xFF2A3142)
    val BG_WALL_TOP = Color(0xFF3D4660)
    val GRID_LINE = Color(0x10FFFFFF)

    // ---------- 角色 ----------
    val PLAYER_SKIN = Color(0xFFE8C39E)
    val PLAYER_OUTLINE = Color(0xFF0E1016)

    // ---------- 敌人 ----------
    val ENEMY_SLIME = Color(0xFF66BB6A)
    val ENEMY_SLIME_DARK = Color(0xFF2E7D32)
    val ENEMY_SKELETON = Color(0xFFECEFF1)
    val ENEMY_SKELETON_DARK = Color(0xFF90A4AE)
    val ENEMY_BAT = Color(0xFF7E57C2)
    val ENEMY_BAT_EYE = Color(0xFFFFEB3B)
    val ENEMY_CASTER = Color(0xFF5C6BC0)
    val ENEMY_BULLET = Color(0xFFCE93D8)
    val ELITE_GLOW = Color(0xFFFFD54F)
    val BOSS_GLOW = Color(0xFFFF5252)

    // ---------- 元素 ----------
    val ELEM_FIRE = Color(0xFFFF7043)
    val ELEM_ICE = Color(0xFF81D4FA)
    val ELEM_LIGHTNING = Color(0xFFFFEE58)

    // ---------- UI ----------
    val UI_TEXT = Color(0xFFECEFF1)
    val UI_HP = Color(0xFFEF5350)
    val UI_HP_GHOST = Color(0xFFFFFFFF)      // 血条掉血残影
    val UI_EXP = Color(0xFF66BB6A)
    val UI_GOLD = Color(0xFFFFD54F)
    val UI_COIN = Color(0xFFBA68C8)
    val UI_PANEL = Color(0xCC1A1F2E)
    val UI_PANEL_EDGE = Color(0x20FFFFFF)
    val UI_BAR_BG = Color(0x66000000)
    val UI_ORB = Color(0xFF7EE38A)
    val UI_BULLET_PLAYER = Color(0xFFFFD54F)
    val UI_DOOR_GLOW = Color(0x66FFD54F)
    val UI_SHOUT = Color(0x55FFFFFF)

    // ---------- 品质 ----------
    val RARITY_COMMON = Color(0xFFB0BEC5)
    val RARITY_RARE = Color(0xFF42A5F5)
    val RARITY_EPIC = Color(0xFFAB47BC)
    val RARITY_LEGENDARY = Color(0xFFFFA726)

    // ---------- 阴影 ----------
    val SHADOW = Color(0x59000000)

    /** 每层地牢环境色（地板/墙/格线/门），索引 = floor-1 */
    data class FloorTone(val floor: Color, val wall: Color, val line: Color, val door: Color)

    private val FLOOR_TONES = listOf(
        FloorTone(Color(0xFF2B2F45), Color(0xFF14161F), Color(0xFF353A54), Color(0xFF4A5170)),  // 1 石窟
        FloorTone(Color(0xFF25333D), Color(0xFF111A20), Color(0xFF304552), Color(0xFF43606F)),  // 2 冰窟
        FloorTone(Color(0xFF273427), Color(0xFF121A12), Color(0xFF33452F), Color(0xFF4A6642)),  // 3 毒沼
        FloorTone(Color(0xFF332A44), Color(0xFF181322), Color(0xFF453862), Color(0xFF5E4E86)),  // 4 幽殿
        FloorTone(Color(0xFF3D2626), Color(0xFF1D1010), Color(0xFF553232), Color(0xFF7A4747)),  // 5 熔核
    )

    fun floorPalette(floor: Int): FloorTone = FLOOR_TONES[(floor - 1).coerceIn(0, FLOOR_TONES.lastIndex)]
}
