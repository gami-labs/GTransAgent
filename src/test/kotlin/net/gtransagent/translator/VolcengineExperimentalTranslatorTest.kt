package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator
import net.gtransagent.translator.experimental.VolcengineExperimentalTranslator

class VolcengineExperimentalTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return VolcengineExperimentalTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return VolcengineExperimentalTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return VolcengineExperimentalTranslator.supportedEngines.map { it.code }
    }
}