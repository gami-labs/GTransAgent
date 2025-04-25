package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.FullBatchTranslator
import okhttp3.*
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*

/**
 *
 * https://ai.youdao.com/console/#/service-singleton/text-translation
 *
 */
class YoudaoTranslator : FullBatchTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()

    companion object {
        private const val URL = "https://openapi.youdao.com/v2/api"

        const val NAME = "Youdao"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "youdao"
            name = NAME
        })
    }

    private lateinit var apiKey: String
    private lateinit var apiSecret: String

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
            (System.getenv("YOUDAO_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("YoudaoTranslator init failed, Youdao apiKey not found. Please set apiKey in Youdao.yaml or YOUDAO_API_KEY in environment.")
            return false
        }

        val fileApiSecret = (configs["apiSecret"] as String?)
        apiSecret = if (fileApiSecret.isNullOrBlank()) {
            (System.getenv("YOUDAO_API_SECRET") ?: "").trim()
        } else {
            fileApiSecret.trim()
        }

        if (apiSecret.isBlank()) {
            logger.error("YoudaoTranslator init failed, Youdao apiSecret not found. Please set apiSecret in Youdao.yaml or YOUDAO_API_SECRET in environment.")
            return false
        }

        logger.info("YoudaoTranslator init success, supportedEngines: $supportedEngines")
        return true
    }

    private fun convertLang(origin: String): String {
        val langOnly = CommonUtils.getLang(origin).lowercase()
        if (langOnly == "zh") {
            return "zh-CHS"
        }
        return origin
    }

    fun truncate(qArray: List<String>): String {
        val batchQStr = java.lang.String.join("", qArray)
        val len = batchQStr.length
        return if (len <= 20) batchQStr else batchQStr.substring(0, 10) + len + batchQStr.substring(len - 10, len)
    }

    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        targetLang: String,
        inputs: List<String>,
        sourceLang: String?,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        try {
            logger.debug("Youdao translateTexts start, target:${targetLang}, inputs:${inputs}")

            val target = convertLang(targetLang)

            val begin = System.currentTimeMillis()

            val builder: FormBody.Builder = FormBody.Builder(StandardCharsets.UTF_8)

            for (text in inputs) {
                builder.add("q", text)
            }

            val salt = UUID.randomUUID().toString()
            builder.add("from", "auto")
            builder.add("to", target)
            builder.add("signType", "v3")
            val curtime = (System.currentTimeMillis() / 1000).toString()
            builder.add("curtime", curtime)
            val signStr = apiKey + truncate(inputs) + salt + curtime + apiSecret
            val sign = DigestUtils.sha256Hex(signStr)
            builder.add("appKey", apiKey)
            builder.add("salt", salt)
            builder.add("sign", sign)
            builder.add("detectFilter", "true")

            val body: RequestBody = builder.build()

            val requestBuilder = Request.Builder()
            requestBuilder.url(URL)
            val request = requestBuilder.post(body).build()
            val response = client.newCall(request).execute()
            val results = mutableListOf<String>()
            //调用成功
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val result = response.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                val requestId = map["requestId"] as String
                val errorCode = map["errorCode"] as String
                if (errorCode != "0") {
                    logger.error("translateTexts errorCode invalid, params:${builder.build()}, errorCode:${errorCode}, requestId:$requestId")
                    throw Status.UNAVAILABLE.withDescription("Youdao return code invalid, code:${errorCode.toInt()}")
                        .asRuntimeException()
                }
                val translateResults = map["translateResults"] as List<Map<String, Any>>
                translateResults.forEach { item ->
                    val resultStr = item["translation"] as String
                    results.add(resultStr)
                }

                val end = System.currentTimeMillis()
                logger.info("Youdao translateTexts end, time:${end - begin}, results:${results}, inputs:${inputs}, target:${targetLang}")
                return results
            } else {
                logger.error("Youdao translation return code invalid, inputs:${inputs}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Google Cloud return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Youdao translation failure, inputs:${inputs}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }
}