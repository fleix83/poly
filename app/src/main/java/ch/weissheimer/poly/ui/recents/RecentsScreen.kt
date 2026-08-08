package ch.weissheimer.poly.ui.recents

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.weissheimer.poly.R
import ch.weissheimer.poly.appContainer
import ch.weissheimer.poly.core.DocumentFormat
import coil3.compose.AsyncImage

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
        RecentsViewModel(
            container.recentsRepository,
            container.fileRepository,
            container.annotationRepository,
        )
    }
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    var aboutOpen by remember { mutableStateOf(false) }

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
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { aboutOpen = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_title))
                    }
                },
            )
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
                items(recents, key = { it.entity.uri }) { row ->
                    DismissableRecentRow(
                        row = row,
                        onClick = { onOpenDocument(Uri.parse(row.entity.uri)) },
                        onRemove = { viewModel.remove(row.entity.uri) },
                    )
                }
            }
        }
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissableRecentRow(
    row: RecentRow,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onRemove()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.recents_remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.align(
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        }
                    ),
                )
            }
        },
    ) {
        RecentFileRow(row = row, onClick = onClick)
    }
}

@Composable
private fun RecentFileRow(
    row: RecentRow,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val recent = row.entity
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
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        headlineContent = { Text(recent.displayName, maxLines = 1) },
        supportingContent = { Text(subtitle) },
        leadingContent = { RecentThumbnail(recent = recent, format = format) },
        trailingContent = {
            if (row.hasAnnotations) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(
                        Icons.Default.Draw,
                        contentDescription = stringResource(R.string.recents_annotated),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    )
}

@Composable
private fun RecentThumbnail(
    recent: ch.weissheimer.poly.data.db.RecentFileEntity,
    format: DocumentFormat,
) {
    val container = LocalContext.current.appContainer
    val shape = RoundedCornerShape(8.dp)
    val thumbModifier = Modifier
        .size(44.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    when {
        format.isImage -> AsyncImage(
            model = recent.uri,
            contentDescription = null,
            modifier = thumbModifier,
            contentScale = ContentScale.Crop,
        )

        format == DocumentFormat.PDF -> {
            val thumbFile by produceState<java.io.File?>(null, recent.uri, recent.sha256) {
                value = container.thumbnailStore.pdfThumbnail(Uri.parse(recent.uri), recent.sha256)
            }
            if (thumbFile != null) {
                AsyncImage(
                    model = thumbFile,
                    contentDescription = null,
                    modifier = thumbModifier,
                    contentScale = ContentScale.Crop,
                )
            } else {
                FormatIconBox(format, thumbModifier)
            }
        }

        else -> FormatIconBox(format, thumbModifier)
    }
}

@Composable
private fun FormatIconBox(format: DocumentFormat, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            format.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_text))
                Text(
                    stringResource(R.string.about_licenses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
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
