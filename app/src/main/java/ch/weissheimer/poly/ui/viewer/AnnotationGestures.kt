package ch.weissheimer.poly.ui.viewer

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The annotation-mode pointer split: with the mode active, a single finger
 * marks (tap/drag, consumed in the Initial pass so nothing scrolls), a second
 * finger cancels marking and hands the gesture back to scroll/zoom.
 */
fun Modifier.annotationGestures(
    enabled: Boolean,
    onTap: (Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // Wait for the first finger down.
                var event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.firstOrNull { it.pressed && it.previousPressed.not() }
                    ?: continue
                val startPosition = down.position
                var dragging = false
                var cancelled = false
                down.consume()

                gesture@ while (true) {
                    event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }

                    if (pressed.size >= 2) {
                        // Second finger: cancel marking, let scroll/zoom run.
                        cancelled = true
                        if (dragging) onDragEnd(true)
                        dragging = false
                        while (event.changes.any { it.pressed }) {
                            event = awaitPointerEvent(PointerEventPass.Initial)
                        }
                        break@gesture
                    }

                    val change = event.changes.firstOrNull() ?: continue
                    if (!change.pressed) {
                        if (dragging) {
                            onDragEnd(false)
                        } else if (!cancelled) {
                            onTap(startPosition)
                        }
                        change.consume()
                        break@gesture
                    }

                    val position = change.position
                    if (!dragging &&
                        (position - startPosition).getDistance() > viewConfiguration.touchSlop
                    ) {
                        dragging = true
                        onDragStart(startPosition)
                    }
                    if (dragging) onDrag(position)
                    change.consume()
                }
            }
        }
    }
}
