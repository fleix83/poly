package ch.weissheimer.poly.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.weissheimer.poly.R
import ch.weissheimer.poly.appContainer
import ch.weissheimer.poly.core.DocumentFormat
import ch.weissheimer.poly.data.DocumentInfo
import ch.weissheimer.poly.ui.viewer.renderers.ImageRenderer
import ch.weissheimer.poly.ui.viewer.renderers.MarkdownRenderer
import ch.weissheimer.poly.ui.viewer.renderers.TextRenderer

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

    when (val s = state) {
        ViewerUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is ViewerUiState.Error -> RendererError(
            message = stringResource(
                if (s.permissionLost) R.string.viewer_error_permission
                else R.string.viewer_error_open
            ),
            onRetry = { viewModel.load() },
        )

        is ViewerUiState.Ready -> DocumentViewer(document = s.document, onBack = onBack)
    }
}

@Composable
private fun DocumentViewer(document: DocumentInfo, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    var chromeVisible by rememberSaveable { mutableStateOf(true) }

    val viewerState = remember(document) {
        ViewerState(document = document, onContentTap = { chromeVisible = !chromeVisible })
    }
    val renderer = remember(document) {
        when (document.format) {
            DocumentFormat.TXT -> TextRenderer(container.fileRepository)
            DocumentFormat.MARKDOWN -> MarkdownRenderer(container.fileRepository)
            DocumentFormat.JPEG, DocumentFormat.PNG, DocumentFormat.GIF -> ImageRenderer()
            else -> UnsupportedRenderer()
        }
    }

    ImmersiveMode(hideSystemBars = !chromeVisible)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        renderer.Content(viewerState, Modifier.fillMaxSize())

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ViewerTopBar(viewerState, onBack)
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            // Annotation mode slot – activated in a later phase.
            FloatingActionButton(
                onClick = {},
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.outline,
            ) {
                Icon(
                    Icons.Default.Draw,
                    contentDescription = stringResource(R.string.viewer_mark),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBar(state: ViewerState, onBack: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(state.document.displayName, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.viewer_back),
                )
            }
        },
        actions = {
            IconButton(onClick = { shareOriginal(context, state.document) }) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.viewer_share))
            }
            if (state.document.format == DocumentFormat.TXT) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.viewer_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (state.monospace) R.string.viewer_monospace_off
                                    else R.string.viewer_monospace_on
                                )
                            )
                        },
                        onClick = {
                            state.monospace = !state.monospace
                            menuOpen = false
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    )
}

/** Hides/shows the system bars; always restores them when leaving the viewer. */
@Composable
private fun ImmersiveMode(hideSystemBars: Boolean) {
    val view = LocalView.current
    DisposableEffect(hideSystemBars) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideSystemBars) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun shareOriginal(context: Context, document: DocumentInfo) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = document.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, document.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
