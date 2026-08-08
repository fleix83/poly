package ch.weissheimer.poly.ui.recents

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.weissheimer.poly.R
import ch.weissheimer.poly.appContainer
import ch.weissheimer.poly.core.DocumentFormat
import ch.weissheimer.poly.data.db.RecentFileEntity

/** MIME types offered to the system file picker. */
private val PICKER_MIME_TYPES = arrayOf(
    "text/plain",
    "text/markdown",
    "text/x-markdown",
    "text/html",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream",
    "image/jpeg",
    "image/png",
    "image/gif",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    onOpenDocument: (Uri) -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel: RecentsViewModel = viewModel {
        RecentsViewModel(container.recentsRepository, container.fileRepository)
    }
    val recents by viewModel.recents.collectAsStateWithLifecycle()

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onDocumentPicked(uri)
            onOpenDocument(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openDocumentLauncher.launch(PICKER_MIME_TYPES) },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                text = { Text(stringResource(R.string.recents_open_file)) },
            )
        },
    ) { padding ->
        if (recents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    stringResource(R.string.recents_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    stringResource(R.string.recents_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(recents, key = { it.uri }) { recent ->
                    RecentFileRow(
                        recent = recent,
                        onClick = { onOpenDocument(Uri.parse(recent.uri)) },
                        onRemove = { viewModel.remove(recent.uri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFileRow(
    recent: RecentFileEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val format = runCatching { DocumentFormat.valueOf(recent.format) }.getOrDefault(DocumentFormat.UNKNOWN)
    val subtitle = buildString {
        append(
            DateUtils.getRelativeTimeSpanString(
                recent.lastOpenedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
        )
        recent.sizeBytes?.let {
            append(" · ")
            append(Formatter.formatShortFileSize(context, it))
        }
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(recent.displayName, maxLines = 1) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                format.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.recents_remove),
                )
            }
        },
    )
}

private fun DocumentFormat.icon(): ImageVector = when (this) {
    DocumentFormat.TXT -> Icons.Default.TextSnippet
    DocumentFormat.MARKDOWN -> Icons.AutoMirrored.Filled.Article
    DocumentFormat.PDF -> Icons.Default.PictureAsPdf
    DocumentFormat.DOCX -> Icons.Default.Description
    DocumentFormat.XLSX -> Icons.Default.TableChart
    DocumentFormat.HTML -> Icons.Default.Code
    DocumentFormat.JPEG, DocumentFormat.PNG, DocumentFormat.GIF -> Icons.Default.Image
    DocumentFormat.UNKNOWN -> Icons.Default.Description
}
