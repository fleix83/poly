package ch.weissheimer.poly.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.weissheimer.poly.R
import ch.weissheimer.poly.annotation.AnnotationColor
import ch.weissheimer.poly.annotation.AnnotationSession
import ch.weissheimer.poly.annotation.AnnotationTool
import ch.weissheimer.poly.appContainer
import ch.weissheimer.poly.core.DocumentFormat
import ch.weissheimer.poly.data.DocumentInfo
import ch.weissheimer.poly.export.ExportPayload
import ch.weissheimer.poly.export.Exporter
import ch.weissheimer.poly.export.PdfAnnotationSaver
import ch.weissheimer.poly.ui.viewer.renderers.HtmlRenderer
import ch.weissheimer.poly.ui.viewer.renderers.ImageRenderer
import ch.weissheimer.poly.ui.viewer.renderers.MarkdownRenderer
import ch.weissheimer.poly.ui.viewer.renderers.OfficeRenderer
import ch.weissheimer.poly.ui.viewer.renderers.PdfViewRenderer
import ch.weissheimer.poly.ui.viewer.renderers.TextRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ViewerScreen(
    uri: Uri,
    onBack: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel: ViewerViewModel = viewModel(key = uri.toString()) {
        ViewerViewModel(
            uri,
            container.fileRepository,
            container.recentsRepository,
            container.annotationRepository,
        )
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

        is ViewerUiState.Ready -> DocumentViewer(
            document = s.document,
            session = s.annotationSession,
            onBack = onBack,
        )
    }
}

