package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class DeepLTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return DeepLTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return DeepLTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return DeepLTranslator.supportedEngines.map { it.code }
    }
}