package net.gtransagent.translator.experimental

import com.google.gson.Gson
import org.apache.commons.codec.binary.Base64
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Microsoft Experimental Translator
 * not need apiKey
 */
class MicrosoftExperimentalTranslator : SingleInputTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val gson = Gson()

    data class AuthToken(
        // token
        val token: String,
        //token expiration time, ms
        val tokenExp: Long
    )

    companion object {
        const val NAME = "MicrosoftExperimental"

        private val authTokenLock = Any()

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "microsoft_experimental"
            name = NAME
        })
    }

    private var url: String = "https://api-edge.cognitive.microsofttranslator.com/translate"
    private var authUrl: String = "https://edge.microsoft.com/translate/auth"

    // auth token
    private var authToken: AuthToken? = null

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
            logger.error("MicrosoftExperimentalTranslator init failed, url is invalid: $url")
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

        logger.info("MicrosoftExperimentalTranslator init success, supportedEngines: url:$url, $supportedEngines")
        return true
    }

    /**
     *
     * eg: eyJhbGciOiJFUzI1NiIsImtpZCI6ImtleTEiLCJ0eXAiOiJKV1QifQ.eyJyZWdpb24iOiJnbG9iYWwiLCJzdWJzY3JpcHRpb24taWQiOiI2ZjY1YjliY2JkNjA0ZDg4ODhiZWI2M2I4MTM4ODZlZSIsInByb2R1Y3QtaWQiOiJUZXh0VHJhbnNsYXRvci5TMyIsImNvZ25pdGl2ZS1zZXJ2aWNlcy1lbmRwb2ludCI6Imh0dHBzOi8vYXBpLmNvZ25pdGl2ZS5taWNyb3NvZnQuY29tL2ludGVybmFsL3YxLjAvIiwiYXp1cmUtcmVzb3VyY2UtaWQiOiIvc3Vic2NyaXB0aW9ucy84MWZjMTU3Yi0zMDdlLTRjMjEtOWY3MS0zM2QxMDMwNGRmMzMvcmVzb3VyY2VHcm91cHMvRWRnZV9UcmFuc2xhdGVfUkcvcHJvdmlkZXJzL01pY3Jvc29mdC5Db2duaXRpdmVTZXJ2aWNlcy9hY2NvdW50cy9UcmFuc2xhdGUiLCJzY29wZSI6Imh0dHBzOi8vYXBpLm1pY3Jvc29mdHRyYW5zbGF0b3IuY29tLyIsImF1ZCI6InVybjptcy5taWNyb3NvZnR0cmFuc2xhdG9yIiwiZXhwIjoxNzQ1MjM3MzgwLCJpc3MiOiJ1cm46bXMuY29nbml0aXZlc2VydmljZXMifQ.GbE5mbNG4tZPPT4CtDf_nI9GnLBsKZwFikSF_w25OUroLzdesshSJUCvV5IYRXkIswP7CkcL1yDEPSKytG5Mig
     *
     */
    private fun requestMsAuth(): AuthToken {
        val builder = Request.Builder()
        builder.url(authUrl)
        val request = builder.get().addHeader("Content-Type", "text/plain;charset=UTF-8").build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful && Objects.nonNull(response.body)) {
            val token = response.body!!.string()

            /**
             * {"region":"global","subscription-id":"6f65b9bcbd604d8888beb63b813886ee","product-id":"TextTranslator.S3","cognitive-services-endpoint":"https://api.cognitive.microsoft.com/internal/v1.0/","azure-resource-id":"/subscriptions/81fc157b-307e-4c21-9f71-33d10304df33/resourceGroups/Edge_Translate_RG/providers/Microsoft.CognitiveServices/accounts/Translate","scope":"https://api.microsofttranslator.com/","aud":"urn:ms.microsofttranslator","exp":1745237380,"iss":"urn:ms.cognitiveservices"}
             */
            val data = String(Base64.decodeBase64(token.split(".")[1]), StandardCharsets.UTF_8)
            val json = JSONObject(data)
            val tokenExp = json.getLong("exp") * 1000

            this.authToken = AuthToken(token, tokenExp)
            logger.info("Microsoft Experimental translation auth success, token:${this.authToken!!.token}, tokenExp:${this.authToken!!.tokenExp}")
            return this.authToken!!
        } else {
            logger.error("Microsoft Experimental translation auth failed, code:${response.code}")
            throw Status.UNAVAILABLE.withDescription("Microsoft Experimental translation auth failed, code:${response.code}")
                .asRuntimeException()
        }
    }

    private fun getAuthToken(): AuthToken {
        synchronized(authTokenLock){
            if (this.authToken == null) {
                return requestMsAuth()
            }
            if (this.authToken!!.tokenExp <= System.currentTimeMillis()) {
                return requestMsAuth()
            }
            return this.authToken!!
        }
    }

    private fun langConvert(origin: String): String {
        if (origin == "zh-CN" || origin == "zh_Hans") {
            return "zh-Hans"
        } else if (origin == "zh-TW" || origin == "zh_Hant") {
            return "zh-Hant"
        }
        return origin
    }

    @Throws(Exception::class)
    override fun sendRequest(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        input: String,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): String {
        try {
            logger.debug("Microsoft Experimental translateTexts start, requestId:$requestId, target:${targetLang}, input:${input}")

            val begin = System.currentTimeMillis()

            val urlBuilder = url.toHttpUrlOrNull()!!.newBuilder()
            urlBuilder.addQueryParameter("api-version", "3.0")
            urlBuilder.addQueryParameter("from", "") //auto
            urlBuilder.addQueryParameter("to", langConvert(targetLang))

            val jsonMap = mutableListOf<Map<String, String>>()
            jsonMap.add(mapOf(Pair("Text", input)))

            val payload = gson.toJson(jsonMap)
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val token = getAuthToken().token

            val builder = Request.Builder()
            builder.url(urlBuilder.build())
            val request = builder.post(body).addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $token").addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0"
                ).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()

                /**
                 * [{"detectedLanguage":{"language":"en","score":0.99},"translations":[{"text":"检查中断","to":"zh-Hans"}]}]
                 */
                val responseStr = response.body!!.string()
                val list = gson.fromJson(responseStr, List::class.java)
                val result = ((list[0] as Map<*, *>)["translations"] as List<Map<*, *>>)[0]["text"] as String?
                logger.info("Microsoft Experimental translateTexts end, requestId:$requestId, time:${end - begin}, result:${result}, input:${input}, target:${targetLang}")
                return result ?: ""
            } else {
                logger.error("Microsoft Experimental translation return code invalid, requestId:$requestId, input:${input}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Microsoft Experimental return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Microsoft Experimental translation failure, requestId:$requestId, input:${input}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

}