package wtf.buddyworks.iperfclient

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import wtf.buddyworks.iperfclient.data.AppStore
import wtf.buddyworks.iperfclient.net.ConnectionInfo
import wtf.buddyworks.iperfclient.net.connectionFlow

class IperfApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val store: AppStore by lazy { AppStore(this) }
    val connection: StateFlow<ConnectionInfo> by lazy {
        connectionFlow(this).stateIn(scope, SharingStarted.Eagerly, ConnectionInfo.None)
    }
}

val Context.iperfApp: IperfApp
    get() = applicationContext as IperfApp
