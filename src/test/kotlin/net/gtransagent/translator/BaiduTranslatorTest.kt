package net.gtransagent.translator

import net.gtransagent.translator.base.ITranslator

class BaiduTranslatorTest : TranslatorTest() {

    override fun getEachTranslationWaitTime(): Long {
        return 10000
    }

    override fun getTranslatorCode(): String {
        return BaiduTranslator.NAME
    }

    override fun getITranslator(): ITranslator {
        return BaiduTranslator()
    }

    override fun getTranslationEngines(): List<String> {
        return BaiduTranslator.supportedEngines.map { it.code }
    }
}