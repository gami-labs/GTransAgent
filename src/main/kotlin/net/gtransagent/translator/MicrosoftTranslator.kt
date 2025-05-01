package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.translator.base.FullBatchTranslator
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class MicrosoftTranslator : FullBatchTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()

    companion object {
        const val NAME = "Microsoft"

        private const val URL = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "microsoft"
            name = NAME
        })
    }

    private lateinit var apiKey: String
    private lateinit var resourceRegion: String

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
        val fileResourceRegion = (configs["resourceRegion"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("AZURE_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        resourceRegion = if (fileResourceRegion.isNullOrBlank()) {
            (System.getenv("AZURE_RESOURCE_REGION") ?: "").trim()
        } else {
            fileResourceRegion.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("MicrosoftTranslator init failed, Azure apiKey not found. Please set apiKey in Microsoft.yaml or AZURE_API_KEY in environment.")
            return false
        }

        if (resourceRegion.isBlank()) {
            logger.error("MicrosoftTranslator init failed, Azure location not found. Please set resourceRegion in Microsoft.yaml or AZURE_RESOURCE_REGION in environment.")
            return false
        }

        logger.info("MicrosoftTranslator init success, supportedEngines: $supportedEngines")
        return true
    }

    private fun langConvert(origin: String): String {
        if (origin == "zh-CN" || origin == "zh_Hans") {
            return "zh-Hans"
        } else if (origin == "zh-TW" || origin == "zh_Hant") {
            return "zh-Hant"
        }
        return origin
    }

    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        targetLang: String,
        input: List<String>,
        sourceLang: String?,
        isSourceLanguageUserSetToAuto: Boolean, // true if user selects "auto" as the source language
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        try {
            logger.debug("MicrosoftTranslator translateTexts start, target:${targetLang}, input:${input}")

            val begin = System.currentTimeMillis()

            val jsonMap = mutableListOf<MutableMap<String, String>>()
            input.forEach {
                val map = mutableMapOf<String, String>()
                map["text"] = it
                jsonMap.add(map)
            }
            val payload = gson.toJson(jsonMap)
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val url = "$URL&to=${langConvert(targetLang)}"

            val request = Request.Builder().url(url)
                .addHeader("Ocp-Apim-Subscription-Key", apiKey)
                .addHeader("Ocp-Apim-Subscription-Region", resourceRegion)
                .post(body).build()

            val response = client.newCall(request).execute()
            val results = mutableListOf<String>()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()

                /**
                 * [
                 *     {
                 *         "translations":[
                 *             {"text":"你好, 你叫什么名字？","to":"zh-Hans"}
                 *         ]
                 *     },
                 *     {
                 *         "translations":[
                 *             {"text":"我很好，谢谢你。","to":"zh-Hans"}
                 *         ]
                 *     }
                 * ]
                 */
                val resultStr = response.body!!.string()
                val list = gson.fromJson(resultStr, List::class.java)
                list.forEach {
                    val map = it as Map<*, *>
                    if (map["translations"] == null) {
                        logger.error("MicrosoftTranslator translation return invalid,  input:${input}, target:${targetLang}, result:${resultStr}")
                        throw Status.UNAVAILABLE.withDescription("MicrosoftTranslator translation return invalid")
                            .asRuntimeException()
                    }
                    results.add(((map["translations"] as List<Map<*, *>>)[0]["text"] as String))
                }
                logger.info("MicrosoftTranslator translateTexts end, time:${end - begin}, results:${results}, input:${input}, target:${targetLang}")
                return results
            } else {
                logger.error("MicrosoftTranslator translation return code invalid,  input:${input}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("MicrosoftTranslator return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "MicrosoftTranslator translation failure, input:${input}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }

}