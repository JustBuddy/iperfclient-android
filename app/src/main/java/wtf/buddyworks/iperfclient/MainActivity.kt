package wtf.buddyworks.iperfclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import wtf.buddyworks.iperfclient.ui.AppRoot
import wtf.buddyworks.iperfclient.ui.theme.IperfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IperfTheme {
                AppRoot()
            }
        }
    }
}
