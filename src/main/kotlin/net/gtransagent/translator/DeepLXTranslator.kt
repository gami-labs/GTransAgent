package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


/**
 *
 * DeepLX
 * https://deeplx.owo.network/
 *
 */
class DeepLXTranslator : SingleInputTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    companion object {
        const val NAME = "DeepLX"

        const val API = "/v2/translate"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "deeplx"
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

    private lateinit var url: String

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
        if ((configs["url"] as String?).isNullOrBlank()) {
            logger.error("DeepLXTranslator init failed, DeepLX url not found. Please set apiKey in DeepLX.yaml.")
            return false
        }
        url = (configs["url"] as String)
        mConcurrent = (configs["concurrent"] as Int?) ?: 1

        logger.info("DeepLXTranslator init success, supportedEngines: $supportedEngines")
        return true
    }

    override fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        input: String,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): String {
        if (!isSupported(sourceLang)) {
            logger.error("${getName()} translate failed, sourceLang:$sourceLang is not supported")
            throw Status.UNIMPLEMENTED.withDescription("${getName()} translate failed, sourceLang:$sourceLang is not supported")
                .asRuntimeException()
        }

        logger.debug("DeepLX translateTexts start, sourceLang:$sourceLang, targetLang:${targetLang}, input:${input}")

        val begin = System.currentTimeMillis()
        val realTargetLang = if (targetLang.equals("EN", true)) {
            "EN-US"
        } else if (targetLang.equals("PT", true)) {
            "PT-PT"
        } else {
            CommonUtils.getLang(targetLang)
        }

        try {
            val jsonMap = mutableMapOf<String, Any>()
            // \n => " ", \n causes DeepLX return code 503
            jsonMap["text"] = listOf(input.replace("\n", " "))
            jsonMap["target_lang"] = realTargetLang
            val payload = gson.toJson(jsonMap)
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val requestUrl = "$url$API"
            val request =
                Request.Builder().url(requestUrl).addHeader("Content-Type", "application/json").post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val result = response.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                val translations = (map["translations"] as List<Map<*, *>>)
                val output = (translations[0] as Map<String, String>)["text"] as String
                logger.info("DeepLX translateTexts end, time: ${end - begin} ms, target: ${targetLang}, results: ${output}, input: ${input}")
                return output
            } else {
                logger.error("DeepLX translation return code invalid,  input:${input}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("DeepLX return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "DeepLX translation failure, input:${input}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }


}