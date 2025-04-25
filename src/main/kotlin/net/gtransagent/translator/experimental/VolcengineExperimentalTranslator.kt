package net.gtransagent.translator.experimental

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Volcengine Experimental Translator
 * not need apiKey
 *
 */
@Deprecated("Volcengine Experimental Translator is deprecated, please use VolcengineTranslator instead.")
class VolcengineExperimentalTranslator : SingleInputTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()


    companion object {
        const val NAME = "VolcengineExperimental"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "volcengine_experimental"
            name = NAME
        })
    }

    private var url: String = "https://translate.volcengine.com/crx/translate/v1"

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
            logger.error("VolcengineExperimentalTranslator init failed, url is invalid: $url")
            return false
        }

        mConcurrent = (configs["concurrent"] as Int?) ?: 3

        logger.info("VolcengineExperimentalTranslator init success, supportedEngines: url:$url, $supportedEngines")
        return true
    }


    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        input: String,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): String {
        try {
            logger.debug("Volcengine Experimental translateTexts start, requestId:$requestId, target:${targetLang}, input:${input}")

            val begin = System.currentTimeMillis()

            val urlBuilder = url.toHttpUrlOrNull()!!.newBuilder()

            val jsonMap = mutableMapOf<String, String>()
            jsonMap["source_language"] = CommonUtils.getLang(sourceLang)
            jsonMap["target_language"] = CommonUtils.getLang(targetLang)
            jsonMap["text"] = input

            val payload = gson.toJson(jsonMap)
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val builder = Request.Builder()
            builder.url(urlBuilder.build())
            val request = builder.post(body).addHeader("Content-Type", "application/json").addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0"
                ).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                
                val responseStr = response.body!!.string()
                val map = gson.fromJson(responseStr, Map::class.java)
                val result = map["translation"] as String?
                logger.info("Volcengine Experimental translateTexts end, requestId:$requestId, time:${end - begin}, result:${result}, input:${input}, target:${targetLang}")
                return result ?: ""
            } else {
                logger.error("Volcengine Experimental translation return code invalid, requestId:$requestId, input:${input}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Volcengine Experimental return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Volcengine Experimental translation failure, requestId:$requestId, input:${input}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

}