package handboard.app.core.theme

import android.content.Context
import android.os.Build
import android.view.Window
import android.view.WindowManager

/**
 * Frosted-glass IME background (FrostKeys-inspired, AOSP path only).
 *
 * Uses cross-window blur behind the IME window when the platform offers it
 * (Android 12+, blur enabled, no battery saver); otherwise everything
 * collapses to the solid theme background by design — never blur-or-crash.
 */
object FrostedGlass {
    const val DEFAULT_RADIUS_PX = 80

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val wm = context.getSystemService(WindowManager::class.java) ?: return false
            wm.isCrossWindowBlurEnabled
        } catch (_: Exception) {
            false
        }
    }

    fun applyToWindow(window: Window?, radiusPx: Int = DEFAULT_RADIUS_PX) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || window == null) return
        try {
            window.setBackgroundBlurRadius(radiusPx)
        } catch (_: Exception) {
        }
    }

    fun clear(window: Window?) = applyToWindow(window, 0)
}