@Composable
private fun DocumentViewer(
    document: DocumentInfo,
    session: AnnotationSession,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    var chromeRequested by rememberSaveable { mutableStateOf(true) }
    val chromeVisible = chromeRequested || session.modeActive

    val viewerState = remember(document, session) {
        ViewerState(
            document = document,
            annotations = session,
            onContentTap = {
                if (!session.modeActive) chromeRequested = !chromeRequested
            },
        )
    }
    val renderer = remember(document) {
        when (document.format) {
            DocumentFormat.TXT -> TextRenderer(container.fileRepository)
            DocumentFormat.MARKDOWN -> MarkdownRenderer(container.fileRepository)
            DocumentFormat.JPEG, DocumentFormat.PNG, DocumentFormat.GIF -> ImageRenderer()
            DocumentFormat.PDF -> PdfViewRenderer()
            DocumentFormat.DOCX, DocumentFormat.XLSX -> OfficeRenderer()
            DocumentFormat.HTML -> HtmlRenderer(container.fileRepository)
            else -> UnsupportedRenderer()
        }
    }
    val annotatable = document.format != DocumentFormat.UNKNOWN

    ImmersiveMode(hideSystemBars = !chromeVisible)

    val snackbarHostState = remember { SnackbarHostState() }
    val orphanMessage = stringResource(R.string.annotation_orphaned, session.orphanedCount)
    LaunchedEffect(session.orphanedCount) {
        if (session.orphanedCount > 0) snackbarHostState.showSnackbar(orphanMessage)
    }

    // --- Export / save / share ---
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportPayload?>(null) }
    var overwriteConfirm by remember { mutableStateOf(false) }
    var shareDialog by remember { mutableStateOf(false) }
    val exportFailedMessage = stringResource(R.string.export_failed)
    val exportDoneMessage = stringResource(R.string.export_done)
    val gifHintMessage = stringResource(R.string.gif_static_hint)

    fun deliverPending(target: Uri?) {
        val payload = pendingExport
        pendingExport = null
        if (target == null || payload == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        payload.file.inputStream().use { it.copyTo(out) }
                    } != null
                }.getOrDefault(false)
            }
            snackbarHostState.showSnackbar(if (ok) exportDoneMessage else exportFailedMessage)
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { deliverPending(it) }
    val savePngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { deliverPending(it) }
    val saveJpegLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg"),
    ) { deliverPending(it) }

    fun launchSaveFor(payload: ExportPayload) {
        pendingExport = payload
        when (payload.mimeType) {
            "image/png" -> savePngLauncher.launch(payload.suggestedName)
            "image/jpeg" -> saveJpegLauncher.launch(payload.suggestedName)
            else -> savePdfLauncher.launch(payload.suggestedName)
        }
    }

    fun exportAnnotated() {
        if (exporting) return
        scope.launch {
            exporting = true
            try {
                val payload = Exporter.buildAnnotatedExport(context, container, viewerState)
                if (document.format == DocumentFormat.GIF) {
                    snackbarHostState.showSnackbar(gifHintMessage)
                }
                launchSaveFor(payload)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(exportFailedMessage)
            } finally {
                exporting = false
            }
        }
    }

    fun overwriteOriginal() {
        if (exporting) return
        scope.launch {
            exporting = true
            try {
                withContext(Dispatchers.IO) {
                    val source = Exporter.pdfSource(context, document)
                    context.contentResolver.openOutputStream(document.uri, "wt")?.use { out ->
                        PdfAnnotationSaver.save(context, source, session.annotations.toList(), out)
                    } ?: throw IllegalStateException("no write stream")
                }
                snackbarHostState.showSnackbar(exportDoneMessage)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(exportFailedMessage)
            } finally {
                exporting = false
            }
        }
    }

    fun shareAnnotated() {
        if (exporting) return
        scope.launch {
            exporting = true
            try {
                val payload = Exporter.buildAnnotatedExport(context, container, viewerState)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", payload.file,
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = payload.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, null))
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(exportFailedMessage)
            } finally {
                exporting = false
            }
        }
    }

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
            Column {
                ViewerTopBar(
                    state = viewerState,
                    onBack = onBack,
                    onShare = {
                        if (session.annotations.isEmpty()) {
                            shareOriginal(context, document)
                        } else {
                            shareDialog = true
                        }
                    },
                    onExport = ::exportAnnotated,
                    onOverwrite = if (document.format == DocumentFormat.PDF) {
                        { overwriteConfirm = true }
                    } else null,
                )
                AnimatedVisibility(visible = session.modeActive) {
                    AnnotationStatusBar(session, showTools = viewerState.document.format.isImage)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        if (annotatable) {
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { session.toggleMode() },
                    containerColor = if (session.modeActive) {
                        session.activeColor.color
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (session.modeActive) {
                        Color.Black.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ) {
                    Icon(
                        Icons.Default.Draw,
                        contentDescription = stringResource(
                            if (session.modeActive) R.string.annotation_mode_off
                            else R.string.viewer_mark
                        ),
                    )
                }
            }
        }

        if (exporting) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.export_working))
                }
            }
        }

        if (overwriteConfirm) {
            AlertDialog(
                onDismissRequest = { overwriteConfirm = false },
                title = { Text(stringResource(R.string.overwrite_title)) },
                text = { Text(stringResource(R.string.overwrite_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        overwriteConfirm = false
                        overwriteOriginal()
                    }) { Text(stringResource(R.string.overwrite_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { overwriteConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (shareDialog) {
            AlertDialog(
                onDismissRequest = { shareDialog = false },
                title = { Text(stringResource(R.string.viewer_share)) },
                text = { Text(stringResource(R.string.share_choice_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        shareDialog = false
                        shareAnnotated()
                    }) { Text(stringResource(R.string.share_with_annotations)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        shareDialog = false
                        shareOriginal(context, document)
                    }) { Text(stringResource(R.string.share_original)) }
                },
            )
        }

        session.editTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { session.editTarget = null },
                title = { Text(stringResource(R.string.annotation_edit_title)) },
                text = {
                    ColorRow(
                        selected = target.color,
                        onSelect = { color -> session.recolor(target, color) },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { session.remove(target) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(stringResource(R.string.annotation_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { session.editTarget = null }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }
    }
}

@Composable
private fun AnnotationStatusBar(session: AnnotationSession, showTools: Boolean) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.annotation_active),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (showTools) {
                FilledIconToggleButton(
                    checked = session.activeTool == AnnotationTool.RECT,
                    onCheckedChange = { session.activeTool = AnnotationTool.RECT },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.CropSquare,
                        contentDescription = stringResource(R.string.annotation_tool_rect),
                        modifier = Modifier.size(18.dp),
                    )
                }
                FilledIconToggleButton(
                    checked = session.activeTool == AnnotationTool.FREEHAND,
                    onCheckedChange = { session.activeTool = AnnotationTool.FREEHAND },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Gesture,
                        contentDescription = stringResource(R.string.annotation_tool_freehand),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            ColorRow(
                selected = session.activeColor,
                onSelect = { session.activeColor = it },
                compact = true,
            )
            IconButton(
                onClick = { session.undo() },
                enabled = session.canUndo,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.annotation_undo),
                )
            }
            IconButton(
                onClick = { session.redo() },
                enabled = session.canRedo,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.annotation_redo),
                )
            }
        }
    }
}

@Composable
private fun ColorRow(
    selected: AnnotationColor,
    onSelect: (AnnotationColor) -> Unit,
    compact: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
        AnnotationColor.entries.forEach { color ->
            Box(
                modifier = Modifier
                    .size(if (compact) 24.dp else 36.dp)
                    .background(color.color, CircleShape)
                    .border(
                        width = if (color == selected) 3.dp else 1.dp,
                        color = if (color == selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .selectable(
                        selected = color == selected,
                        onClick = { onSelect(color) },
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBar(
    state: ViewerState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onOverwrite: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val format = state.document.format

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
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.viewer_share))
            }
            if (format != DocumentFormat.UNKNOWN) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.viewer_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val exportLabel = when {
                        format == DocumentFormat.PDF -> R.string.menu_save_copy
                        format.isImage -> R.string.menu_export_image
                        else -> R.string.menu_export_pdf
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(exportLabel)) },
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                    if (onOverwrite != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_overwrite)) },
                            onClick = {
                                menuOpen = false
                                onOverwrite()
                            },
                        )
                    }
                    if (format == DocumentFormat.TXT) {
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
