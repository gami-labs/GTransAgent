package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.FullBatchTranslator
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.codec.binary.Hex
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


/**
 *
 * https://translate.volcengine.com/api
 *
 */
class VolcengineTranslator : FullBatchTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    companion object {
        private const val URL = "https://translate.volcengineapi.com"

        const val NAME = "Volcengine"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "volcengine"
            name = NAME
        })
    }

    private lateinit var apiKey: String
    private lateinit var apiSecret: String

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
        val fileApiKey = (configs["apiKey"] as String?)

        apiKey = if (fileApiKey.isNullOrBlank()) {
            (System.getenv("VOLCENGINE_TRANSLATION_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("VolcengineTranslator init failed, Volcengine apiKey not found. Please set apiKey in Volcengine.yaml or VOLCENGINE_TRANSLATION_API_KEY in environment.")
            return false
        }

        val fileApiSecret = (configs["apiSecret"] as String?)
        apiSecret = if (fileApiSecret.isNullOrBlank()) {
            (System.getenv("VOLCENGINE_TRANSLATION_API_SECRET") ?: "").trim()
        } else {
            fileApiSecret.trim()
        }

        if (apiSecret.isBlank()) {
            logger.error("VolcengineTranslator init failed, Volcengine apiSecret not found. Please set apiSecret in Volcengine.yaml or VOLCENGINE_TRANSLATION_API_SECRET in environment.")
            return false
        }

        logger.info("VolcengineTranslator init success, supportedEngines: $supportedEngines")
        return true
    }

    private fun getCurrentUTCTime(): String {
        val utcTime = ZonedDateTime.now(ZoneOffset.UTC)
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        return utcTime.format(formatter)
    }

    @Throws(java.lang.Exception::class)
    private fun hashSHA256(content: ByteArray?): String {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            return Hex.encodeHexString(md.digest(content))
        } catch (e: java.lang.Exception) {
            throw java.lang.Exception(
                "Unable to compute hash while signing request: " + e.message, e
            )
        }
    }

    @Throws(java.lang.Exception::class)
    private fun hmacSHA256(key: ByteArray, content: String): ByteArray {
        try {
            val mac: Mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(content.toByteArray())
        } catch (e: java.lang.Exception) {
            throw java.lang.Exception(
                "Unable to calculate a request signature: " + e.message, e
            )
        }
    }

    @Throws(java.lang.Exception::class)
    private fun genSigningSecretKeyV4(secretKey: String, date: String, region: String, service: String): ByteArray {
        val kDate = hmacSHA256((secretKey).toByteArray(), date)
        val kRegion = hmacSHA256(kDate, region)
        val kService = hmacSHA256(kRegion, service)
        return hmacSHA256(kService, "request")
    }


    /**
     *
     * curl -X POST \
     *   'https://translate.volcengineapi.com/?Action=TranslateText&Version=2020-06-01' \
     *   -H 'Authorization: HMAC-SHA256 Credential=AKLTN2RlOTdmZWQ5NjI5NDBhM2FmMGU5NmU0Zjg3NjYxMmM/20250419/cn-beijing/translate/request, SignedHeaders=host;x-content-sha256;x-date, Signature=17a83df5eec6a8ab8d87de875776e3699a36188fa863d8e57da4cf8519f54340' \
     *   -H 'Content-Type: application/json' \
     *   -H 'Host: translate.volcengineapi.com' \
     *   -H 'X-Content-Sha256: 3975637cef98905529e559581015d3eefa4d4070df233cb807f8948b94a4b5d1' \
     *   -H 'X-Date: 20250419T043014Z' \
     *   -d '{"SourceLanguage":"en","TargetLanguage":"zh","TextList":["test"]}'
     *
     *   {"TranslationList":[{"Translation":"测试","DetectedSourceLanguage":"","Extra":{"input_characters":"4","source_language":"en"}}],"ResponseMetadata":{"RequestId":"20250419152412A8B9214C50F565673B81","Action":"TranslateText","Version":"2020-06-01","Service":"translate","Region":"cn-beijing"},"ResponseMetaData":{"RequestId":"20250419152412A8B9214C50F565673B81","Action":"TranslateText","Version":"2020-06-01","Service":"translate","Region":"cn-beijing"}}
     *
     */
    override fun sendRequest(
        requestId: String,
        targetLang: String,
        inputs: List<String>,
        sourceLang: String?,
        isSourceLanguageUserSetToAuto: Boolean, // true if user selects "auto" as the source language
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        try {
            logger.debug("Volcengine translateTexts start, requestId:$requestId, source:${sourceLang}, target:${targetLang}, inputs:${inputs}")

            val begin = System.currentTimeMillis()

            val jsonMap = mutableMapOf<String, Any>(
                Pair("TargetLanguage", CommonUtils.getLang(targetLang)),
                Pair("TextList", inputs),
            )

            val payload = gson.toJson(jsonMap)

            logger.debug("Volcengine translateTexts, requestId:$requestId, source:${sourceLang}, target:${targetLang}, payload:${payload}")

            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val xDate = getCurrentUTCTime()
            val shortXDate: String = xDate.substring(0, 8)
            val region = "cn-beijing"
            val service = "translate"
            val signHeader = "host;x-content-sha256;x-date"

            val urlBuilder = URL.toHttpUrlOrNull()!!.newBuilder()
            urlBuilder.addQueryParameter("Action", "TranslateText")
            urlBuilder.addQueryParameter("Version", "2020-06-01")

            val xContentSha256 = hashSHA256(payload.toByteArray())

            val canonicalRequest =
                "POST\n" + "/\n" + "Action=TranslateText&Version=2020-06-01\n" + "host:translate.volcengineapi.com\n" + "x-content-sha256:$xContentSha256\n" + "x-date:$xDate\n" + "\n" + "$signHeader\n" + "$xContentSha256"

            val hashcanonicalString = hashSHA256(canonicalRequest.toByteArray(Charsets.UTF_8))
            val credentialScope = "$shortXDate/$region/$service/request"
            val signString = "HMAC-SHA256\n" + "$xDate\n" + "$credentialScope\n" + "$hashcanonicalString"

            val signKey = genSigningSecretKeyV4(apiSecret, shortXDate, region, service)
            val signature = Hex.encodeHexString(hmacSHA256(signKey, signString))

            val url = urlBuilder.build().toString()
            val builder = Request.Builder().addHeader("X-Date", xDate).addHeader("Content-Type", "application/json")
                .addHeader("X-Content-Sha256", xContentSha256).addHeader(
                    "Authorization",
                    "HMAC-SHA256 Credential=${apiKey}/${credentialScope}, SignedHeaders=${signHeader}, Signature=${signature}"
                )

            builder.url(url)
            val request = builder.post(body).build()
            val response = client.newCall(request).execute()
            val results = mutableListOf<String>()
            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val resultStr = response.body!!.string()
                try {
                    val map = gson.fromJson(resultStr, Map::class.java)
                    val translateTextResponseList = (map["TranslationList"] as List<Map<*, *>>)
                    translateTextResponseList.forEach {
                        results.add(it["Translation"] as String)
                    }
                    logger.info("Volcengine translateTexts end, requestId:$requestId, time:${end - begin}, results:${results}, inputs:${inputs}, target:${targetLang}")
                    return results
                } catch (e: Exception) {
                    logger.error(
                        "Volcengine translation parse error, requestId:$requestId, resultStr:${resultStr}, target:${targetLang}, error:${e}",
                        e
                    )
                    throw Status.UNAVAILABLE.withDescription("Volcengine translation parse error").asRuntimeException()
                }
            } else {
                logger.error("Volcengine translation return code invalid, requestId:$requestId, inputs:${inputs}, target:${targetLang}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Volcengine return code invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Volcengine translation failure, requestId:$requestId, inputs:${inputs}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

}