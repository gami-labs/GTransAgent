package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class MicrosoftTranslatorTest : TranslatorTest() {

    override fun getTranslatorCode(): String {
        return MicrosoftTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return MicrosoftTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return MicrosoftTranslator.supportedEngines.map { it.code }
    }
}