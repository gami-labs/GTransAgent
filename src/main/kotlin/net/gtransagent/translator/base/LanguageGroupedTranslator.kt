package net.gtransagent.translator.base

import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.gtransagent.grpc.InputItem
import net.gtransagent.grpc.LangItem
import net.gtransagent.grpc.ResultItem
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

abstract class LanguageGroupedTranslator : ITranslator {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected var mConcurrent: Int = 1

    override fun getConcurrent(): Int {
        return mConcurrent
    }

    protected val client: OkHttpClient by lazy {
        initOkHttpClient()
    }

    override fun destroy() {
        client.connectionPool.evictAll()
        executor.shutdownNow()
        logger.info("destroy")
    }

    private val executor: ExecutorService by lazy {
        defaultExecutor()
    }

    private fun defaultExecutor(): ExecutorService {
        return Executors.newFixedThreadPool(mConcurrent)
    }


    data class TranslateContext(
        val requestId: String,
        val targetLang: String,
        val totalCount: Int,
        val transFinished: AtomicInteger = AtomicInteger(0),
        val engineCode: String,
        val isAutoTrans: Boolean,
        val callback: (
            requestId: String, isAllItemTransFinished: Boolean, resultItems: List<ResultItem>, status: Status?
        ) -> Unit
    )

    inner class TranslateHandler(
        private val translateContext: TranslateContext,
        private val srcLang: String,
        private val inputItems: List<InputItem>
    ) : Runnable {
        override fun run() {
            val requestId = translateContext.requestId
            val targetLang = translateContext.targetLang
            val engineCode = translateContext.engineCode
            val isAutoTrans = translateContext.isAutoTrans
            val callback = translateContext.callback

            val glossaryWords = mutableListOf<Pair<String, String>>()
            val glossaryIgnoreCase: Boolean = inputItems.first().glossaryIgnoreCase

            inputItems.forEach { item ->
                item.glossaryListList.forEach { it ->
                    glossaryWords.add(Pair(it.srcWords, it.targetWords))
                }
            }

            val inputs = mutableListOf<String>()
            inputItems.forEach { item ->
                inputs.add(item.input)
            }

            fun emitResult(itemId: Int, text: String, status: Status?) {
                val finishedCount = translateContext.transFinished.incrementAndGet()
                val isAllFinished = finishedCount >= translateContext.totalCount
                callback(
                    requestId, isAllFinished,
                    listOf(ResultItem.newBuilder().setId(itemId).setResult(text).build()),
                    status
                )
            }

            try {
                val result = sendRequest(
                    requestId,  srcLang, targetLang, inputs, engineCode, glossaryWords, glossaryIgnoreCase
                )

                for (i in inputs.indices) {
                    val itemId = inputItems[i].id
                    if (i >= result.size) {
                        logger.error("${getName()} translate failed, index out of bounds: $i, result size: ${result.size}")
                        emitResult(itemId, "", Status.INTERNAL)
                        continue
                    }
                    val output = result[i]
                    emitResult(itemId, output, null)
                }
            } catch (e: Exception) {
                logger.error("${getName()} translate failed, error: $e")
                val status = if (e is StatusRuntimeException) {
                    e.status
                } else {
                    Status.INTERNAL
                }
                inputItems.forEach { item ->
                    emitResult(item.id, "", status)
                }
            }
        }
    }


    override fun translate(
        requestId: String,
        targetLang: String,
        engineCode: String,
        isAutoTrans: Boolean,
        langItems: List<LangItem>,
        sourceLang: String, // source language, e.g. zh_Hans, en, when isSourceLanguageSetToAuto is true, sourceLang is set to the language automatically detected based on all input texts; otherwise, sourceLang is set to user selected language.
        isSourceLanguageUserSetToAuto: Boolean, // true if user selects "auto" as the source language
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

        val count = langItems.sumOf { it.inputItemListList.size }
        logger.info("${getName()} translate $requestId, count: $count")

        val translateContext = TranslateContext(
            requestId, targetLang, count, engineCode = engineCode, isAutoTrans = isAutoTrans, callback = callback
        )

        langItems.forEach { langItem ->
            val srcLang = langItem.inputLang
            val handler = TranslateHandler(translateContext, srcLang, langItem.inputItemListList)
            executor.submit(handler)
        }
    }

    @Throws(Exception::class)
    abstract fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        inputs: List<String>,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String>


}
