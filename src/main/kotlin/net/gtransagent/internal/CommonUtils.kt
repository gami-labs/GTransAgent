package net.gtransagent.internal

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.haibiiin.json.repair.JSONRepair
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.collections.iterator


object CommonUtils {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val jsonRepair = JSONRepair()

    fun getLang(language: String): String {
        return language.split(Regex("[_\\-]"))[0]
    }

    fun textsToJson(texts: List<String>): String {
        val list = mutableListOf<Map<String, Any>>()
        texts.forEachIndexed { index, s ->
            list.add(mutableMapOf(Pair("id", index + 1), Pair("text", s)))
        }
        val om = jacksonObjectMapper()
        return om.writeValueAsString(list)
    }


    fun jsonToTextsWithIndex(jsonStr: String, inputs: List<String>): List<String> {
        try {
            var objectMapper = jacksonObjectMapper()
            var rootMap: Map<*, *>
            try {
                rootMap = objectMapper.readValue(jsonStr, Map::class.java)
            } catch (e: Exception) {
                logger.warn("jsonToTextsWithIndex error:$e, jsonStr:$jsonStr, try support non-standard JSON format")
                objectMapper =
                    JsonMapper.builder().enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER) // 允许出现特殊字符和转义符
                        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)// 允许出现单引号
                        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)// 允许使用不带引号的字段名称的功能
                        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)// 允许 JSON 字符串包含未转义控制字符（值小于 32 的 ASCII 字符，包括制表符和换行符）的功能。
                        .enable(JsonReadFeature.ALLOW_MISSING_VALUES)// 允许将“缺失”值（JSON 数组上下文中两个逗号之间的空格）转换为 null 值，而不是异常
                        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)// 允许在最终值（在数组中）或成员（在对象中）后面有单个尾随逗号的功能。这些逗号将被忽略。
                        .build()
                rootMap = objectMapper.readValue(jsonStr, Map::class.java)
            }

            if (rootMap.isEmpty()) {
                logger.warn("jsonToTextsWithIndex jsonStr is null:${jsonStr}")
                return emptyList()
            }

            val rootKey = rootMap.keys.first()
            val dataList = rootMap.get(rootKey)!! as List<*>

            //val dataList = om.readValue(jsonStr, List::class.java)
            val map = mutableMapOf<Int, String>()
            dataList.forEach { any ->
                val itemMap = any as Map<String, Any>
                val index = if (itemMap["id"] is Number) {
                    (itemMap["id"] as Number).toInt()
                } else {
                    (itemMap["id"]).toString().toInt()
                }
                map[index] = itemMap["text"] as String
            }

            val texts = mutableListOf<String>()
            inputs.forEachIndexed { index, text ->
                val key = index + 1
                if (map.contains(key)) {
                    texts.add(map[key]!!)
                } else {
                    logger.warn("jsonToTextsWithIndex miss key:$key, item:$text, jsonStr:$jsonStr, input:$inputs")
                    texts.add(text)
                }
            }

            return texts
        } catch (e: Exception) {
            logger.warn("jsonToTextsWithIndex error:$e, jsonStr:$jsonStr, inputs:$inputs")
            throw e
        }
    }

    /**
     *
     */
    fun jsonRepair(input: String): String {
        val correctJSON = jsonRepair.handle(input)
        return correctJSON
    }


    fun jsonStrToList(resultText: String, texts: List<String>): List<String> {
        return try {
            jsonToTextsWithIndex(resultText, texts)
        } catch (e: Exception) {
            //json格式修复
            val repairedJson = jsonRepair(resultText)
            logger.warn("json repair, result:$repairedJson, origin:$resultText")
            jsonToTextsWithIndex(repairedJson, texts)
        }
    }

    /**
     * get local ip address
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val hostAddress = address.hostAddress
                        if (hostAddress.indexOf(':') < 0) { // 排除 IPv6 地址
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

}