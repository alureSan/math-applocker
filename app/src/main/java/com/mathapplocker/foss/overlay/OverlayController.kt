package com.mathapplocker.foss.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mathapplocker.foss.math.QuizQuestion
import com.mathapplocker.foss.ui.theme.MathAppTheme

data class OverlayInsets(
    val left: Dp = 0.dp,
    val top: Dp = 0.dp,
    val right: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

/**
 * Owns the `TYPE_APPLICATION_OVERLAY` window used for the math challenge.
 * A real overlay appears instantly over the locked app and does not push the
 * app into Picture-in-Picture or disturb the task/recents stack.
 */
class OverlayController(
    private val context: Context,
    private val questions: List<QuizQuestion>,
    private val allowNegative: Boolean,
    private val onSolved: () -> Unit,
    private val onExitHome: () -> Unit,
) {

    private val windowManager: WindowManager? =
        context.getSystemService(WindowManager::class.java)

    private var host: OverlayHostView? = null

    var isShown: Boolean = false
        private set

    /** @return true when the overlay was attached successfully. */
    fun show(): Boolean {
        if (isShown) return true
        val manager = windowManager ?: return false

        val host = OverlayHostView(context).apply {
            content = {
                MathAppTheme {
                    QuizOverlayScreen(
                        questions = questions,
                        allowNegative = allowNegative,
                        insets = readInsets(manager),
                        onSolved = {
                            hide()
                            onSolved()
                        },
                        onExitHome = {
                            hide()
                            onExitHome()
                        },
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Math challenge"
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Do not let the system inset the overlay around system bars; the
                // scrim must cover the entire screen.
                setFitInsetsTypes(0)
                setFitInsetsIgnoringVisibility(true)
            }
        }

        return try {
            manager.addView(host, params)
            host.attach()
            this.host = host
            isShown = true
            true
        } catch (_: Throwable) {
            runCatching { manager.removeView(host) }
            runCatching { host.destroy() }
            false
        }
    }

    fun hide() {
        val host = this.host ?: return
        this.host = null
        isShown = false
        runCatching { windowManager?.removeView(host) }
        host.destroy()
    }

    private fun readInsets(manager: WindowManager): OverlayInsets {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = manager.currentWindowMetrics
                val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                OverlayInsets(
                    left = (bars.left / density).dp,
                    top = (bars.top / density).dp,
                    right = (bars.right / density).dp,
                    bottom = (bars.bottom / density).dp,
                )
            } else {
                legacyInsets(manager, density)
            }
        }.getOrDefault(OverlayInsets(top = 24.dp, bottom = 16.dp))
    }

    @Suppress("DEPRECATION")
    private fun legacyInsets(manager: WindowManager, density: Float): OverlayInsets {
        val display = manager.defaultDisplay
        val real = DisplayMetrics().also { display.getRealMetrics(it) }
        val usable = DisplayMetrics().also { display.getMetrics(it) }

        val statusBarPx = context.resources
            .getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }
            ?.let { context.resources.getDimensionPixelSize(it) }
            ?: 0

        val landscape = real.widthPixels > real.heightPixels
        return if (landscape) {
            // Navigation bar is normally on the right in landscape.
            val navWidth = (real.widthPixels - usable.widthPixels).coerceAtLeast(0)
            OverlayInsets(
                top = (statusBarPx / density).dp,
                right = (navWidth / density).dp,
            )
        } else {
            val totalBars = (real.heightPixels - usable.heightPixels).coerceAtLeast(0)
            val navHeight = (totalBars - statusBarPx).coerceAtLeast(0)
            OverlayInsets(
                top = (statusBarPx / density).dp,
                bottom = (navHeight / density).dp,
            )
        }
    }
}
