package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator
import net.gtransagent.translator.experimental.GoogleExperimentalTranslator

class GoogleExperimentalTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return GoogleExperimentalTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return GoogleExperimentalTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return GoogleExperimentalTranslator.supportedEngines.map { it.code }
    }
}