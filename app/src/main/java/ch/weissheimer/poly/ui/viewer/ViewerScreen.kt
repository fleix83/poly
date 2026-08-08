package ch.weissheimer.poly.ui.viewer

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.weissheimer.poly.R
import ch.weissheimer.poly.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    uri: Uri,
    onBack: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel: ViewerViewModel = viewModel(key = uri.toString()) {
        ViewerViewModel(uri, container.fileRepository, container.recentsRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? ViewerUiState.Ready)?.document?.displayName.orEmpty()
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.viewer_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                ViewerUiState.Loading -> CircularProgressIndicator()

                is ViewerUiState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(
                            if (s.permissionLost) R.string.viewer_error_permission
                            else R.string.viewer_error_open
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { viewModel.load() }) {
                        Text(stringResource(R.string.viewer_retry))
                    }
                }

                is ViewerUiState.Ready -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        stringResource(
                            R.string.viewer_unsupported,
                            stringResource(s.document.format.labelRes),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
