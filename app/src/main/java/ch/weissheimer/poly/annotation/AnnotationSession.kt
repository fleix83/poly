package ch.weissheimer.poly.annotation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.weissheimer.poly.data.AnnotationRepository
import ch.weissheimer.poly.data.DocumentInfo
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Per-document annotation state: mode, color/tool, displayable annotations,
 * undo/redo. Every mutation is persisted immediately (auto-save).
 */
class AnnotationSession(
    private val scope: CoroutineScope,
    private val repository: AnnotationRepository,
    private val document: DocumentInfo,
) {
    var modeActive by mutableStateOf(false)
    var activeColor by mutableStateOf(AnnotationColor.YELLOW)
    var activeTool by mutableStateOf(AnnotationTool.FREEHAND)

    val annotations = mutableStateListOf<Annotation>()
    var orphanedCount by mutableStateOf(0)
        private set
    var loaded by mutableStateOf(false)
        private set

    /** Annotation whose edit popup (color / delete) is open. */
    var editTarget by mutableStateOf<Annotation?>(null)

    private sealed interface Action {
        data class Add(val annotation: Annotation) : Action
        data class Remove(val annotation: Annotation) : Action
        data class Recolor(val id: String, val from: AnnotationColor, val to: AnnotationColor) : Action
    }

    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /**
     * Loads annotations once. [text] enables re-anchoring for text highlights;
     * pass null for purely geometric formats (images).
     */
    fun load(text: String?) {
        if (loaded) return
        scope.launch {
            val outcome = repository.loadFor(document, text)
            annotations.clear()
            annotations.addAll(outcome.anchored)
            orphanedCount = outcome.orphanedCount
            loaded = true
        }
    }

    fun toggleMode() {
        modeActive = !modeActive
        if (!modeActive) editTarget = null
    }

    fun newTextHighlight(anchor: TextAnchor): Annotation = Annotation(
        id = UUID.randomUUID().toString(),
        fileHash = document.sha256,
        fileUri = document.uri.toString(),
        format = document.format,
        type = AnnotationType.TEXT_HIGHLIGHT,
        color = activeColor,
        anchor = anchor,
        pageIndex = null,
        points = emptyList(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    fun newShape(type: AnnotationType, points: List<Float>, pageIndex: Int? = null): Annotation =
        Annotation(
            id = UUID.randomUUID().toString(),
            fileHash = document.sha256,
            fileUri = document.uri.toString(),
            format = document.format,
            type = type,
            color = activeColor,
            anchor = null,
            pageIndex = pageIndex,
            points = points,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

    fun add(annotation: Annotation) {
        apply(Action.Add(annotation))
        undoStack.addLast(Action.Add(annotation))
        redoStack.clear()
        updateFlags()
    }

    fun remove(annotation: Annotation) {
        apply(Action.Remove(annotation))
        undoStack.addLast(Action.Remove(annotation))
        redoStack.clear()
        updateFlags()
    }

    fun recolor(annotation: Annotation, color: AnnotationColor) {
        if (annotation.color == color) return
        val action = Action.Recolor(annotation.id, annotation.color, color)
        apply(action)
        undoStack.addLast(action)
        redoStack.clear()
        updateFlags()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        apply(invert(action))
        redoStack.addLast(action)
        updateFlags()
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        apply(action)
        undoStack.addLast(action)
        updateFlags()
    }

    private fun invert(action: Action): Action = when (action) {
        is Action.Add -> Action.Remove(action.annotation)
        is Action.Remove -> Action.Add(action.annotation)
        is Action.Recolor -> Action.Recolor(action.id, from = action.to, to = action.from)
    }

    private fun apply(action: Action) {
        when (action) {
            is Action.Add -> {
                annotations.add(action.annotation)
                persist(action.annotation)
            }
            is Action.Remove -> {
                annotations.removeAll { it.id == action.annotation.id }
                if (editTarget?.id == action.annotation.id) editTarget = null
                scope.launch { repository.delete(action.annotation.id) }
            }
            is Action.Recolor -> {
                val index = annotations.indexOfFirst { it.id == action.id }
                if (index >= 0) {
                    val updated = annotations[index].copy(
                        color = action.to,
                        updatedAt = System.currentTimeMillis(),
                    )
                    annotations[index] = updated
                    if (editTarget?.id == action.id) editTarget = updated
                    persist(updated)
                }
            }
        }
    }

    private fun persist(annotation: Annotation) {
        scope.launch { repository.save(annotation) }
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
}
