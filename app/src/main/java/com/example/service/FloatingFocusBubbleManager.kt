package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlin.math.abs

/**
 * Manages system-level floating ("volant") overlays drawn over other apps when `SYSTEM_ALERT_WINDOW`
 * ("Display over other apps") is granted:
 *
 * 1. **Draggable Return-to-Focus Volant Bubble**: Appears when the Pomodoro timer is auto-paused because
 *    the user switched to a non-blocked app. Can be freely dragged anywhere on the screen with a finger
 *    and stays visible until the user taps it to return to Focus Island or opens Focus Island directly.
 * 2. **Friend Focus Nudge Toast Banner**: Appears over any screen when a friend in a Study Group / Challenge
 *    sends a "Return to Focus" nudge, complete with a "Return to Focus" action button.
 */
object FloatingFocusBubbleManager {

    private const val TAG = "FloatingFocusBubble"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var pausedBubbleView: View? = null
    private var pausedTitleView: TextView? = null
    private var pausedParams: WindowManager.LayoutParams? = null

    @Volatile
    private var nudgeBannerView: View? = null
    private var nudgeParams: WindowManager.LayoutParams? = null

    val isPausedBubbleShowing: Boolean
        get() = pausedBubbleView != null

    val isNudgeBannerShowing: Boolean
        get() = nudgeBannerView != null

    /**
     * Shows or updates the draggable floating ("volant") Return-to-Focus notification bubble.
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
                    gravity = Gravity.TOP or Gravity.START
                    x = dp(16)
                    y = dp(110)
                }

                val root = LinearLayout(appContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(28).toFloat()
                        setColor(Color.parseColor("#EE132A2E"))
                        setStroke(dp(2), Color.parseColor("#FF2DD4BF"))
                    }
                    elevation = dp(10).toFloat()
                }

                val iconView = ImageView(appContext).apply {
                    setImageResource(R.drawable.ic_widget_flame)
                    setColorFilter(Color.parseColor("#FF2DD4BF"))
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                        marginEnd = dp(10)
                    }
                }
                root.addView(iconView)

                val textColumn = LinearLayout(appContext).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp(12)
                    }
                }

                val titleTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.floating_focus_paused_title, remainingClockText)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                pausedTitleView = titleTv
                textColumn.addView(titleTv)

                val subtitleTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.floating_focus_paused_subtitle)
                    setTextColor(Color.parseColor("#B8E2F2"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                textColumn.addView(subtitleTv)
                root.addView(textColumn)

                val ctaTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.floating_focus_return_cta)
                    setTextColor(Color.parseColor("#082026"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(18).toFloat()
                        setColor(Color.parseColor("#FF2DD4BF"))
                    }
                    setOnClickListener {
                        returnToFocusApp(appContext)
                    }
                }
                root.addView(ctaTv)

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
     * Shows an interactive floating toast banner over any screen when a friend sends a "Return to Focus" nudge.
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

                // Remove any previous nudge banner before showing the fresh one
                nudgeBannerView?.let { old ->
                    runCatching { wm.removeViewImmediate(old) }
                    nudgeBannerView = null
                    nudgeParams = null
                }

                val density = appContext.resources.displayMetrics.density
                fun dp(value: Int): Int = (value * density).toInt()
                val screenWidth = appContext.resources.displayMetrics.widthPixels

                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val bannerWidth = (screenWidth - dp(24)).coerceAtMost(dp(420))
                val params = WindowManager.LayoutParams(
                    bannerWidth,
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
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(22).toFloat()
                        setColor(Color.parseColor("#F2162930"))
                        setStroke(dp(2), Color.parseColor("#FFF59E0B"))
                    }
                    elevation = dp(12).toFloat()
                }

                val topRow = LinearLayout(appContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val iconView = ImageView(appContext).apply {
                    setImageResource(R.drawable.ic_widget_flame)
                    setColorFilter(Color.parseColor("#FFF59E0B"))
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                        marginEnd = dp(10)
                    }
                }
                topRow.addView(iconView)

                val textCol = LinearLayout(appContext).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val titleTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.nudge_banner_title, fromFriendName)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.DEFAULT_BOLD
                }
                textCol.addView(titleTv)

                val bodyTv = TextView(appContext).apply {
                    text = appContext.getString(R.string.nudge_banner_message)
                    setTextColor(Color.parseColor("#D5E8EE"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                }
                textCol.addView(bodyTv)
                topRow.addView(textCol)

                val closeTv = TextView(appContext).apply {
                    text = "✕"
                    setTextColor(Color.parseColor("#99FFFFFF"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setPadding(dp(8), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        hideNudgeBanner(appContext)
                    }
                }
                topRow.addView(closeTv)
                root.addView(topRow)

                val actionRow = LinearLayout(appContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(10)
                    }
                }

                val returnBtn = TextView(appContext).apply {
                    text = appContext.getString(R.string.nudge_banner_return_button)
                    setTextColor(Color.parseColor("#1A1300"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(18).toFloat()
                        setColor(Color.parseColor("#FFF59E0B"))
                    }
                    setOnClickListener {
                        hideNudgeBanner(appContext)
                        onReturnClicked?.invoke()
                        returnToFocusAndStartTimer(appContext)
                    }
                }
                actionRow.addView(returnBtn)
                root.addView(actionRow)

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
            }.onFailure {
                Log.w(TAG, "Could not show floating nudge banner: ${it.message}")
            }
        }
    }

    fun hideNudgeBanner(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
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
