package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class VolcengineTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return VolcengineTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return VolcengineTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return VolcengineTranslator.supportedEngines.map { it.code }
    }
}