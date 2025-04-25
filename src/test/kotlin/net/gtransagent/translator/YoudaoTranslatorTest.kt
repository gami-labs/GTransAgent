package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class YoudaoTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return YoudaoTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return YoudaoTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return YoudaoTranslator.supportedEngines.map { it.code }
    }
}