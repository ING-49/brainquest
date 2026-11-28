package com.brainquest.game.game.dungeon.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
            // 批19：徽章挂在「远门」（朝邻房那侧的门）上方，与门位一致
            val farIsB = next === b
            val bx: Float; val by: Float
            if (a.gy == b.gy) {
                val l = minOf(game.roomLeft(a) + a.w, game.roomLeft(b) + b.w) - DungeonGame.DOOR_PROBE
                val r = maxOf(game.roomLeft(a), game.roomLeft(b)) + DungeonGame.DOOR_PROBE
                val cy = a.gy * DungeonGame.GRID_Y
                bx = (if (farIsB) r else l) - game.camX
                by = cy - DungeonGame.DOOR_H / 2 - 30f - game.camY
            } else {
                val t = minOf(game.roomTop(a) + a.h, game.roomTop(b) + b.h) - DungeonGame.DOOR_PROBE
                val bb = maxOf(game.roomTop(a), game.roomTop(b)) + DungeonGame.DOOR_PROBE
                val cx = a.gx * DungeonGame.GRID_X
                bx = cx - DungeonGame.DOOR_H / 2 - 30f - game.camX
                by = (if (farIsB) bb else t) - game.camY
            }
            drawDoorBadge(scope, next.type, bx, by)
        }
        // 门框绘制（批19）：门在走廊两端（即各房间的入口边上），锁门时门内能量屏障
        val DOOR_WOOD = Color(0xFF8D6E63)   // 门框木色（批19：高对比可辨认）
        fun doorFrameH(ex: Float, sy: Float, h: Float) {   // 横向走廊的门（门柱竖直、门楣水平）
            scope.drawRect(DOOR_WOOD, Offset(ex - 5f, sy - 14f), Size(10f, 16f))         // 上门柱
            scope.drawRect(DOOR_WOOD, Offset(ex - 5f, sy + h - 2f), Size(10f, 16f))     // 下门柱
            scope.drawRect(Color(0xFFBCAAA4), Offset(ex - 7f, sy - 16f), Size(14f, 4f))  // 门楣
            if (!open) {
                scope.drawRect(Color(0xCCE15A5A), Offset(ex - 3f, sy), Size(6f, h))     // 锁门屏障（亮红）
                scope.drawRect(Color(0x99FFFFFF), Offset(ex - 0.5f, sy), Size(1.5f, h)) // 屏障高光
            } else scope.drawRect(DOOR_GLOW, Offset(ex - 5f, sy), Size(10f, h))
        }
        fun doorFrameV(ex: Float, sy: Float, w: Float) {   // 纵向走廊的门
            scope.drawRect(DOOR_WOOD, Offset(ex - 5f, sy - 5f), Size(16f, 10f))
            scope.drawRect(DOOR_WOOD, Offset(ex + w - 11f, sy - 5f), Size(16f, 10f))
            scope.drawRect(Color(0xFFBCAAA4), Offset(ex - 7f, sy - 7f), Size(w + 14f, 4f))
            if (!open) {
                scope.drawRect(Color(0xCCE15A5A), Offset(ex, sy - 3f), Size(w, 6f))
                scope.drawRect(Color(0x99FFFFFF), Offset(ex, sy - 0.5f), Size(w, 1.5f))
            } else scope.drawRect(DOOR_GLOW, Offset(ex, sy - 5f), Size(w, 10f))
        }
        if (a.gy == b.gy) {
            val l = minOf(game.roomLeft(a) + a.w, game.roomLeft(b) + b.w) - DungeonGame.DOOR_PROBE
            val r = maxOf(game.roomLeft(a), game.roomLeft(b)) + DungeonGame.DOOR_PROBE
            val cy = a.gy * DungeonGame.GRID_Y
            val sx = l - game.camX
            val sy = cy - DungeonGame.DOOR_H / 2 - game.camY
            val w = r - l
            val h = DungeonGame.DOOR_H
            scope.drawRect(pal.floor, Offset(sx, sy), Size(w, h))   // 走廊地板
            doorFrameH(sx, sy, h)          // 左端门（房间 A 边上）
            doorFrameH(sx + w, sy, h)      // 右端门（房间 B 边上）
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
            doorFrameV(sx, sy, w)          // 上端门
            doorFrameV(sx, sy + h, w)      // 下端门
        }
    }

    /** 门后房间类型徽章：彩色圆 + 白色类型图形（战=交叉剑线 英=菱 箱=方 店=圆 王=红底白眼） */
    private fun drawDoorBadge(scope: DrawScope, type: RoomType, bx: Float, by: Float) {
        val (col, r) = when (type) {
            RoomType.BATTLE -> 0xFFE57373 to 13f
            RoomType.ELITE -> 0xFFFFD54F to 14f
            RoomType.CHEST -> 0xFFA1887F to 13f
            RoomType.EVENT -> 0xFFBA68C8 to 13f
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
            RoomType.EVENT -> {   // 骰子：白方块 + 黑点
                scope.drawRoundRect(Color.White, Offset(bx - 6f, by - 6f), Size(12f, 12f), CornerRadius(2f))
                scope.drawCircle(Color(0xFF263238), 1.4f, Offset(bx - 3f, by - 3f))
                scope.drawCircle(Color(0xFF263238), 1.4f, Offset(bx + 3f, by + 3f))
            }
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

        // 地板（批64 贴图化）：程序化砖块贴图 BitmapShader 平铺——每房 1 次 shader 调用，替代平涂+网格线
        val tex = pal.tex
        val step = 40f
        scope.drawIntoCanvas { cv ->
            cv.nativeCanvas.drawRoundRect(
                android.graphics.RectF(l, t, l + w, t + h), 18f, 18f,
                TileTex.floorPaint(game.floor, pal),
            )
        }
        // 批64：清房亮起过渡（峰终反馈）——0.8s 扩散亮环+地板增亮
        if (room.cleared && room.clearedAtWall > 0L) {
            val dt = (System.currentTimeMillis() - room.clearedAtWall) / 1000f
            if (dt < 0.8f) {
                val k = dt / 0.8f
                scope.drawRoundRect(Color.White.copy(alpha = (1f - k) * 0.25f), Offset(l, t), Size(w, h), CornerRadius(18f))
                scope.drawCircle(GamePalette.UI_GOLD.copy(alpha = (1f - k) * 0.8f), 120f + 480f * k, Offset(l + w / 2f, t + h / 2f), style = Stroke(6f * (1f - k) + 1.5f))
            }
        }
        // 无状态地板装饰（批64：降频让位贴图纹理，色随层 accent）——按 (房间网格, 格索引) hash，每格恒定
        run {
            var cx2 = l + step / 2f
            var col = 0
            while (cx2 < l + w - 8f) {
                var cy2 = t + step / 2f
                var row = 0
                while (cy2 < t + h - 8f) {
                    val hsh = (room.gx * 73856093) xor (room.gy * 19349663) xor (col * 83492791) xor (row * 29712150)
                    when ((hsh ushr 7) % 100) {
                        in 0..2 -> {   // 裂纹：三段折线（层裂纹色）
                            val cc = tex.crack.copy(alpha = 0.8f)
                            scope.drawLine(cc, Offset(cx2 - 9f, cy2 - 4f), Offset(cx2, cy2 + 2f), 1.5f)
                            scope.drawLine(cc, Offset(cx2, cy2 + 2f), Offset(cx2 + 8f, cy2 - 5f), 1.5f)
                        }
                        in 3..4 -> scope.drawCircle(Color(0x22FFFFFF), 3.2f, Offset(cx2, cy2))   // 碎石
                        in 5..6 -> {   // 骨头：短杆+两端节
                            val bc = Color(0x4DECEFF1)
                            scope.drawLine(bc, Offset(cx2 - 5f, cy2 + 3f), Offset(cx2 + 5f, cy2 - 3f), 2f)
                            scope.drawCircle(bc, 1.8f, Offset(cx2 - 5f, cy2 + 3f))
                            scope.drawCircle(bc, 1.8f, Offset(cx2 + 5f, cy2 - 3f))
                        }
                        in 7..9 -> {   // 层主题点缀（accent）
                            scope.drawCircle(tex.accent.copy(alpha = 0.30f), 4.5f, Offset(cx2, cy2))
                            scope.drawCircle(tex.accent.copy(alpha = 0.18f), 7f, Offset(cx2, cy2))
                        }
                        else -> {}
                    }
                    cy2 += step; row++
                }
                cx2 += step; col++
            }
        }
        // 批43：房型地面元素——进房/隔门望即感知类型（SHOP 金环 / EVENT 紫法阵 / CHEST 淡金光斑）
        val gcx = l + w / 2f
        val gcy = t + h / 2f
        when (room.type) {
            RoomType.SHOP -> {
                scope.drawCircle(Color(0x30FFC107), 200f, Offset(gcx, gcy))
                scope.drawCircle(Color(0x55FFC107), 160f, Offset(gcx, gcy), style = Stroke(6f))
            }
            RoomType.EVENT -> {
                scope.drawCircle(Color(0x407C4DFF), 190f, Offset(gcx, gcy), style = Stroke(5f))
                scope.drawCircle(Color(0x307C4DFF), 130f, Offset(gcx, gcy), style = Stroke(3f))
            }
            RoomType.CHEST -> {
                scope.drawCircle(Color(0x2AFFC107), 140f, Offset(gcx, gcy))
            }
            else -> {}
        }
        // 墙（描边）+ 墙顶亮边 + 墙体投影暗带（立体感）——批64：Boss 房墙描边慢脉动（未清时"门后有大家伙"）
        val wallC = if (room.type == RoomType.BOSS && !room.cleared)
            pal.wall.copy(alpha = 0.72f + 0.28f * (0.5f + 0.5f * sin(time * 2f)))
        else pal.wall
        scope.drawRoundRect(wallC, Offset(l, t), Size(w, h), CornerRadius(18f), style = Stroke(DungeonGame.WALL))
        scope.drawRect(GamePalette.BG_WALL_TOP, Offset(l + DungeonGame.WALL, t + DungeonGame.WALL), Size(w - DungeonGame.WALL * 2, 6f))
        // 批64：墙带内砖纹（横缝 12px + 错缝竖 joint——贴墙语言）
        run {
            val mortar = tex.grout.copy(alpha = 0.55f)
            var wy = t + DungeonGame.WALL + 12f
            var wi = 0
            while (wy < t + h - DungeonGame.WALL - 8f) {
                scope.drawLine(mortar, Offset(l + 3f, wy), Offset(l + w - 3f, wy), 1.5f)
                var jx = l + (if (wi % 2 == 0) 30f else 62f)
                while (jx < l + w - 4f) {
                    scope.drawLine(mortar, Offset(jx, wy - 12f), Offset(jx, wy), 1.5f)
                    jx += 64f
                }
                wy += 12f; wi++
            }
        }
        // 批34：房间内阴影补全——光从上来：顶边亮带保留（原有），左/右/底加暗带。
        // 房间=下沉的坑，四边都有暗带才有"凹陷感"，此前只有顶边（1/4）
        scope.drawRect(Color(0x30000000), Offset(l + DungeonGame.WALL, t + DungeonGame.WALL + 6f), Size(w - DungeonGame.WALL * 2, 24f))
        scope.drawRect(Color(0x1C000000), Offset(l + DungeonGame.WALL, t + DungeonGame.WALL + 6f), Size(20f, h - DungeonGame.WALL * 2 - 6f))
        scope.drawRect(Color(0x1C000000), Offset(l + w - DungeonGame.WALL - 20f, t + DungeonGame.WALL + 6f), Size(20f, h - DungeonGame.WALL * 2 - 6f))
        scope.drawRect(Color(0x24000000), Offset(l + DungeonGame.WALL + 20f, t + h - DungeonGame.WALL - 20f), Size(w - DungeonGame.WALL * 2 - 40f, 20f))
        // 批64：南墙挤出立面（面向镜头的砖纹墙脚）——底边南邻接时跳过门口段
        run {
            val faceH = 20f
            val fy = t + h + DungeonGame.WALL / 2f
            val faceC = tex.lo
            val hasSouth = room.neighbors.containsKey(Dir.DOWN)
            fun faceSeg(x0: Float, x1: Float) {
                val wSeg = x1 - x0
                if (wSeg <= 4f) return
                scope.drawRect(faceC, Offset(x0, fy), Size(wSeg, faceH))
                scope.drawLine(tex.grout.copy(alpha = 0.6f), Offset(x0, fy + faceH * 0.4f), Offset(x1, fy + faceH * 0.4f), 1.5f)
                scope.drawLine(tex.grout.copy(alpha = 0.6f), Offset(x0, fy + faceH * 0.75f), Offset(x1, fy + faceH * 0.75f), 1.5f)
                var jx = x0 + 26f
                var k = 0
                while (jx < x1 - 8f) {
                    scope.drawLine(tex.grout.copy(alpha = 0.45f), Offset(jx, fy + (if (k % 2 == 0) 0f else faceH * 0.4f)), Offset(jx, fy + (if (k % 2 == 0) faceH * 0.4f else faceH)), 1.5f)
                    jx += 44f; k++
                }
                scope.drawLine(Color(0x26FFFFFF), Offset(x0, fy), Offset(x1, fy), 1.5f)   // 顶面亮线
            }
            val doorL = l + w / 2f - DungeonGame.DOOR_H / 2f
            val doorR = l + w / 2f + DungeonGame.DOOR_H / 2f
            if (hasSouth) {
                faceSeg(l + DungeonGame.WALL / 2f, doorL)
                faceSeg(doorR, l + w - DungeonGame.WALL / 2f)
            } else {
                faceSeg(l + DungeonGame.WALL / 2f, l + w - DungeonGame.WALL / 2f)
            }
        }
        // 批64：墙面火把——沿墙 hash 等距（避门口），壁架+双层火苗(sin(time*13f) 闪烁)+暖光晕+熏黑；
        // Boss 房未清改红光；战斗聚焦（未清战斗房火苗亮度略降）
        run {
            val space = 420f
            fun torch(x: Float, y: Float, seed: Int) {
                val hs = (room.gx * 83492791) xor (room.gy * 29712150) xor seed * 126271
                val fl = 0.72f + 0.28f * (0.5f + 0.5f * sin(time * 13f + (hs % 628) / 100f))
                val dim = if (room.cleared || room.type == RoomType.START) 1f else 0.7f
                val warm = if (room.type == RoomType.BOSS && !room.cleared) Color(0x30E15A5A) else Color(0x2AFFB300)
                scope.drawCircle(warm, 58f * fl, Offset(x, y))                                     // 暖光晕
                scope.drawCircle(Color(0x14000000), 30f, Offset(x, y))                             // 墙面熏黑
                scope.drawRoundRect(Color(0xFF4E342E), Offset(x - 7f, y - 2f), Size(14f, 5f), CornerRadius(2f))   // 壁架
                scope.drawOval(Color(0xFFFFB300).copy(alpha = 0.85f * fl * dim), Offset(x - 5f, y - 20f + (1f - fl) * 3f), Size(10f, 18f * fl))   // 火苗外层
                scope.drawOval(Color(0xFFFFF59D).copy(alpha = 0.9f * fl * dim), Offset(x - 2.5f, y - 13f + (1f - fl) * 2f), Size(5f, 10f * fl))   // 火苗内芯
            }
            val doorHalf = DungeonGame.DOOR_H / 2f + 60f
            var tx = l + 220f
            while (tx < l + w - 160f) {
                val nearDoor = room.neighbors.containsKey(Dir.UP) && kotlin.math.abs(tx - (l + w / 2f)) < doorHalf
                if (!nearDoor) torch(tx, t + DungeonGame.WALL / 2f + 4f, tx.toInt())
                tx += space
            }
            var bx2 = l + 220f
            while (bx2 < l + w - 160f) {
                val nearDoor = room.neighbors.containsKey(Dir.DOWN) && kotlin.math.abs(bx2 - (l + w / 2f)) < doorHalf
                if (!nearDoor) torch(bx2, t + h - DungeonGame.WALL / 2f - 2f, bx2.toInt() + 7)
                bx2 += space
            }
            var ly = t + 320f
            while (ly < t + h - 200f) {
                val nearDoorL = room.neighbors.containsKey(Dir.LEFT) && kotlin.math.abs(ly - (t + h / 2f)) < doorHalf
                if (!nearDoorL) torch(l + DungeonGame.WALL / 2f + 4f, ly, ly.toInt() + 13)
                val nearDoorR = room.neighbors.containsKey(Dir.RIGHT) && kotlin.math.abs(ly - (t + h / 2f)) < doorHalf
                if (!nearDoorR) torch(l + w - DungeonGame.WALL / 2f - 4f, ly, ly.toInt() + 29)
                ly += space * 1.6f
            }
        }
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

    /** 批64：房外氛围层（画在走廊/房间之下，只在外部黑暗区可见）——远景石柱剪影（视差 0.5）+ hash 钉扎的远处火光 */
    fun drawAmbient(scope: DrawScope, game: DungeonGame, time: Float) {
        val vw = scope.size.width
        val vh = scope.size.height
        // 远景剪影：220px 网格 hash，视差 0.5（camX*0.5——比场景"更远"，移动更慢）
        run {
            val px = game.camX * 0.5f
            val py = game.camY * 0.5f
            val cell = 220f
            var cx = -(px % cell).let { if (it < 0) it + cell else it }
            var col = (px / cell).toInt()
            while (cx < vw) {
                var cy = -(py % cell).let { if (it < 0) it + cell else it }
                var row = (py / cell).toInt()
                while (cy < vh) {
                    val hsh = (col * 73856093) xor (row * 19349663)
                    if ((hsh ushr 5) % 100 < 26) {
                        val jx = cx + (hsh % 90)
                        val jy = cy + ((hsh ushr 8) % 120)
                        val ph = 60f + (hsh ushr 12) % 90f
                        val c = Color(0x0AFFFFFF)
                        scope.drawLine(c, Offset(jx, jy + ph), Offset(jx + ph * 0.22f, jy), 7f)
                        scope.drawLine(c, Offset(jx + ph * 0.22f, jy), Offset(jx + ph * 0.4f, jy + ph), 7f)
                    }
                    cy += cell; row++
                }
                cx += cell; col++
            }
        }
        // 远处火光：640px 网格 hash 钉扎（视差 1，世界锚定），暖金闪烁
        run {
            val cell = 640f
            var cx = -(game.camX % cell).let { if (it < 0) it + cell else it }
            var col = (game.camX / cell).toInt()
            while (cx < vw) {
                var cy = -(game.camY % cell).let { if (it < 0) it + cell else it }
                var row = (game.camY / cell).toInt()
                while (cy < vh) {
                    val hsh = (col * 83492791) xor (row * 29712150)
                    if ((hsh ushr 6) % 100 < 18) {
                        val fl = 0.5f + 0.5f * sin(time * 7f + (hsh % 628) / 100f)
                        scope.drawCircle(Color(0x1AFFB300).copy(alpha = 0.5f + 0.5f * fl), 26f + fl * 10f, Offset(cx + 60f, cy + 40f))
                        scope.drawCircle(Color(0x2AFFB74D), 8f + fl * 3f, Offset(cx + 60f, cy + 40f))
                    }
                    cy += cell; row++
                }
                cx += cell; col++
            }
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
    fun drawEnemy(scope: DrawScope, game: DungeonGame, e: com.brainquest.game.game.core.CombatEngine.Enemy, time: Float, spritesOn: Boolean = false) {
        val sx = e.x - game.camX
        val sy = e.y - game.camY
        if (sx < -60f || sy < -60f || sx > scope.size.width + 60f || sy > scope.size.height + 60f) return
        // 死亡动画：先弹大再缩没（deathTimer 0.3 → 0）
        val dieK = if (e.dying) (e.deathTimer / 0.3f).coerceIn(0f, 1f) else 1f
        if (dieK <= 0.02f) return
        val spawnK = e.spawnPop   // 出生弹入（批16）：1→0，从小弹出
        val popScale = when {
            e.dying -> 0.4f + 1.1f * dieK * dieK
            spawnK > 0f -> 0.35f + 0.75f * (1f - spawnK) + 0.18f * spawnK * (1f - spawnK) * 4f * 0.5f + 0.18f * e.hitSquash
            else -> 1f + 0.18f * e.hitSquash   // 受击 pop（squash&stretch）
        }
        if (spawnK > 0.5f) {   // 出生落地白环（批16）
            scope.drawCircle(Color.White.copy(alpha = 0.5f * (spawnK - 0.5f) * 2f), e.r + 14f * (1.5f - spawnK), Offset(sx, sy), style = Stroke(3f))
        }
        // 全种类精灵帧动画（批17：0x72 CC0；Boss 走大恶魔轮换；否则矢量）
        if (spritesOn && SpriteAnims.ready) {
            val spriteName = when {
                e.bossFloor > 0 -> listOf("big_demon", "chort", "big_zombie", "ogre")[(e.bossFloor - 1).mod(4)]
                else -> SpriteAnims.KIND_NAMES[e.kind.name]
            }
            if (spriteName != null) {
                // 批54：移动步态——非前摇状态下播 run 帧（13 精灵的 run 动画全活）
                val frames = SpriteAnims.frames(
                    spriteName,
                    run = e.hitSquash > 0f || e.kbT > 0f || (!e.dormant && e.atkState == 0),
                )
                if (frames.isNotEmpty()) {
                    val (fw, fh) = SpriteAnims.firstSize(frames)
                    val fi = ((time * (if (e.atkState == 1) 12f else 6f) + e.wobbleSeed).toInt()).mod(frames.size)
                    val frame = frames[fi]
                    val dsize = (e.r * (if (e.bossFloor > 0) 2.4f else 2.3f)).toInt()
                    val flip = game.engine.px < e.x
                    // 攻击姿态（批18/批20）：前摇放大+倾身；Boss 冲刺预警（dashWarn）同样倾身+更大放大
                    val atkLean = when {
                        e.bossFloor > 0 && e.dashWarn > 0f -> Offset(e.atkDirX * 6f, e.atkDirY * 6f)
                        e.atkState == 1 -> {
                            val dx = game.engine.px - e.x; val dy = game.engine.py - e.y
                            val dl = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                            Offset(dx / dl * 3f, dy / dl * 3f)
                        }
                        else -> Offset.Zero
                    }
                    val atkGrow = when {
                        e.bossFloor > 0 && e.dashWarn > 0f -> 1.12f
                        e.atkState == 1 -> 1.06f
                        else -> 1f
                    }
                    scope.withTransform({
                        if (flip) scale(-atkGrow, atkGrow, pivot = Offset(sx, sy)) else scale(atkGrow, atkGrow, pivot = Offset(sx, sy))
                    }) {
                        scope.drawImage(
                            frame,
                            srcOffset = IntOffset(0, 0), srcSize = IntSize(fw, fh),
                            dstOffset = IntOffset((sx + atkLean.x - dsize / 2).toInt(), (sy + atkLean.y - dsize / 2).toInt()),
                            dstSize = IntSize(dsize, dsize),
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                        )
                    }
                    if (e.atkState == 2 && e.atkTimer > 0f && e.bossFloor == 0) {   // 爪击弧
                        val ka = (e.atkTimer / 0.5f).coerceIn(0f, 1f)
                        val toP = Math.toDegrees(kotlin.math.atan2(game.engine.py - e.y, game.engine.px - e.x).toDouble()).toFloat()
                        scope.drawArc(Color.White.copy(alpha = 0.7f * ka), toP - 50f, 100f, false,
                            Offset(sx - e.r - 6f, sy - e.r - 6f), Size((e.r + 6f) * 2f, (e.r + 6f) * 2f), style = Stroke(3f * ka + 1f))
                    }
                    if (e.atkState == 1 && !e.dying) {
                        val pulse = sin(time * 22f) * 3f
                        scope.drawCircle(Color(0x88FFEB3B), e.r + 6f + pulse, Offset(sx, sy), style = Stroke(3f))
                    }
                    if (e.hitFlash > 0f) scope.drawCircle(Color(0xAAFFFFFF), e.r + 1f, Offset(sx, sy))
                    if (e.bossFloor > 0 && e.phase >= 2) scope.drawCircle(Color(0x33FF5252), e.r + 10f, Offset(sx, sy))
                    if (e.bossFloor > 0 && e.dashWarn > 0f) {   // 冲刺蓄能弧（批20-B）：沿冲刺方向渐亮
                        val ka = 1f - e.dashWarn / 0.45f
                        val toP = Math.toDegrees(kotlin.math.atan2(e.atkDirY.toDouble(), e.atkDirX.toDouble())).toFloat()
                        scope.drawArc(Color(0xFFE15A5A).copy(alpha = 0.3f + 0.6f * ka), toP - 70f, 140f, false,
                            Offset(sx - e.r - 12f, sy - e.r - 12f), Size((e.r + 12f) * 2f, (e.r + 12f) * 2f), style = Stroke(5f * ka + 1f))
                    }
                    val hb = (e.hp / e.maxHp).coerceIn(0f, 1f)
                    if (hb < 1f && !e.dying) {
                        scope.drawRoundRect(Color(0x66000000), Offset(sx - e.r, sy - e.r - 8f), Size(e.r * 2f, 4f), CornerRadius(2f))
                        scope.drawRoundRect(if (hb > 0.4f) Color(0xFF7EE38A) else Color(0xFFE15A5A), Offset(sx - e.r, sy - e.r - 8f), Size(e.r * 2f * hb, 4f), CornerRadius(2f))
                    }
                    return
                }
            }
        }
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
        // 批43：盔甲色相按层轮换（深渊红/腐沼绿/炎狱橙/寒渊蓝），狂暴期整体向 BOSS_GLOW 变亮
        val floorArmor = listOf(
            Color(0xFF6D2A2A) to Color(0xFF3A1414),   // 深渊领主
            Color(0xFF2E5333) to Color(0xFF14261A),   // 腐沼之主
            Color(0xFF7A3212) to Color(0xFF3A1A0A),   // 炎狱魔将
            Color(0xFF1F4E79) to Color(0xFF0D2438),   // 寒渊暴君
        )[(e.bossFloor - 1).mod(4)]
        val body = if (rage) androidx.compose.ui.graphics.lerp(floorArmor.first, GamePalette.BOSS_GLOW, 0.3f + 0.1f * sin(time * 10f)) else floorArmor.first
        val dark = floorArmor.second
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
            // 批46：按层形态分化——腐沼触须(2) / 炎狱双角(3) / 寒渊冰晶(4)；深渊领主(1)为基础形态
            val sway2 = sway
            when ((e.bossFloor - 1).mod(4)) {
                1 -> {   // 腐沼之主：体侧两条摆动触须
                    for (t in 0..1) {
                        val side = if (t == 0) -1f else 1f
                        val bx = sx + side * r * 0.75f + sway2
                        val by = sy - r * 0.1f
                        val wag = sin(time * 3f + t * 2.1f) * r * 0.18f
                        drawLine(dark, Offset(bx, by), Offset(bx + side * r * 0.42f, by + r * 0.18f + wag), 7f)
                        drawLine(dark, Offset(bx + side * r * 0.42f, by + r * 0.18f + wag), Offset(bx + side * r * 0.6f, by + r * 0.42f + wag * 1.6f), 5f)
                    }
                }
                2 -> {   // 炎狱魔将：双角（头两侧上翘三角线）
                    val hx = cx
                    val hy = headY - r * 0.30f
                    drawLine(Color(0xFFFF7043), Offset(hx - r * 0.30f, hy), Offset(hx - r * 0.5f, hy - r * 0.5f), 6f)
                    drawLine(Color(0xFFFF7043), Offset(hx + r * 0.30f, hy), Offset(hx + r * 0.5f, hy - r * 0.5f), 6f)
                    drawLine(Color(0xFFFFAB91), Offset(hx - r * 0.30f, hy), Offset(hx - r * 0.44f, hy - r * 0.34f), 3f)
                    drawLine(Color(0xFFFFAB91), Offset(hx + r * 0.30f, hy), Offset(hx + r * 0.44f, hy - r * 0.34f), 3f)
                }
                3 -> {   // 寒渊暴君：周身旋转冰晶（四个小菱形环绕）
                    for (k in 0..3) {
                        val ang = time * 1.3f + k * 1.5707963f
                        val ix = cx + kotlin.math.cos(ang) * r * 1.05f
                        val iy = sy + sin(ang) * r * 0.85f
                        drawLine(Color(0xFF80DEEA), Offset(ix, iy - 9f), Offset(ix + 7f, iy), 3f)
                        drawLine(Color(0xFF80DEEA), Offset(ix + 7f, iy), Offset(ix, iy + 9f), 3f)
                        drawLine(Color(0xFF80DEEA), Offset(ix, iy + 9f), Offset(ix - 7f, iy), 3f)
                        drawLine(Color(0xFF80DEEA), Offset(ix - 7f, iy), Offset(ix, iy - 9f), 3f)
                    }
                }
            }
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
            // 批51：品质色光柱（向上渐隐）+ 菱形缓旋——掉落更有存在感
            scope.drawRect(
                Brush.verticalGradient(listOf(c.copy(alpha = 0.05f), c.copy(alpha = 0.30f))),
                Offset(sx - 7f, sy - 90f), Size(14f, 90f),
            )
            scope.withTransform({
                translate(sx, sy)
                rotate(time * 1.2f, pivot = Offset.Zero)
            }) {
                scope.drawLine(c, Offset(0f, -r), Offset(r, 0f), 4f)
                scope.drawLine(c, Offset(r, 0f), Offset(0f, r), 4f)
                scope.drawLine(c, Offset(0f, r), Offset(-r, 0f), 4f)
                scope.drawLine(c, Offset(-r, 0f), Offset(0f, -r), 4f)
            }
            scope.drawCircle(SHADOW, 8f, Offset(sx, sy + 12f))
        }
    }

    // ---------- 宝箱与货摊（CHEST/SHOP 房心的交互实体） ----------
    /** 金币实体（小圆金币：铜边+高光，随时间轻微浮动） */
    fun drawCoins(scope: DrawScope, game: DungeonGame, time: Float) {
        for (c in game.engine.coinDrops) {
            val sx = c.x - game.camX
            val sy = c.y - game.camY + kotlin.math.sin(time * 6f + c.x * 0.05f) * 2f
            // 批51：硬币翻转（宽度按 cos 收缩模拟旋转）+ 磁吸拖尾短线
            val spin = kotlin.math.cos(time * 7f + c.x * 0.13f)
            val rw = 7f * kotlin.math.abs(spin).coerceAtLeast(0.25f)
            if (c.magnet) {
                val dx = game.engine.px - c.x; val dy = game.engine.py - c.y
                val dl = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                scope.drawLine(Color(0x66FDE68A), Offset(sx, sy), Offset(sx + dx / dl * 26f, sy + dy / dl * 26f), 3f)
            }
            scope.drawOval(Color(0xFFD97706), Offset(sx - rw, sy - 7f), Size(rw * 2, 14f))
            scope.drawOval(Color(0xFFFDE68A), Offset(sx - rw * 0.4f, sy - 2.8f), Size(rw * 0.8f, 5.6f))
            scope.drawOval(Color(0xFFD97706), Offset(sx - rw, sy - 7f), Size(rw * 2, 14f), style = Stroke(1.5f))
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
            // 箱体 + 金边 + 锁扣——批64：箱盖微颤 + 锁扣偶发星光
            drawRoundRect(Color(0xFF795548), Offset(sx - 26f, sy - 14f), Size(52f, 34f), CornerRadius(6f))
            drawRoundRect(Color(0xFF5D4037), Offset(sx - 26f, sy - 14f + kotlin.math.sin(time * 9f) * 1.2f), Size(52f, 12f), CornerRadius(6f))
            drawRect(GamePalette.UI_GOLD, Offset(sx - 26f, sy - 3f), Size(52f, 4f))
            drawRect(GamePalette.UI_GOLD, Offset(sx - 4f, sy - 6f), Size(8f, 12f))
            drawCircle(Color(0xFFFFF59D), 2.5f, Offset(sx, sy))
            if (sin(time * 2.3f) > 0.88f) {
                val sp = (sin(time * 2.3f) - 0.88f) / 0.12f
                drawLine(Color.White.copy(alpha = 0.8f * sp), Offset(sx - 8f, sy - 12f), Offset(sx + 8f, sy - 4f), 1.5f)
                drawLine(Color.White.copy(alpha = 0.8f * sp), Offset(sx + 8f, sy - 12f), Offset(sx - 8f, sy - 4f), 1.5f)
            }
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
        // 遮阳棚：红白条纹——批64：布幔 sway（逐条微移，宽 22f 重叠防露缝）
        repeat(4) { i ->
            val c = if (i % 2 == 0) Color(0xFFE15A5A) else Color(0xFFF5F5F5)
            drawRect(c, Offset(sx - 40f + i * 20f + sin(time * 2f + i) * 1.4f, sy - 34f), Size(22f, 16f))
        }
                drawRect(Color(0xFF4E342E), Offset(sx - 40f, sy - 18f), Size(80f, 4f))
                // 金币招牌
                drawCircle(GamePalette.UI_GOLD, 9f, Offset(sx, sy - 48f))
                drawCircle(Color(0xFFFFF59D), 4f, Offset(sx, sy - 48f), style = Stroke(2f))
            }
        }
        game.eventSpot?.let { ev ->
            val sx = ev.first - game.camX
            val sy = ev.second - game.camY
            if (sx < -100f || sy < -100f || sx > scope.size.width + 100f || sy > scope.size.height + 100f) return@let
            scope.run {
                // 紫色神秘光晕脉动
                drawCircle(Color(0x33BA68C8), 55f + sin(time * 3f) * 6f, Offset(sx, sy))
                drawOval(SHADOW, Offset(sx - 34f, sy + 20f), Size(68f, 14f))
                // 圆桌
                drawCircle(Color(0xFF5D4037), 30f, Offset(sx, sy))
                drawCircle(Color(0xFF795548), 25f, Offset(sx, sy))
                drawCircle(Color(0xFF3E2723), 30f, Offset(sx, sy), style = Stroke(3f))
        // 桌上骰子两枚（红白）——批64：骰子 idle 微滚
        scope.withTransform({ rotate(sin(time * 1.5f) * 0.18f, pivot = Offset(sx - 8f, sy - 2f)) }) {
            drawRoundRect(Color(0xFFE15A5A), Offset(sx - 14f, sy - 8f), Size(12f, 12f), CornerRadius(3f))
            drawCircle(Color.White, 1.5f, Offset(sx - 10f, sy - 4f))
        }
        drawRoundRect(Color(0xFFF5F5F5), Offset(sx + 2f, sy - 4f), Size(12f, 12f), CornerRadius(3f))
        drawCircle(Color(0xFF263238), 1.5f, Offset(sx + 5f, sy))
        drawCircle(Color(0xFF263238), 1.5f, Offset(sx + 9f, sy + 3f))
        // 顶部紫水晶——批64：呼吸
        drawCircle(Color(0xFFBA68C8), 8f + sin(time * 2.5f) * 1.5f, Offset(sx, sy - 44f))
        drawCircle(Color(0xFFE1BEE7), 3.5f, Offset(sx, sy - 44f))
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

    fun drawPlayer(scope: DrawScope, game: DungeonGame, time: Float, spritesOn: Boolean = false) {
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

        // 精灵玩家（批17：knight_m idle/run/hit 帧动画；early-return 跳过矢量身体）
        if (spritesOn && SpriteAnims.ready) {
            val facingRight = kotlin.math.cos(game.engine.facing) >= 0f
            val clsSprite = SpriteAnims.classSprite(cls.id)
            val framesP = when {
                game.engine.playerFlash > 0f -> SpriteAnims.classHit(cls.id)
                game.engine.moving -> SpriteAnims.frames(clsSprite, run = true)
                else -> SpriteAnims.frames(clsSprite, run = false)
            }
            if (framesP.isNotEmpty()) {
                val (fw, fh) = SpriteAnims.firstSize(framesP)
                val fi = ((time * (if (game.engine.moving) 10f else 5f)).toInt()).mod(framesP.size)
                val frame = framesP[fi]
                val dsize = 46
                val flip = !facingRight
                val flick = 1f + sin(time * 11f) * 0.04f + sin(time * 23f + 1.7f) * 0.03f   // 批64：火把呼吸
                scope.drawCircle(Color(0x06FFFFFF), 95f * flick, Offset(sx, sy))
                scope.drawCircle(Color(0x0AFFFFFF), 60f * flick, Offset(sx, sy))
                scope.drawOval(SHADOW, Offset(sx - 18f, sy + 14f), Size(36f, 10f))
                // 出手前冲（批17）：刚出手瞬间向面向位移，攻击可见
                val atkProgP = if (game.engine.attackInterval > 0f)
                    (1f - game.engine.attackTimer / game.engine.attackInterval).coerceIn(0f, 1f) else 0f
                val lunge = ((1f - atkProgP) * 5f).coerceIn(0f, 5f)
                val lx = sx + kotlin.math.cos(game.engine.facing) * lunge
                val ly = sy + kotlin.math.sin(game.engine.facing) * lunge * 0.5f
                scope.withTransform({
                    if (flip) scale(-1f, 1f, pivot = Offset(sx, sy))
                    if (game.engine.moving) rotate(sin(game.engine.walkPhase) * 3f, pivot = Offset(sx, sy))
                }) {
                    scope.drawImage(
                        frame,
                        srcOffset = IntOffset(0, 0), srcSize = IntSize(fw, fh),
                        dstOffset = IntOffset((lx - dsize / 2).toInt(), (ly - dsize / 2 - 4).toInt()),
                        dstSize = IntSize(dsize, dsize),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                    )
                }
                scope.drawCircle(accent, 3.5f, Offset(sx, sy - 32f))   // 职业色徽标
                // 攻击三段姿态叠加（批18：蓄力后仰 → 挥击武器 → 收招）
                if (atkProgP > 0.78f) {   // 蓄力：后仰 + 微放大
                    scope.withTransform({ rotate(-6f, pivot = Offset(sx, sy)) }) {
                        scope.drawCircle(accent.copy(alpha = 0.25f), 20f, Offset(sx, sy - 8f), style = Stroke(2f))
                    }
                } else if (lunge > 0.5f) {   // 挥击：职业武器动作
                    val fx = kotlin.math.cos(game.engine.facing)
                    val fy = kotlin.math.sin(game.engine.facing)
                    val k = (lunge / 5f).coerceIn(0f, 1f)
                    when (cls.id) {
                        "knight" -> {   // 白色挥砍弧（沿面向扫过）
                            scope.drawArc(
                                Color.White.copy(alpha = 0.75f * k),
                                Math.toDegrees(game.engine.facing.toDouble()).toFloat() - 55f, 110f, false,
                                Offset(lx - 30f, ly - 30f), Size(60f, 60f), style = Stroke(5f * k + 1f),
                            )
                        }
                        "mage" -> {   // 法杖前戳 + 宝珠闪光
                            scope.drawCircle(Color(0x99FFD54F).copy(alpha = 0.8f * k), 7f + 5f * k, Offset(lx + fx * 16f, ly + fy * 16f - 6f))
                            scope.drawCircle(Color.White.copy(alpha = 0.9f * k), 3.5f, Offset(lx + fx * 16f, ly + fy * 16f - 6f))
                        }
                        else -> {   // 游侠：弓弦释放 + 箭影
                            scope.drawLine(Color(0xCCFFFFFF).copy(alpha = 0.8f * k),
                                Offset(lx, ly - 4f), Offset(lx + fx * 34f, ly + fy * 34f - 4f), 2.5f)
                            scope.drawCircle(Color.White.copy(alpha = 0.7f * k), 3f, Offset(lx + fx * 20f, ly + fy * 20f - 4f))
                        }
                    }
                }
                if (game.engine.playerFlash > 0f) scope.drawCircle(Color(0x66FF5252), 24f, Offset(sx, sy - 6f))
                if (game.engine.shieldTime > 0f) {
                    val pulse = 0.5f + 0.5f * sin(time * 5f)
                    scope.drawCircle(GamePalette.ELEM_ICE.copy(alpha = 0.2f + 0.15f * pulse), 34f, Offset(sx, sy - 4f), style = Stroke(3f))
                }
                return
            }
        }

        // 朝向四象限：右/左（眼睛左右偏）、上（背面，不画眼）、下（正面）
        val fx = kotlin.math.cos(game.engine.facing)
        val fy = kotlin.math.sin(game.engine.facing)
        val facingRight = fx >= 0f
        val facingUp = fy < -0.5f

        // 移动倾斜（批16）：随步伐 ±3° 摇摆，加动感
        val lean = if (game.engine.moving) sin(game.engine.walkPhase) * 3f else 0f
        // 0 角色微光（同心圆代替渐变笔刷，零分配）——批64：火把呼吸
        val flick = 1f + sin(time * 11f) * 0.04f + sin(time * 23f + 1.7f) * 0.03f
        scope.drawCircle(Color(0x06FFFFFF), 95f * flick, Offset(sx, sy))
        scope.drawCircle(Color(0x0AFFFFFF), 60f * flick, Offset(sx, sy))
        scope.withTransform({ rotate(lean, pivot = Offset(sx, sy)) }) {
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
        // 攻击冷却进度（批16）：1=即将出手（蓄能满），0=刚出手（后座大）
        val atkProg = if (game.engine.attackInterval > 0f)
            (1f - game.engine.attackTimer / game.engine.attackInterval).coerceIn(0f, 1f) else 0f
        val recoil = (1f - atkProg).coerceIn(0f, 1f)   // 刚出手=1，渐回
        val swingOff = game.engine.activeSlash?.let { sl ->
            if (sl.timer >= sl.windup && sl.timer <= sl.windup + 0.15f)
                Math.toDegrees((-60.0 + 120.0 * ((sl.timer - sl.windup) / 0.15f))).toFloat()
            else 0f
        } ?: 0f
        val deg = Math.toDegrees((game.engine.facing + sin(game.engine.walkPhase * 0.5f) * 0.2f).toDouble()).toFloat() + swingOff
        // 批54：骑士蓄力环（对齐精灵路径批18：atkProg>0.78 蓄力预警）
        if (cls.id == "knight" && atkProg > 0.78f) {
            val ck = (atkProg - 0.78f) / 0.22f
            drawCircle(GamePalette.UI_GOLD.copy(alpha = 0.45f * ck), 26f + 4f * ck, Offset(sx, sy), style = Stroke(2.5f))
        }
        scope.rotate(deg, pivot = Offset(handX, handY)) {
            when (cls.id) {
                "knight" -> if (spritesOn) Unit else {   // 精灵玩家自带剑（批17）
                    // 剑：护手 + 刃
                    drawRect(Color(0xFF8D6E63), Offset(handX - 2f, handY - 4f), Size(4f, 10f))
                    drawRect(weaponC, Offset(handX - 6f, handY - 8f), Size(12f, 3f))
                    drawRect(Color(0xFFECEFF1), Offset(handX - 2f, handY - 34f), Size(4f, 26f))
                }
                "mage" -> {
                    // 法杖：杆 + 顶端宝珠（批16：随冷却蓄能变大发光，出手瞬间后座缩小）
                    val charge = atkProg * atkProg
                    drawRect(Color(0xFF8D6E63), Offset(handX - 2f, handY - 30f), Size(4f, 34f))
                    drawCircle(BULLET_ORB.copy(alpha = 0.25f + 0.45f * charge), 5f + 4f * charge, Offset(handX, handY - 32f))
                    drawCircle(Color(0xFFFFFFFF), (2f + 2.5f * charge), Offset(handX, handY - 32f))
                }
                "ranger" -> {
                    // 弓：弧 + 弦（批16：拉弓/后座随攻击冷却进度）
                    val drawPull = atkProg * 5f   // 快出手时弦拉满（蓄力感）
                    drawArc(Color(0xFF8D6E63), -80f, 160f, false, Offset(handX - 4f - drawPull * 0.3f, handY - 18f), Size(8f, 36f), style = Stroke(3f))
                    drawLine(Color(0xFFECEFF1), Offset(handX, handY - 17f), Offset(handX - drawPull, handY + 17f), 1.5f)
                }
            }
        }
        }   // lean rotate（批16）
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
