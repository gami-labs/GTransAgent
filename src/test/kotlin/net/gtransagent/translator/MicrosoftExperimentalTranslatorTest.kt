package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator
import net.gtransagent.translator.experimental.MicrosoftExperimentalTranslator

class MicrosoftExperimentalTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return MicrosoftExperimentalTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return MicrosoftExperimentalTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return MicrosoftExperimentalTranslator.supportedEngines.map { it.code }
    }
}