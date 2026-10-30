package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.AppStyle
import com.example.ui.theme.FocusStyles
import com.example.ui.theme.ThemeMode
import kotlin.math.abs

/**
 * Manages system-level floating ("volant") overlays drawn over other apps when `SYSTEM_ALERT_WINDOW`
 * ("Display over other apps") is granted:
 *
 * 1. **Draggable Return-to-Focus Volant Capsule**: Appears when the Pomodoro timer is auto-paused because
 *    the user switched to a non-blocked app. Can be freely dragged anywhere on the screen with a finger
 *    and stays visible until the user taps it to return to Focus Island or opens Focus Island directly.
 * 2. **Friend Focus Nudge Toast Capsule**: Appears over any screen when a friend in a Study Group / Challenge
 *    sends a "Return to Focus" nudge. Tapping anywhere on the capsule returns to focus.
 */
object FloatingFocusBubbleManager {

    private const val TAG = "FloatingFocusBubble"
    private const val PREFS_NAME = "focusflow_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var pausedBubbleView: View? = null
    private var pausedTitleView: TextView? = null
    private var pausedParams: WindowManager.LayoutParams? = null

    @Volatile
    private var nudgeBannerView: View? = null
    private var nudgeParams: WindowManager.LayoutParams? = null
    private var nudgeAutoHideRunnable: Runnable? = null

    val isPausedBubbleShowing: Boolean
        get() = pausedBubbleView != null

    val isNudgeBannerShowing: Boolean
        get() = nudgeBannerView != null

