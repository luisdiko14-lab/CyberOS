package com.cyberos

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs

@Composable
internal fun Modifier.cyberGestureInput(onFirefoxSwipe: () -> Unit, onQuickDiagnostics: () -> Unit): Modifier {
    val launchFirefox by rememberUpdatedState(onFirefoxSwipe)
    val runDiagnostics by rememberUpdatedState(onQuickDiagnostics)
    return pointerInput(Unit) {
        awaitEachGesture {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val gestureStart = firstDown.uptimeMillis
            var maxPointers = 1
            var twoFingerStartY: Float? = null
            var threeFingerStartY: Float? = null
            var lastCentroidY = firstDown.position.y
            var threeFingerMoved = false
            var gestureEnd = gestureStart

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isNotEmpty()) {
                    val centroidY = pressed.map { it.position.y }.average().toFloat()
                    lastCentroidY = centroidY
                    if (pressed.size > maxPointers) maxPointers = pressed.size
                    if (pressed.size >= 2 && twoFingerStartY == null) twoFingerStartY = centroidY
                    if (pressed.size >= 3 && threeFingerStartY == null) threeFingerStartY = centroidY
                    if (threeFingerStartY != null && abs(centroidY - threeFingerStartY!!) > 24f) threeFingerMoved = true
                }
                gestureEnd = event.changes.maxOfOrNull { it.uptimeMillis } ?: gestureEnd
            } while (event.changes.any { it.pressed })

            val twoFingerStart = twoFingerStartY
            if (maxPointers >= 2 && twoFingerStart != null && twoFingerStart - lastCentroidY > 72f) {
                launchFirefox()
            } else if (maxPointers >= 3 && !threeFingerMoved && gestureEnd - gestureStart < 700L) {
                runDiagnostics()
            }
        }
    }
}

internal fun performCyberHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    CyberSoundEngine.click()
}