package net.gtransagent.translator.experimental

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.LanguageGroupedTranslator
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.net.URLEncoder
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Yandex Experimental Translator
 * not need apiKey
 */
class YandexExperimentalTranslator : LanguageGroupedTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()

    data class SidToken(
        // sid
        val sid: String,
        //sid expiration time, ms
        val sidExp: Long
    )

    companion object {
        const val NAME = "YandexExperimental"

        private val sidTokenLock = Any()

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "yandex_experimental"
            name = NAME
        })
    }

    private var url: String = "https://translate.yandex.net/api/v1/tr.json/translate"
    private var authUrl: String =
        "https://translate.yandex.net/website-widget/v1/widget.js?widgetId=ytWidget&pageLang=es&widgetTheme=light&autoMode=false"

    // sid token
    private var sidToken: SidToken? = null

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
            logger.error("YandexExperimentalTranslator init failed, url is invalid: $url")
            return false
        }

        if (configs["authUrl"] != null && (configs["authUrl"] as String).isNotBlank()) {
            authUrl = (configs["authUrl"] as String).trim()
        }

        try {
            URL(authUrl)
        } catch (e: Exception) {
            logger.error("MicrosoftExperimentalTranslator init failed, authUrl is invalid: $authUrl")
            return false
        }

        mConcurrent = (configs["concurrent"] as Int?) ?: 3

        logger.info("YandexExperimentalTranslator init success, supportedEngines: url:$url, $supportedEngines")
        return true
    }

    private fun requestSid(): SidToken {
        val builder = Request.Builder()
        builder.url(authUrl)
        val request = builder.get().addHeader("Content-Type", "text/plain;charset=UTF-8").build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful && Objects.nonNull(response.body)) {
            val responseText = response.body!!.string()
            // sid: 'c7145ef8.68074ae0.7cf18985.74722d75726c2d776964676574',
            val reg = Regex("sid\\:\\s\\'([0-9a-f\\.]+)\\'")
            val findResult = reg.find(responseText)
            val sid = findResult!!.groups[1]!!.value

            // 20 minutes
            val tokenExp = System.currentTimeMillis() + 1000 * 60 * 20

            this.sidToken = SidToken(sid, tokenExp)
            logger.info("Yandex Experimental translation auth success, token:${this.sidToken!!.sid}, tokenExp:${this.sidToken!!.sidExp}")
            return this.sidToken!!
        } else {
            logger.error("Yandex Experimental translation auth failed, code:${response.code}")
            throw Status.UNAVAILABLE.withDescription("Yandex Experimental translation auth failed, code:${response.code}")
                .asRuntimeException()
        }
    }

    private fun getSid(): SidToken {
        synchronized(sidTokenLock) {
            if (this.sidToken == null) {
                return requestSid()
            }
            if (this.sidToken!!.sidExp <= System.currentTimeMillis()) {
                return requestSid()
            }
            return this.sidToken!!
        }
    }

    @Throws(Exception::class)
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
            logger.debug("Yandex Experimental translateTexts start, requestId:$requestId, target:${targetLang}, inputs:${inputs}")

            val begin = System.currentTimeMillis()

            val sid = getSid().sid

            val urlBuilder = url.toHttpUrlOrNull()!!.newBuilder()
            urlBuilder.addQueryParameter("srv", "tr-url-widget")
            urlBuilder.addQueryParameter("id", "$sid-0-0")
            urlBuilder.addQueryParameter("format", "html")

            // not support auto
            urlBuilder.addQueryParameter(
                "lang",
                "${CommonUtils.getLang(sourceLang)}-${CommonUtils.getLang(targetLang)}"
            )

            inputs.forEach {
                urlBuilder.addQueryParameter("text", it)
            }
            val url = urlBuilder.build()
            logger.debug("Yandex Experimental translateTexts, requestId:$requestId, url: $url")
            val builder = Request.Builder()
            builder.url(url)
            val request = builder.get().addHeader("Content-Type", "application/x-www-form-urlencoded").addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0"
            ).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()

                /**
                 * {"code":200,"lang":"es-zh","nmt_code":200,"text":["部长坚持认为在他的发言在蒙克洛亚前法官的发型的妻子们的总统一直享有这种类型的帮手"]}
                 */
                val responseStr = response.body!!.string()
                logger.debug("Yandex Experimental translateTexts end, requestId:$requestId, responseStr: $responseStr")

                val map = gson.fromJson(responseStr, Map::class.java)
                val results = (map["text"] as List<String>)
                logger.info("Yandex Experimental translateTexts end, requestId:$requestId, time:${end - begin}, results:${results}, inputs:${inputs}, target:${targetLang}")
                return results
            } else {
                logger.error("Yandex Experimental translation return code invalid, requestId:$requestId, inputs:${inputs}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Yandex Experimental return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Yandex Experimental translation failure, requestId:$requestId, inputs:${inputs}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

}