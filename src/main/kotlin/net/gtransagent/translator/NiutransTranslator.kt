package net.gtransagent.translator

import com.google.gson.Gson
import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.LanguageGroupedTranslator
import okhttp3.*
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 *
 * https://niutrans.com/dev-page?type=text
 *
 */
class NiutransTranslator : LanguageGroupedTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val gson = Gson()

    companion object {
        const val NAME = "Niutrans"

        const val USE_V2_API = false

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "niutrans"
            name = NAME
        })
    }

    private lateinit var apiKey: String
    private lateinit var appId: String

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
            (System.getenv("NIUTRANS_API_KEY") ?: "").trim()
        } else {
            fileApiKey.trim()
        }

        if (apiKey.isBlank()) {
            logger.error("NiutransTranslator init failed, Niutrans apiKey not found. Please set apiKey in Niutrans.yaml or NIUTRANS_API_KEY in environment.")
            return false
        }

        val fileAppId = (configs["appId"] as String?)
        appId = if (fileAppId.isNullOrBlank()) {
            (System.getenv("NIUTRANS_APP_ID") ?: "").trim()
        } else {
            fileAppId.trim()
        }

        if (appId.isBlank()) {
            logger.error("NiutransTranslator init failed, Niutrans appId not found. Please set appId in Niutrans.yaml or NIUTRANS_APP_ID in environment.")
            return false
        }

        logger.info("NiutransTranslator init success, supportedEngines: $supportedEngines")
        return true
    }


    // generate authStr
    private fun authStrGenerate(requestParamsMap: TreeMap<String, Any>): String {
        val input = requestParamsMap.entries.joinToString("&") { "${it.key}=${it.value}" }
        requestParamsMap.remove("apikey")
        return DigestUtils.md5Hex(input)
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
        if (USE_V2_API) {
            return translateTextV2Api(
                requestId, sourceLang, targetLang, inputs, engineCode, glossaryWords, glossaryIgnoreCase
            )
        }
        return translateTextV1Api(
            requestId, sourceLang, targetLang, inputs, engineCode, glossaryWords, glossaryIgnoreCase
        )
    }

    /**
     *
     * @param glossaryWords not supported
     * @param glossaryIgnoreCase not supported
     */
    private fun translateTextV2Api(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        inputs: List<String>,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        logger.debug("Niutrans translateTextV2Api start, requestId:$requestId, v2:${USE_V2_API}, source:${sourceLang}, target:${targetLang}, inputs:${inputs}")
        val begin = System.currentTimeMillis()
        val target = CommonUtils.getLang(targetLang)
        val src = CommonUtils.getLang(sourceLang)

        val sp = "\n"
        val sp2 = "\r\n"

        try {
            //Record the original line breaks
            val lineRecordMap = mutableMapOf<String, Pair<Int, String>>()
            inputs.forEachIndexed { index, s ->
                if (s.contains(sp2)) {
                    // attention: according to "\n" split, ("\r" will be automatically removed during translation), use "\r\n" to merge the translation
                    lineRecordMap[index.toString()] = Pair(s.split(sp).size - 1, sp2)
                } else if (s.contains(sp)) {
                    // attention: according to "\n" split, use "\n" to merge the translation
                    lineRecordMap[index.toString()] = Pair(s.split(sp).size - 1, sp)
                }
            }

            val textsStr = inputs.joinToString(sp)

            val timestamp = System.currentTimeMillis()

            val requestParamsMap = TreeMap<String, Any>()
            requestParamsMap["from"] = src
            requestParamsMap["to"] = target
            requestParamsMap["srcText"] = textsStr
            requestParamsMap["appId"] = appId
            requestParamsMap["apikey"] = apiKey
            requestParamsMap["timestamp"] = timestamp.toString()

            requestParamsMap["authStr"] = authStrGenerate(requestParamsMap)

            val payload = gson.toJson(requestParamsMap)
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val URL = "https://api.niutrans.com/v2/text/translate"
            val request: Request = Request.Builder().url(URL).post(body).build()
            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val result = response.body!!.bytes().toString(StandardCharsets.UTF_8)
                val map = gson.fromJson(result, Map::class.java)

                if (map.containsKey("errorCode")) {
                    //https://niutrans.com/documents/contents/trans_text#accessMode
                    val errorCode = map["errorCode"] as String
                    if (errorCode != "0") {
                        logger.error("translateTexts errorCode invalid, requestId:$requestId, v2:${USE_V2_API}, errorCode:${errorCode}, result:$result")
                        throw Status.UNAVAILABLE.withDescription("Niutrans return code invalid, code:${response.code}, result:$result")
                            .asRuntimeException()
                    }
                }
                if (!map.containsKey("tgtText")) {
                    logger.error("Niutrans result not include tgtText, requestId:$requestId, v2:${USE_V2_API}, result:$result")
                    throw Status.UNAVAILABLE.withDescription("Niutrans result not include tgtText, result:$result")
                        .asRuntimeException()
                }
                val transResultStr = map["tgtText"] as String
                val originTransResult = transResultStr.split(sp)
                var transResult = originTransResult
                // if original text contains line break
                if (lineRecordMap.isNotEmpty()) {
                    transResult = mutableListOf<String>()
                    val buffer = StringBuilder()
                    // record the current record index
                    var itemIndex = 0
                    // record the number of line breaks in the current record
                    var itemLineCount = 0
                    originTransResult.forEach { s ->
                        buffer.append(s)
                        if (lineRecordMap.contains(itemIndex.toString()) && lineRecordMap[itemIndex.toString()]!!.first > itemLineCount) {
                            // current record add line break, and not switch to the next record
                            buffer.append(lineRecordMap[itemIndex.toString()]!!.second)
                            itemLineCount++
                        } else {
                            transResult.add(buffer.toString())
                            buffer.clear()
                            itemIndex++
                            itemLineCount = 0
                        }
                    }
                }
                val end = System.currentTimeMillis()
                logger.info("Niutrans translateTexts end, requestId:$requestId, v2:${USE_V2_API}, time:${end - begin}, srcLang:${sourceLang}, targetLang:${targetLang}, results:${transResult}, inputs:${inputs}")
                return transResult
            } else {
                logger.error("translateTexts return code invalid, params:${payload}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Niutrans return result invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Niutrans translation failure, requestId:$requestId, v2:${USE_V2_API}, inputs:${inputs}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

    private fun translateTextV1Api(
        requestId: String,
        sourceLang: String,
        targetLang: String,
        inputs: List<String>,
        engineCode: String,
        glossaryWords: List<Pair<String, String>>?,
        glossaryIgnoreCase: Boolean
    ): List<String> {
        logger.debug("Niutrans translateTextV1Api start, requestId:$requestId, v2:${USE_V2_API}, source:${sourceLang}, target:${targetLang}, inputs:${inputs}")
        val begin = System.currentTimeMillis()
        val target = CommonUtils.getLang(targetLang)
        val src = CommonUtils.getLang(sourceLang)

        val sp = "\n"
        val sp2 = "\r\n"

        try {
            //Record the original line breaks
            val lineRecordMap = mutableMapOf<String, Pair<Int, String>>()
            inputs.forEachIndexed { index, s ->
                if (s.contains(sp2)) {
                    // attention: according to "\n" split, ("\r" will be automatically removed during translation), use "\r\n" to merge the translation
                    lineRecordMap[index.toString()] = Pair(s.split(sp).size - 1, sp2)
                } else if (s.contains(sp)) {
                    // attention: according to "\n" split, use "\n" to merge the translation
                    lineRecordMap[index.toString()] = Pair(s.split(sp).size - 1, sp)
                }
            }

            val textsStr = inputs.joinToString(sp)

            val timestamp = System.currentTimeMillis()

            val requestParamsMap = TreeMap<String, Any>()
            requestParamsMap["from"] = src
            requestParamsMap["to"] = target
            requestParamsMap["src_text"] = textsStr
            requestParamsMap["apikey"] = apiKey

            if (glossaryWords?.isNotEmpty() == true) {
                val dictMap = mutableMapOf<String, String>()
                glossaryWords.forEach { word ->
                    dictMap[word.first] = word.second
                }
                if (dictMap.isNotEmpty()) {
                    //https://niutrans.com/documents/contents/trans_dictionary#accessMode
                    requestParamsMap["dictflag"] = "1"
                    requestParamsMap["dict"] = dictMap
                }
            }

            val payload = gson.toJson(requestParamsMap)
            val body: RequestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val URL = "https://api.niutrans.com/NiuTransServer/translation"
            val request: Request = Request.Builder().url(URL).post(body).build()
            val response: Response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val result = response.body!!.bytes().toString(StandardCharsets.UTF_8)
                val map = gson.fromJson(result, Map::class.java)

                if (map.containsKey("error_code")) {
                    //https://niutrans.com/documents/contents/trans_text#accessMode
                    val errorCode = map["error_code"] as String
                    if (errorCode != "0") {
                        logger.error("translateTextV1Api errorCode invalid, requestId:$requestId, v2:${USE_V2_API}, errorCode:${errorCode}, result:$result")
                        throw Status.UNAVAILABLE.withDescription("Niutrans return code invalid, code:${response.code}, result:$result")
                            .asRuntimeException()
                    }
                }
                if (!map.containsKey("tgt_text")) {
                    logger.error("Niutrans result not include tgt_text, requestId:$requestId, v2:${USE_V2_API}, result:$result")
                    throw Status.UNAVAILABLE.withDescription("Niutrans result not include tgt_text, result:$result")
                        .asRuntimeException()
                }
                val transResultStr = map["tgt_text"] as String
                val originTransResult = transResultStr.split(sp)
                var transResult = originTransResult
                // if original text contains line break
                if (lineRecordMap.isNotEmpty()) {
                    transResult = mutableListOf<String>()
                    val buffer = StringBuilder()
                    // record the current record index
                    var itemIndex = 0
                    // record the number of line breaks in the current record
                    var itemLineCount = 0
                    originTransResult.forEach { s ->
                        buffer.append(s)
                        if (lineRecordMap.contains(itemIndex.toString()) && lineRecordMap[itemIndex.toString()]!!.first > itemLineCount) {
                            // current record add line break, and not switch to the next record
                            buffer.append(lineRecordMap[itemIndex.toString()]!!.second)
                            itemLineCount++
                        } else {
                            transResult.add(buffer.toString())
                            buffer.clear()
                            itemIndex++
                            itemLineCount = 0
                        }
                    }
                }
                val end = System.currentTimeMillis()
                logger.info("Niutrans translateTextV1Api end, requestId:$requestId, v2:${USE_V2_API}, time:${end - begin}, srcLang:${sourceLang}, targetLang:${targetLang}, results:${transResult}, inputs:${inputs}")
                return transResult
            } else {
                logger.error("translateTextV1Api return code invalid, v2:${USE_V2_API}, code:${response.code}")
                throw Status.UNAVAILABLE.withDescription("Niutrans return result invalid, code:${response.code}")
                    .asRuntimeException()
            }
        } catch (e: Exception) {
            logger.warn(
                "Niutrans translation failure, requestId:$requestId, v2:${USE_V2_API}, inputs:${inputs}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

}