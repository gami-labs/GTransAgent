package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class AnthropicTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return AnthropicTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return AnthropicTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("claude-3-haiku")
    }
}