package ch.weissheimer.poly.ui.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.RecentsRepository
import ch.weissheimer.poly.data.db.RecentFileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentsViewModel(
    private val recentsRepository: RecentsRepository,
    private val fileRepository: FileRepository,
) : ViewModel() {

    val recents: StateFlow<List<RecentFileEntity>> = recentsRepository.observeRecents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onDocumentPicked(uri: android.net.Uri) {
        fileRepository.persistReadPermission(uri)
    }

    fun remove(uri: String) {
        viewModelScope.launch { recentsRepository.remove(uri) }
    }
}
