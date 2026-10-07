package com.brainquest.game.game.dungeon.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.brainquest.game.game.dungeon.DungeonGame
import com.brainquest.game.game.dungeon.Equipment
import com.brainquest.game.game.dungeon.model.Dir
import com.brainquest.game.game.dungeon.model.Room
import com.brainquest.game.game.dungeon.model.RoomType
import kotlin.math.abs
import kotlin.math.sin

/**
 * 地牢幸存者实体渲染器：纯绘制，零逻辑、零状态变更。
 * 纪律：颜色/样式全部预缓存；不每帧创建对象；屏幕外实体跳过。
 * 玩家按 分层绘制（阴影→脚→身体→手臂→头→发型→朝向眼→武器），走路动画 sin(walkPhase)。
 */
object EntityRenderer {

    // ---------- 每层地牢色调：统一由 GamePalette 派生 ----------
    private fun pal(game: DungeonGame) = GamePalette.floorPalette(game.floor)

    // 颜色别名：全部指向 GamePalette（唯一取色处）
    private val SHADOW = GamePalette.SHADOW
    private val OUTLINE = GamePalette.PLAYER_OUTLINE
    private val SLIME_C = GamePalette.ENEMY_SLIME
    private val SLIME_DARK = GamePalette.ENEMY_SLIME_DARK
    private val BONE_C = GamePalette.ENEMY_SKELETON
    private val BONE_DARK = GamePalette.ENEMY_SKELETON_DARK
    private val BAT_C = GamePalette.ENEMY_BAT
    private val CASTER_C = GamePalette.ENEMY_CASTER
    private val ENEMY_BULLET = GamePalette.ENEMY_BULLET
    private val FIRE_C = GamePalette.ELEM_FIRE
    private val ICE_C = GamePalette.ELEM_ICE
    private val THUNDER_C = GamePalette.ELEM_LIGHTNING
    private val ORB_C = GamePalette.UI_ORB
    private val BULLET_P = GamePalette.UI_BULLET_PLAYER
    private val DUMMY_WOOD = Color(0xFFB0885A)
    private val DUMMY_DARK = Color(0xFF7A5C3A)
    private val DUMMY_HEAD = Color(0xFFD8B98A)
    private val SKIN = GamePalette.PLAYER_SKIN
    private val WHITE = Color(0xFFFFFFFF)
    private val DOOR_GLOW = GamePalette.UI_DOOR_GLOW
    private val LABEL_C = GamePalette.UI_SHOUT

    // ---------- 世界 ----------
    fun drawWorld(scope: DrawScope, game: DungeonGame, time: Float) {
        val pal = pal(game)
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
        val r = l + room.w + pad * 2
        val b = t + room.h + pad * 2
        return r < vl || b < vt || l > vr || t > vb
    }