    private fun resolveActiveColorScheme(context: Context): ColorScheme {
        val style = AppStyle.read(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = ThemeMode.from(prefs.getString(KEY_THEME_MODE, null))
        val isDark = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
        return FocusStyles.of(style).colors(isDark)
    }

    /**
     * Shows or updates the sleek, buttonless draggable floating ("volant") Return-to-Focus capsule
     * styled with the user's active [AppStyle] colors.
     */
    fun showPausedBubble(context: Context, remainingClockText: String) {
        val appContext = context.applicationContext
        mainHandler.post {
            runCatching {
                if (!Settings.canDrawOverlays(appContext)) return@post
                val wm = appContext.getSystemService(WindowManager::class.java) ?: return@post

                val existingTitle = pausedTitleView
                if (pausedBubbleView != null && existingTitle != null) {
                    existingTitle.text = appContext.getString(R.string.floating_focus_paused_title, remainingClockText)
                    return@post
                }

                val density = appContext.resources.displayMetrics.density
                fun dp(value: Int): Int = (value * density).toInt()
                val scheme = resolveActiveColorScheme(appContext)

                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = 0
                    y = dp(48)
                }

                val root = LinearLayout(appContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(7), dp(14), dp(7))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(999).toFloat()
                        setColor(scheme.surfaceContainerHigh.toArgb())
                        setStroke(dp(1), scheme.primary.copy(alpha = 0.55f).toArgb())
                    }
                    elevation = dp(8).toFloat()
                }

                val iconBadge = LinearLayout(appContext).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                        marginEnd = dp(10)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(scheme.primaryContainer.toArgb())
                    }
                }

                val iconView = ImageView(appContext).apply {
                    setImageResource(R.drawable.ic_widget_flame)
                    setColorFilter(scheme.onPrimaryContainer.toArgb())
                    layoutParams = LinearLayout.LayoutParams(dp(15), dp(15))
                }
                iconBadge.addView(iconView)
                root.addView(iconBadge)

                val textColumn = LinearLayout(appContext).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val titleTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.floating_focus_paused_title, remainingClockText)
                    setTextColor(scheme.onSurface.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = dp(240)
                }
                pausedTitleView = titleTv
                textColumn.addView(titleTv)

                val subtitleTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.floating_focus_paused_subtitle)
                    setTextColor(scheme.onSurfaceVariant.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = dp(240)
                }
                textColumn.addView(subtitleTv)
                root.addView(textColumn)

                attachDragAndTapListener(
                    view = root,
                    params = params,
                    windowManager = wm,
                    onTap = { returnToFocusApp(appContext) }
                )

                wm.addView(root, params)
                pausedBubbleView = root
                pausedParams = params
            }.onFailure {
                Log.w(TAG, "Could not show floating paused bubble: ${it.message}")
            }
        }
    }

    /**
     * Removes the floating Return-to-Focus bubble if currently visible.
     */
    fun hidePausedBubble(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
            val view = pausedBubbleView ?: return@post
            pausedBubbleView = null
            pausedTitleView = null
            pausedParams = null
            runCatching {
                appContext.getSystemService(WindowManager::class.java)?.removeViewImmediate(view)
            }
        }
    }

    /**
     * Shows a sleek, compact pill-shaped toast banner over any screen when a friend sends a "Return to Focus" nudge,
     * styled with the user's active [AppStyle] colors.
     */
    fun showNudgeBanner(
        context: Context,
        fromFriendName: String,
        onReturnClicked: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        mainHandler.post {
            runCatching {
                if (!Settings.canDrawOverlays(appContext)) return@post
                val wm = appContext.getSystemService(WindowManager::class.java) ?: return@post

                nudgeAutoHideRunnable?.let { mainHandler.removeCallbacks(it) }
                nudgeAutoHideRunnable = null

                // Remove any previous nudge banner before showing the fresh one
                nudgeBannerView?.let { old ->
                    runCatching { wm.removeViewImmediate(old) }
                    nudgeBannerView = null
                    nudgeParams = null
                }

                val density = appContext.resources.displayMetrics.density
                fun dp(value: Int): Int = (value * density).toInt()
                val scheme = resolveActiveColorScheme(appContext)

                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = 0
                    y = dp(44)
                }

                val root = LinearLayout(appContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(7), dp(14), dp(7))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(999).toFloat()
                        setColor(scheme.surfaceContainerHigh.toArgb())
                        setStroke(dp(1), scheme.primary.copy(alpha = 0.55f).toArgb())
                    }
                    elevation = dp(8).toFloat()
                }

                val iconBadge = LinearLayout(appContext).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                        marginEnd = dp(10)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(scheme.primaryContainer.toArgb())
                    }
                }

                val iconView = ImageView(appContext).apply {
                    setImageResource(R.drawable.ic_widget_flame)
                    setColorFilter(scheme.onPrimaryContainer.toArgb())
                    layoutParams = LinearLayout.LayoutParams(dp(15), dp(15))
                }
                iconBadge.addView(iconView)
                root.addView(iconBadge)

                val textCol = LinearLayout(appContext).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val titleTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.nudge_banner_title, fromFriendName)
                    setTextColor(scheme.onSurface.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = dp(230)
                }
                textCol.addView(titleTv)

                val bodyTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.nudge_banner_message)
                    setTextColor(scheme.onSurfaceVariant.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = dp(230)
                }
                textCol.addView(bodyTv)
                root.addView(textCol)

                attachDragAndTapListener(
                    view = root,
                    params = params,
                    windowManager = wm,
                    onTap = {
                        hideNudgeBanner(appContext)
                        onReturnClicked?.invoke()
                        returnToFocusAndStartTimer(appContext)
                    }
                )

                wm.addView(root, params)
                nudgeBannerView = root
                nudgeParams = params

                val autoHide = Runnable {
                    hideNudgeBanner(appContext)
                    onReturnClicked?.invoke()
                }
                nudgeAutoHideRunnable = autoHide
                mainHandler.postDelayed(autoHide, 4_500L)
            }.onFailure {
                Log.w(TAG, "Could not show floating nudge banner: ${it.message}")
            }
        }
    }

    fun hideNudgeBanner(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
            nudgeAutoHideRunnable?.let { mainHandler.removeCallbacks(it) }
            nudgeAutoHideRunnable = null
            val view = nudgeBannerView ?: return@post
            nudgeBannerView = null
            nudgeParams = null
            runCatching {
                appContext.getSystemService(WindowManager::class.java)?.removeViewImmediate(view)
            }
        }
    }

    internal fun returnToFocusApp(context: Context) {
        hidePausedBubble(context)
        runCatching {
            val resumeIntent = Intent(context, PomodoroTimerService::class.java)
                .setAction(PomodoroTimerService.ACTION_AUTO_RESUME)
            ContextCompat.startForegroundService(context, resumeIntent)
        }
        runCatching {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TARGET_SCREEN, "focus")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(openAppIntent)
        }
    }

    internal fun returnToFocusAndStartTimer(context: Context) {
        hidePausedBubble(context)
        hideNudgeBanner(context)
        runCatching {
            val startIntent = Intent(context, PomodoroTimerService::class.java)
                .setAction(PomodoroTimerService.ACTION_START)
            ContextCompat.startForegroundService(context, startIntent)
        }
        runCatching {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TARGET_SCREEN, "focus")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(openAppIntent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragAndTapListener(
        view: View,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        onTap: () -> Unit
    ) {
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = (initialY + dy).coerceAtLeast(24)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onTap()
                    }
                    true
                }
                else -> false
            }
        }
    }
}
