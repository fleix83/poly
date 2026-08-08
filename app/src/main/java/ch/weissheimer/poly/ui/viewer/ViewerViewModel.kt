package ch.weissheimer.poly.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.weissheimer.poly.annotation.AnnotationSession
import ch.weissheimer.poly.data.AnnotationRepository
import ch.weissheimer.poly.data.DocumentAccessException
import ch.weissheimer.poly.data.DocumentInfo
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.RecentsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Ready(
        val document: DocumentInfo,
        val annotationSession: AnnotationSession,
    ) : ViewerUiState

    data class Error(val permissionLost: Boolean) : ViewerUiState
}

class ViewerViewModel(
    private val uri: Uri,
    private val fileRepository: FileRepository,
    private val recentsRepository: RecentsRepository,
    private val annotationRepository: AnnotationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = ViewerUiState.Loading
        viewModelScope.launch {
            try {
                val info = fileRepository.resolve(uri)
                recentsRepository.record(info)
                val session = AnnotationSession(viewModelScope, annotationRepository, info)
                _state.value = ViewerUiState.Ready(info, session)
            } catch (e: DocumentAccessException) {
                _state.value = ViewerUiState.Error(permissionLost = true)
            } catch (e: Exception) {
                _state.value = ViewerUiState.Error(permissionLost = false)
            }
        }
    }
}
