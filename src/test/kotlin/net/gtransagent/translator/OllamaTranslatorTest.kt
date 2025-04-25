package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator


class OllamaTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return OllamaTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return OllamaTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return listOf("qwen-2.5-1.5b", "gemma-3-1b")
    }
}