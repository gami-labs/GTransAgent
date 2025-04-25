package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class BigModelTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return BigModelTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return BigModelTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("glm-4-plus", "glm-4-flash", "glm-4-long")
    }
}