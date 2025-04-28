package net.gtransagent.translator.experimental

import io.grpc.Status
import net.gtransagent.core.PublicConfig
import net.gtransagent.internal.CommonUtils
import net.gtransagent.translator.base.SingleInputTranslator
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*


/**
 * Google Experimental Translator
 * not need apiKey
 * Notice: There is a limit of frequency of calls, which results in 429 error code.
 */
class GoogleExperimentalTranslator : SingleInputTranslator() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val NAME = "GoogleExperimental"

        val supportedEngines = listOf(PublicConfig.TranslateEngine().apply {
            code = "google_experimental"
            name = NAME
        })
    }

    private var url: String = "https://translation.googleapis.com/language/translate/v2"

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
            logger.error("GoogleExperimentalTranslator init failed, url is invalid: $url")
            return false
        }

        mConcurrent = (configs["concurrent"] as Int?) ?: 3

        logger.info("GoogleExperimentalTranslator init success, supportedEngines: url:$url, $supportedEngines")
        return true
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
            logger.debug("Google Experimental translateTexts start, requestId:$requestId, target:${targetLang}, input:${input}")

            val begin = System.currentTimeMillis()

            val urlBuilder = url.toHttpUrlOrNull()!!.newBuilder()
            urlBuilder.addQueryParameter("client", "gtx")
            urlBuilder.addQueryParameter("dt", "t")
            urlBuilder.addQueryParameter("sl", CommonUtils.getLang(sourceLang))
            urlBuilder.addQueryParameter("tl", CommonUtils.getLang(targetLang))
            urlBuilder.addQueryParameter("q", input)

            val builder = Request.Builder()
            builder.url(urlBuilder.build())
            val request = builder.get().addHeader("Content-Type", "text/plain;charset=UTF-8").addHeader(
                "Content-Type",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"
            ).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful && Objects.nonNull(response.body)) {
                val end = System.currentTimeMillis()
                val responseStr = response.body!!.string()
                var result = paramResultPairsV1(responseStr)
                if (result.isNullOrBlank()) {
                    result = paramResultPairsV2(responseStr)
                }
                logger.info("Google Experimental translateTexts end, requestId:$requestId, time:${end - begin}, result:${result}, input:${input}, target:${targetLang}")
                return result ?: ""
            } else {
                // 429 Too Many Requests
                if (response.code == 429) {
                    logger.warn("Google Experimental translation too many requests, requestId:$requestId, input:${input}, target:${targetLang}, code:${response.code}")
                    throw Status.RESOURCE_EXHAUSTED.withDescription("Google Experimental translation too many requests, code:${response.code}")
                        .asRuntimeException()
                } else {
                    logger.error("Google Experimental translation return code invalid, requestId:$requestId, input:${input}, target:${targetLang}, code:${response.code}")
                    throw Status.UNAVAILABLE.withDescription("Google Experimental return code invalid, code:${response.code}")
                        .asRuntimeException()
                }
            }
        } catch (e: Exception) {
            logger.warn(
                "Google Experimental translation failure, requestId:$requestId, input:${input}, target:${targetLang}, error:${e}",
                e
            )
            throw e
        }
    }

    /**
     *
     * old version Google translation result parser. It will first try to use this method to parse, if it fails, it will try to use the new version parsing method
     * old：
     * [[["512341\n","512341\n",null,null,3,null,null,[[null,"offline"]],[[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true]]],["Giám sát\n","Monitoring\n",null,null,3,null,null,[[null,"offline"]],[[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true]]],["512342\n","512342\n",null,null,3,null,null,[[null,"offline"]],[[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true]]],["Đám mây AI","Cloud AI",null,null,3,null,null,[[null,"offline"]],[[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"]]]]],null,"en",null,null,null,0.79812336,[],[["en"],null,[0.79812336],["en"]]]
     *
     */
    private fun paramResultPairsV1(
        responseStr: String
    ): String? {
        val root: JSONArray = JSONArray(responseStr)
        if (root.length() <= 0 || (root.get(0) as JSONArray).length() <= 0) {
            return null
        }

        val resultList = (root.get(0) as JSONArray)
        if (resultList.length() <= 0 || (resultList.get(0) as JSONArray).length() <= 0) {
            return null
        }

        val result = resultList.map {
            val item = it as JSONArray
            if (item.length() <= 0 || item.get(0) == null) {
                return@map ""
            }
            val result = item[0].toString()
            return@map result
        }.joinToString(" ")

        /*        val item = (resultList.get(0) as JSONArray)
                val result = item[0].toString()
        */

        return result
    }

    /**
     *
     * new version Google translation result parser. It will first try to use the old version to parse, if it fails, it will try to use the new version parsing method
     * new:
     * [[["512341\n\u003d Điều kiện lọc Tên tiền thưởng lọc\n512342\nTrạng thái ↑\n512343\nTỷ lệ phần trăm còn lại\n512344\nSố tiền còn lại\n512345\nGiá trị ban đầu\n512346\nkiểu\n512347\nId tiền thưởng\n512348\nPhạm vi 6\n512349\nNgày bắt đầu","512341\n\u003d过滤条件 过滤赠金赠金名称\n512342\n状态 ↑\n512343\n剩余百分比\n512344\n剩余金额\n512345\n原始值\n512346\n类型\n512347\n赠金ID\n512348\n范围 6\n512349\n开始日期",null,null,11,null,null,[[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"],[null,"offline"]],[[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[null,true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["61549914d65604307a34fd1855292577","offline_launch_doc.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[null,true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true],[["f7c9e6647a60832c987a11d0bedd32d6","efficient_models_2022q2.md"],true]]]],null,"zh-CN",null,null,null,null,[]]
     *
     */
    private fun paramResultPairsV2(
        responseStr: String
    ): String? {
        val root: JSONArray = JSONArray(responseStr)
        if (root.length() <= 0 || (root.get(0) as JSONArray).length() <= 0) {
            return null
        }

        val second = (root.get(0) as JSONArray)
        if (second.length() <= 0 || (second.get(0) as JSONArray).length() <= 0) {
            return null
        }

        /*
        val resultStr = second.map {
                    val item = it as JSONArray
                    if (item.length() <= 0 || item.get(0) == null) {
                        return@map ""
                    }
                    val result = item[0].toString()
                    return@map result
                }.joinToString(" ")
       */

        /* */
        val third = (second.get(0) as JSONArray)
        if (third.length() <= 1) {
            return null
        }

        val resultStr = third.get(0) as String

        return resultStr
    }

}