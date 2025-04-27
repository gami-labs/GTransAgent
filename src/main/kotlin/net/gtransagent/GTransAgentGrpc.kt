package net.gtransagent

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import net.gtransagent.core.TransDataConverter
import net.gtransagent.grpc.*
import net.gtransagent.grpc.GTransAgentServiceGrpc
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GTransAgentGrpc : GTransAgentServiceGrpc.GTransAgentServiceImplBase() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun agentInfo(request: AgentInfoRequest, responseObserver: StreamObserver<AgentInfoResponse>) {
        logger.info("AgentInfo request: ${request.clientVersion}")
        val responseBuilder = AgentInfoResponse.newBuilder()
        val keyVerificationResult = try {
            val decoded = TransDataConverter.decryptData(request.ciphertext)
            val plaintext = String(decoded, Charsets.UTF_8)
            if (plaintext != request.plaintext) {
                logger.warn("key verification failed: $plaintext != ${request.plaintext}")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.warn(
                "key verification failed: ${e.message}, request: $request", e
            )
            false
        }

        responseBuilder.setKeyVerificationResult(keyVerificationResult)
        responseBuilder.setAgentVersion(AgentFactory.internalConfig.agentVersionName)
        responseBuilder.setAgentVersionNumber(AgentFactory.internalConfig.agentVersionNumber)

        AgentFactory.getAllTranslateEngine().forEach {
            responseBuilder.addEngines(TranslateEngine.newBuilder().setCode(it.code).setName(it.name).build())
        }

        responseObserver.onNext(responseBuilder.build())
        responseObserver.onCompleted()
    }

    override fun usabilityCheck(
        request: UsabilityCheckRequest,
        responseObserver: StreamObserver<UsabilityCheckResponse>
    ) {
        val responseBuilder = UsabilityCheckResponse.newBuilder()
        responseBuilder.setCt(request.ct)
        responseBuilder.setAt(System.currentTimeMillis())

        val engineCode = request.engineCode
        val translator = AgentFactory.getTranslatorByEngineKey(engineCode)
        if (translator == null) {
            logger.error("No translator found for engineCode: $engineCode")
            responseBuilder.setResult(false)
            responseObserver.onNext(responseBuilder.build())
            responseObserver.onCompleted()
            return
        }

        val langTransItems: List<LangItem> = listOf(
            LangItem.newBuilder().setInputLang("en")
                .addInputItemList(InputItem.newBuilder().setId(1).setInput("Hi").build()).build()
        )
        translator.translate(
            System.currentTimeMillis().toString(), "es", request.engineCode, false, langTransItems
        ) { requestId, isAllItemTransFinished, transResultItems, status ->
            if (status != null) {
                logger.error("Translate error: ${status.code} ${status.description}")
                responseBuilder.setResult(false)
                responseObserver.onNext(responseBuilder.build())
                responseObserver.onCompleted()
                return@translate
            }

            responseBuilder.setResult(true)
            responseObserver.onNext(responseBuilder.build())
            responseObserver.onCompleted()
        }
    }

    override fun translate(request: TranslateRequest, responseObserver: StreamObserver<TranslateResponse>) {
        logger.info("Translate request: ${request.requestId}")

        val beginTime = System.currentTimeMillis()
        val engineCode = request.engineCode
        val translator = AgentFactory.getTranslatorByEngineKey(engineCode)
        if (translator == null) {
            logger.error("No translator found for engineCode: $engineCode")
            responseObserver.onError(StatusRuntimeException(Status.NOT_FOUND))
            return
        }

        val inputs = try {
            TransDataConverter.decryptData(request.inputDataListList)
        } catch (e: Exception) {
            logger.error("decrypt error: ${e.message}", e)
            responseObserver.onError(StatusRuntimeException(Status.PERMISSION_DENIED))
            return
        }
        translator.translate(
            request.requestId, request.targetLang, request.engineCode, request.isAutoTrans, inputs
        ) { requestId, isAllItemTransFinished, transResultItems, status ->
            if (status != null) {
                logger.error("Translate error: ${status.code} ${status.description}")
                responseObserver.onError(status.asRuntimeException())
                return@translate
            }
            val responseBuilder = TranslateResponse.newBuilder()
            responseBuilder.requestId = requestId
            responseBuilder.isAllItemTransFinished = isAllItemTransFinished
            responseBuilder.addAllOutputDataList(TransDataConverter.encryptResultData(transResultItems))

            logger.info("Translate onNext: $requestId, isAllItemTransFinished: $isAllItemTransFinished, transResultItems: ${transResultItems.size}")
            responseObserver.onNext(responseBuilder.build())

            if (isAllItemTransFinished) {
                var endTime = System.currentTimeMillis()
                logger.info("Translate finished: $requestId, time: ${endTime - beginTime} ms")
                responseObserver.onCompleted()
            }
        }
    }
}