package com.brainquest.game.util

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 云存档加密：口令经 PBKDF2 派生密钥 → AES-GCM 加密存档 JSON，产出「信封」字符串。
 * 服务器只存信封（不透明字符串），无法读出内容；口令是唯一凭证，丢失无法解密。
 * 信封格式 {"fmt":"BQENC1","salt","iters","iv","ct"}（均 base64，iters 除外）。
 */
object SaveCrypto {

    private const val FORMAT = "BQENC1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 60_000
    private const val TAG_BITS = 128

    private val json = Json { ignoreUnknownKeys = true }

    /** 是否为加密信封（旧版明文存档 JSON 返回 false，走免口令兼容通道） */
    fun isEnvelope(text: String): Boolean = runCatching {
        json.parseToJsonElement(text).jsonObject["fmt"]?.jsonPrimitive?.content == FORMAT
    }.getOrDefault(false)

    /** 加密：明文存档 JSON + 口令 → 信封字符串 */
    fun encrypt(plainJson: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        return buildJsonObject {
            put("fmt", FORMAT)
            put("salt", b64(salt))
            put("iters", ITERATIONS)
            put("iv", b64(iv))
            put("ct", b64(ct))
        }.toString()
    }

    /** 解密：信封字符串 + 口令 → 明文存档 JSON；口令错误或数据损坏返回 null */
    fun decrypt(envelope: String, password: String): String? = runCatching {
        val obj = json.parseToJsonElement(envelope).jsonObject
        check(obj["fmt"]?.jsonPrimitive?.content == FORMAT) { "未知格式" }
        val salt = Base64.decode(obj["salt"]!!.jsonPrimitive.content, Base64.NO_WRAP)
        val iters = obj["iters"]?.jsonPrimitive?.content?.toIntOrNull() ?: ITERATIONS
        val iv = Base64.decode(obj["iv"]!!.jsonPrimitive.content, Base64.NO_WRAP)
        val ct = Base64.decode(obj["ct"]!!.jsonPrimitive.content, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iters), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
