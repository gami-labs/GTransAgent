package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class GoogleTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return GoogleTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return GoogleTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return GoogleTranslator.supportedEngines.map { it.code }
    }
}