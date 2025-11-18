package net.gtransagent.translator

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.internal.LangCodes
import net.gtransagent.translator.base.LanguageGroupedTranslator
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*

/**
 *
 * https://www.anthropic.com/claude
 *
 */
class AnthropicTranslator : LanguageGroupedTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    private val disableHtmlEscapingGson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    val DEFAULT_SYSTEM_PROMPTS = """
        You are a professional translation processor. Strictly follow:
        1. Maintain strict ID-text correspondence
        2. Detect and correct OCR errors (e.g., character confusion, missing symbols)
        3. Apply vocabulary replacements during translation
        4. Preserve JSON structure with proper escaping
        5. Output only required translations
    """.trimIndent()

    val DEFAULT_USER_PROMPTS = """
        Translate JSON entries from {{srcLang}} to {{targetLang}}:
        
        Input Format:
        [{"id":1,"text":"Source Text"}]
        
        Output Format:
        {"translations":[{"id":1,"text":"Translated Text"}]}
        
        Vocabulary (case-{{glossarySensitive}}):
        {{glossaryList}}
        
        Instructions:
        1. Processing sequence:
           - Phase 1: Fix OCR errors (e.g., 1↔l, 0↔O, missing punctuation)
           - Phase 2: Translate text while applying vocabulary replacements
           - Phase 3: Ensure JSON-safe escaping for all outputs
        2. Preserve numerical values and technical codes unchanged
        3. Never add explanations or non-requested content
        
        
        BEGIN INPUT:
        {{original}}
    """.trimIndent()

    companion object {
        const val NAME = "Anthropic"
    }

    private var url: String = "https://api.anthropic.com/v1/messages"
    private var systemPrompts = DEFAULT_SYSTEM_PROMPTS
    private var userPrompts = DEFAULT_USER_PROMPTS

    private var engineAndModelMap: MutableMap<String, String> = mutableMapOf()
    private var supportedEngines: MutableList<PublicConfig.TranslateEngine> = mutableListOf()

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
            logger.error("ClaudeTranslator init failed, url is invalid: $url")
            return false
        }

        val fileApiKey = (configs["apiKey"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("ANTHROPIC_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("ClaudeTranslator init failed, OpenAI apiKey not found. Please set apiKey in OpenAI.yaml or ANTHROPIC_API_KEY in environment.")
            return false
        }

        mConcurrent = (configs["concurrent"] as Int?) ?: 1
        systemPrompts = ((configs["systemPrompts"] as String?) ?: DEFAULT_SYSTEM_PROMPTS).trim()
        userPrompts = ((configs["userPrompts"] as String?) ?: DEFAULT_USER_PROMPTS).trim()

        if (configs["engineMapping"] == null || (configs["engineMapping"] as Map<String, String>).isEmpty()) {
            logger.error("ClaudeTranslator init failed, engineMapping is null or empty")
            return false
        }

        try {
            val engineMapping = (configs["engineMapping"] as Map<String, Map<String, String>>)
            engineMapping.forEach { (engineCode, value) ->
                val engineName = value["name"] as String
                val engineModel = value["model"] as String

                supportedEngines.add(PublicConfig.TranslateEngine().apply {
                    code = engineCode
                    name = engineName
                })

                engineAndModelMap[engineCode] = engineModel
            }
        } catch (e: Exception) {
            logger.error("ClaudeTranslator init failed, engineMapping is invalid: $e")
            return false
        }

        logger.info("ClaudeTranslator init success, url: $url, supportedEngines: $supportedEngines")
        return true
    }


    private fun formatSystemPromptWords(
        srcLangLangName: String,
        targetLangLangName: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): String {
        return systemPrompts.replace("{{targetLang}}", LangCodes.langNameUppercase(targetLangLangName))
            .replace("{{srcLang}}", LangCodes.langNameUppercase(srcLangLangName))
            .replace("{{glossarySensitive}}", if (glossaryIgnoreCase) "insensitive" else "sensitive").trim()
    }

    private fun formatPromptWords(
        srcLangLangName: String,
        targetLangLangName: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean,
        text: String
    ): String {
        val glossaryListStr = if (glossaryWords.isNullOrEmpty()) {
            ""
        } else {
            /**
             * {
             *   "test": "检测",
             *   "internet": "互联网"
             * }
             *
             */
            val map = mutableMapOf<String, String>()
            glossaryWords.forEach { item ->
                map[item.first] = item.second
            }
            gson.toJson(map)
        }
        val promptWords = userPrompts.replace("{{targetLang}}", LangCodes.langNameUppercase(targetLangLangName))
            .replace("{{srcLang}}", LangCodes.langNameUppercase(srcLangLangName))
            .replace("{{glossaryList}}", glossaryListStr)
            .replace("{{glossarySensitive}}", if (glossaryIgnoreCase) "insensitive" else "sensitive")
            .replace("{{original}}", text)

        return promptWords
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
        try {
            val srcLangName = LangCodes.langCodeToName(sourceLang)
            val targetLangName = LangCodes.langCodeToName(targetLang)

            val systemMap = mutableMapOf(
                Pair("role", "system")
            )

            systemMap["content"] = formatSystemPromptWords(
                srcLangName, targetLangName, glossaryWords, glossaryIgnoreCase
            )

            val userMap = mutableMapOf(
                Pair("role", "user")
            )

            val inputStr = CommonUtils.textsToJson(inputs)

            val promptWords = formatPromptWords(
                srcLangName, targetLangName, glossaryWords, glossaryIgnoreCase, inputStr
            )

            userMap["content"] = promptWords

            val jsonMap = mutableMapOf<String, Any>(
                Pair("messages", mutableListOf(systemMap, userMap))
            )

            //jsonMap["response_format"] = mapOf(Pair("type", "json_object"))
            jsonMap["model"] = engineAndModelMap[engineCode]!!

            val payload = disableHtmlEscapingGson.toJson(jsonMap)

            logger.info("ClaudeTranslator translateTexts begin, payload: $payload, src: $sourceLang, target: $targetLang, engine:$engineCode")

            val begin = System.currentTimeMillis()
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val builder = Request.Builder()
            builder.url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
            val request = builder.post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val result = response.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                var content = ((map["content"] as List<Map<*, *>>)[0]["text"] as String).trim()

                content = content.replace(" ", " ")
                content = content.trim()

                if (content.startsWith("[")) {
                    content = "{\"translations\":$content}"
                }

                val results = CommonUtils.jsonStrToList(content, inputs)
                logger.info("ClaudeTranslator translateTexts end, time: ${end - begin} ms, result: ${results}, input: ${inputs}, src: ${sourceLang}, target: ${targetLang}, engine:${engineCode}")
                return results
            } else {
                logger.error("ClaudeTranslator return code invalid,  input:${inputs}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("ClaudeTranslator return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "ClaudeTranslator failure, input:${inputs}, error:${e}", e
            )
            throw e
        }
    }
}
