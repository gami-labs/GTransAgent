package net.gtransagent.internal

import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 *
 * OkHttp event listener
 *
 */
class RoundTripEventListener : EventListener() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private var callStartNanos: Long = 0

    private fun printEvent(name: String) {
        val nowNanos = System.nanoTime()
        if (name == "callStart") {
            callStartNanos = nowNanos
        }
        val elapsedNanos = nowNanos - callStartNanos
        logger.info("%.3f %s".format(elapsedNanos / 1000000000.0, name))
    }


    override fun callStart(call: Call) {
        printEvent("callStart")
    }

    override fun dnsStart(call: Call, domainName: String) {
        printEvent("dnsStart")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        printEvent("dnsEnd")
    }

    override fun connectStart(
        call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy
    ) {
        printEvent("connectStart")
    }

    override fun secureConnectStart(call: Call) {
        printEvent("secureConnectStart")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        printEvent("secureConnectEnd")
    }

    override fun connectEnd(
        call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?
    ) {
        printEvent("connectEnd")
    }

    override fun connectFailed(
        call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException
    ) {
        printEvent("connectFailed")
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        printEvent("connectionAcquired")
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        printEvent("connectionReleased")
    }

    override fun requestHeadersStart(call: Call) {
        printEvent("requestHeadersStart")
    }

    override fun callEnd(call: Call) {
        printEvent("callEnd")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        printEvent("callFailed")
    }

}