package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class DeepLXTranslatorTest : TranslatorTest() {
    override fun onlyUseCommonLanguage(): Boolean {
        return true
    }

    override fun getTranslatorCode(): String {
        return DeepLXTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return DeepLXTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return DeepLXTranslator.supportedEngines.map { it.code }
    }
}