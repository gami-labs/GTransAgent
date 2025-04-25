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
 * https://aistudio.google.com/app/apikey
 *
 */
class GeminiTranslator : LanguageGroupedTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    private val disableHtmlEscapingGson = GsonBuilder().disableHtmlEscaping().create()

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
        
        Vocabulary (case-{{{glossarySensitive}}}):
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
        const val NAME = "Gemini"
    }

    private var url: String = "https://generativelanguage.googleapis.com/v1beta/models/"
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
            logger.error("GeminiTranslator init failed, url is invalid: $url")
            return false
        }

        val fileApiKey = (configs["apiKey"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("GEMINI_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("GeminiTranslator init failed, Gemini apiKey not found. Please set apiKey in Gemini.yaml or GEMINI_API_KEY in environment.")
            return false
        }

        mConcurrent = (configs["concurrent"] as Int?) ?: 1
        systemPrompts = ((configs["systemPrompts"] as String?) ?: DEFAULT_SYSTEM_PROMPTS).trim()
        userPrompts = ((configs["userPrompts"] as String?) ?: DEFAULT_USER_PROMPTS).trim()

        if (configs["engineMapping"] == null || (configs["engineMapping"] as Map<String, String>).isEmpty()) {
            logger.error("GeminiTranslator init failed, engineMapping is null or empty")
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
            logger.error("GeminiTranslator init failed, engineMapping is invalid: $e")
            return false
        }

        logger.info("GeminiTranslator init success, url: $url, supportedEngines: $supportedEngines")
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

            val systemMap = mutableMapOf<String, Any>(
                Pair(
                    "parts", listOf(
                        mapOf(
                            Pair(
                                "text", formatSystemPromptWords(
                                    srcLangName, targetLangName, glossaryWords, glossaryIgnoreCase
                                )
                            )
                        )
                    )
                )
            )

            val inputStr = CommonUtils.textsToJson(inputs)

            val promptWords = formatPromptWords(
                srcLangName, targetLangName, glossaryWords, glossaryIgnoreCase, inputStr
            )
            val contentsMap = mutableMapOf<String, Any>(
                Pair(
                    "parts", listOf(
                        mapOf(
                            Pair(
                                "text", promptWords
                            )
                        )
                    )
                )
            )

            val jsonMap = mutableMapOf<String, Any>(
                Pair("system_instruction", systemMap), Pair("contents", contentsMap)
            )

            jsonMap["generationConfig"] = mapOf(Pair("response_mime_type", "application/json"))

            jsonMap["safetySettings"] = listOf(
                mapOf(
                    Pair("category", "HARM_CATEGORY_HARASSMENT"),
                    Pair("threshold", "BLOCK_NONE"),
                ), mapOf(
                    Pair("category", "HARM_CATEGORY_HATE_SPEECH"),
                    Pair("threshold", "BLOCK_NONE"),
                ), mapOf(
                    Pair("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT"),
                    Pair("threshold", "BLOCK_NONE"),
                ), mapOf(
                    Pair("category", "HARM_CATEGORY_DANGEROUS_CONTENT"),
                    Pair("threshold", "BLOCK_NONE"),
                ), mapOf(
                    Pair("category", "HARM_CATEGORY_CIVIC_INTEGRITY"),
                    Pair("threshold", "BLOCK_NONE"),
                )
            )


            val payload = disableHtmlEscapingGson.toJson(jsonMap)

            logger.info("GeminiTranslator translateTexts begin, payload: $payload, src: $sourceLang, target: $targetLang, engine:$engineCode")

            val begin = System.currentTimeMillis()
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val builder = Request.Builder()
            val requestUrl = "${url}${engineAndModelMap[engineCode]}:generateContent?key=${apiKey}"

            builder.url(requestUrl).addHeader("content-type", "application/json")
            val request = builder.post(body).build()

            /**
             * {
             *   "candidates": [
             *     {
             *       "content": {
             *         "parts": [
             *           {
             *             "text": "AI learns patterns from data to make predictions or decisions.\n"
             *           }
             *         ],
             *         "role": "model"
             *       },
             *       "finishReason": "STOP",
             *       "avgLogprobs": -0.275710125764211
             *     }
             *   ],
             *   "usageMetadata": {
             *     "promptTokenCount": 8,
             *     "candidatesTokenCount": 12,
             *     "totalTokenCount": 20,
             *     "promptTokensDetails": [
             *       {
             *         "modality": "TEXT",
             *         "tokenCount": 8
             *       }
             *     ],
             *     "candidatesTokensDetails": [
             *       {
             *         "modality": "TEXT",
             *         "tokenCount": 12
             *       }
             *     ]
             *   },
             *   "modelVersion": "gemini-2.0-flash"
             * }
             *
             */
            val response = client.newCall(request).execute()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val result = response.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                val parts =
                    (((map["candidates"] as List<Map<*, *>>)[0]["content"]) as Map<*, *>)["parts"] as List<Map<*, *>>
                var content = parts[0]["text"].toString()

                content = content.trim()
                if (content.startsWith("[") && !content.contains("translations")) {
                    content = "{\"translations\":$content}"
                } else if (content.startsWith("[") && content.endsWith("]") && content.contains("translations")) {
                    content = content.substring(1, content.length - 1)
                }

                val results = CommonUtils.jsonStrToList(content, inputs)
                logger.info("GeminiTranslator translateTexts end, time: ${end - begin} ms, result: ${results}, input: ${inputs}, src: ${sourceLang}, target: ${targetLang}, engine:${engineCode}")
                return results
            } else {
                logger.error("GeminiTranslator return code invalid,  input:${inputs}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("GeminiTranslator return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "GeminiTranslator failure, input:${inputs}, error:${e}", e
            )
            throw e
        }
    }
}