    private fun drawCorridor(scope: DrawScope, game: DungeonGame, a: Room, b: Room, pal: GamePalette.FloorTone) {
        val open = game.doorOpen(a, b)
        // 门后房间类型徽章（Hades 门奖励预览）：锁门时预告门后是什么房，进门前做路线决策
        val next = if (game.currentRoom === a) b else if (game.currentRoom === b) a else null
        if (!open && next != null) {
            val horizontal = a.gy == b.gy
            val bx: Float; val by: Float
            if (horizontal) {
                val l = minOf(game.roomLeft(a) + a.w, game.roomLeft(b) + b.w) - DungeonGame.DOOR_PROBE
                val r = maxOf(game.roomLeft(a), game.roomLeft(b)) + DungeonGame.DOOR_PROBE
                val cy = a.gy * DungeonGame.GRID_Y
                bx = (l + r) / 2f - game.camX; by = cy - DungeonGame.DOOR_H / 2 - 26f - game.camY
            } else {
                val t = minOf(game.roomTop(a) + a.h, game.roomTop(b) + b.h) - DungeonGame.DOOR_PROBE
                val bb = maxOf(game.roomTop(a), game.roomTop(b)) + DungeonGame.DOOR_PROBE
                val cx = a.gx * DungeonGame.GRID_X
                bx = cx - DungeonGame.DOOR_H / 2 - 26f - game.camX; by = (t + bb) / 2f - game.camY
            }
            drawDoorBadge(scope, next.type, bx, by)
        }
        if (a.gy == b.gy) {
            val l = minOf(game.roomLeft(a) + a.w, game.roomLeft(b) + b.w) - DungeonGame.DOOR_PROBE
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
            // 纵向走廊：上房底边 → 下房顶边
            val t = minOf(game.roomTop(a) + a.h, game.roomTop(b) + b.h) - DungeonGame.DOOR_PROBE
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

    /** 门后房间类型徽章：彩色圆 + 白色类型图形（战=交叉剑线 英=菱 箱=方 店=圆 王=红底白眼） */
    private fun drawDoorBadge(scope: DrawScope, type: RoomType, bx: Float, by: Float) {
        val (col, r) = when (type) {
            RoomType.BATTLE -> 0xFFE57373 to 13f
            RoomType.ELITE -> 0xFFFFD54F to 14f
            RoomType.CHEST -> 0xFFA1887F to 13f
            RoomType.SHOP -> 0xFF81C784 to 13f
            RoomType.BOSS -> 0xFFE53935 to 16f
            else -> return
        }
        val c = Color(col)
        scope.drawCircle(c.copy(alpha = 0.85f), r, Offset(bx, by))
        scope.drawCircle(Color(0xAA000000), r, Offset(bx, by), style = Stroke(2f))
        when (type) {
            RoomType.BATTLE -> {   // 交叉剑：两条白斜线
                scope.drawLine(Color.White, Offset(bx - 6f, by - 6f), Offset(bx + 6f, by + 6f), 2.5f)
                scope.drawLine(Color.White, Offset(bx + 6f, by - 6f), Offset(bx - 6f, by + 6f), 2.5f)
            }
            RoomType.ELITE -> scope.drawDiamond(bx, by, Color.White)
            RoomType.CHEST -> scope.drawRect(Color.White, Offset(bx - 6f, by - 5f), Size(12f, 10f))
            RoomType.SHOP -> scope.drawCircle(Color.White, 5f, Offset(bx, by))
            RoomType.BOSS -> {   // 骷髅双眼
                scope.drawCircle(Color.White, 3f, Offset(bx - 5f, by - 3f))
                scope.drawCircle(Color.White, 3f, Offset(bx + 5f, by - 3f))
                scope.drawRect(Color.White, Offset(bx - 1.5f, by + 3f), Size(3f, 6f))
            }
            else -> {}
        }
    }

    private fun drawRoom(scope: DrawScope, game: DungeonGame, room: Room, pal: GamePalette.FloorTone, time: Float) {
        val l = game.roomLeft(room) - game.camX
        val t = game.roomTop(room) - game.camY
        val w = room.w
        val h = room.h

        // 地板
        scope.drawRoundRect(pal.floor, Offset(l, t), Size(w, h), CornerRadius(18f))
        // 地板拼格线（细分 40px：画面更显大，后续地形/陷阱可直接挂格）
        val step = 40f
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
        // 无状态地板装饰：按 (房间网格, 格索引) hash 撒裂缝/碎石/骨头/苔藓（每层色调不同、每格恒定）
        run {
            var cx2 = l + step / 2f
            var col = 0
            while (cx2 < l + w - 8f) {
                var cy2 = t + step / 2f
                var row = 0
                while (cy2 < t + h - 8f) {
                    val hsh = (room.gx * 73856093) xor (room.gy * 19349663) xor (col * 83492791) xor (row * 29712150)
                    when ((hsh ushr 7) % 100) {
                        in 0..5 -> {   // 裂缝：三段折线
                            val cc = pal.line.copy(alpha = 0.85f)
                            scope.drawLine(cc, Offset(cx2 - 9f, cy2 - 4f), Offset(cx2, cy2 + 2f), 1.5f)
                            scope.drawLine(cc, Offset(cx2, cy2 + 2f), Offset(cx2 + 8f, cy2 - 5f), 1.5f)
                        }
                        in 6..10 -> scope.drawCircle(Color(0x20FFFFFF), 3.2f, Offset(cx2, cy2))   // 碎石
                        in 11..13 -> {   // 骨头：短杆+两端节
                            val bc = Color(0x4DECEFF1)
                            scope.drawLine(bc, Offset(cx2 - 5f, cy2 + 3f), Offset(cx2 + 5f, cy2 - 3f), 2f)
                            scope.drawCircle(bc, 1.8f, Offset(cx2 - 5f, cy2 + 3f))
                            scope.drawCircle(bc, 1.8f, Offset(cx2 + 5f, cy2 - 3f))
                        }
                        in 14..19 -> scope.drawCircle(Color(0x1A66BB6A), 5.5f, Offset(cx2, cy2))   // 苔藓
                        else -> {}
                    }
                    cy2 += step; row++
                }
                cx2 += step; col++
            }
        }
        // 墙（描边）+ 墙顶亮边 + 墙体投影暗带（立体感）
        scope.drawRoundRect(pal.wall, Offset(l, t), Size(w, h), CornerRadius(18f), style = Stroke(DungeonGame.WALL))
        scope.drawRect(GamePalette.BG_WALL_TOP, Offset(l + DungeonGame.WALL, t + DungeonGame.WALL), Size(w - DungeonGame.WALL * 2, 6f))
        scope.drawRect(Color(0x30000000), Offset(l + DungeonGame.WALL, t + DungeonGame.WALL + 6f), Size(w - DungeonGame.WALL * 2, 24f))
        // 清怪后的房间中心标记（房型）
        val cx = l + w / 2
        val cy = t + h / 2
        if (room.cleared) {
            scope.drawCircle(LABEL_C, 34f, Offset(cx, cy), style = Stroke(3f))
            // 房型用形状区分：战斗=三角、精英=菱形、宝箱=方（开箱后的“已开启”标记）、Boss=王冠
            // （商店=货摊实体替代标记；宝箱未开时有实体，也不画标记）
            when (room.type) {
                RoomType.BATTLE -> scope.drawPolygon3(cx, cy, pal.door)
                RoomType.ELITE -> scope.drawDiamond(cx, cy, pal.door)
                RoomType.CHEST -> scope.drawRect(pal.door, Offset(cx - 16f, cy - 12f), Size(32f, 24f))
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

    // ---------- 敌人（按种类差异化；受击闪白/元素状态/精英发光） ----------
    fun drawEnemy(scope: DrawScope, game: DungeonGame, e: com.brainquest.game.game.core.CombatEngine.Enemy, time: Float) {
        val sx = e.x - game.camX
        val sy = e.y - game.camY
        if (sx < -60f || sy < -60f || sx > scope.size.width + 60f || sy > scope.size.height + 60f) return
        // 死亡动画：先弹大再缩没（deathTimer 0.3 → 0）
        val dieK = if (e.dying) (e.deathTimer / 0.3f).coerceIn(0f, 1f) else 1f
        if (dieK <= 0.02f) return
        val popScale = if (e.dying) 0.4f + 1.1f * dieK * dieK else 1f + 0.18f * e.hitSquash   // 受击 pop（squash&stretch）
        scope.withTransform({ scale(popScale, popScale, pivot = Offset(sx, sy)) }) {
            val hop = if (e.kind == com.brainquest.game.game.dungeon.model.EnemyKind.SLIME) abs(sin(time * 5f + e.wobbleSeed)) * 6f else 0f
            val wing = sin(time * 14f + e.wobbleSeed)

            if (e.elite && !e.dying) {
                // 精英：金色发光外圈 + 环绕粒子
                drawCircle(GamePalette.ELITE_GLOW.copy(alpha = 0.4f), e.r + 8f, Offset(sx, sy), style = Stroke(4f))
                for (i in 0..2) {
                    val a = time * 2.4f + i * 2.094f
                    drawCircle(GamePalette.ELITE_GLOW.copy(alpha = 0.8f), 2.5f,
                        Offset(sx + kotlin.math.cos(a) * (e.r + 14f), sy + kotlin.math.sin(a) * (e.r + 14f)))
                }
            }
            // 攻击前摇预警：黄色脉冲环（可预判可躲避）
            if (e.atkState == 1 && !e.dying) {
                val pulse = sin(time * 22f) * 3f
                drawCircle(Color(0x88FFEB3B), e.r + 6f + pulse, Offset(sx, sy), style = Stroke(3f))
            }
            when (e.kind) {
                com.brainquest.game.game.dungeon.model.EnemyKind.SLIME -> {
                    // 突进时拉长，蓄力时压扁
                    val squash = if (e.atkState == 1) 0.72f
                        else if (e.atkState == 2) 1.15f
                        else 1f + sin(time * 8f + e.wobbleSeed) * 0.08f
                    drawOval(SLIME_C, Offset(sx - e.r, sy - e.r * squash - hop), Size(e.r * 2, e.r * 2 * squash))
                    drawOval(SLIME_DARK, Offset(sx - e.r, sy - e.r * squash - hop), Size(e.r * 2, e.r * 2 * squash), style = Stroke(2f))
                    drawCircle(Color(0x40FFFFFF), e.r * 0.28f, Offset(sx - e.r * 0.4f, sy - e.r * 1.1f - hop))   // 顶部高光
                    drawCircle(SLIME_DARK, 2.5f, Offset(sx - 5f, sy - e.r * 0.6f - hop))
                    drawCircle(SLIME_DARK, 2.5f, Offset(sx + 5f, sy - e.r * 0.6f - hop))
                }
                com.brainquest.game.game.dungeon.model.EnemyKind.SKELETON -> drawSkeletonBody(sx, sy, e.r, time, e.wobbleSeed)
                com.brainquest.game.game.dungeon.model.EnemyKind.BAT -> {
                    val hover = if (e.atkState == 1) -10f else 0f   // 蓄力拉高
                    val wy = wing * 6f
                    val by = sy + hover
                    drawLine(BAT_C, Offset(sx, by - 4f), Offset(sx - e.r * 1.5f, by - 10f + wy), 5f)
                    drawLine(BAT_C, Offset(sx, by - 4f), Offset(sx + e.r * 1.5f, by - 10f + wy), 5f)
                    drawCircle(BAT_C, e.r * 0.7f, Offset(sx, by))
                    drawCircle(GamePalette.PLAYER_OUTLINE, e.r * 0.7f, Offset(sx, by), style = Stroke(2f))
                    drawCircle(GamePalette.ENEMY_BAT_EYE, 2f, Offset(sx - 3f, by - 2f))
                    drawCircle(GamePalette.ENEMY_BAT_EYE, 2f, Offset(sx + 3f, by - 2f))
                }
                com.brainquest.game.game.dungeon.model.EnemyKind.CASTER -> {
                    val c = CASTER_C
                    drawLine(c, Offset(sx - e.r, sy + e.r * 0.8f), Offset(sx, sy - e.r * 0.9f), 10f)
                    drawLine(c, Offset(sx + e.r, sy + e.r * 0.8f), Offset(sx, sy - e.r * 0.9f), 10f)
                    drawLine(c, Offset(sx - e.r, sy + e.r * 0.8f), Offset(sx + e.r, sy + e.r * 0.8f), 10f)
                    drawCircle(SLIME_DARK, e.r * 0.45f, Offset(sx, sy - e.r * 0.9f))
                    drawLine(DUMMY_WOOD, Offset(sx + e.r * 0.9f, sy + e.r * 0.6f), Offset(sx + e.r * 1.1f, sy - e.r * 1.1f), 3f)
                    // 蓄力发光：前摇期杖顶宝珠变大发光
                    val charging = e.atkState == 1
                    if (charging) drawCircle(THUNDER_C.copy(alpha = 0.35f), 9f, Offset(sx + e.r * 1.1f, sy - e.r * 1.15f))
                    drawCircle(THUNDER_C, if (charging) 5f else 3.5f, Offset(sx + e.r * 1.1f, sy - e.r * 1.15f))
                }
                com.brainquest.game.game.dungeon.model.EnemyKind.BONE_ARCHER -> {
                    drawSkeletonBody(sx, sy, e.r * 0.95f, time, e.wobbleSeed)
                    // 弓：朝向玩家的弓环；拉弓前摇画引满的弦
                    val ang = kotlin.math.atan2(game.engine.py - e.y, game.engine.px - e.x)
                    val bx = sx + kotlin.math.cos(ang) * e.r * 1.2f
                    val by = sy + kotlin.math.sin(ang) * e.r * 1.2f
                    drawCircle(BONE_DARK, 7f, Offset(bx, by), style = Stroke(3f))
                    if (e.atkState == 1) drawLine(Color(0xFFFFEB3B), Offset(bx, by), Offset(sx, sy), 2.5f)
                }
                com.brainquest.game.game.dungeon.model.EnemyKind.SHIELD_GUARD -> {
                    val sway = sin(time * 2f + e.wobbleSeed) * 2f
                    // 灰甲身躯
                    drawRoundRect(Color(0xFF546E7A), Offset(sx - e.r * 0.8f + sway, sy - e.r * 0.9f), Size(e.r * 1.6f, e.r * 1.7f), CornerRadius(8f))
                    drawRoundRect(GamePalette.PLAYER_OUTLINE, Offset(sx - e.r * 0.8f + sway, sy - e.r * 0.9f), Size(e.r * 1.6f, e.r * 1.7f), CornerRadius(8f), style = Stroke(2.5f))
                    drawCircle(Color(0xFF37474F), e.r * 0.4f, Offset(sx + sway, sy - e.r * 1.15f))
                    drawLine(GamePalette.PLAYER_OUTLINE, Offset(sx - e.r * 0.3f + sway, sy - e.r * 1.15f), Offset(sx + e.r * 0.3f + sway, sy - e.r * 1.15f), 2f)
                    // 大盾：面向玩家一侧的木盾（金边）
                    val ang = kotlin.math.atan2(game.engine.py - e.y, game.engine.px - e.x)
                    val gx2 = sx + kotlin.math.cos(ang) * e.r * 1.15f
                    val gy2 = sy + kotlin.math.sin(ang) * e.r * 1.15f
                    drawRoundRect(Color(0xFF8D6E63), Offset(gx2 - e.r * 0.38f, gy2 - e.r * 0.85f), Size(e.r * 0.76f, e.r * 1.7f), CornerRadius(6f))
                    drawRoundRect(GamePalette.UI_GOLD, Offset(gx2 - e.r * 0.38f, gy2 - e.r * 0.85f), Size(e.r * 0.76f, e.r * 1.7f), CornerRadius(6f), style = Stroke(2f))
                }
                com.brainquest.game.game.dungeon.model.EnemyKind.BOOM_SLIME -> {
                    val fuse = e.atkState == 1
                    // 引爆预警：红色脉冲晕 + 快闪身体
                    if (fuse) drawCircle(Color(0x55FF1744), e.r * 2.3f, Offset(sx, sy))
                    val flash = if (fuse && (time * 14f).toInt() % 2 == 0) Color(0xFFFFAB91) else SLIME_C
                    drawOval(flash, Offset(sx - e.r, sy - e.r), Size(e.r * 2, e.r * 2))
                    drawOval(Color(0xFFBF360C), Offset(sx - e.r, sy - e.r), Size(e.r * 2, e.r * 2), style = Stroke(2f))
                    drawCircle(Color(0x40FFFFFF), e.r * 0.28f, Offset(sx - e.r * 0.4f, sy - e.r * 1.05f))
                    drawCircle(Color(0xFFBF360C), 2.5f, Offset(sx - 5f, sy - e.r * 0.55f))
                    drawCircle(Color(0xFFBF360C), 2.5f, Offset(sx + 5f, sy - e.r * 0.55f))
                }
                else -> if (e.bossFloor > 0) drawBossBody(this, sx, sy, e, time) else drawDummyBody(sx, sy, e.r, time, e.wobbleSeed, boss = false)
            }
            if (e.dying) return@withTransform
            // 元素状态特效（死亡动画期间不再叠加）
            if (e.burnStacks > 0 && (time * 10f).toInt() % 2 == 0) {
                drawCircle(FIRE_C.copy(alpha = 0.25f), e.r + 2f, Offset(sx, sy))
            }
            if (e.slowStacks > 0 && e.frozen <= 0f) {
                drawCircle(ICE_C.copy(alpha = 0.12f * e.slowStacks), e.r + 2f, Offset(sx, sy))
            }
            if (e.frozen > 0f) {
                drawCircle(ICE_C.copy(alpha = 0.4f), e.r + 2f, Offset(sx, sy))
                // 冰晶：四根小刺
                for (d in listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))) {
                    drawLine(ICE_C, Offset(sx + d.x * e.r, sy + d.y * e.r), Offset(sx + d.x * (e.r + 7f), sy + d.y * (e.r + 7f)), 3f)
                }
            }
            if (e.hitFlash > 0f) {
                drawCircle(Color(0xAAFFFFFF), e.r + 1f, Offset(sx, sy))
            }
            if (e.hp < e.maxHp) {
                val w = e.r * 2f
                drawRect(BAR_BG, Offset(sx - e.r, sy - e.r - 10f), Size(w, 4f))
                drawRect(HP_C, Offset(sx - e.r, sy - e.r - 10f), Size(w * (e.hp / e.maxHp).coerceIn(0f, 1f), 4f))
            }
            // 精英词缀标识（头顶小菱形）：狂暴=红 / 护盾=冰蓝 / 分裂=绿 / 吸血=深红 / 再生=亮绿 / 霜环=青
            if (e.affix != null && !e.dying) {
                val c = when (e.affix) {
                    com.brainquest.game.game.core.CombatEngine.Affix.RAGE -> GamePalette.BOSS_GLOW
                    com.brainquest.game.game.core.CombatEngine.Affix.SHIELD -> GamePalette.ELEM_ICE
                    com.brainquest.game.game.core.CombatEngine.Affix.VAMPIRE -> Color(0xFFC2185B)
                    com.brainquest.game.game.core.CombatEngine.Affix.REGEN -> Color(0xFF9CCC65)
                    com.brainquest.game.game.core.CombatEngine.Affix.FROST -> Color(0xFF26C6DA)
                    else -> GamePalette.ENEMY_SLIME
                }
                val iy = sy - e.r - 20f
                drawLine(c, Offset(sx - 5f, iy), Offset(sx, iy - 5f), 3f)
                drawLine(c, Offset(sx, iy - 5f), Offset(sx + 5f, iy), 3f)
                drawLine(c, Offset(sx + 5f, iy), Offset(sx, iy + 5f), 3f)
                drawLine(c, Offset(sx, iy + 5f), Offset(sx - 5f, iy), 3f)
            }
        }
    }

    private fun DrawScope.drawSkeletonBody(sx: Float, sy: Float, r: Float, time: Float, seed: Float) {
        val bob = sin(time * 9f + seed) * 1.5f          // 骨架上下轻晃
        val armS = sin(time * 9f + seed) * 3f           // 肋骨横摆
        drawCircle(BONE_C, r * 0.62f, Offset(sx, sy - r * 0.5f + bob))
        drawCircle(GamePalette.PLAYER_OUTLINE, r * 0.62f, Offset(sx, sy - r * 0.5f + bob), style = Stroke(2f))
        drawCircle(Color(0xFFE53935), 2.5f, Offset(sx - 4f, sy - r * 0.55f + bob))
        drawCircle(Color(0xFFE53935), 2.5f, Offset(sx + 4f, sy - r * 0.55f + bob))
        for (i in 0..2) {
            val off = armS * (i + 1) * 0.3f
            drawLine(BONE_DARK, Offset(sx - r * 0.5f + off, sy + i * 8f - 4f + bob), Offset(sx + r * 0.5f + off, sy + i * 8f - 4f + bob), 3f)
        }
        drawRect(BONE_C, Offset(sx - 3f, sy - r * 0.1f + bob), Size(6f, r * 0.9f))
    }

    /** Boss 专属外观：黑甲武士——大躯干+王冠+发光眼，狂暴期变亮红 */
    private fun drawBossBody(scope: DrawScope, sx: Float, sy: Float, e: com.brainquest.game.game.core.CombatEngine.Enemy, time: Float) {
        val r = e.r
        val rage = e.phase >= 2
        val body = if (rage) androidx.compose.ui.graphics.lerp(Color(0xFF8E2424), GamePalette.BOSS_GLOW, 0.3f + 0.1f * sin(time * 10f)) else Color(0xFF6D2A2A)
        val dark = Color(0xFF3A1414)
        val sway = sin(time * 2f + e.wobbleSeed) * 3f
        scope.run {
            // 阴影
            drawOval(GamePalette.SHADOW, Offset(sx - 52f, sy + 22f), Size(104f, 24f))
            // 双肩甲
            drawCircle(dark, r * 0.32f, Offset(sx - r * 0.78f + sway, sy - r * 0.5f))
            drawCircle(dark, r * 0.32f, Offset(sx + r * 0.78f + sway, sy - r * 0.5f))
            // 躯干（黑甲圆角矩形）+ 描边
            drawRoundRect(body, Offset(sx - r * 0.72f + sway, sy - r * 0.85f), Size(r * 1.44f, r * 1.6f), CornerRadius(14f))
            drawRoundRect(GamePalette.PLAYER_OUTLINE, Offset(sx - r * 0.72f + sway, sy - r * 0.85f), Size(r * 1.44f, r * 1.6f), CornerRadius(14f), style = Stroke(3f))
            // 胸口纹章（金色菱形）
            val cx = sx + sway
            val cy = sy - r * 0.15f
            drawLine(GamePalette.UI_GOLD, Offset(cx - 8f, cy), Offset(cx, cy - 8f), 3f)
            drawLine(GamePalette.UI_GOLD, Offset(cx, cy - 8f), Offset(cx + 8f, cy), 3f)
            drawLine(GamePalette.UI_GOLD, Offset(cx + 8f, cy), Offset(cx, cy + 8f), 3f)
            drawLine(GamePalette.UI_GOLD, Offset(cx, cy + 8f), Offset(cx - 8f, cy), 3f)
            // 头（黑盔）+ 发光眼
            val headY = sy - r * 1.3f
            drawCircle(dark, r * 0.42f, Offset(cx, headY))
            drawCircle(GamePalette.PLAYER_OUTLINE, r * 0.42f, Offset(cx, headY), style = Stroke(2.5f))
            val eyeC = if (rage) GamePalette.BOSS_GLOW else Color(0xFFFF8A80)
            val pulse = 0.5f + 0.5f * sin(time * 6f)
            drawCircle(eyeC.copy(alpha = 0.35f + 0.2f * pulse), 7f, Offset(cx - 8f, headY - 2f))
            drawCircle(eyeC.copy(alpha = 0.35f + 0.2f * pulse), 7f, Offset(cx + 8f, headY - 2f))
            drawCircle(eyeC, 3.5f, Offset(cx - 8f, headY - 2f))
            drawCircle(eyeC, 3.5f, Offset(cx + 8f, headY - 2f))
            // 王冠（金色三尖）
            val crownY = headY - r * 0.42f
            drawRect(GamePalette.UI_GOLD, Offset(cx - 18f, crownY - 8f), Size(36f, 8f))
            drawLine(GamePalette.UI_GOLD, Offset(cx - 18f, crownY - 8f), Offset(cx - 14f, crownY - 20f), 5f)
            drawLine(GamePalette.UI_GOLD, Offset(cx, crownY - 8f), Offset(cx, crownY - 24f), 5f)
            drawLine(GamePalette.UI_GOLD, Offset(cx + 18f, crownY - 8f), Offset(cx + 14f, crownY - 20f), 5f)
        }
    }

    // ---------- 木桩身体（DUMMY 与 Boss 占位共用） ----------
    fun DrawScope.drawDummyBody(sx: Float, sy: Float, r: Float, time: Float, seed: Float, boss: Boolean) {
        val scale = if (boss) 1.7f else 1f
        val sway = sin(time * 2f + seed) * 2f * scale
        drawOval(SHADOW, Offset(sx - 20f * scale, sy + 12f * scale), Size(40f * scale, 12f * scale))
        drawOval(DUMMY_DARK, Offset(sx - 16f * scale, sy + 6f * scale), Size(32f * scale, 10f * scale))
        drawRect(DUMMY_WOOD, Offset(sx - 5f * scale + sway, sy - 30f * scale), Size(10f * scale, 38f * scale))
        drawRect(DUMMY_WOOD, Offset(sx - 22f * scale + sway, sy - 24f * scale), Size(44f * scale, 8f * scale))
        drawCircle(DUMMY_HEAD, 11f * scale, Offset(sx + sway, sy - 40f * scale))
        drawCircle(DUMMY_DARK, 11f * scale, Offset(sx + sway, sy - 40f * scale), style = Stroke(2f))
        if (boss) drawRect(Color(0xFFE15A5A), Offset(sx - 11f * scale + sway, sy - 48f * scale), Size(22f * scale, 7f * scale))
    }

    // ---------- 装备掉落物（品质色脉动菱形） ----------
    fun drawDrops(scope: DrawScope, game: DungeonGame, time: Float) {
        for (d in game.drops) {
            if (!d.alive) continue
            val sx = d.x - game.camX
            val sy = d.y - game.camY
            if (sx < -30f || sy < -30f || sx > scope.size.width + 30f || sy > scope.size.height + 30f) continue
            val c = Color(d.item.rarityColorLong)
            val pulse = 1f + sin(time * 5f + d.t) * 0.15f
            val r = 10f * pulse
            scope.drawLine(c, Offset(sx, sy - r), Offset(sx + r, sy), 4f)
            scope.drawLine(c, Offset(sx + r, sy), Offset(sx, sy + r), 4f)
            scope.drawLine(c, Offset(sx, sy + r), Offset(sx - r, sy), 4f)
            scope.drawLine(c, Offset(sx - r, sy), Offset(sx, sy - r), 4f)
            scope.drawCircle(SHADOW, 8f, Offset(sx, sy + 12f))
        }
    }

    // ---------- 宝箱与货摊（CHEST/SHOP 房心的交互实体） ----------
    /** 金币实体（小圆金币：铜边+高光，随时间轻微浮动） */
    fun drawCoins(scope: DrawScope, game: DungeonGame, time: Float) {
        for (c in game.engine.coinDrops) {
            val sx = c.x - game.camX
            val sy = c.y - game.camY + kotlin.math.sin(time * 6f + c.x * 0.05f) * 2f
            scope.drawCircle(Color(0xFFD97706), 7f, Offset(sx, sy))
            scope.drawCircle(Color(0xFFFDE68A), 2.8f, Offset(sx - 1.6f, sy - 1.6f))
            scope.drawCircle(Color(0xFFD97706), 7f, Offset(sx, sy), style = Stroke(1.5f))
        }
    }

    fun drawFixtures(scope: DrawScope, game: DungeonGame, time: Float) {
        game.chest?.let { c ->
            val sx = c.first - game.camX
            val sy = c.second - game.camY
            if (sx < -80f || sy < -80f || sx > scope.size.width + 80f || sy > scope.size.height + 80f) return
            scope.run {
                // 脉动金光 + 影子
                drawCircle(Color(0x33FFD54F), 46f + sin(time * 4f) * 6f, Offset(sx, sy))
                drawOval(SHADOW, Offset(sx - 24f, sy + 16f), Size(48f, 14f))
                // 箱体 + 金边 + 锁扣
                drawRoundRect(Color(0xFF795548), Offset(sx - 26f, sy - 14f), Size(52f, 34f), CornerRadius(6f))
                drawRoundRect(Color(0xFF5D4037), Offset(sx - 26f, sy - 14f), Size(52f, 12f), CornerRadius(6f))
                drawRect(GamePalette.UI_GOLD, Offset(sx - 26f, sy - 3f), Size(52f, 4f))
                drawRect(GamePalette.UI_GOLD, Offset(sx - 4f, sy - 6f), Size(8f, 12f))
                drawCircle(Color(0xFFFFF59D), 2.5f, Offset(sx, sy))
            }
        }
        game.shop?.let { s ->
            val sx = s.first - game.camX
            val sy = s.second - game.camY
            if (sx < -100f || sy < -100f || sx > scope.size.width + 100f || sy > scope.size.height + 100f) return
            scope.run {
                drawCircle(Color(0x337EE38A), 55f + sin(time * 3f) * 5f, Offset(sx, sy))
                drawOval(SHADOW, Offset(sx - 40f, sy + 22f), Size(80f, 16f))
                // 柜台
                drawRoundRect(Color(0xFF6D4C41), Offset(sx - 40f, sy - 6f), Size(80f, 26f), CornerRadius(5f))
                drawRect(Color(0xFF8D6E63), Offset(sx - 40f, sy - 6f), Size(80f, 7f))
                // 遮阳棚：红白条纹
                repeat(4) { i ->
                    val c = if (i % 2 == 0) Color(0xFFE15A5A) else Color(0xFFF5F5F5)
                    drawRect(c, Offset(sx - 40f + i * 20f, sy - 34f), Size(20f, 16f))
                }
                drawRect(Color(0xFF4E342E), Offset(sx - 40f, sy - 18f), Size(80f, 4f))
                // 金币招牌
                drawCircle(GamePalette.UI_GOLD, 9f, Offset(sx, sy - 48f))
                drawCircle(Color(0xFFFFF59D), 4f, Offset(sx, sy - 48f), style = Stroke(2f))
            }
        }
    }

    // ---------- 传送门（Boss 后：旋转漩涡） ----------
    fun drawPortal(scope: DrawScope, game: DungeonGame, time: Float) {
        val pt = game.portal ?: return
        val sx = pt.first - game.camX
        val sy = pt.second - game.camY
        scope.run {
            // 外圈光晕
            drawCircle(Color(0x33BA68C8), 60f + sin(time * 3f) * 5f, Offset(sx, sy))
            drawCircle(GamePalette.UI_COIN.copy(alpha = 0.5f), 34f, Offset(sx, sy), style = Stroke(5f))
            // 旋转漩涡：三段弧随时间转
            repeat(3) { i ->
                val a = time * 140f + i * 120f
                drawArc(GamePalette.UI_COIN, a, 100f, false, Offset(sx - 26f, sy - 26f), Size(52f, 52f), style = Stroke(4f))
            }
            drawCircle(Color(0xFFE1BEE7), 7f, Offset(sx, sy))
            // 靠近提示字由 Screen 层绘制（Compose 文本）
        }
    }

    // ---------- 子弹与经验球 ----------
    fun drawProjectiles(scope: DrawScope, game: DungeonGame) {
        for (b in game.engine.bullets) {
            if (!b.alive) continue
            val sx = b.x - game.camX
            val sy = b.y - game.camY
            if (sx < -20f || sy < -20f || sx > scope.size.width + 20f || sy > scope.size.height + 20f) continue
            val c = when (b.element) {
                com.brainquest.game.game.core.Element.FIRE -> FIRE_C
                com.brainquest.game.game.core.Element.ICE -> ICE_C
                com.brainquest.game.game.core.Element.THUNDER -> THUNDER_C
                else -> if (b.fromEnemy) ENEMY_BULLET else BULLET_P
            }
            // 弹道拖尾：沿速度反方向双段渐隐（弹丸更「有速度」）
            val vmag = kotlin.math.hypot(b.dx, b.dy)
            if (vmag > 1f) {
                val ux = b.dx / vmag; val uy = b.dy / vmag
                scope.drawLine(c.copy(alpha = 0.35f), Offset(sx - ux * b.r * 2.4f, sy - uy * b.r * 2.4f), Offset(sx, sy), 2.5f)
                scope.drawLine(c.copy(alpha = 0.16f), Offset(sx - ux * b.r * 5.2f, sy - uy * b.r * 5.2f), Offset(sx - ux * b.r * 2.4f, sy - uy * b.r * 2.4f), 2f)
            }
            scope.drawCircle(c, b.r, Offset(sx, sy))
        }
        for (o in game.engine.orbs) {
            if (!o.alive) continue
            val sx = o.x - game.camX
            val sy = o.y - game.camY
            if (sx < -20f || sy < -20f || sx > scope.size.width + 20f || sy > scope.size.height + 20f) continue
            scope.drawCircle(ORB_C, 6f, Offset(sx, sy))
            scope.drawCircle(Color(0xFF1B5E20), 2.5f, Offset(sx, sy))
        }
    }

    fun drawPlayer(scope: DrawScope, game: DungeonGame, time: Float) {
        val cls = game.cls ?: return
        // 无敌帧闪烁：10Hz 跳过绘制（借鉴幸存者模式）
        if (game.engine.invincible > 0f && (time * 10f).toInt() % 2 == 0) return
        val sx = game.engine.px - game.camX
        val sy = game.engine.py - game.camY
        val body0 = Color(cls.bodyColor)
        val accent0 = Color(cls.accentColor)
        // 装备外观：头盔/护甲/武器随品质变色
        val helmetC = game.slots[Equipment.Slot.HELMET]?.let { Color(it.rarityColorLong) }
        val weaponC = game.slots[Equipment.Slot.WEAPON]?.let { Color(it.rarityColorLong) } ?: accent0
        val armorC = game.slots[Equipment.Slot.ARMOR]?.let { Color(it.rarityColorLong) }
        val body = if (armorC != null) lerp(body0, armorC, 0.45f) else body0
        val accent = helmetC ?: accent0
        val swing = if (game.engine.moving) sin(game.engine.walkPhase) * 4f else 0f
        val breathe = if (!game.engine.moving) sin(time * 2.2f) * 1f else 0f

        // 朝向四象限：右/左（眼睛左右偏）、上（背面，不画眼）、下（正面）
        val fx = kotlin.math.cos(game.engine.facing)
        val fy = kotlin.math.sin(game.engine.facing)
        val facingRight = fx >= 0f
        val facingUp = fy < -0.5f

        // 0 角色微光（同心圆代替渐变笔刷，零分配）
        scope.drawCircle(Color(0x06FFFFFF), 95f, Offset(sx, sy))
        scope.drawCircle(Color(0x0AFFFFFF), 60f, Offset(sx, sy))
        // 1 阴影
        scope.drawOval(SHADOW, Offset(sx - 18f, sy + 14f), Size(36f, 10f))

        // 2 脚：两个小圆角矩形，走路交替
        scope.drawRoundRect(OUTLINE, Offset(sx - 10f, sy + 6f + swing), Size(9f, 12f), CornerRadius(4f))
        scope.drawRoundRect(OUTLINE, Offset(sx + 1f, sy + 6f - swing), Size(9f, 12f), CornerRadius(4f))

        // 3 身体：职业色圆角矩形 + 呼吸起伏 + 顶部高光 + 金腰带
        val bodyTop = sy - 20f + breathe
        scope.drawRoundRect(body, Offset(sx - 13f, bodyTop), Size(26f, 30f), CornerRadius(7f))
        scope.drawRect(Color(0x26FFFFFF), Offset(sx - 11f, bodyTop + 2f), Size(22f, 7f))   // 顶部高光
        scope.drawRect(GamePalette.UI_GOLD, Offset(sx - 13f, bodyTop + 19f), Size(26f, 3f))   // 腰带
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

        // 7.5 受击红闪 + 护盾光环
        if (game.engine.playerFlash > 0f) {
            scope.drawCircle(Color(0x66FF5252), 24f, Offset(sx, sy - 6f))
        }
        if (game.engine.shieldTime > 0f) {
            val pulse = 0.5f + 0.5f * sin(time * 5f)
            scope.drawCircle(GamePalette.ELEM_ICE.copy(alpha = 0.2f + 0.15f * pulse), 34f, Offset(sx, sy - 4f), style = Stroke(3f))
        }

        // 7 武器：跟手、随职业（右臂端点为支点，指向 facing）
        val handX = sx + 15f + swing * 0.6f
        val handY = bodyTop + 12f + swing * 0.6f
        // 挥砍时武器从 -60° 甩到 +60°（随 activeSlash 进度）
        val swingOff = game.engine.activeSlash?.let { sl ->
            if (sl.timer >= sl.windup && sl.timer <= sl.windup + 0.15f)
                Math.toDegrees((-60.0 + 120.0 * ((sl.timer - sl.windup) / 0.15f))).toFloat()
            else 0f
        } ?: 0f
        val deg = Math.toDegrees((game.engine.facing + sin(game.engine.walkPhase * 0.5f) * 0.2f).toDouble()).toFloat() + swingOff
        scope.rotate(deg, pivot = Offset(handX, handY)) {
            when (cls.id) {
                "knight" -> {
                    // 剑：护手 + 刃
                    drawRect(Color(0xFF8D6E63), Offset(handX - 2f, handY - 4f), Size(4f, 10f))
                    drawRect(weaponC, Offset(handX - 6f, handY - 8f), Size(12f, 3f))
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

    // ---------- HUD 数值条（供 Screen 的 Canvas 调用；dp 定位适配横竖屏） ----------
    fun drawBars(scope: DrawScope, game: DungeonGame, time: Float = 0f) {
        val barW = with(scope) { (scope.size.width * 0.20f).coerceAtMost(220.dp.toPx()) }
        val x = with(scope) { 12.dp.toPx() }
        val hpY = with(scope) { 42.dp.toPx() }
        val hpH = with(scope) { 8.dp.toPx() }
        val xpY = with(scope) { 54.dp.toPx() }
        val xpH = with(scope) { 4.dp.toPx() }
        // 面板底框（半透明圆角 + 亮边，游戏 HUD 风）+ 职业头像圆
        val panelH = with(scope) { 38.dp.toPx() }
        val avatarR = with(scope) { 13.dp.toPx() }
        scope.drawRoundRect(GamePalette.UI_PANEL, Offset(x - 8f, hpY - 14f), Size(barW + avatarR * 2 + 16f, panelH), CornerRadius(12f))
        scope.drawRoundRect(GamePalette.UI_PANEL_EDGE, Offset(x - 8f, hpY - 14f), Size(barW + avatarR * 2 + 16f, panelH), CornerRadius(12f), style = Stroke(1.5f))
        val bodyC = game.cls?.let { Color(it.bodyColor) } ?: GamePalette.UI_TEXT
        val acC = game.cls?.let { Color(it.accentColor) } ?: GamePalette.UI_GOLD
        scope.drawCircle(bodyC, avatarR, Offset(x + avatarR, hpY + panelH / 2f - 6f))
        scope.drawCircle(acC, avatarR * 0.55f, Offset(x + avatarR, hpY + panelH / 2f - 6f - avatarR * 0.45f))
        scope.drawCircle(OUTLINE, avatarR, Offset(x + avatarR, hpY + panelH / 2f - 6f), style = Stroke(2f))
        val bx = x + avatarR * 2 + 8f
        // 血条（权威数据在 engine）：白色残影显示刚掉的血，红色为当前；低血(<30%)时血条+边框红色呼吸脉冲（越低越明显）
        val hpFrac = if (game.engine.maxHp > 0) game.engine.hp.toFloat() / game.engine.maxHp else 1f
        val lowPulse = if (hpFrac < 0.30f) ((kotlin.math.sin(time * 6f) + 1f) / 2f) * (0.30f - hpFrac) / 0.30f else -1f
        scope.drawRoundRect(BAR_BG, Offset(bx, hpY), Size(barW, hpH), CornerRadius(hpH / 2))
        val ghostW = barW * (game.hpGhost / game.engine.maxHp).coerceIn(0f, 1f)
        val hpW = barW * hpFrac.coerceIn(0f, 1f)
        if (ghostW > hpW) scope.drawRoundRect(Color(0xAAFFFFFF), Offset(bx, hpY), Size(ghostW, hpH), CornerRadius(hpH / 2))
        val hpColor = if (lowPulse >= 0f) HP_C.copy(red = (HP_C.red + (1f - HP_C.red) * (0.3f + 0.5f * lowPulse)).coerceAtMost(1f)) else HP_C
        scope.drawRoundRect(hpColor, Offset(bx, hpY), Size(hpW, hpH), CornerRadius(hpH / 2))
        if (lowPulse >= 0f) {
            scope.drawRoundRect(
                Color(0xFFE15A5A).copy(alpha = 0.25f + 0.55f * lowPulse),
                Offset(bx - 3f, hpY - 3f), Size(barW + 6f, hpH + 6f), CornerRadius((hpH + 6f) / 2), style = Stroke(1.5f + 1.5f * lowPulse),
            )
        }
        // 经验条（真实数据）
        scope.drawRoundRect(BAR_BG, Offset(bx, xpY), Size(barW, xpH), CornerRadius(xpH / 2))
        scope.drawRoundRect(
            GamePalette.UI_EXP,
            Offset(bx, xpY),
            Size(barW * (game.engine.xp.toFloat() / game.engine.xpNext).coerceIn(0f, 1f), xpH),
            CornerRadius(xpH / 2),
        )
    }

    private val BAR_BG = GamePalette.UI_BAR_BG
    private val HP_C = GamePalette.UI_HP

    // ---------- 大厅人物立绘（放大版分层模型，无相机耦合） ----------
    fun drawPortrait(scope: DrawScope, cls: com.brainquest.game.game.dungeon.model.ClassDef, cx: Float, cy: Float, scale: Float, time: Float) {
        val body0 = Color(cls.bodyColor)
        val accent0 = Color(cls.accentColor)
        val breathe = sin(time * 2.2f) * 2f * scale
        val s = scale
        scope.run {
            // 地台光圈
            drawOval(GamePalette.SHADOW, Offset(cx - 46f * s, cy + 62f * s), Size(92f * s, 22f * s))
            drawCircle(accent0.copy(alpha = 0.10f), 88f * s, Offset(cx, cy + 20f * s))
            // 脚
            drawRoundRect(OUTLINE, Offset(cx - 22f * s, cy + 44f * s), Size(20f * s, 26f * s), CornerRadius(8f * s))
            drawRoundRect(OUTLINE, Offset(cx + 2f * s, cy + 44f * s), Size(20f * s, 26f * s), CornerRadius(8f * s))
            // 身体 + 高光 + 金腰带
            val bodyTop = cy - 34f * s + breathe
            drawRoundRect(body0, Offset(cx - 28f * s, bodyTop), Size(56f * s, 62f * s), CornerRadius(14f * s))
            drawRect(Color(0x26FFFFFF), Offset(cx - 24f * s, bodyTop + 4f * s), Size(48f * s, 14f * s))
            drawRect(GamePalette.UI_GOLD, Offset(cx - 28f * s, bodyTop + 40f * s), Size(56f * s, 6f * s))
            drawRoundRect(OUTLINE, Offset(cx - 28f * s, bodyTop), Size(56f * s, 62f * s), CornerRadius(14f * s), style = Stroke(2.5f))
            // 手臂
            drawRoundRect(body0, Offset(cx - 42f * s, bodyTop + 12f * s), Size(15f * s, 34f * s), CornerRadius(6f * s))
            drawRoundRect(body0, Offset(cx + 27f * s, bodyTop + 12f * s), Size(15f * s, 34f * s), CornerRadius(6f * s))
            // 头 + 头饰
            val headY = bodyTop - 26f * s
            drawCircle(GamePalette.PLAYER_SKIN, 24f * s, Offset(cx, headY))
            drawCircle(OUTLINE, 24f * s, Offset(cx, headY), style = Stroke(2.5f))
            when (cls.id) {
                "knight" -> {
                    drawArc(accent0, 180f, 180f, false, Offset(cx - 24f * s, headY - 24f * s), Size(48f * s, 44f * s), style = Stroke(10f))
                    drawRect(accent0, Offset(cx - 4f * s, headY - 48f * s), Size(8f * s, 16f * s))
                }
                "mage" -> {
                    drawRect(accent0, Offset(cx - 30f * s, headY - 26f * s), Size(60f * s, 10f * s))
                    drawLine(accent0, Offset(cx - 20f * s, headY - 26f * s), Offset(cx, headY - 66f * s), 16f)
                    drawLine(accent0, Offset(cx + 20f * s, headY - 26f * s), Offset(cx, headY - 66f * s), 16f)
                }
                "ranger" -> {
                    drawArc(accent0, -30f, 120f, false, Offset(cx - 26f * s, headY - 26f * s), Size(52f * s, 52f * s), style = Stroke(10f))
                }
            }
            // 眼睛（正面）
            drawCircle(OUTLINE, 4f * s, Offset(cx - 9f * s, headY - 2f * s))
            drawCircle(OUTLINE, 4f * s, Offset(cx + 9f * s, headY - 2f * s))
            // 武器（右手侧竖直）
            val wx = cx + 40f * s
            val wy = bodyTop + 26f * s
            when (cls.id) {
                "knight" -> {
                    drawRect(Color(0xFF8D6E63), Offset(wx - 4f * s, wy - 8f * s), Size(8f * s, 20f * s))
                    drawRect(accent0, Offset(wx - 13f * s, wy - 16f * s), Size(26f * s, 6f * s))
                    drawRect(Color(0xFFECEFF1), Offset(wx - 4f * s, wy - 72f * s), Size(8f * s, 56f * s))
                }
                "mage" -> {
                    drawRect(Color(0xFF8D6E63), Offset(wx - 4f * s, wy - 64f * s), Size(8f * s, 72f * s))
                    drawCircle(GamePalette.ELEM_FIRE, 10f * s, Offset(wx, wy - 68f * s))
                }
                "ranger" -> {
                    drawArc(Color(0xFF8D6E63), -80f, 160f, false, Offset(wx - 8f * s, wy - 38f * s), Size(16f * s, 76f * s), style = Stroke(6f))
                    drawLine(Color(0xFFECEFF1), Offset(wx, wy - 37f * s), Offset(wx, wy + 37f * s), 2.5f)
                }
            }
        }
    }
}
