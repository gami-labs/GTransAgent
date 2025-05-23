package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class UnTsDeepLXTranslatorTest : TranslatorTest() {

    override fun onlyUseCommonLanguage(): Boolean {
        return true
    }

    override fun getTranslatorCode(): String {
        return UnTsDeepLXTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return UnTsDeepLXTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return UnTsDeepLXTranslator.supportedEngines.map { it.code }
    }
}