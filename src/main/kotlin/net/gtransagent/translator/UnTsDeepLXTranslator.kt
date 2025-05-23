package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

/**
 *
 * UnTsDeepLX
 * An unofficial but powerful and easy-to-use yet free DeepL API client for Node.js using DeepL by porting OwO-Network/UnTsDeepLX.
 * https://github.com/un-ts/deeplx
 *
 */
class UnTsDeepLXTranslator : SingleInputTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    companion object {
        const val NAME = "UnTsDeepLX"


        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "untsdeeplx"
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
            logger.error("UnTsDeepLXTranslator init failed, UnTsDeepLX url not found. Please set url in UnTsDeepLX.yaml.")
            return false
        }
        url = (configs["url"] as String)
        mConcurrent = (configs["concurrent"] as Int?) ?: 1

        logger.info("UnTsDeepLXTranslator init success, supportedEngines: $supportedEngines")
        return true
    }

    /**
     *
     * POST {"text": "have a try", "source_lang": "auto", "target_lang": "ZH"} to https://deeplx.vercel.app/translate
     * {"code":200,"data":".+Supports+invoking+privately+deployed+LLMs+like+Qwen-Turbo+%2C+Gemma+3+...+%28via+ol1ama+%29."}
     */
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

        logger.debug("UnTsDeepLX translateTexts start, sourceLang:$sourceLang, targetLang:${targetLang}, input:${input}")

        val begin = System.currentTimeMillis()
        val realTargetLang = if (targetLang.equals("EN", true)) {
            "EN-US"
        } else if (targetLang.equals("PT", true)) {
            "PT-PT"
        } else {
            CommonUtils.getLang(targetLang)
        }

        try {
            val bodyBuilder = FormBody.Builder()
                .add("source_lang", "auto")
                .add("target_lang", realTargetLang)
                .add("text", input)
            val body: RequestBody = bodyBuilder.build()
            val request: Request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val result = response.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                if ((map["code"] as Number).toInt() != 200) {
                    logger.error("UnTsDeepLX translation result code invalid, input:${input}, target:${targetLang}, code:${map["code"]}")
                    throw Status.UNAVAILABLE.withDescription("UnTsDeepLX result code invalid, code:${map["code"]}")
                        .asRuntimeException()
                }
                val translation = (map["data"] as String)
                val output = URLDecoder.decode(translation, "UTF-8")
                logger.info("UnTsDeepLX translateTexts end, time: ${end - begin} ms, target: ${targetLang}, results: ${output}, input: ${input}")
                return output
            } else {
                logger.error("UnTsDeepLX translation return code invalid,  input:${input}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("UnTsDeepLX return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "UnTsDeepLX translation failure, input:${input}, target:${targetLang}, error:${e}", e
            )
            throw e
        }
    }


}