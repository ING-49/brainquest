package com.brainquest.game.update

import java.io.File

/**
 * 自研增量补丁 BQDELTA1 解码端（PC 端 update-server/release.py + delta.py 生成）。
 *
 * 格式：
 * ```
 * magic "BQDELTA1"  (8 字节)
 * varint oldSize
 * varint newSize
 * 操作流：
 *   0x00 varint(len) len字节          → 字面量拷贝
 *   0x01 varint(oldOff) varint(len)   → 从旧文件 COPY
 * ```
 *
 * 服务端发布时会用 Python 端同逻辑校验合成结果 SHA-256 == 新版 APK，安全性再由
 * 下载哈希校验 + 签名校验双重兜底。
 */
object BSPatch {

    fun patch(old: ByteArray, patchFile: File): ByteArray = patch(old, patchFile.readBytes())

    fun patch(old: ByteArray, patch: ByteArray): ByteArray {
        require(patch.size > 10) { "补丁文件不完整" }
        require(String(patch, 0, 8, Charsets.US_ASCII) == "BQDELTA1") { "不是有效的 BQDELTA1 补丁文件" }

        var pos = 8
        var r = readVarint(patch, pos)
        val oldSize = r.first
        pos = r.second
        r = readVarint(patch, pos)
        val newSize = r.first
        pos = r.second
        require(oldSize == old.size.toLong()) { "补丁与当前版本不匹配 (old $oldSize != ${old.size})" }
        require(newSize in 0..(512L shl 20)) { "补丁输出尺寸异常" }

        val result = ByteArray(newSize.toInt())
        var w = 0
        while (pos < patch.size) {
            val op = patch[pos].toInt() and 0xFF
            pos++
            when (op) {
                0x00 -> {
                    r = readVarint(patch, pos)
                    val len = r.first.toInt()
                    pos = r.second
                    require(pos + len <= patch.size && w + len <= result.size) { "字面量段越界" }
                    System.arraycopy(patch, pos, result, w, len)
                    pos += len
                    w += len
                }
                0x01 -> {
                    r = readVarint(patch, pos)
                    val off = r.first.toInt()
                    pos = r.second
                    r = readVarint(patch, pos)
                    val len = r.first.toInt()
                    pos = r.second
                    require(off >= 0 && off + len <= old.size && w + len <= result.size) { "COPY 段越界" }
                    System.arraycopy(old, off, result, w, len)
                    w += len
                }
                else -> throw IllegalArgumentException("未知操作码 $op")
            }
        }
        require(w == result.size) { "补丁输出不完整" }
        return result
    }

    /** 解无符号 LEB128 varint，返回 (值, 新位置) */
    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var shift = 0
        var result = 0L
        var pos = start
        while (true) {
            require(pos < data.size) { "varint 越界" }
            val b = data[pos].toInt() and 0xFF
            pos++
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result to pos
            shift += 7
            require(shift < 64) { "varint 过长" }
        }
    }
}
