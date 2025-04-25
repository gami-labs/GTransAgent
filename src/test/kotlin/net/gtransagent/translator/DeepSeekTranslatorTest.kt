package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class DeepSeekTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return DeepSeekTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return DeepSeekTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("deepseek-chat")
    }
}