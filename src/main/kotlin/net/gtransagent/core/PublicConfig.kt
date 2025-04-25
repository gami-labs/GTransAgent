package net.gtransagent.core

class PublicConfig {
    class TranslateEngine {
        // engine code
        lateinit var code: String
        // engine name
        lateinit var name: String

        override fun toString(): String {
            return "TranslateEngine(code='$code', name='$name')"
        }
    }

    class TranslatorDefine {
        lateinit var translatorCode: String
        lateinit var classFile: String

        override fun toString(): String {
            return "TranslatorDefine(translatorCode='$translatorCode', classFile='$classFile')"
        }
    }

    var port: Int = 6028
    lateinit var enablesTranslators: List<String>
    lateinit var translatorDefines: List<TranslatorDefine>

    override fun toString(): String {
        return "PublicConfig(port=$port, enablesTranslators=$enablesTranslators, translatorDefines=$translatorDefines)"
    }
}