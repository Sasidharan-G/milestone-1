package com.kadaikutty.pos.core.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns a low-level connection failure into something a shop owner can act on.
 *
 * The raw text ("Unable to resolve host ...: No address associated with hostname") means nothing to
 * them. The result is retryable with status 0: the server was never reached, so callers treat it
 * as "offline", never as the server refusing anything.
 */
internal fun friendlyNetworkError(e: IOException): BackendApiException {
    if (e is BackendApiException) return e
    val (code, message) = when (e) {
        is UnknownHostException ->
            "NETWORK_DNS" to "Can't reach the server. Check the internet connection, or try Wi-Fi instead of mobile data (or the other way round). If it keeps failing, open Settings, search for 'Private DNS' and set it to Off or Automatic."
        is SSLException ->
            "NETWORK_TLS" to "Secure connection failed. Make sure the phone's date and time are correct (set them to Automatic) and try again."
        is InterruptedIOException ->
            "NETWORK_TIMEOUT" to "The server is taking too long to respond. Check the internet connection and try again."
        is ConnectException, is NoRouteToHostException ->
            "NETWORK_UNREACHABLE" to "Could not connect to the server. Check the internet connection and try again."
        else ->
            "NETWORK_ERROR" to "Network problem. Check the internet connection and try again."
    }
    return BackendApiException(code = code, message = message, retryable = true, statusCode = 0).also { it.initCause(e) }
}
