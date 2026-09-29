package com.kadaikutty.pos.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.kadaikutty.pos.BuildConfig
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the POS backend can actually be reached right now.
 *
 * The phone saying "connected" is not enough: a SIM with no data balance, a Wi-Fi login page or a
 * server outage all look connected. So this pings `/health` while the OS reports a validated
 * network, and only calls the app online when that answers.
 *
 * The app works either way — billing is saved on the device and synced later — so this only
 * drives the offline banner, the online-only buttons, and the sync kick when the link comes back.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncScheduler: SyncScheduler,
    private val sessionStore: SessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val healthClient = OkHttpClient.Builder()
        .dns(ResilientDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    init {
        scope.launch {
            networkAvailable(context).collectLatest { hasNetwork ->
                if (!hasNetwork) {
                    setOnline(false)
                    return@collectLatest
                }
                // Cancelled by collectLatest the moment the network drops.
                while (true) {
                    setOnline(pingHealth())
                    delay(if (_isOnline.value) HEALTHY_INTERVAL_MS else RETRY_INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun setOnline(value: Boolean) {
        val wasOnline = _isOnline.value
        _isOnline.value = value
        // Offline -> online: push what was billed offline and pull what changed meanwhile, now,
        // rather than whenever the 15-minute periodic sync next fires.
        if (value && !wasOnline && sessionStore.activeSession.first() != null) {
            runCatching {
                syncScheduler.request()
                syncScheduler.requestPull()
            }.onFailure { android.util.Log.w("ConnectivityMonitor", "Could not queue reconnect sync", it) }
        }
    }

    private suspend fun pingHealth(): Boolean = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/health"
        runCatching {
            healthClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.isSuccessful) response.headers.getDate("Date")?.let { com.kadaikutty.pos.core.common.TrustedClock.onServerTime(it.time) }
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val HEALTHY_INTERVAL_MS = 30_000L
        const val RETRY_INTERVAL_MS = 10_000L
    }
}

private fun hasValidatedNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun networkAvailable(context: Context): Flow<Boolean> = callbackFlow {
    val appContext = context.applicationContext
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (cm == null) {
        trySend(false)
        awaitClose { }
        return@callbackFlow
    }
    // Re-read the whole state on every callback rather than tracking networks one by one: losing
    // Wi-Fi while mobile data is up must not read as offline.
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(hasValidatedNetwork(appContext)) }
        override fun onLost(network: Network) { trySend(hasValidatedNetwork(appContext)) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(hasValidatedNetwork(appContext)) }
    }
    trySend(hasValidatedNetwork(appContext))
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
