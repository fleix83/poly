package ch.weissheimer.poly.ui.viewer.renderers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.weissheimer.poly.R
import ch.weissheimer.poly.core.DocumentFormat
import ch.weissheimer.poly.data.LocalFileCache
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.RendererError
import ch.weissheimer.poly.ui.viewer.ViewerState
import ch.weissheimer.poly.viewer.office.DocxToHtml
import ch.weissheimer.poly.viewer.office.SheetInfo
import ch.weissheimer.poly.viewer.office.XlsxToHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ROW_STEP = 500

private sealed interface OfficeLoad {
    data object Converting : OfficeLoad
    data class Ready(val html: String, val sheets: List<SheetInfo> = emptyList()) : OfficeLoad
    data object Failed : OfficeLoad
}

/** docx and xlsx: converted to HTML (cached per content hash), shown in the WebView. */
class OfficeRenderer : DocumentRenderer {

    override val capabilities = RendererCapabilities(textSelectable = true, zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        val context = LocalContext.current.applicationContext
        val document = state.document
        var sheetIndex by rememberSaveable { mutableIntStateOf(0) }
        var rowLimit by rememberSaveable { mutableIntStateOf(ROW_STEP) }
        val truncatedLabel = stringResource(R.string.xlsx_rows_shown)
        val moreLabel = stringResource(R.string.xlsx_more_rows)

        val load by produceState<OfficeLoad>(
            OfficeLoad.Converting, document.uri, sheetIndex, rowLimit,
        ) {
            value = OfficeLoad.Converting
            value = try {
                withContext(Dispatchers.Default) {
                    val cacheName = when (document.format) {
                        DocumentFormat.DOCX -> "${document.sha256}-docx.html"
                        else -> "${document.sha256}-s$sheetIndex-r$rowLimit.html"
                    }
                    val cached = LocalFileCache.cacheFile(context, cacheName)
                    if (document.format == DocumentFormat.DOCX) {
                        val html = if (cached.length() > 0) {
                            cached.readText()
                        } else {
                            val file = LocalFileCache.localCopy(context, document.uri, document.sha256, "docx")
                            DocxToHtml().convert(file).also { cached.writeText(it) }
                        }
                        OfficeLoad.Ready(html)
                    } else {
                        val file = LocalFileCache.localCopy(context, document.uri, document.sha256, "xlsx")
                        val converter = XlsxToHtml()
                        val sheets = converter.sheets(file)
                        if (sheets.isEmpty()) {
                            OfficeLoad.Failed
                        } else {
                            val selected = sheets.getOrElse(sheetIndex) { sheets.first() }
                            val html = if (cached.length() > 0) {
                                cached.readText()
                            } else {
                                converter.convertSheet(file, selected, rowLimit, truncatedLabel, moreLabel)
                                    .html.also { cached.writeText(it) }
                            }
                            OfficeLoad.Ready(html, sheets)
                        }
                    }
                }
            } catch (e: Exception) {
                OfficeLoad.Failed
            }
        }

        when (val l = load) {
            OfficeLoad.Converting -> ConversionProgress(modifier)
            OfficeLoad.Failed -> RendererError(stringResource(R.string.office_error), modifier)
            is OfficeLoad.Ready -> Column(modifier.fillMaxSize()) {
                if (l.sheets.size > 1) {
                    SheetChips(
                        sheets = l.sheets,
                        selected = sheetIndex,
                        onSelect = { index ->
                            sheetIndex = index
                            rowLimit = ROW_STEP
                        },
                    )
                }
                RestrictedWebView(
                    html = l.html,
                    session = state.annotations,
                    onContentTap = { state.onContentTap() },
                    onLoadMoreRows = { rowLimit += ROW_STEP },
                    pageIndex = if (document.format == DocumentFormat.XLSX) sheetIndex else null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ConversionProgress(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                stringResource(R.string.office_converting),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SheetChips(
    sheets: List<SheetInfo>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sheets.forEachIndexed { index, sheet ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    label = { Text(sheet.name, maxLines = 1) },
                )
            }
        }
    }
}
