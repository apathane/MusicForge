package dev.musicforge.awareness

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * The app's self-awareness: one flow that reflects the device/app situation.
 * Uses callbacks, not polling loops.
 */
@OptIn(UnstableApi::class)
class Awareness(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService<ConnectivityManager>()!!
    private val power = context.getSystemService<PowerManager>()!!
    
    private val _network = MutableStateFlow<NetworkState>(NetworkState.Unknown)
    private val _metered = MutableStateFlow<Boolean?>(null)
    private val _validatedButSlow = MutableStateFlow(false)
    private val _batterySaver = MutableStateFlow(power.isPowerSaveMode)
    private val _lowStorage = MutableStateFlow(false)
    private val _streamFailures = MutableStateFlow(0)
    private val _syncStale = MutableStateFlow(false)
    private val _syncFailing = MutableStateFlow(false)

    /** Stream failures in the last 5 minutes. */
    private val failureTimestamps = mutableListOf<Long>()
    private val failureWindowMs = 5 * 60 * 1000L
    private val failureThreshold = 3

    init {
        startNetworkObserver()
        startBatterySaverObserver()
        startStorageChecker()
    }

    private fun startNetworkObserver() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetwork(network)
            }

            override fun onLost(network: Network) {
                _network.value = NetworkState.Offline
                _metered.value = null
                _validatedButSlow.value = false
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateNetwork(network, capabilities)
            }
        })
    }

    private fun updateNetwork(network: Network, caps: NetworkCapabilities? = null) {
        val capabilities = caps ?: connectivity.getNetworkCapabilities(network)
            ?: return

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = connectivity.isActiveNetworkMetered
        val isNotRestricted = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)

        when {
            !hasInternet || !isValidated || !isNotRestricted -> _network.value = NetworkState.Offline
            isMetered -> _network.value = NetworkState.Metered
            else -> _network.value = NetworkState.Online
        }
        _metered.value = if (hasInternet && isValidated) isMetered else null

        // Validated but slow: has internet + validated + no metered but bandwidth < 500 Kbps (heuristic)
        val linkDown = capabilities.linkDownstreamBandwidthKbps
        val slow = isValidated && hasInternet && !isMetered && linkDown > 0 && linkDown < 500
        _validatedButSlow.value = slow
    }

    private fun startBatterySaverObserver() {
        scope.launch(Dispatchers.Main) {
            val filter = android.content.IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                    _batterySaver.value = power.isPowerSaveMode
                }
            }
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            // No unregister needed - lives as long as the app
        }
    }

    private fun startStorageChecker() {
        scope.launch(Dispatchers.IO) {
            while (true) {
                checkStorage()
                kotlinx.coroutines.delay(TimeUnit.MINUTES.toMillis(5))
            }
        }
    }

    private fun checkStorage() {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val stat = StatFs(dir.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val low = freeBytes < 1_000_000_000L // 1 GB
        _lowStorage.value = low
    }

    /** Call when a stream resolution or playback fails. */
    fun recordStreamFailure() {
        val now = System.currentTimeMillis()
        failureTimestamps.add(now)
        pruneFailures()
        _streamFailures.value = failureTimestamps.size
    }

    private fun pruneFailures() {
        val cutoff = System.currentTimeMillis() - failureWindowMs
        failureTimestamps.removeAll { it < cutoff }
        if (failureTimestamps.size < failureThreshold) {
            _streamFailures.value = failureTimestamps.size
        }
    }

    /** Call when account sync runs and succeeds. */
    fun recordSyncSuccess() {
        _syncStale.value = false
        _syncFailing.value = false
    }

    /** Call when account sync fails. */
    fun recordSyncFailure() {
        _syncFailing.value = true
    }

    /** Call when account sync hasn't run in > 1 hour. */
    fun recordSyncStale() {
        _syncStale.value = true
    }

    /** The combined situation as a data class. */
    val situation: StateFlow<Situation> = combine(
        _network, _metered, _validatedButSlow, _batterySaver, _lowStorage,
        _streamFailures, _syncStale, _syncFailing
    ) { network: NetworkState, metered: Boolean?, slow: Boolean, battery: Boolean, storage: Boolean,
        failures: Int, syncStale: Boolean, syncFailing: Boolean ->
        Situation(network, metered, slow, battery, storage, failures, syncStale, syncFailing)
    }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), Situation.Unknown)

    enum class NetworkState { Unknown, Offline, Metered, Online }

    data class Situation(
        val network: NetworkState = NetworkState.Unknown,
        val metered: Boolean? = null,
        val validatedButSlow: Boolean = false,
        val batterySaver: Boolean = false,
        val lowStorage: Boolean = false,
        val recentStreamFailures: Int = 0,
        val syncStale: Boolean = false,
        val syncFailing: Boolean = false,
    ) {
        companion object {
            val Unknown = Situation()
        }

        val isHealthy: Boolean
            get() = network == NetworkState.Online && !validatedButSlow && !batterySaver
                && !lowStorage && recentStreamFailures < failureThreshold && !syncFailing

        val hasAnyIssue: Boolean
            get() = this != Unknown && !isHealthy
        
        /** Whether artwork should be requested at a smaller size (metered, battery saver, or slow). */
        val shouldReduceArtworkQuality: Boolean
            get() = metered == true || batterySaver || validatedButSlow
        
        /** Whether read-ahead should be paused (metered or battery saver). */
        val shouldPauseReadAhead: Boolean
            get() = metered == true || batterySaver
    }

    companion object {
        /** The single most important message for the current situation, or null if healthy. */
        fun situationMessage(s: Situation): String? = when {
            s.network == NetworkState.Offline -> "You're offline. Showing what's downloaded."
            s.network == NetworkState.Metered -> "Metered network. Artwork is lighter."
            s.validatedButSlow -> "Network is slow. Artwork is lighter."
            s.batterySaver -> "Battery saver is on, so artwork is lighter."
            s.lowStorage -> "Low storage. Downloads paused."
            s.recentStreamFailures >= 3 -> "Streaming is having trouble. Playing what's cached."
            s.syncFailing -> "Account sync is failing. Some things may be out of date."
            s.syncStale -> "Account sync hasn't run in a while. Pull to refresh."
            else -> null
        }
    }
}