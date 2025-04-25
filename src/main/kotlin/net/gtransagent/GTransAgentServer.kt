package net.gtransagent

import io.grpc.Grpc
import io.grpc.InsecureServerCredentials
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import io.grpc.protobuf.services.HealthStatusManager
import net.gtransagent.core.SecurityKeyAccessor
import net.gtransagent.internal.Constant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * A server that hosts HostnameGreeter, plus infrastructure services like health and reflection.
 *
 *
 * This server is intended to be a general purpose "dummy" server.
 */
object GTransAgentServer {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Throws(IOException::class, InterruptedException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        // check and create security key
        SecurityKeyAccessor.checkAndCreateSecurityKey()

        // init agent factory
        try {
            AgentFactory.init()
        } catch (e: Exception) {
            logger.error("init agent factory error: ${e.message}", e)
            AgentFactory.destroy()
            exitProcess(1) // exit
        }

        val port = AgentFactory.publicConfig.port

        val health = HealthStatusManager()
        val server =
            Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create()).addService(GTransAgentGrpc())
                .addService(ProtoReflectionServiceV1.newInstance()).addService(health.healthService).build().start()
        logger.warn("GTransAgent is listening on port $port. The service can be accessed at http://localhost:$port, with the security key located in the file at ${SecurityKeyAccessor.getFilePath()}")
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                // Start graceful shutdown
                server.shutdown()
                try {
                    // Wait up to 30 seconds for RPCs to complete processing.
                    server.awaitTermination(10, TimeUnit.SECONDS)
                } catch (ex: InterruptedException) {
                    currentThread().interrupt()
                }
                // Cancel any remaining RPCs. If awaitTermination() returned true above, then there are no
                // RPCs and the server is already terminated. But it is safe to call even when terminated.
                server.shutdownNow()
                // shutdownNow isn't instantaneous, so you want an additional awaitTermination() to give
                // time to clean resources up gracefully. Normally it will return in well under a second. In
                // this example, the server.awaitTermination() in main() provides that delay.
            }
        })
        // This would normally be tied to the service's dependencies. For example, if HostnameGreeter
        // used a Channel to contact a required service, then when 'channel.getState() ==
        // TRANSIENT_FAILURE' we'd want to set NOT_SERVING. But HostnameGreeter has no dependencies, so
        // hard-coding SERVING is appropriate.
        health.setStatus("", ServingStatus.SERVING)
        server.awaitTermination()
    }
}