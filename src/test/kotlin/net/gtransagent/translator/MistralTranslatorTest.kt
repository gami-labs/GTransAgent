package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class MistralTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return MistralTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return MistralTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("mistral-small", "ministral-8b")
    }
}