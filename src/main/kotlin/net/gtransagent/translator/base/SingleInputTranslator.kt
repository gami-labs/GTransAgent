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

abstract class SingleInputTranslator : ITranslator {
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
        return Executors.newFixedThreadPool(getConcurrent())
    }


    data class TranslateContext(
        val requestId: String,
        val targetLang: String,
        val totalCount: Int,
        @Volatile
        var transFinished: Int = 0,
        val engineCode: String,
        val isAutoTrans: Boolean,
        val callback: (
            requestId: String, isAllItemTransFinished: Boolean, resultItems: List<ResultItem>, status: Status?
        ) -> Unit
    )

    inner class TranslateHandler(
        private val translateContext: TranslateContext, private val srcLang: String, private val inputItem: InputItem
    ) : Runnable {
        override fun run() {
            val requestId = translateContext.requestId
            val targetLang = translateContext.targetLang
            val engineCode = translateContext.engineCode
            val isAutoTrans = translateContext.isAutoTrans
            val callback = translateContext.callback

            val itemId = inputItem.id
            val inputString = inputItem.input
            val glossaryIgnoreCase = inputItem.glossaryIgnoreCase
            val glossaryWordsList = inputItem.glossaryListList

            val glossaryWords = glossaryWordsList?.map {
                Pair(it.srcWords, it.targetWords)
            }

            try {
                val result = sendRequest(
                    requestId, srcLang, targetLang, inputString, engineCode, glossaryWords, glossaryIgnoreCase
                )
                callback(
                    requestId, ++translateContext.transFinished >= translateContext.totalCount, listOf(
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
                callback(
                    requestId, ++translateContext.transFinished >= translateContext.totalCount, emptyList(), status
                )
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
            requestId, targetLang, count, 0, engineCode = engineCode, isAutoTrans = isAutoTrans, callback = callback
        )

        langItems.forEach { langItem ->
            val srcLang = langItem.inputLang
            langItem.inputItemListList.forEach { inputItem ->
                val handler = TranslateHandler(translateContext, srcLang, inputItem)
                executor.submit(handler)
            }
        }
    }

    @Throws(Exception::class)
    abstract fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        input: String,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): String

}