package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class VolcengineArkTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return VolcengineArkTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return VolcengineArkTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("doubao-1.5-lite", "deepseek-v3-volcengine")
    }
}