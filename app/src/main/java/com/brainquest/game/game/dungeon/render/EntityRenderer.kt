package com.brainquest.game.game.dungeon.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.brainquest.game.game.dungeon.DungeonGame
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.Room
import com.brainquest.game.game.dungeon.model.RoomType
import kotlin.math.sin

/**
 * 地牢幸存者实体渲染器：纯绘制，零逻辑、零状态变更。
 * 纪律：颜色/样式全部预缓存；不每帧创建对象；屏幕外实体跳过。
 * 玩家按 分层绘制（阴影→脚→身体→手臂→头→发型→朝向眼→武器），走路动画 sin(walkPhase)。
 */
object EntityRenderer {

    // ---------- 每层地牢色调（地板/墙/地板线） ----------
    private data class Palette(val floor: Color, val wall: Color, val line: Color, val door: Color)

    private val PALETTES = listOf(
        Palette(Color(0xFF2B2F45), Color(0xFF14161F), Color(0xFF353A54), Color(0xFF4A5170)),  // 1 石窟
        Palette(Color(0xFF25333D), Color(0xFF111A20), Color(0xFF304552), Color(0xFF43606F)),  // 2 冰窟
        Palette(Color(0xFF273427), Color(0xFF121A12), Color(0xFF33452F), Color(0xFF4A6642)),  // 3 毒沼
        Palette(Color(0xFF332A44), Color(0xFF181322), Color(0xFF453862), Color(0xFF5E4E86)),  // 4 幽殿
        Palette(Color(0xFF3D2626), Color(0xFF1D1010), Color(0xFF553232), Color(0xFF7A4747)),  // 5 熔核
    )

    private val SHADOW = Color(0x59000000)
    private val OUTLINE = Color(0xFF0E1016)
    private val DUMMY_WOOD = Color(0xFFB0885A)
    private val DUMMY_DARK = Color(0xFF7A5C3A)
    private val DUMMY_HEAD = Color(0xFFD8B98A)
    private val SKIN = Color(0xFFE8C39E)
    private val WHITE = Color(0xFFFFFFFF)
    private val DOOR_GLOW = Color(0x66FFD54F)
    private val LABEL_C = Color(0x55FFFFFF)

    // ---------- 世界 ----------
    fun drawWorld(scope: DrawScope, game: DungeonGame, time: Float) {
        val pal = PALETTES[(game.floor - 1).coerceIn(0, 4)]
        val vw = scope.size.width
        val vh = scope.size.height
        // 视口包围盒（世界坐标）
        val vl = game.camX
        val vt = game.camY
        val vr = game.camX + vw
        val vb = game.camY + vh

        // 走廊先画（压在房间地板下面），开门画地板、关门画封门墙
        for (room in game.rooms) {
            if (roomOffscreen(room, game, vl, vt, vr, vb, 1200f)) continue
            for ((d, n) in room.neighbors) {
                if (d == Dir.LEFT || d == Dir.UP) continue   // 每条走廊只画一次
                drawCorridor(scope, game, room, n, pal)
            }
        }
        // 房间
        for (room in game.rooms) {
            if (roomOffscreen(room, game, vl, vt, vr, vb, 400f)) continue
            drawRoom(scope, game, room, pal, time)
        }
    }

    private fun roomOffscreen(room: Room, game: DungeonGame, vl: Float, vt: Float, vr: Float, vb: Float, pad: Float): Boolean {
        val l = game.roomLeft(room) - pad
        val t = game.roomTop(room) - pad
        val r = l + DungeonGame.ROOM_W + pad * 2
        val b = t + DungeonGame.ROOM_H + pad * 2
        return r < vl || b < vt || l > vr || t > vb
    }

