package com.brainquest.game.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** 更新引擎：manifest 检查、内容包热更、APK 下载与增量合成 */
class UpdateManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val contentDir: File get() = File(context.filesDir, "content/packs")
    val cacheDir: File get() = File(context.cacheDir, "update").apply { mkdirs() }

    // ---------- manifest ----------

    fun fetchManifest(baseUrl: String): UpdateManifest {
        val url = joinUrl(baseUrl, "manifest.json")
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("服务器响应 ${resp.code}")
            resp.body?.string() ?: throw IOException("空响应")
        }
        return json.decodeFromString<UpdateManifest>(body)
    }

    // ---------- 内容包热更新 ----------

    /** 检查并下载所有比本地版本新的内容包，返回更新的包列表 */
    fun updateContentPacks(
        baseUrl: String,
        manifest: UpdateManifest,
        localVersions: Map<String, Int>,
        onProgress: (String) -> Unit,
    ): List<ContentPackInfo> {
        val updated = mutableListOf<ContentPackInfo>()
        for (pack in manifest.contentPacks) {
            val local = localVersions[pack.id] ?: 0
            if (pack.version <= local) continue
            onProgress("下载内容包：${pack.id} v${pack.version}")
            val url = Companion.joinUrl(baseUrl, pack.file)
            val file = download(url, pack.sha256)
            onProgress("校验并解压：${pack.id}")
            unzipJsons(file, File(contentDir, pack.id))
            updated.add(pack)
        }
        return updated
    }

    // ---------- APK 下载（全量/补丁通用） ----------

    fun download(
        url: String,
        expectedSha256: String,
        onProgress: (DownloadProgress) -> Unit = {},
        outputName: String? = null,
    ): File {
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        resp.use { r ->
            if (!r.isSuccessful) throw IOException("下载失败 HTTP ${r.code}")
            val body = r.body ?: throw IOException("空响应")
            val total = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            val out = File.createTempFile("dl_", ".tmp", cacheDir)
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        onProgress(DownloadProgress(read, total))
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256.isNotBlank() && !hash.equals(expectedSha256, ignoreCase = true)) {
                out.delete()
                throw IOException("SHA-256 校验失败，文件可能被篡改")
            }
            val final = File(cacheDir, outputName ?: "content_${hash.take(12)}.bin")
            if (final.exists()) final.delete()
            out.renameTo(final)
            return final
        }
    }

    // ---------- 增量更新：合成新 APK ----------

    /** 用已下载的补丁文件把当前已安装 APK 合成为新版本 APK（已做哈希校验） */
    fun applyIncrementalPatch(patchFile: File, targetApkSha256: String, onProgress: (String) -> Unit): File {
        onProgress("读取当前已安装 APK…")
        val baseApk = File(context.applicationInfo.sourceDir)
        onProgress("应用 bsdiff 增量补丁…")
        val newData = BSPatch.patch(baseApk.readBytes(), patchFile)
        onProgress("校验合成结果…")
        val digest = MessageDigest.getInstance("SHA-256").digest(newData)
        val hash = digest.joinToString("") { "%02x".format(it) }
        if (targetApkSha256.isNotBlank() && !hash.equals(targetApkSha256, ignoreCase = true)) {
            throw IOException("合成 APK 校验失败，增量补丁与当前版本不匹配")
        }
        val out = File(cacheDir, "brainquest_new.apk")
        out.writeBytes(newData)
        onProgress("增量合成成功")
        return out
    }

    // ---------- 校验工具 ----------

    fun verifyApkSignature(apk: File): Boolean {
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES) ?: return false
        val sigA = installed.signatures?.firstOrNull() ?: return false
        val sigB = archive.signatures?.firstOrNull() ?: return false
        return sigA.toByteArray().contentEquals(sigB.toByteArray())
    }

    fun unzipJsons(zip: File, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json")) {
                    val name = File(entry.name).name // 展平目录结构
                    File(targetDir, name).outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
        zip.delete()
    }

    companion object {
        fun joinUrl(base: String, path: String): String {
            val b = base.trimEnd('/')
            return if (path.startsWith("http")) path else "$b/$path"
        }
    }
}
