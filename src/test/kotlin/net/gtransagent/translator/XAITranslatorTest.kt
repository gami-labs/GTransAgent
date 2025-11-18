package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class XAITranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return XAITranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return XAITranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("grok-4-fast", "grok-3-mini")
    }
}