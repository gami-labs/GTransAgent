package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class OpenAITranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return OpenAITranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return OpenAITranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("gpt-4.1-nano", "gpt-4o-mini")
    }
}