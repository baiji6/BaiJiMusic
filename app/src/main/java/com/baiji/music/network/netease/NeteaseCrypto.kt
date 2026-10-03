package com.baiji.music.network.netease

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云加密工具。
 * - EAPI 参数加密（AES-ECB/PKCS5Padding + hex）
 * - 封面图片 ID 加密（XOR + MD5 + Base64）
 */
object NeteaseCrypto {

    private val AES_KEY = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)
    private const val MAGIC = "3go8&$8*3*3h0k(2)2"

    fun md5Hex(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        return hex(md.digest(text.toByteArray(Charsets.UTF_8)))
    }

    private fun hex(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            val v = b.toInt() and 0xff
            sb.append(Character.forDigit(v shr 4, 16))
            sb.append(Character.forDigit(v and 0x0f, 16))
        }
        return sb.toString()
    }

    /**
     * EAPI 参数加密。
     * 明文格式：`{apiPath}-36cd479b6b5-{payloadJson}-36cd479b6b5-{digest}`
     * 其中 digest = md5("nobody{apiPath}use{payloadJson}md5forencrypt")，apiPath 由 eapi 路径改写而来。
     */
    fun encryptParams(eapiPath: String, payloadJson: String): String {
        val path = eapiPath.replace("/eapi/", "/api/")
        val digest = md5Hex("nobody${path}use${payloadJson}md5forencrypt")
        val text = "$path-36cd479b6b5-$payloadJson-36cd479b6b5-$digest"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(AES_KEY, "AES"))
        return hex(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    /** 网易云图片 ID 加密算法（用于拼接封面直链） */
    fun encryptId(id: String): String {
        val sb = StringBuilder(id.length)
        for (i in id.indices) {
            sb.append((id[i].code xor MAGIC[i % MAGIC.length].code).toChar())
        }
        val md5 = MessageDigest.getInstance("MD5").digest(sb.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(md5, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /** 由图片 ID 生成封面直链 */
    fun picUrl(picId: Long?, size: Int = 300): String {
        if (picId == null || picId == 0L) return ""
        val enc = encryptId(picId.toString())
        return "https://p3.music.126.net/$enc/$picId.jpg?param=${size}y$size"
    }
}
