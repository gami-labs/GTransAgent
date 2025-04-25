package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator
import net.gtransagent.translator.experimental.GoogleExperimentalTranslator
import net.gtransagent.translator.experimental.YandexExperimentalTranslator

class YandexExperimentalTranslatorTest : TranslatorTest() {
    override fun getTranslatorCode(): String {
        return YandexExperimentalTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return YandexExperimentalTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return YandexExperimentalTranslator.supportedEngines.map { it.code }
    }
}