    private fun drawCorridor(scope: DrawScope, game: DungeonGame, a: Room, b: Room, pal: Palette) {
        val open = game.doorOpen(a, b)
        if (a.gy == b.gy) {
            val l = minOf(game.roomLeft(a) + DungeonGame.ROOM_W, game.roomLeft(b) + DungeonGame.ROOM_W) - DungeonGame.DOOR_PROBE
            val r = maxOf(game.roomLeft(a), game.roomLeft(b)) + DungeonGame.DOOR_PROBE
            val cy = a.gy * DungeonGame.GRID_Y
            val sx = l - game.camX
            val sy = cy - DungeonGame.DOOR_H / 2 - game.camY
            val w = r - l
            val h = DungeonGame.DOOR_H
            // 走廊地板
            scope.drawRect(pal.floor, Offset(sx, sy), Size(w, h))
            if (!open) {
                // 封门墙：走廊中段一道厚墙
                val wl = sx + w / 2 - 30f
                scope.drawRect(pal.wall, Offset(wl, sy - 6f), Size(60f, h + 12f))
                scope.drawRect(pal.line, Offset(wl, sy - 6f), Size(60f, 6f))
            } else {
                scope.drawRect(DOOR_GLOW, Offset(sx + w / 2 - 14f, sy), Size(28f, h))
            }
        } else {
            val t = minOf(game.roomTop(a) + DungeonGame.ROOM_H, game.roomTop(b) + DungeonGame.ROOM_H) - DungeonGame.DOOR_PROBE
            val bb = maxOf(game.roomTop(a), game.roomTop(b)) + DungeonGame.DOOR_PROBE
            val cx = a.gx * DungeonGame.GRID_X
            val sx = cx - DungeonGame.DOOR_H / 2 - game.camX
            val sy = t - game.camY
            val w = DungeonGame.DOOR_H
            val h = bb - t
            scope.drawRect(pal.floor, Offset(sx, sy), Size(w, h))
            if (!open) {
                val wt = sy + h / 2 - 30f
                scope.drawRect(pal.wall, Offset(sx - 6f, wt), Size(w + 12f, 60f))
                scope.drawRect(pal.line, Offset(sx - 6f, wt), Size(w + 12f, 6f))
            } else {
                scope.drawRect(DOOR_GLOW, Offset(sx, sy + h / 2 - 14f), Size(w, 28f))
            }
        }
    }

    private fun drawRoom(scope: DrawScope, game: DungeonGame, room: Room, pal: Palette, time: Float) {
        val l = game.roomLeft(room) - game.camX
        val t = game.roomTop(room) - game.camY
        val w = DungeonGame.ROOM_W
        val h = DungeonGame.ROOM_H

        // 地板
        scope.drawRoundRect(pal.floor, Offset(l, t), Size(w, h), CornerRadius(18f))
        // 地板拼格线
        val step = 80f
        var gx = l + step
        while (gx < l + w - 4f) {
            scope.drawLine(pal.line, Offset(gx, t + 12f), Offset(gx, t + h - 12f), 2f)
            gx += step
        }
        var gy = t + step
        while (gy < t + h - 4f) {
            scope.drawLine(pal.line, Offset(l + 12f, gy), Offset(l + w - 12f, gy), 2f)
            gy += step
        }
        // 墙（描边）
        scope.drawRoundRect(pal.wall, Offset(l, t), Size(w, h), CornerRadius(18f), style = Stroke(DungeonGame.WALL))
        // 清怪后的房间中心标记（房型）
        val cx = l + w / 2
        val cy = t + h / 2
        if (room.cleared) {
            scope.drawCircle(LABEL_C, 34f, Offset(cx, cy), style = Stroke(3f))
            // 房型用形状区分：战斗=三角、精英=菱形、宝箱=方、商店=圆、Boss=王冠
            when (room.type) {
                RoomType.BATTLE -> scope.drawPolygon3(cx, cy, pal.door)
                RoomType.ELITE -> scope.drawDiamond(cx, cy, pal.door)
                RoomType.CHEST -> scope.drawRect(pal.door, Offset(cx - 16f, cy - 12f), Size(32f, 24f))
                RoomType.SHOP -> scope.drawCircle(pal.door, 15f, Offset(cx, cy))
                RoomType.BOSS -> scope.drawCrown(cx, cy)
                else -> {}
            }
        }
        // Boss 房地面红圈预警
        if (room.type == RoomType.BOSS) {
            scope.drawCircle(Color(0x33E15A5A), 150f, Offset(cx, cy), style = Stroke(5f))
        }
    }

    // 小形状辅助（DrawScope 扩展，避免每帧 Path 分配：多边形直接用线段拼）
    private fun DrawScope.drawPolygon3(cx: Float, cy: Float, c: Color) {
        val r = 20f
        drawLine(c, Offset(cx, cy - r), Offset(cx + r * 0.87f, cy + r * 0.5f), 5f)
        drawLine(c, Offset(cx + r * 0.87f, cy + r * 0.5f), Offset(cx - r * 0.87f, cy + r * 0.5f), 5f)
        drawLine(c, Offset(cx - r * 0.87f, cy + r * 0.5f), Offset(cx, cy - r), 5f)
    }

