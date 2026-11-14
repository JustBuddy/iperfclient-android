package wtf.buddyworks.iperfclient.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class Transport { WiFi, Cellular, Ethernet, None }

/** What the phone is connected through, as far as an app can see it without location access. */
data class ConnectionInfo(
    val transport: Transport,
    /** 4, 5, 6 or 7 for Wi-Fi 4…7. */
    val wifiGeneration: Int? = null,
    val bandGhz: String? = null,
    val linkMbps: Int? = null,
    val rssi: Int? = null,
    val vpn: Boolean = false,
) {
    companion object {
        val None = ConnectionInfo(Transport.None)
    }
}

fun connectionFlow(context: Context): Flow<ConnectionInfo> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    fun emit(caps: NetworkCapabilities?) {
        trySend(caps?.let(::describe) ?: ConnectionInfo.None)
    }
    emit(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) })
    val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = emit(caps)
        override fun onLost(network: Network) = emit(null)
    }
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

private fun describe(caps: NetworkCapabilities): ConnectionInfo {
    val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val wifi = caps.transportInfo as? WifiInfo
            ConnectionInfo(
                transport = Transport.WiFi,
                wifiGeneration = when (wifi?.wifiStandard) {
                    ScanResult.WIFI_STANDARD_11N -> 4
                    ScanResult.WIFI_STANDARD_11AC -> 5
                    ScanResult.WIFI_STANDARD_11AX -> 6
                    ScanResult.WIFI_STANDARD_11BE -> 7
                    else -> null
                },
                bandGhz = wifi?.frequency?.takeIf { it > 0 }?.let {
                    when {
                        it < 3000 -> "2.4"
                        it < 5925 -> "5"
                        else -> "6"
                    }
                },
                linkMbps = wifi?.let { maxOf(it.txLinkSpeedMbps, it.rxLinkSpeedMbps, it.linkSpeed) }?.takeIf { it > 0 },
                rssi = wifi?.rssi?.takeIf { it > -127 && it < 0 },
                vpn = vpn,
            )
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionInfo(Transport.Ethernet, vpn = vpn)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionInfo(
            Transport.Cellular,
            linkMbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 }?.div(1000),
            vpn = vpn,
        )
        else -> ConnectionInfo(Transport.None, vpn = vpn)
    }
}
