package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class NiutransTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return NiutransTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return NiutransTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return NiutransTranslator.supportedEngines.map { it.code }
    }
}