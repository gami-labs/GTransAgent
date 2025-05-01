package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.FullBatchTranslator
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


/**
 *
 * https://cloud.yandex.com/services/translate
 *
 */
class YandexTranslator : FullBatchTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    companion object {
        const val NAME = "Yandex"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "yandex"
            name = NAME
        })
    }

    private var url: String = "https://translate.api.cloud.yandex.net/translate/v2/translate"
    private lateinit var apiKey: String

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
            logger.error("YandexTranslator init failed, url is invalid: $url")
            return false
        }

        val fileApiKey = (configs["apiKey"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("YANDEX_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("YandexTranslator init failed, Yandex apiKey not found. Please set apiKey in Yandex.yaml or YANDEX_API_KEY in environment.")
            return false
        }

        logger.info("YandexTranslator init success, supportedEngines: $supportedEngines")
        return true
    }

    override fun sendRequest(
        requestId: String,
        targetLang: String,
        inputs: List<String>,
        sourceLang: String?,
        isSourceLanguageUserSetToAuto: Boolean, // true if user selects "auto" as the source language
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        try {
            logger.debug("Yandex translateTexts start, requestId:$requestId, source:${sourceLang}, target:${targetLang}, inputs:${inputs}")

            val begin = System.currentTimeMillis()

            val jsonMap = mutableMapOf<String, Any>(
                Pair("targetLanguageCode", CommonUtils.getLang(targetLang)),
                Pair("format", "PLAIN_TEXT"),
                Pair("texts", inputs),
            )

            if (sourceLang != null) {
                jsonMap["sourceLanguageCode"] = CommonUtils.getLang(sourceLang)
            }

            if (!glossaryWords.isNullOrEmpty()) {
                val glossaryPairs = mutableListOf<Map<String, Any>>()
                glossaryWords.forEach {
                    val glossaryPair = mutableMapOf<String, Any>()
                    glossaryPair["sourceText"] = it.first
                    glossaryPair["translatedText"] = it.second
                    glossaryPair["exact"] = glossaryIgnoreCase
                    glossaryPairs.add(glossaryPair)
                }
                val glossaryMap = mutableMapOf<String, Any>()
                glossaryMap["glossaryData"] = mapOf(Pair("glossaryPairs", glossaryPairs))
                jsonMap["glossaryConfig"] = glossaryMap
            }

            val payload = gson.toJson(jsonMap)

            logger.debug("Yandex translateTexts, requestId:$requestId, source:${sourceLang}, target:${targetLang}, payload:${payload}")

            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val builder = Request.Builder()
            builder.url(url).addHeader("Authorization", "Api-Key $apiKey")
            val request = builder.post(body).build()
            val response = client.newCall(request).execute()
            val results = mutableListOf<String>()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val resultStr = response.body!!.string()
                val map = gson.fromJson(resultStr, Map::class.java)
                val translateTextResponseList = (map["translations"] as List<Map<*, *>>)
                translateTextResponseList.forEach {
                    results.add(it["text"] as String)
                }
                logger.info("Yandex Cloud translateTexts end, requestId:$requestId, time:${end - begin}, results:${results}, inputs:${inputs}, target:${targetLang}")
                return results
            } else {
                logger.error("Yandex Cloud translation return code invalid, requestId:$requestId, inputs:${inputs}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Yandex Cloud return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Yandex translation failure, requestId:$requestId, inputs:${inputs}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

}