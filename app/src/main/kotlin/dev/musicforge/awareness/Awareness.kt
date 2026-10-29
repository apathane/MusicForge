package dev.musicforge.awareness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the app knows about its own situation: the network, battery saver, free space and how
 * its own streams are doing. Everything arrives by callback (no polling); free space is checked
 * whenever the network changes, which is often enough to notice a filling disk.
 */
class Awareness(private val context: Context) {
    data class Situation(
        val online: Boolean = true,
        val metered: Boolean = false,
        val batterySaver: Boolean = false,
        val lowStorage: Boolean = false,
        val failingStreams: Boolean = false,
    )

    private val _situation = MutableStateFlow(Situation())
    val situation: StateFlow<Situation> = _situation.asStateFlow()

    private val failures = ArrayDeque<Long>()

    fun start() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                _situation.update { it.copy(online = online, metered = metered, lowStorage = lowStorage()) }
            }

            override fun onLost(network: Network) = _situation.update { it.copy(online = false) }
        })
        // No default network at all means no callback ever fires, so start from what's there now.
        if (connectivity?.activeNetwork == null) _situation.update { it.copy(online = false) }
        _situation.update { it.copy(batterySaver = power?.isPowerSaveMode == true, lowStorage = lowStorage()) }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = _situation.update { it.copy(batterySaver = power?.isPowerSaveMode == true) }
            },
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Called by the player on each playback error; three within five minutes counts as trouble. */
    @Synchronized
    fun streamFailed() {
        val now = SystemClock.elapsedRealtime()
        failures.addLast(now)
        while (failures.isNotEmpty() && now - failures.first() > FAILURE_WINDOW_MS) failures.removeFirst()
        _situation.update { it.copy(failingStreams = failures.size >= 3) }
    }

    /** Called when a track starts playing fine again. */
    @Synchronized
    fun streamRecovered() {
        failures.clear()
        _situation.update { it.copy(failingStreams = false) }
    }

    private fun lowStorage(): Boolean = runCatching {
        StatFs(context.filesDir.absolutePath).availableBytes < LOW_STORAGE_BYTES
    }.getOrDefault(false)

    companion object {
        private const val FAILURE_WINDOW_MS = 5 * 60_000L
        private const val LOW_STORAGE_BYTES = 1_000_000_000L

        /**
         * The one line worth saying, most important first, or null when all is well. Only claims
         * what the app actually does about it (ReadAhead already holds back on metered networks
         * and battery saver). Like a good waiter: mentions the problem once and then gets out of the way.
         */
        fun message(s: Situation): String? = when {
            !s.online -> "You're offline. Your downloads still play."
            s.failingStreams -> "Streaming is struggling right now. Downloaded songs are unaffected."
            s.lowStorage -> "Storage is almost full, so new downloads may fail."
            s.batterySaver -> "Battery saver is on, so upcoming songs aren't preloaded."
            s.metered -> "On mobile data: upcoming songs are only lightly preloaded."
            else -> null
        }
    }
}
