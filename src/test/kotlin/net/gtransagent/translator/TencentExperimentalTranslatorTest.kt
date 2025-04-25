package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator
import net.gtransagent.translator.experimental.TencentExperimentalTranslator

class TencentExperimentalTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return TencentExperimentalTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return TencentExperimentalTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return TencentExperimentalTranslator.supportedEngines.map { it.code }
    }
}