package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.translator.base.FullBatchTranslator
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*


class GoogleTranslator : FullBatchTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()

    companion object {
        const val NAME = "Google"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "google"
            name = NAME
        })
    }

    private var url: String = "https://translation.googleapis.com/language/translate/v2"
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
            logger.error("GoogleTranslator init failed, url is invalid: $url")
            return false
        }

        val fileApiKey = (configs["apiKey"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("GOOGLE_CLOUD_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("GoogleTranslator init failed, Google apiKey not found. Please set apiKey in Google.yaml or GOOGLE_CLOUD_API_KEY in environment.")
            return false
        }

        logger.info("GoogleTranslator init success, supportedEngines: $supportedEngines")
        return true
    }


    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        targetLang: String,
        input: List<String>,
        sourceLang: String?,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        try {
            logger.debug("Google Cloud translateTexts start, target:${targetLang}, input:${input}")

            val begin = System.currentTimeMillis()

            val bodyBuilder = FormBody.Builder().add("key", apiKey)
                // .add("source", sourceLang) // auto
                .add("target", targetLang)
            input.forEach {
                bodyBuilder.add("q", it)
            }
            val body: RequestBody = bodyBuilder.build()

            val builder = Request.Builder()
            builder.url(url)
            val request = builder.post(body).build()
            val response = client.newCall(request).execute()
            val results = mutableListOf<String>()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val resultStr = response.body!!.string()
                val map = gson.fromJson(resultStr, Map::class.java)
                val translateTextResponseList = ((map["data"] as Map<*, *>)["translations"] as List<Map<*, *>>)
                translateTextResponseList.forEach {
                    results.add(it["translatedText"] as String)
                }
                logger.info("Google Cloud translateTexts end, time:${end - begin}, results:${results}, input:${input}, target:${targetLang}")
                return results
            } else {
                logger.error("Google Cloud translation return code invalid,  input:${input}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Google Cloud return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Google Cloud translation failure, input:${input}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }

}