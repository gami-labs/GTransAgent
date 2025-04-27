package net.gtransagent.translator

import io.grpc.Status
import kotlinx.coroutines.*
import net.gtransagent.AgentFactory
import net.gtransagent.grpc.Glossary
import net.gtransagent.grpc.InputItem
import net.gtransagent.grpc.LangItem
import net.gtransagent.grpc.ResultItem
import net.gtransagent.translator.base.ITranslator
import org.junit.jupiter.api.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class TranslatorTest {
    private lateinit var translator: ITranslator
    private var hasFinished = false

    /**
     * each translation wait time
     * @return wait time in milliseconds
     */
    open fun getEachTranslationWaitTime(): Long {
        return 0
    }

    abstract fun getTranslatorCode(): String
    abstract fun getITranslator(): ITranslator
    abstract fun getTranslationEngines(): List<String>

    @BeforeEach
    fun setUp() {
        translator = getITranslator()
        val success = translator.init(AgentFactory.loadTranslatorConfigMap(getTranslatorCode()))
        Assertions.assertTrue(success)
    }

    @AfterEach
    fun tearDown() {
        translator.destroy()
    }

    @Test
    fun translate() {
        getTranslationEngines().forEach { engineCode ->
            runBlocking {
                translate(engineCode)
                delay(getEachTranslationWaitTime())
                translateTexts(
                    engineCode, "en", "zh_Hans", listOf(
                        "READ FOR FREE EVERY DAY",
                        "WUF",
                        "appyEnding,",
                        "THE PATH",
                        "OF A STAR",
                        "20 GOINS",
                        "30 COINS",
                        "25 COINS ONLY!",
                        "C Seoulmui . mongchere / KIDARISTUDIO",
                        "O Onginal Novel Kim Aso | Writer: Yeonog / KIDARISTUDIO",
                        "Download Lezhin Comics App",
                        "PLUS",
                        "Lezhin Comics PLUS+",
                        "Google Play",
                        "+ Discounts 24/7",
                        "+ View all content",
                        "Android Only",
                        "App Store",
                        "About Lezhin",
                        "Terms of Use",
                        "Privacy Policy",
                        "Customer Support",
                        "Submission",
                        "English/US",
                        "Lezhin Entertainment, LLC | Contact Us : contact@lezhin.com All titles published by Lezhin Comics are protected by copyright.",
                        "LEZHIN",
                        "MySişter's"
                    )
                )
            }
        }
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    private suspend fun translateTexts(
        engineCode: String, srcLang: String, target: String, inputs: List<String>
    ): Boolean {
        return suspendCoroutine { continuation ->
            hasFinished = false

            val begin = System.currentTimeMillis()
            val requestId = "e${System.currentTimeMillis()}"

            val inputItems = inputs.mapIndexed { index, input ->
                InputItem.newBuilder().setId(index + 1).setInput(input).build()
            }

            val isAutoTrans = true
            val langItems = listOf(LangItem.newBuilder().setInputLang(srcLang).addAllInputItemList(inputItems).build())

            val size = inputs.size
            println("${translator.getName()} translate begin, requestId=$requestId, engineCode=$engineCode, size:$size")

            var finished = 0
            translator.translate(
                requestId, target, engineCode, isAutoTrans, langItems, fun(
                    requestId: String, isAllItemTransFinished: Boolean, resultItems: List<ResultItem>, status: Status?
                ) {
                    val resultStr = resultItems.joinToString(",") {
                        "(${it.id}, ${it.result})"
                    }
                    finished += resultItems.size
                    println("${translator.getName()} translate end, requestId=$requestId, isAllItemTransFinished=$isAllItemTransFinished, time: ${System.currentTimeMillis() - begin} ms, resultStr=${resultStr}, status=$status")
                    Assertions.assertTrue(status == null || status == Status.OK)
                    if (isAllItemTransFinished) {
                        continuation.resume((status == null || status == Status.OK) && finished == size)
                    }
                })
        }
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    private suspend fun translate(engineCode: String): Boolean {
        return suspendCoroutine { continuation ->
            hasFinished = false

            val begin = System.currentTimeMillis()
            val requestId = "e${System.currentTimeMillis()}"

            val targetLang = "zh_Hans"
            val isAutoTrans = true
            val langItems = listOf(
                LangItem.newBuilder().setInputLang("en").addInputItemList(
                    InputItem.newBuilder().setId(1)
                        .setInput("The tool can be used for many purposes. For relatively simple copying operations, it tends to be slower than domain-specific alternatives, but it excels at overwriting or truncating a file at any point or seeking in a file.")
                        .build()
                ).addInputItemList(
                    InputItem.newBuilder().setId(2)
                        .setInput("The source language of the initial request, detected automatically, if no source language was passed within the initial request. If the source language was passed, auto-detection of the language will not occur and this field will be omitted.")
                        .build()
                ).addInputItemList(
                    InputItem.newBuilder().setId(3).setInput(
                        "Why should I test my internet speed with an internet speed test?\n" + "Invest in your future\n"
                    ).setGlossaryIgnoreCase(true)
                        .addGlossaryList(Glossary.newBuilder().setSrcWords("test").setTargetWords("检测").build())
                        .addGlossaryList(Glossary.newBuilder().setSrcWords("internet").setTargetWords("互联网").build())
                ).build(), LangItem.newBuilder().setInputLang("es").addInputItemList(
                    InputItem.newBuilder().setId(4)
                        .setInput("El ministro ha insistido en su declaración en La Moncloa ante el magistrado Peinado que las esposas de los presidentes siempre han contado con este tipo de ayudantes")
                        .build()
                ).build()
            )

            val size = langItems.sumOf { it.inputItemListList.size }
            println("${translator.getName()} translate begin, requestId=$requestId, engineCode=$engineCode, size:$size")

            var finished = 0
            translator.translate(
                requestId, targetLang, engineCode, isAutoTrans, langItems, fun(
                    requestId: String, isAllItemTransFinished: Boolean, resultItems: List<ResultItem>, status: Status?
                ) {
                    val resultStr = resultItems.joinToString(",") {
                        "(${it.id}, ${it.result})"
                    }
                    finished += resultItems.size

                    println("${translator.getName()} translate end, requestId=$requestId, isAllItemTransFinished=$isAllItemTransFinished, time: ${System.currentTimeMillis() - begin} ms, resultStr=${resultStr}, status=$status")
                    Assertions.assertTrue(status == null || status == Status.OK)
                    if (isAllItemTransFinished) {
                        continuation.resume((status == null || status == Status.OK) && finished == size)
                    }
                })
        }
    }
}