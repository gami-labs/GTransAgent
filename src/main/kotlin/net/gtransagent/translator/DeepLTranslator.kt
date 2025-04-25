package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.FullBatchTranslator
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


class DeepLTranslator : FullBatchTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()

    companion object {
        private const val URL = "https://api.deepl.com/v2/translate"

        const val NAME = "Deepl"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "deepl"
            name = NAME
        })


        /**
         * [{"language":"BG","name":"Bulgarian"},{"language":"CS","name":"Czech"},{"language":"DA","name":"Danish"},{"language":"DE","name":"German"},{"language":"EL","name":"Greek"},{"language":"EN","name":"English"},{"language":"ES","name":"Spanish"},{"language":"ET","name":"Estonian"},{"language":"FI","name":"Finnish"},{"language":"FR","name":"French"},{"language":"HU","name":"Hungarian"},{"language":"ID","name":"Indonesian"},{"language":"IT","name":"Italian"},{"language":"JA","name":"Japanese"},{"language":"KO","name":"Korean"},{"language":"LT","name":"Lithuanian"},{"language":"LV","name":"Latvian"},{"language":"NB","name":"Norwegian"},{"language":"NL","name":"Dutch"},{"language":"PL","name":"Polish"},{"language":"PT","name":"Portuguese"},{"language":"RO","name":"Romanian"},{"language":"RU","name":"Russian"},{"language":"SK","name":"Slovak"},{"language":"SL","name":"Slovenian"},{"language":"SV","name":"Swedish"},{"language":"TR","name":"Turkish"},{"language":"UK","name":"Ukrainian"},{"language":"ZH","name":"Chinese"}]
         *
         */
        val SUPPORT_SOURCE_LANGS = mapOf(
            Pair("AR", "Arabic"),
            Pair("BG", "Bulgarian"),
            Pair("CS", "Czech"),
            Pair("DA", "Danish"),
            Pair("DE", "German"),
            Pair("EL", "Greek"),
            Pair("EN", "English"),
            Pair("ES", "Spanish"),
            Pair("ET", "Estonian"),
            Pair("FI", "Finnish"),
            Pair("FR", "French"),
            Pair("HU", "Hungarian"),
            Pair("ID", "Indonesian"),
            Pair("IT", "Italian"),
            Pair("JA", "Japanese"),
            Pair("KO", "Korean"),
            Pair("LT", "Lithuanian"),
            Pair("LV", "Latvian"),
            Pair("NB", "Norwegian"),
            Pair("NL", "Dutch"),
            Pair("PL", "Polish"),
            Pair("PT", "Portuguese"),
            Pair("RO", "Romanian"),
            Pair("RU", "Russian"),
            Pair("SK", "Slovak"),
            Pair("SL", "Slovenian"),
            Pair("SV", "Swedish"),
            Pair("TR", "Turkish"),
            Pair("UK", "Ukrainian"),
            Pair("ZH", "Chinese")
        )

        /**
         * [{"language":"BG","name":"Bulgarian","supports_formality":false},{"language":"CS","name":"Czech","supports_formality":false},{"language":"DA","name":"Danish","supports_formality":false},{"language":"DE","name":"German","supports_formality":true},{"language":"EL","name":"Greek","supports_formality":false},{"language":"EN-GB","name":"English (British)","supports_formality":false},{"language":"EN-US","name":"English (American)","supports_formality":false},{"language":"ES","name":"Spanish","supports_formality":true},{"language":"ET","name":"Estonian","supports_formality":false},{"language":"FI","name":"Finnish","supports_formality":false},{"language":"FR","name":"French","supports_formality":true},{"language":"HU","name":"Hungarian","supports_formality":false},{"language":"ID","name":"Indonesian","supports_formality":false},{"language":"IT","name":"Italian","supports_formality":true},{"language":"JA","name":"Japanese","supports_formality":true},{"language":"KO","name":"Korean","supports_formality":false},{"language":"LT","name":"Lithuanian","supports_formality":false},{"language":"LV","name":"Latvian","supports_formality":false},{"language":"NB","name":"Norwegian","supports_formality":false},{"language":"NL","name":"Dutch","supports_formality":true},{"language":"PL","name":"Polish","supports_formality":true},{"language":"PT-BR","name":"Portuguese (Brazilian)","supports_formality":true},{"language":"PT-PT","name":"Portuguese (European)","supports_formality":true},{"language":"RO","name":"Romanian","supports_formality":false},{"language":"RU","name":"Russian","supports_formality":true},{"language":"SK","name":"Slovak","supports_formality":false},{"language":"SL","name":"Slovenian","supports_formality":false},{"language":"SV","name":"Swedish","supports_formality":false},{"language":"TR","name":"Turkish","supports_formality":false},{"language":"UK","name":"Ukrainian","supports_formality":false},{"language":"ZH","name":"Chinese (simplified)","supports_formality":false}]
         */
        val SUPPORT_TARGET_LANGS = mapOf(
            Pair("AR", "Arabic"),
            Pair("BG", "Bulgarian"),
            Pair("CS", "Czech"),
            Pair("DA", "Danish"),
            Pair("DE", "German"),
            Pair("EL", "Greek"),
            Pair("EN", "English (American)"),
            Pair("ES", "Spanish"),
            Pair("ET", "Estonian"),
            Pair("FI", "Finnish"),
            Pair("FR", "French"),
            Pair("HU", "Hungarian"),
            Pair("ID", "Indonesian"),
            Pair("IT", "Italian"),
            Pair("JA", "Japanese"),
            Pair("KO", "Korean"),
            Pair("LT", "Lithuanian"),
            Pair("LV", "Latvian"),
            Pair("NB", "Norwegian"),
            Pair("NL", "Dutch"),
            Pair("PL", "Polish"),
            Pair("PT-BR", "Portuguese (Brazilian)"),
            Pair("PT", "Portuguese (European)"),
            Pair("RO", "Romanian"),
            Pair("RU", "Russian"),
            Pair("SK", "Slovak"),
            Pair("SL", "Slovenian"),
            Pair("SV", "Swedish"),
            Pair("TR", "Turkish"),
            Pair("UK", "Ukrainian"),
            Pair("ZH", "Chinese (simplified)")
        )
    }

    private lateinit var apiKey: String

    override fun getName(): String {
        return NAME
    }

    override fun isSupported(srcLanguage: String, targetLanguage: String): Boolean {
        val targetLangOnly = CommonUtils.getLang(targetLanguage)
        val srcLangOnly = CommonUtils.getLang(srcLanguage)

        return SUPPORT_SOURCE_LANGS.containsKey(srcLangOnly.uppercase()) && SUPPORT_TARGET_LANGS.containsKey(
            targetLangOnly.uppercase()
        )
    }

    override fun isSupported(targetLanguage: String): Boolean {
        val targetLangOnly = CommonUtils.getLang(targetLanguage)
        return SUPPORT_TARGET_LANGS.containsKey(
            targetLangOnly.uppercase()
        )
    }

    override fun getSupportedEngines(): List<PublicConfig.TranslateEngine> {
        return supportedEngines
    }


    override fun init(configs: Map<*, *>): Boolean {
        val fileApiKey = (configs["apiKey"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("DEEPL_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("DeeplTranslator init failed, Deepl apiKey not found. Please set apiKey in Deepl.yaml or DEEPL_API_KEY in environment.")
            return false
        }

        logger.info("DeeplTranslator init success, supportedEngines: $supportedEngines")
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
        logger.debug("Deepl translateTexts start, target:${targetLang}, input:${input}")
        val begin = System.currentTimeMillis()
        val realTargetLang = if (targetLang.equals("EN", true)) {
            "EN-US"
        } else if (targetLang.equals("PT", true)) {
            "PT-PT"
        } else {
            CommonUtils.getLang(targetLang)
        }

        try {
            val jsonMap = mutableMapOf<String, Any>(
                Pair("split_sentences", "nonewlines"),
                Pair("preserve_formatting", true),
                Pair("formality", "default"),
                Pair("tag_handling", "xml"),
                Pair("outline_detection", false),
                Pair("splitting_tags", listOf("x")),
            )

            jsonMap["text"] = input
            jsonMap["target_lang"] = realTargetLang
            val payload = gson.toJson(jsonMap)
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request =
                Request.Builder().url(URL).addHeader("Authorization", "DeepL-Auth-Key $apiKey").post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val result = response.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                val targetText = (map["translations"] as List<Map<*, *>>)

                val transResult = mutableListOf<String>()
                targetText.forEach { item ->
                    val text = item["text"] as String
                    transResult.add(text)
                }

                logger.info("Deepl translateTexts end, time:${end - begin}, target:${targetLang}, results:${transResult}, input:${input}")
                return transResult
            } else {
                logger.error("Deepl translation return code invalid,  input:${input}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Deepl return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Deepl translation failure, input:${input}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }
}