    private fun DrawScope.drawDiamond(cx: Float, cy: Float, c: Color) {
        val r = 20f
        drawLine(c, Offset(cx, cy - r), Offset(cx + r, cy), 5f)
        drawLine(c, Offset(cx + r, cy), Offset(cx, cy + r), 5f)
        drawLine(c, Offset(cx, cy + r), Offset(cx - r, cy), 5f)
        drawLine(c, Offset(cx - r, cy), Offset(cx, cy - r), 5f)
    }

    private fun DrawScope.drawCrown(cx: Float, cy: Float) {
        val c = Color(0xFFFFD54F)
        drawRect(c, Offset(cx - 22f, cy - 4f), Size(44f, 18f))
        drawLine(c, Offset(cx - 22f, cy - 4f), Offset(cx - 22f, cy - 22f), 6f)
        drawLine(c, Offset(cx, cy - 4f), Offset(cx, cy - 26f), 6f)
        drawLine(c, Offset(cx + 22f, cy - 4f), Offset(cx + 22f, cy - 22f), 6f)
    }

    // ---------- 木桩占位怪 ----------
    fun drawDummy(scope: DrawScope, game: DungeonGame, dummy: DungeonGame.Dummy, time: Float) {
        val sx = dummy.x - game.camX
        val sy = dummy.y - game.camY
        val scale = if (dummy.big) 1.7f else 1f
        val sway = sin(dummy.t * 2f) * 2f * scale
        // 阴影
        scope.drawOval(SHADOW, Offset(sx - 20f * scale, sy + 12f * scale), Size(40f * scale, 12f * scale))
        // 底座
        scope.drawOval(DUMMY_DARK, Offset(sx - 16f * scale, sy + 6f * scale), Size(32f * scale, 10f * scale))
        // 立柱
        scope.drawRect(DUMMY_WOOD, Offset(sx - 5f * scale + sway, sy - 30f * scale), Size(10f * scale, 38f * scale))
        // 横杆
        scope.drawRect(DUMMY_WOOD, Offset(sx - 22f * scale + sway, sy - 24f * scale), Size(44f * scale, 8f * scale))
        // 头（木球）
        scope.drawCircle(DUMMY_HEAD, 11f * scale, Offset(sx + sway, sy - 40f * scale))
        scope.drawCircle(DUMMY_DARK, 11f * scale, Offset(sx + sway, sy - 40f * scale), style = Stroke(2f))
        if (dummy.big) {
            // Boss 预告：红头巾
            scope.drawRect(Color(0xFFE15A5A), Offset(sx - 11f * scale + sway, sy - 48f * scale), Size(22f * scale, 7f * scale))
        }
    }

