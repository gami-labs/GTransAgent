package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class YandexTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return YandexTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return YandexTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return YandexTranslator.supportedEngines.map { it.code }
    }
}