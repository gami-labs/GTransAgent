package net.gtransagent.translator.base

import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.gtransagent.grpc.LangItem
import net.gtransagent.grpc.ResultItem
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


abstract class FullBatchTranslator : ITranslator {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected val client: OkHttpClient by lazy {
        initOkHttpClient()
    }

    override fun destroy() {
        client.connectionPool.evictAll()
        logger.info("destroy")
    }


    override fun translate(
        requestId: String,
        targetLang: String,
        engineCode: String,
        isAutoTrans: Boolean,
        langItems: List<LangItem>,
        callback: (
            requestId: String, isAllItemTransFinished: Boolean, resultItems: List<ResultItem>, status: Status?
        ) -> Unit
    ) {
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


        val inputs = mutableListOf<String>()
        langItems.forEach { langItem ->
            langItem.inputItemListList.forEach { inputItem ->
                inputs.add(inputItem.input)
            }
        }

        // If all input items share the same source language, set sourceLang to that language. Otherwise, set sourceLang to null.
        val srcLang: String? = if (langItems.size == 1) {
            langItems.first().inputLang
        } else {
            null
        }

        val glossaryIgnoreCase: Boolean = langItems.first().inputItemListList.first().glossaryIgnoreCase
        val glossaryWords = mutableListOf<Pair<String, String>>()

        // only if all input items share the same source language
        if (langItems.size == 1) {
            langItems.first().inputItemListList.forEach { item ->
                item.glossaryListList.forEach { it ->
                    glossaryWords.add(Pair(it.srcWords, it.targetWords))
                }
            }
        }

        logger.info("${getName()} translate $requestId, targetLang:$targetLang, count: ${inputs.size}")
        try {
            val results = sendRequest(requestId, targetLang, inputs, srcLang, glossaryWords, glossaryIgnoreCase)

            val resultItems = mutableListOf<ResultItem>()

            var index = 0
            langItems.forEach { langItem ->
                langItem.inputItemListList.forEach { inputItem ->
                    if (results.size <= index) {
                        callback(requestId, true, emptyList(), Status.DATA_LOSS)
                        return
                    }
                    resultItems.add(
                        ResultItem.newBuilder().setId(inputItem.id).setResult(results[index++] ?: "").build()
                    )
                }
            }

            val resultStr = resultItems.joinToString(",") {
                "(${it.id}, ${it.result})"
            }

            val inputStr = langItems.joinToString(",") {
                "[${it.inputLang}]" + it.inputItemListList.joinToString(",") { inputItem ->
                    "(${inputItem.id}, ${inputItem.input})"
                }
            }

            logger.info(
                "${getName()} translate $requestId, targetLang:$targetLang, count: ${resultItems.size}, results: $resultStr, input:$inputStr"
            )
            callback.invoke(requestId, true, resultItems, Status.OK)
        } catch (e: Exception) {
            var status = Status.INTERNAL
            if (e is StatusRuntimeException) {
                status = e.status
            }
            callback(requestId, true, emptyList(), status)
        }
    }

    /**
     *
     * @param sourceLang, If all input items share the same source language, set sourceLang to that language. Otherwise, set sourceLang to null.
     */
    @Throws(Exception::class)
    abstract fun sendRequest(
        requestId: String,
        targetLang: String,
        inputs: List<String>,
        sourceLang: String? = null,
        glossaryWords: List<Pair<String, String>>? = null,
        glossaryIgnoreCase: Boolean = false
    ): List<String>

}