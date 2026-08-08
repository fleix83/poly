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

    /**
     * URI of the intent already turned into navigation. Survives recreation:
     * a plain config change must not re-open the document, but a relaunch
     * with a NEW intent (e.g. after process death the OS delivers the fresh
     * VIEW intent alongside restored state) must.
     */
    private var handledIntentUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handledIntentUri = savedInstanceState?.getString(KEY_HANDLED_INTENT_URI)
        val uri = extractDocumentUri(intent)
        if (uri != null && uri.toString() != handledIntentUri) {
            incomingDocument.value = uri
            handledIntentUri = uri.toString()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_HANDLED_INTENT_URI, handledIntentUri)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractDocumentUri(intent)?.let {
            incomingDocument.value = it
            handledIntentUri = it.toString()
        }
    }

    private companion object {
        const val KEY_HANDLED_INTENT_URI = "poly.handledIntentUri"
    }

    private fun extractDocumentUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}
