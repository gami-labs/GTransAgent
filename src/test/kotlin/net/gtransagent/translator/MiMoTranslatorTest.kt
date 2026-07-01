package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class MiMoTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return MiMoTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return MiMoTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("mimo-v2.5", "mimo-v2.5-pro")
    }
}