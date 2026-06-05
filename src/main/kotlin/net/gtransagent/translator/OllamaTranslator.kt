package net.gtransagent.translator

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.gtransagent.core.PublicConfig
import net.gtransagent.grpc.LangItem
import net.gtransagent.grpc.ResultItem
import net.gtransagent.internal.AiTransContext
import net.gtransagent.internal.LangCodes
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class OllamaTranslator : SingleInputTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val NAME = "Ollama"
    }

    private val gson = Gson()
    private val disableHtmlEscapingGson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    val DEFAULT_SYSTEM_PROMPTS = """
        You are a professional translator to {{targetLang}}.
        Source text is from OCR and may have minor errors—infer the intended meaning.
        Produce only the {{targetLang}} translation, no explanations or commentary.
        Keep abbreviations, codes, and identifiers as-is.
        Maintain original formatting (e.g., line breaks, punctuation).
    """.trimIndent()

    val DEFAULT_USER_PROMPTS = """
        Vocabulary (case-{{glossarySensitive}}):
        {{glossaryList}}

        Translate to {{targetLang}}:
        {{original}}
    """.trimIndent()

    private var systemPrompts = DEFAULT_SYSTEM_PROMPTS
    private var userPrompts = DEFAULT_USER_PROMPTS

    private lateinit var url: String

    /**
     * Whether to pass surrounding input items as context when translating.
     * When enabled, previous and next items in the same batch are included as context
     * to help the LLM maintain translation consistency. Default is true.
     */
    private var enableContext: Boolean = true

    /**
     * Whether to enable thinking mode in models that support it.
     */
    private var enableThinking: Boolean = false

    private var engineAndModelMap: MutableMap<String, String> = mutableMapOf()
    private var supportedEngines: MutableList<PublicConfig.TranslateEngine> = mutableListOf()

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
        if (configs["url"] == null) {
            logger.error("OllamaTranslator init failed, url is null")
            return false
        }
        url = (configs["url"] as String).trim()

        try {
            URL(url)
        } catch (e: Exception) {
            logger.error("OllamaTranslator init failed, url is invalid: $url")
            return false
        }

        mConcurrent = (configs["concurrent"] as Int?) ?: 1
        enableContext = (configs["enableContext"] as Boolean?) ?: true
        enableThinking = (configs["enableThinking"] as Boolean?) ?: false
        systemPrompts = ((configs["systemPrompts"] as String?) ?: DEFAULT_SYSTEM_PROMPTS).trim()
        userPrompts = ((configs["userPrompts"] as String?) ?: DEFAULT_USER_PROMPTS).trim()

        if (configs["engineMapping"] == null || (configs["engineMapping"] as Map<String, Map<String, String>>).isEmpty()) {
            logger.error("OllamaTranslator init failed, engineMapping is null or empty")
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
            logger.error("OllamaTranslator init failed, engineMapping is invalid: $e")
            return false
        }

        logger.info("OllamaTranslator init success, url: $url, concurrent: $mConcurrent, enableContext: $enableContext, supportedEngines: $supportedEngines")
        return true
    }

    private fun formatSystemPromptWords(
        srcLangLangName: String,
        targetLangLangName: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean = true
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
            glossaryWords.joinToString("\n") { item ->
                "- \"${item.first.replace("\"", " ")}\": \"${item.second.replace("\"", " ")}\"]"
            }
        }
        val promptWords = userPrompts.replace("{{targetLang}}", LangCodes.langNameUppercase(targetLangLangName))
            .replace("{{srcLang}}", LangCodes.langNameUppercase(srcLangLangName))
            .replace("{{glossaryList}}", glossaryListStr)
            .replace("{{glossarySensitive}}", if (glossaryIgnoreCase) "insensitive" else "sensitive")
            .replace("{{original}}", text)

        return promptWords
    }

    /**
     * Override translate to inject surrounding items as context for each input when enableContext is true.
     * When enableContext is enabled, collects all input texts from the batch and builds
     * prev/next context for each item using AiTransContext.
     */
    override fun translate(
        requestId: String,
        targetLang: String,
        engineCode: String,
        isAutoTrans: Boolean,
        langItems: List<LangItem>,
        sourceLang: String,
        isSourceLanguageUserSetToAuto: Boolean,
        previousTranslationInputs: List<String>,
        customPrompt: String,
        callback: (
            requestId: String, isAllItemTransFinished: Boolean, resultItems: List<ResultItem>, status: Status?
        ) -> Unit
    ) {
        if (!enableContext) {
            // Delegate to parent SingleInputTranslator when context is disabled
            super.translate(
                requestId, targetLang, engineCode, isAutoTrans, langItems, sourceLang,
                isSourceLanguageUserSetToAuto, previousTranslationInputs, customPrompt, callback
            )
            return
        }

        if (getSupportedEngines().none { x -> x.code == engineCode }) {
            logger.error("${getName()} translate failed, engineCode is invalid: $engineCode")
            callback(requestId, true, emptyList(), Status.INVALID_ARGUMENT)
            return
        }

        if (!isSupported(targetLang)) {
            logger.error("${getName()} translate failed, targetLang:$targetLang is not supported")
            callback(requestId, true, emptyList(), Status.UNIMPLEMENTED)
            return
        }

        // Collect all input texts in order for context building
        data class ItemInfo(
            val srcLang: String,
            val inputItem: net.gtransagent.grpc.InputItem,
            val indexInAll: Int
        )

        val allInputTexts = mutableListOf<String>()
        val itemInfoList = mutableListOf<ItemInfo>()

        langItems.forEach { langItem ->
            val srcLang = langItem.inputLang
            langItem.inputItemListList.forEach { inputItem ->
                val idx = allInputTexts.size
                allInputTexts.add(inputItem.input)
                itemInfoList.add(ItemInfo(srcLang, inputItem, idx))
            }
        }

        val count = itemInfoList.size
        logger.info("${getName()} translate $requestId, count: $count, enableContext: true")

        val transFinished = java.util.concurrent.atomic.AtomicInteger(0)

        itemInfoList.forEach { info ->
            executor.submit {
                val inputItem = info.inputItem
                val itemId = inputItem.id
                val inputString = inputItem.input
                val glossaryIgnoreCase = inputItem.glossaryIgnoreCase
                val glossaryWords = inputItem.glossaryListList?.map {
                    Pair(it.srcWords, it.targetWords)
                }

                // Build neighbor context from surrounding items in the batch
                val prevContext = AiTransContext.buildContext(info.indexInAll, allInputTexts, true)
                val nextContext = AiTransContext.buildContext(info.indexInAll, allInputTexts, false)

                // Combine neighbor context with previousTranslationInputs
                val combinedContext = mutableListOf<String>()
                if (previousTranslationInputs.isNotEmpty()) {
                    combinedContext.addAll(previousTranslationInputs)
                }
                if (prevContext.isNotEmpty()) {
                    combinedContext.addAll(prevContext)
                }
                if (nextContext.isNotEmpty()) {
                    combinedContext.addAll(nextContext)
                }

                try {
                    val result = sendRequest(
                        requestId, info.srcLang, targetLang, inputString, engineCode,
                        glossaryWords, glossaryIgnoreCase, combinedContext, customPrompt
                    )
                    val finished = transFinished.incrementAndGet()
                    callback(
                        requestId, finished >= count, listOf(
                            ResultItem.newBuilder().setId(itemId).setResult(result).build()
                        ), null
                    )
                } catch (e: Exception) {
                    logger.error("${getName()} translate failed, error: $e")
                    val status = if (e is StatusRuntimeException) {
                        e.status
                    } else {
                        Status.INTERNAL
                    }
                    val finished = transFinished.incrementAndGet()
                    callback(
                        requestId, finished >= count, emptyList(), status
                    )
                }
            }
        }
    }

    /**
     *
     *
     * {
     * 	"model": "gemma3:4b",
     * 	"created_at": "2025-03-29T03:42:51.5483029Z",
     * 	"message": {
     * 		"role": "assistant",
     * 		"content": "在哥伦比亚大学的示威营地，那些引发全国范围内反巴圣徒浪潮的学生，在周五坚守了第十天，从加利福尼亚到康涅狄格的大学校园管理人员和警方正在努力应对这些示威活动，这些活动出现了与警察的打斗以及数百人被捕的情况。"
     * 	},
     * 	"done_reason": "stop",
     * 	"done": true,
     * 	"total_duration": 5956194700,
     * 	"load_duration": 2540440800,
     * 	"prompt_eval_count": 89,
     * 	"prompt_eval_duration": 275571600,
     * 	"eval_count": 74,
     * 	"eval_duration": 3139558600
     * }
     *
     */
    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        input: String,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean,
        previousTranslationInputs: List<String>,
        customPrompt: String
    ): String {
        try {
            val systemMap = mutableMapOf(
                Pair("role", "system")
            )

            val srcLangName = LangCodes.langCodeToName(sourceLang)
            val targetLangName = LangCodes.langCodeToName(targetLang)

            // Build system prompt, append custom prompt if provided
            var systemContent = formatSystemPromptWords(
                srcLangName, targetLangName, glossaryWords, glossaryIgnoreCase
            )
            if (customPrompt.isNotBlank()) {
                systemContent += "\n$customPrompt"
            }
            if (previousTranslationInputs.isNotEmpty()) {
                systemContent += "\nPrevious translations for context (DO NOT translate, for reference only):\n${
                    previousTranslationInputs.joinToString(
                        "\n"
                    )
                }"
            }
            systemMap["content"] = systemContent

            val promptWords = formatPromptWords(
                srcLangName, targetLangName, glossaryWords, glossaryIgnoreCase, input
            )

            val messagesMap = mutableMapOf(
                Pair("role", "user"), Pair("content", promptWords)
            )

            val jsonMap = mutableMapOf<String, Any>(
                Pair("messages", mutableListOf(systemMap, messagesMap))
            )

            jsonMap["model"] = engineAndModelMap[engineCode]!!
            jsonMap["stream"] = false
            jsonMap["keep_alive"] = "30m"
            jsonMap["think"] = enableThinking

            val payload = disableHtmlEscapingGson.toJson(jsonMap)

            logger.debug("Ollama translateTexts start, input:${input}, src:${sourceLang}, target:${targetLang}, engine:${engineCode}, payload:${payload}")

            val begin = System.currentTimeMillis()
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val builder = Request.Builder()

            builder.url(url).addHeader("Content-Type", "application/json")

            val request = builder.post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val result = response.body!!.string()
                val map = gson.fromJson(result, Map::class.java)
                var content = (map["message"] as Map<*, *>)["content"].toString()
                content = content.trim()
                logger.info("Ollama translateTexts end, time: ${end - begin} ms, result: ${content}, input: ${input}, src: ${sourceLang}, target: ${targetLang}, engine:${engineCode}")
                return content
            } else {
                logger.error("Ollama return code invalid,  input:${input}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Ollama return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Ollama failure, input:${input}, error:${e}", e
            )
            throw e
        }
    }


}