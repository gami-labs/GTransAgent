package net.gtransagent.translator.experimental

import com.google.gson.Gson
import org.apache.commons.codec.binary.Base64
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.LanguageGroupedTranslator
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Tencent Experimental Translator
 * not need apiKey
 */
class TencentExperimentalTranslator : LanguageGroupedTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()

    companion object {
        const val NAME = "TencentExperimental"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "tencent_experimental"
            name = NAME
        })
    }

    private var url: String = "https://transmart.qq.com/api/imt"

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
        if (configs["url"] != null && (configs["url"] as String).isNotBlank()) {
            url = (configs["url"] as String).trim()
        }

        try {
            URL(url)
        } catch (e: Exception) {
            logger.error("TencentExperimentalTranslator init failed, url is invalid: $url")
            return false
        }

        mConcurrent = (configs["concurrent"] as Int?) ?: 3

        logger.info("TencentExperimentalTranslator init success, supportedEngines: url:$url, $supportedEngines")
        return true
    }

    private fun langConvert(origin: String): String {
        if (origin == "zh-CN") {
            return "zh-Hans"
        } else if (origin == "zh-TW") {
            return "zh-Hant"
        }
        return origin
    }

    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        inputs: List<String>,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        logger.debug("Tencent Experimental translateTexts start, requestId:$requestId, target:${targetLang}, inputs:${inputs}")

        val begin = System.currentTimeMillis()

        val clientKey = "browser-edge-chromium-135.0.0-Windows_10-${UUID.randomUUID()}-${System.currentTimeMillis()}"

        val headerMap = mutableMapOf<String, Any>()
        headerMap["fn"] = "auto_translation"
        headerMap["session"] = ""
        headerMap["user"] = ""
        headerMap["client_key"] = clientKey

        val jsonMap = mutableMapOf<String, Any>()
        jsonMap["header"] = headerMap
        jsonMap["type"] = "plain"
        jsonMap["source"] = mapOf(Pair("lang", CommonUtils.getLang(sourceLang)), Pair("text_list", inputs))
        jsonMap["target"] = mapOf(Pair("lang", CommonUtils.getLang(targetLang)))

        val payload = gson.toJson(jsonMap)
        val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder()
        builder.url(url)
        val request = builder.post(body).addHeader("Content-Type", "application/json").addHeader(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0"
        ).build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()

                /**
                 * {
                 * 	"header": {
                 * 		"type": "auto_translation",
                 * 		"ret_code": "succ",
                 * 		"time_cost": 319.0,
                 * 		"request_id": "b86ce0f11f4b11f080dd2a9ff1ca7b06"
                 * 	},
                 * 	"auto_translation": [
                 * 		"为什么我应该用网速测试来测试我的网速？\n投资你的未来\n"
                 * 	],
                 * 	"src_lang": "en",
                 * 	"tgt_lang": "zh"
                 * }
                 *
                 */
                val responseStr = response.body!!.string()
                try {
                    logger.debug("Tencent Experimental translation result: $responseStr, requestId:$requestId")
                    val map = gson.fromJson(responseStr, Map::class.java)
                    val results = (map["auto_translation"] as List<String>)
                    logger.info("Tencent Experimental translateTexts end, requestId:$requestId, time:${end - begin}, results:${results}, inputs:${inputs}, target:${targetLang}")
                    return results
                } catch (e: Exception) {
                    logger.error("Tencent Experimental translation result parsing failure, requestId:$requestId, responseStr:${responseStr}, target:${targetLang}")
                    throw Status.UNAVAILABLE.withDescription("Tencent Experimental translation result parsing failure")
                        .asRuntimeException()
                }
            } else {
                logger.error("Tencent Experimental translation return code invalid, requestId:$requestId, inputs:${inputs}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Tencent Experimental return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Tencent Experimental translation failure, requestId:$requestId, inputs:${inputs}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

}