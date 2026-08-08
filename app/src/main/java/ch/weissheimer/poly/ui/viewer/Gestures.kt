package ch.weissheimer.poly.ui.viewer

import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Intercepts two-finger pinches in the Initial pass (before children see
 * them), so single-finger scrolling of the child keeps working. This is the
 * same pointer-count split the annotation mode uses later.
 */
fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            var zooming = false
            // Wait for the first finger of a gesture.
            do {
                val down = awaitPointerEvent(PointerEventPass.Initial)
            } while (down.changes.none { it.pressed })

            // Track the gesture until all fingers lift.
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    val zoom = event.calculateZoom()
                    if (zooming || abs(1f - zoom) > 0.005f) {
                        zooming = true
                        onZoom(zoom)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}
