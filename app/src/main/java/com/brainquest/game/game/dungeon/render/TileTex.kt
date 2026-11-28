package com.brainquest.game.game.dungeon.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/** 批64：场景贴图化——每层程序化生成可平铺地板砖贴图（零素材依赖，对标元气骑士砖块质感）。
 *  3×3 块砖（块=40px，与 drawRoom 世界格 step=40f 对齐）烘焙进一张 120px ARGB_8888 Bitmap：
 *  砖底色 ±10% 明暗抖动、左上/右下 1px 倒角、砖缝深色、hash 撒 accent 斑点/裂纹/压暗半砖变体。
 *  绘制经 [floorPaint] 的 BitmapShader REPEAT 一次调用填满整间房地板（每房 1 次 shader 调用）。
 *  每层生成 1 张、全进程缓存（共 5 张，无逐帧分配）。 */
object TileTex {

    private const val TILE = 40          // 与 drawRoom 世界格 step=40f 对齐
    private const val SHEET = TILE * 3   // 120px

    private val cache = HashMap<Int, Paint>(8)

    /** 每层一张带 BitmapShader 的复用 Paint（REPEAT 平铺；调用方在圆角约束下 drawRoundRect） */
    fun floorPaint(floor: Int, tone: GamePalette.FloorTone): Paint {
        cache[floor]?.let { return it }
        val bmp = buildFloorBitmap(floor, tone)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(bmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        }
        cache[floor] = p
        return p
    }

    private fun buildFloorBitmap(floor: Int, tone: GamePalette.FloorTone): Bitmap {
        val tex = tone.tex
        val bmp = Bitmap.createBitmap(SHEET, SHEET, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val rng = Random(floor * 9176 + 17)   // 层级确定性：同一层贴图恒定

        // 3×3 块砖：底色 ±10% 明暗抖动 + 倒角 + accent 斑点/裂纹/压暗半砖变体
        for (gy in 0 until 3) {
            for (gx in 0 until 3) {
                val j = (rng.nextFloat() * 2f - 1f) * 0.10f
                val x = gx * TILE; val y = gy * TILE
                c.drawRect(
                    x.toFloat(), y.toFloat(), (x + TILE).toFloat(), (y + TILE).toFloat(),
                    paintOf(jitter(tex.base, j)),
                )
                // 倒角：左上 1px 亮、右下 1px 暗
                c.drawLine(x.toFloat(), y.toFloat(), (x + TILE).toFloat(), y.toFloat(), paintOf(jitter(tex.hi, j)))
                c.drawLine(x.toFloat(), y.toFloat(), x.toFloat(), (y + TILE).toFloat(), paintOf(jitter(tex.hi, j)))
                c.drawLine(x.toFloat(), (y + TILE - 1).toFloat(), (x + TILE).toFloat(), (y + TILE - 1).toFloat(), paintOf(jitter(tex.lo, j)))
                c.drawLine((x + TILE - 1).toFloat(), y.toFloat(), (x + TILE - 1).toFloat(), (y + TILE).toFloat(), paintOf(jitter(tex.lo, j)))
                // 变体：accent 斑点（30%）/ 裂纹（20%）/ 压暗半砖（15%）
                val v = rng.nextInt(100)
                if (v < 30) {
                    val pc = paintOf(jitter(tex.accent, j), 0.28f)
                    repeat(rng.nextInt(2, 4)) {
                        c.drawCircle(x + rng.nextFloat() * TILE, y + rng.nextFloat() * TILE, 1.6f + rng.nextFloat() * 1.6f, pc)
                    }
                } else if (v < 50) {
                    val pc = paintOf(jitter(tex.crack, j), 0.55f)
                    val sx0 = x + rng.nextFloat() * TILE * 0.5f
                    val sy0 = y + rng.nextFloat() * TILE * 0.5f
                    c.drawLine(sx0, sy0, sx0 + 6f + rng.nextFloat() * 6f, sy0 + 3f + rng.nextFloat() * 4f, pc)
                    c.drawLine(
                        sx0 + 6f + rng.nextFloat() * 6f, sy0 + 3f + rng.nextFloat() * 4f,
                        sx0 + 12f + rng.nextFloat() * 8f, sy0 + 1f + rng.nextFloat() * 6f, pc,
                    )
                } else if (v < 65) {
                    c.drawRect(
                        x.toFloat(), (y + TILE * 0.55f).toFloat(),
                        (x + TILE).toFloat(), (y + TILE).toFloat(),
                        paintOf(jitter(tex.lo, j), 0.35f),
                    )
                }
            }
        }
        // 砖缝（grout）：3×3 网格边界 1.5px
        val g = paintOf(tex.grout)
        for (i in 0..3) {
            c.drawLine((i * TILE).toFloat(), 0f, (i * TILE).toFloat(), SHEET.toFloat(), g)
            c.drawLine(0f, (i * TILE).toFloat(), SHEET.toFloat(), (i * TILE).toFloat(), g)
        }
        return bmp
    }

    /** 颜色向黑/白线性偏移（amount ∈ [-1,1]，正=提亮 负=压暗） */
    private fun jitter(c: Color, amount: Float): Color {
        val t = amount.coerceIn(-1f, 1f)
        return if (t >= 0f) Color(
            (c.red + (1f - c.red) * t).coerceIn(0f, 1f),
            (c.green + (1f - c.green) * t).coerceIn(0f, 1f),
            (c.blue + (1f - c.blue) * t).coerceIn(0f, 1f),
            c.alpha,
        ) else Color(
            (c.red * (1f + t)).coerceIn(0f, 1f),
            (c.green * (1f + t)).coerceIn(0f, 1f),
            (c.blue * (1f + t)).coerceIn(0f, 1f),
            c.alpha,
        )
    }

    private val paints = HashMap<Int, Paint>(32)

    private fun paintOf(c: Color, alphaMul: Float = 1f): Paint {
        val key = c.hashCode() * 31 + (alphaMul * 100).toInt()
        return paints.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(
                    (c.alpha * 255 * alphaMul).toInt().coerceIn(0, 255),
                    (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(),
                )
            }
        }
    }
}
