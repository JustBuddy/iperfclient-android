package wtf.buddyworks.iperfclient.net

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Android 17 blocks connections to the local network for apps that target it, unless they hold
 * this runtime permission (shown as "Nearby devices" in Settings).
 */
const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

fun localNetworkPermissionMissing(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 37 &&
        context.checkSelfPermission(ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED

/** True when [host] is (or resolves to) a private, link-local or unique-local address. */
suspend fun isLocalNetworkHost(host: String): Boolean = withContext(Dispatchers.IO) {
    if (host.endsWith(".local", ignoreCase = true)) return@withContext true
    runCatching {
        InetAddress.getAllByName(host).any { a ->
            a.isSiteLocalAddress || a.isLinkLocalAddress ||
                (a is Inet6Address && (a.address[0].toInt() and 0xfe) == 0xfc)
        }
    }.getOrDefault(false)
}
