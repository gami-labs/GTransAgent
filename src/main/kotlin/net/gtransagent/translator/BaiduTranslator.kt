package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.NiutransTranslator.Companion.USE_V2_API
import net.gtransagent.translator.base.LanguageGroupedTranslator
import okhttp3.*
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 *
 * https://api.fanyi.baidu.com/product/113
 * Non-authenticated users get the error: 54003	访问频率受限	请降低您的调用频率，或在管理控制台进行身份认证后切换为高级版/尊享版
 *
 */
class BaiduTranslator : LanguageGroupedTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    companion object {
        private const val URL = "https://fanyi-api.baidu.com/api/trans/vip/translate"

        const val NAME = "Baidu"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "baidu"
            name = NAME
        })
    }

    private lateinit var apiKey: String
    private lateinit var appId: String

    override fun getName(): String {
        return NAME
    }

    override fun isSupported(srcLanguage: String, targetLanguage: String): Boolean {
        return true
    }

    override fun isSupported(targetLanguage: String): Boolean {
        return true
    }

    override fun getSupportedEngines(): List<PublicConfig.TranslateEngine> {
        return supportedEngines
    }

    override fun init(configs: Map<*, *>): Boolean {
        val fileApiKey = (configs["apiKey"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("BAIDU_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("BaiduTranslator init failed, Baidu apiKey not found. Please set apiKey in Baidu.yaml or BAIDU_API_KEY in environment.")
            return false
        }

        val fileAppId = if (configs["appId"] == null) {
            null
        } else if (configs["appId"] is Number) {
            (configs["appId"] as Number).toString()
        } else {
            (configs["appId"] as String)
        }

        appId = if (fileAppId.isNullOrBlank()) {
            (System.getenv("BAIDU_APP_ID") ?: "").trim()
        } else {
            fileAppId.trim()
        }

        if (appId.isBlank()) {
            logger.error("BaiduTranslator init failed, Baidu appId not found. Please set appId in Baidu.yaml or BAIDU_APP_ID in environment.")
            return false
        }

        logger.info("BaiduTranslator init success, supportedEngines: $supportedEngines")
        return true
    }


    override fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        inputs: List<String>,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        logger.debug("Baidu translateTexts start, requestId:$requestId, source:${sourceLang}, target:${targetLang}, inputs:${inputs}")

        val begin = System.currentTimeMillis()
        val target = CommonUtils.getLang(targetLang)
        val src = CommonUtils.getLang(sourceLang)

        val sp = "\n"
        val sp2 = "\r\n"

        try {
            val inputStrAndIndexRecordMap = mutableMapOf<String, Int>()
            inputs.forEachIndexed { index, s ->
                s.split(Pattern.compile("$sp|$sp2")).forEach { item ->
                    if (item.isNotBlank()) {
                        inputStrAndIndexRecordMap[item.trim()] = index
                    }
                }
            }

            val textsStr = inputStrAndIndexRecordMap.keys.joinToString(sp)

            val timestamp = System.currentTimeMillis()

            //appid+q+salt+密钥
            val signOriginStr = "$appId$textsStr$timestamp$apiKey"
            val sign = DigestUtils.md5Hex(signOriginStr)

            val bodyBuilder = FormBody.Builder().add("from", src).add("to", target).add("q", textsStr) // max 2000 chars
                //.add("q", URLEncoder.encode(textsStr, "UTF-8")) // max 2000 chars
                .add("appid", appId).add("salt", timestamp.toString()).add("sign", sign)
            val body: RequestBody = bodyBuilder.build()

            val request: Request = Request.Builder().url(URL).post(body).build()
            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val result = response.body!!.bytes().toString(StandardCharsets.UTF_8)
                val map = gson.fromJson(result, Map::class.java)

                if (map.containsKey("error_code")) {
                    // https://api.fanyi.baidu.com/product/113
                    val errorCode = map["error_code"] as String
                    if (errorCode != "0") {
                        logger.error("translateTexts errorCode invalid, requestId:$requestId, errorCode:${errorCode}, result:$result")
                        throw Status.UNAVAILABLE.withDescription("Baidu return errorCode invalid, error_code:${errorCode}, result:$result")
                            .asRuntimeException()
                    }
                }

                if (!map.containsKey("trans_result")) {
                    logger.error("Baidu result not include trans_result, requestId:$requestId, result:$result")
                    throw Status.UNAVAILABLE.withDescription("Baidu result not include trans_result, result:$result")
                        .asRuntimeException()
                }
                val transResults = (map["trans_result"] as List<Map<*, *>>)
                val resultMap = mutableMapOf<Int, String>()
                transResults.forEach { item ->
                    val index = inputStrAndIndexRecordMap[(item["src"] as String).trim()]
                    if (index == null) {
                        logger.error("Baidu result not include src, requestId:$requestId, item:$item")
                        return@forEach
                    }
                    val dstStr = item["dst"] as String
                    if (resultMap.containsKey(index)) {
                        resultMap[index] = "${resultMap[index]}${dstStr}"
                    } else {
                        resultMap[index] = dstStr
                    }
                }

                val rtResults = mutableListOf<String>()
                inputs.forEachIndexed { index, _ ->
                    if (resultMap.containsKey(index)) {
                        rtResults.add(resultMap[index]!!)
                    } else {
                        rtResults.add("")
                    }
                }

                val end = System.currentTimeMillis()
                logger.info("Baidu translateTexts end, requestId:$requestId, time:${end - begin}, srcLang:${sourceLang}, targetLang:${targetLang}, results:${rtResults}, inputs:${inputs}")
                return rtResults
            } else {
                logger.error("Baidu translateTexts return code invalid, requestId:$requestId, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Baidu return result invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Baidu translation failure, requestId:$requestId, inputs:${inputs}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }

}