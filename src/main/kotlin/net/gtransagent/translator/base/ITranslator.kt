package net.gtransagent.translator.base

import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.grpc.LangItem
import net.gtransagent.grpc.ResultItem
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit


/**
 * Translator interface
 */
interface ITranslator {
    private val logger: Logger
        get() = LoggerFactory.getLogger(this::class.java)

    fun getConcurrent(): Int {
        return 1
    }

    /**
     * Get tranlator name, e.g. "Google", "DeepL"
     */
    fun getName(): String

    /**
     * Check if the translation engine supports the source and target languages
     * @param srcLanguage source language code, e.g. "en" "zh_Hans"
     * @param targetLanguage target language code, e.g. "en" "zh_Hans"
     */
    fun isSupported(srcLanguage: String, targetLanguage: String): Boolean

    /**
     * Check if the translation engine supports the source and target languages
     * @param targetLanguage target language code, e.g. "en" "zh_Hans"
     */
    fun isSupported(targetLanguage: String): Boolean

    /**
     * Get supported translation engines
     */
    fun getSupportedEngines(): List<PublicConfig.TranslateEngine>

    /**
     * Init translator
     * @param configs configuration read from config file
     * @return true if init successfully
     */
    fun init(configs: Map<*, *>): Boolean

    /**
     * Destroy translator
     */
    fun destroy()

    /**
     * Translation request
     * A translation request may contain original texts in multiple source languages; therefore, the sourceLang parameter might be inaccurate.
     * when isSourceLanguageUserSetToAuto is true, the sourceLang parameter is set to the language automatically detected based on all input texts. otherwise, it is set to the language selected by the user.
     * @param sourceLang when isSourceLanguageUserSetToAuto is true, sourceLang is set to the language automatically detected based on all input texts; otherwise, sourceLang is set to user selected language.
     * @param isSourceLanguageUserSetToAuto true if user selects "auto" as the source language
     */
    fun translate(
        requestId: String, // unique request id
        targetLang: String, // target language code, e.g. "zh_Hans"
        engineCode: String, // engine code, e.g. "gemma-3-4b"
        isAutoTrans: Boolean, // true if auto translation is enabled
        langItems: List<LangItem>, // translation items
        sourceLang: String, // source language, e.g. zh_Hans, en, when isSourceLanguageUserSetToAuto is true, sourceLang is set to the language automatically detected based on all input texts; otherwise, sourceLang is set to user selected language.
        isSourceLanguageUserSetToAuto: Boolean, // true if user selects "auto" as the source language
        callback: (
            requestId: String, // unique request id
            isAllItemTransFinished: Boolean, // true if all items are translated
            resultItems: List<ResultItem>, // translation result items
            status: Status? // if status is not null, it means error occurred
        ) -> Unit
    )


    fun initOkHttpClient(): OkHttpClient {
        val logInterceptor = HttpLoggingInterceptor { message -> logger.info(message) }
        logInterceptor.redactHeader("apikey")
        logInterceptor.redactHeader("Authorization")
        logInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
        val builder = OkHttpClient.Builder().connectTimeout(120, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).connectionPool(
                ConnectionPool(
                    getConcurrent(), 60L * 60, TimeUnit.SECONDS
                )
            ).pingInterval(30, TimeUnit.SECONDS).retryOnConnectionFailure(true)

        return builder.addNetworkInterceptor(logInterceptor).build()
    }
}