package com.brainquest.game.update

import kotlinx.serialization.Serializable

/** 更新服务器 manifest.json 结构 */
@Serializable
data class UpdateManifest(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val fullApk: String = "",
    val fullApkSha256: String = "",
    val patches: List<ApkPatchInfo> = emptyList(),
    val contentPacks: List<ContentPackInfo> = emptyList(),
    val notice: String = "",
)

/** APK 增量补丁：from 版本 → to 版本 */
@Serializable
data class ApkPatchInfo(
    val from: Int,
    val to: Int,
    val file: String,
    val sha256: String,
    val size: Long = 0,
)

/** 题库等内容热更包 */
@Serializable
data class ContentPackInfo(
    val id: String,
    val version: Int,
    val file: String,
    val sha256: String,
    val size: Long = 0,
    val note: String = "",
)

data class DownloadProgress(val downloaded: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
    val mbText: String get() = "%.1f/%.1f MB".format(downloaded / 1e6, total / 1e6)
}
