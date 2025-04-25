package net.gtransagent

import io.grpc.*
import io.grpc.stub.StreamObserver
import net.gtransagent.core.SecurityKeyAccessor
import net.gtransagent.core.TransDataConverter.decryptResultData
import net.gtransagent.core.TransDataConverter.encryptData
import net.gtransagent.grpc.*
import net.gtransagent.grpc.GTransAgentServiceGrpc
import java.nio.charset.StandardCharsets

import java.util.logging.Level
import java.util.logging.Logger

class GTransAgentClient(channel: Channel) {
    private val blockingStub: GTransAgentServiceGrpc.GTransAgentServiceBlockingStub
    private val stub: GTransAgentServiceGrpc.GTransAgentServiceStub
    val engine = "qwen-2.5-1.5b"

    init {
        SecurityKeyAccessor.checkAndCreateSecurityKey()
        blockingStub = GTransAgentServiceGrpc.newBlockingStub(channel)
        stub = GTransAgentServiceGrpc.newStub(channel)
    }

    private fun agentInfo() {
        logger.info("Will try to get agentInfo")
        val plaintext = System.currentTimeMillis().toString()
        val request = AgentInfoRequest.newBuilder().setClientVersion(1000)
            .setCiphertext(encryptData(plaintext.toByteArray(StandardCharsets.UTF_8)))
            //.setCiphertext("test")
            .setPlaintext(plaintext).build()
        val response: AgentInfoResponse = try {
            blockingStub.agentInfo(request)
        } catch (e: StatusRuntimeException) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.status)
            return
        }
        logger.info("agentInfo: $response")
    }

    private fun usabilityCheck() {
        logger.info("Will try to usabilityCheck")
        val beginTime = System.currentTimeMillis()
        val request = UsabilityCheckRequest.newBuilder().setCt(System.currentTimeMillis()).setEngineCode(engine).build()
        stub.usabilityCheck(request, object : StreamObserver<UsabilityCheckResponse> {
            override fun onNext(value: UsabilityCheckResponse) {
                logger.info("usabilityCheck: ${value}}")
            }

            override fun onError(t: Throwable) {
                logger.log(Level.WARNING, "usabilityCheck failed: {0}", t)
            }

            override fun onCompleted() {
                val endTime = System.currentTimeMillis()
                logger.info("usabilityCheck completed, time: ${endTime - beginTime} ms")
            }
        })
    }

    private fun translate() {
        logger.info("Will try to translate")

        val langTransItem1 = LangItem.newBuilder().setInputLang("en").addInputItemList(
            InputItem.newBuilder().setId(1)
                .setInput("The tool can be used for many purposes. For relatively simple copying operations, it tends to be slower than domain-specific alternatives, but it excels at overwriting or truncating a file at any point or seeking in a file.")
                .setGlossaryIgnoreCase(true).build()
        ).addInputItemList(
            InputItem.newBuilder().setId(2).setInput(
                "Despite weeks of searching, huge online interest in the case and feverish speculation from true crime enthusiasts, the George family found no answers for 21 days.\n" + "\n" + "When John's body was finally discovered by police in a lemon grove in Rojales, near Alicante, it was clear there had been foul play."
            ).setGlossaryIgnoreCase(true).build()
        ).build()

        val langTransItem2 = LangItem.newBuilder().setInputLang("ja").addInputItemList(
            InputItem.newBuilder().setId(3)
                .setInput("日本語のニュースや番組をテレビとラジオで海外向けに放送しています。海外にお住まいの方、旅行中の方にも情報をお届けします。")
                .setGlossaryIgnoreCase(true).build()
        ).build()

        val langTransItem3 = LangItem.newBuilder().setInputLang("es").addInputItemList(
            InputItem.newBuilder().setId(4)
                .setInput("Las labores de rescate continúan mientras la información llega a cuentagotas desde la antigua Birmania. El Servicio Geológico de Estados Unidos calcula que la cifra de fallecidos podría superar los 10.000")
                .setGlossaryIgnoreCase(true).build()
        ).build()

        val langTransItem4 = LangItem.newBuilder().setInputLang("en").addInputItemList(
            InputItem.newBuilder().setId(4).setInput(
                "Why should I test my internet speed with an internet speed test?\n" + "Invest in your future\n"
            ).setGlossaryIgnoreCase(true)
                .addGlossaryList(Glossary.newBuilder().setSrcWords("test").setTargetWords("检测").build())
                .addGlossaryList(Glossary.newBuilder().setSrcWords("internet").setTargetWords("互联网").build())
        ).build()


        val inputData = encryptData(listOf(langTransItem1, langTransItem2, langTransItem3, langTransItem4))


        val request =
            TranslateRequest.newBuilder().setRequestId(System.currentTimeMillis().toString()).setTargetLang("zh-CN")
                .setEngineCode(engine).setIsAutoTrans(true).addAllInputDataList(inputData).build()
        logger.info("translate requestId: ${request.requestId}, targetLang: ${request.targetLang}, engineCode: ${request.engineCode}")
        try {
            val beginTime = System.currentTimeMillis()
            stub.translate(request, object : StreamObserver<TranslateResponse> {
                override fun onNext(value: TranslateResponse) {
                    //logger.info("translate response: $value")
                    val results = decryptResultData(value.outputDataListList)
                    logger.info("results: ${results.joinToString(",") { "${it.id}, ${it.result}" }}")
                }

                override fun onError(t: Throwable) {
                    logger.log(Level.WARNING, "translate failed: {0}", t)
                }

                override fun onCompleted() {
                    val endTime = System.currentTimeMillis()
                    logger.info("translate completed, time: ${endTime - beginTime} ms")
                }
            })
        } catch (e: StatusRuntimeException) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.status)
            return
        }
    }

    companion object {
        private val logger = Logger.getLogger(GTransAgentClient::class.java.getName())

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val target = "localhost:6028"
            val channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build()
            try {
                val client = GTransAgentClient(channel)
                client.agentInfo()
                client.usabilityCheck()
                client.translate()
            } finally {
                Thread.currentThread().join()
                // channel.shutdownNow().awaitTermination(60, TimeUnit.SECONDS)
            }
        }
    }
}