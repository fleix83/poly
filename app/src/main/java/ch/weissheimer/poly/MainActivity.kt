package ch.weissheimer.poly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import ch.weissheimer.poly.ui.navigation.PolyNavHost
import ch.weissheimer.poly.ui.theme.PolyTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Document handed over via VIEW/SEND; consumed by the nav host. */
    private val incomingDocument = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            incomingDocument.value = extractDocumentUri(intent)
        }
        setContent {
            PolyTheme {
                PolyNavHost(
                    incomingDocument = incomingDocument,
                    onIncomingConsumed = { incomingDocument.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractDocumentUri(intent)?.let { incomingDocument.value = it }
    }

    private fun extractDocumentUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}