    // ---------- 玩家（分层） ----------
    fun drawPlayer(scope: DrawScope, game: DungeonGame, time: Float) {
        val cls = game.cls ?: return
        val sx = game.px - game.camX
        val sy = game.py - game.camY
        val body = Color(cls.bodyColor)
        val accent = Color(cls.accentColor)
        val swing = if (game.moving) sin(game.walkPhase) * 4f else 0f
        val breathe = if (!game.moving) sin(time * 2.2f) * 1f else 0f

        // 朝向四象限：右/左（眼睛左右偏）、上（背面，不画眼）、下（正面）
        val fx = game.cosFacing()
        val fy = game.sinFacing()
        val facingRight = fx >= 0f
        val facingUp = fy < -0.5f

        // 1 阴影
        scope.drawOval(SHADOW, Offset(sx - 18f, sy + 14f), Size(36f, 10f))

        // 2 脚：两个小圆角矩形，走路交替
        scope.drawRoundRect(OUTLINE, Offset(sx - 10f, sy + 6f + swing), Size(9f, 12f), CornerRadius(4f))
        scope.drawRoundRect(OUTLINE, Offset(sx + 1f, sy + 6f - swing), Size(9f, 12f), CornerRadius(4f))

        // 3 身体：职业色圆角矩形 + 呼吸起伏
        val bodyTop = sy - 20f + breathe
        scope.drawRoundRect(body, Offset(sx - 13f, bodyTop), Size(26f, 30f), CornerRadius(7f))
        scope.drawRoundRect(OUTLINE, Offset(sx - 13f, bodyTop), Size(26f, 30f), CornerRadius(7f), style = Stroke(2f))

        // 4 手臂：走路反向摆
        scope.drawRoundRect(body, Offset(sx - 19f, bodyTop + 6f - swing * 0.6f), Size(7f, 16f), CornerRadius(3f))
        scope.drawRoundRect(body, Offset(sx + 12f, bodyTop + 6f + swing * 0.6f), Size(7f, 16f), CornerRadius(3f))

        // 5 头 + 6 发型/帽（上=背面只画头发；左右=侧脸；下=正脸）
        val headY = bodyTop - 13f
        scope.drawCircle(SKIN, 11f, Offset(sx, headY))
        scope.drawCircle(OUTLINE, 11f, Offset(sx, headY), style = Stroke(2f))
        when (cls.id) {
            "knight" -> {
                // 头盔：上半圆 + 顶脊
                scope.drawArc(accent, 180f, 180f, false, Offset(sx - 11f, headY - 11f), Size(22f, 20f), style = Stroke(5f))
                scope.drawRect(accent, Offset(sx - 2f, headY - 22f), Size(4f, 8f))
            }
            "mage" -> {
                // 法师尖帽：帽檐 + 三角（线段拼）
                scope.drawRect(accent, Offset(sx - 14f, headY - 12f), Size(28f, 5f))
                val c = accent
                scope.drawLine(c, Offset(sx - 9f, headY - 12f), Offset(sx, headY - 30f), 8f)
                scope.drawLine(c, Offset(sx + 9f, headY - 12f), Offset(sx, headY - 30f), 8f)
                scope.drawLine(c, Offset(sx - 9f, headY - 12f), Offset(sx + 9f, headY - 12f), 8f)
            }
            "ranger" -> {
                // 兜帽
                scope.drawArc(accent, if (facingRight) -30f else 210f, 120f, false, Offset(sx - 12f, headY - 12f), Size(24f, 24f), style = Stroke(5f))
            }
        }
        // 6 朝向眼（背面不画）
        if (!facingUp) {
            val ex = if (facingRight) 3f else -3f
            scope.drawCircle(OUTLINE, 1.8f, Offset(sx - 4f + ex, headY - 1f))
            scope.drawCircle(OUTLINE, 1.8f, Offset(sx + 4f + ex, headY - 1f))
        }

        // 7 武器：跟手、随职业（右臂端点为支点，指向 facing）
        val handX = sx + 15f + swing * 0.6f
        val handY = bodyTop + 12f + swing * 0.6f
        val deg = Math.toDegrees(game.weaponAngle().toDouble()).toFloat()
        scope.rotate(deg, pivot = Offset(handX, handY)) {
            when (cls.id) {
                "knight" -> {
                    // 剑：护手 + 刃
                    drawRect(Color(0xFF8D6E63), Offset(handX - 2f, handY - 4f), Size(4f, 10f))
                    drawRect(accent, Offset(handX - 6f, handY - 8f), Size(12f, 3f))
                    drawRect(Color(0xFFECEFF1), Offset(handX - 2f, handY - 34f), Size(4f, 26f))
                }
                "mage" -> {
                    // 法杖：杆 + 顶端宝珠
                    drawRect(Color(0xFF8D6E63), Offset(handX - 2f, handY - 30f), Size(4f, 34f))
                    drawCircle(BULLET_ORB, 5f, Offset(handX, handY - 32f))
                }
                "ranger" -> {
                    // 弓：弧 + 弦
                    drawArc(Color(0xFF8D6E63), -80f, 160f, false, Offset(handX - 4f, handY - 18f), Size(8f, 36f), style = Stroke(3f))
                    drawLine(Color(0xFFECEFF1), Offset(handX, handY - 17f), Offset(handX, handY + 17f), 1.5f)
                }
            }
        }
    }

    private val BULLET_ORB = Color(0xFFFF7043)

    // ---------- HUD 数值条（供 Screen 的 Canvas 调用） ----------
    fun drawBars(scope: DrawScope, game: DungeonGame) {
        val barW = (scope.size.width * 0.3f).coerceAtMost(300f)
        // 血条
        scope.drawRoundRect(BAR_BG, Offset(20f, 30f), Size(barW, 16f), CornerRadius(8f))
        scope.drawRoundRect(HP_C, Offset(20f, 30f), Size(barW * (game.hp.toFloat() / game.maxHp).coerceIn(0f, 1f), 16f), CornerRadius(8f))
        // 经验条（阶段 2 接入升级体系后启用，先画空槽）
        scope.drawRoundRect(BAR_BG, Offset(20f, 52f), Size(barW, 8f), CornerRadius(4f))
    }

    private val BAR_BG = Color(0x66000000)
    private val HP_C = Color(0xFFEF5350)
}
