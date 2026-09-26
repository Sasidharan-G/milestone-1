package com.kadaikutty.pos.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ResilientDnsTest {
    private val host = "u3bmxkaw25.execute-api.ap-southeast-2.amazonaws.com"

    @Test
    fun everyPublicResolverAnswersOverHttpsByIpAddress() {
        val online = runCatching { InetAddress.getByName("one.one.one.one") }.isSuccess
        assumeTrue("needs internet access", online)
        for (base in ResilientDns.resolverUrls) {
            val answer = ResilientDns.queryOverHttps(base, host)
            assertTrue("$base returned no address", answer != null && answer.addresses.isNotEmpty())
            answer!!.addresses.forEach {
                assertEquals(host, it.hostName)
                assertEquals(4, it.address.size)
            }
        }
    }

    @Test
    fun connectionFailuresBecomeReadableRetryableErrors() {
        val cases = listOf<Pair<IOException, String>>(
            UnknownHostException("Unable to resolve host") to "NETWORK_DNS",
            SocketTimeoutException("timeout") to "NETWORK_TIMEOUT",
            InterruptedIOException("timeout") to "NETWORK_TIMEOUT",
            SSLHandshakeException("Trust anchor for certification path not found.") to "NETWORK_TLS",
            ConnectException("refused") to "NETWORK_UNREACHABLE",
            IOException("boom") to "NETWORK_ERROR",
        )
        for ((failure, code) in cases) {
            val mapped = friendlyNetworkError(failure)
            assertEquals(code, mapped.code)
            assertTrue(mapped.retryable)
            assertEquals(0, mapped.statusCode)
            assertTrue("raw text must not reach the user", !mapped.message.contains("resolve host") && !mapped.message.contains("Trust anchor"))
        }
    }

    @Test
    fun serverAnswersPassThroughUntouched() {
        val server = BackendApiException("AUTH_INVALID", "Invalid mobile number or password", false, 401)
        assertTrue(friendlyNetworkError(server) === server)
    }
}
