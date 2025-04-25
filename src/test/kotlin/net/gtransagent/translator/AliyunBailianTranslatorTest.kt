package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class AliyunBailianTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return AliyunBailianTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return AliyunBailianTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("qwen-turbo", "deepseek-v3-aliyun")
    }
}