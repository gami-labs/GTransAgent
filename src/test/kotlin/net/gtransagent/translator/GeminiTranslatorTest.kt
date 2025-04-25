package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class GeminiTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return GeminiTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return GeminiTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("gemini-2.0-flash", "ministral-8b")
    }
}