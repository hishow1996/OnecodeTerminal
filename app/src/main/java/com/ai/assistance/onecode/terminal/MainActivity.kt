package com.ai.assistance.onecode.terminal

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ai.assistance.onecode.terminal.main.TerminalScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log


class MainActivity : ComponentActivity() {

    private var statusBarShown = false
    private var lastBackPressedTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private val hideStatusRunnable = Runnable {
        statusBarShown = false
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private var longPressRunnable: Runnable? = null
    private var downX = 0f
    private var downY = 0f
    private val edgeWidth: Int by lazy { (32 * resources.displayMetrics.density).toInt() }
    private val exclusionHeight: Int by lazy { (200 * resources.displayMetrics.density).toInt() }
    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val longPressTimeout = 500L

    private val showStatusRunnable = Runnable {
        statusBarShown = true
        handler.removeCallbacks(hideStatusRunnable)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        handler.postDelayed(hideStatusRunnable, 3000L)
        longPressRunnable = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))

        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ew = edgeWidth
            val eh = exclusionHeight
            val w = resources.displayMetrics.widthPixels
            val leftRect = Rect(0, 0, ew, eh)
            val rightRect = Rect(w - ew, 0, w, eh)
            window.decorView.setSystemGestureExclusionRects(listOf(leftRect, rightRect))
        }

        val terminalManager = TerminalManager.getInstance(this)
        
        lifecycleScope.launch {
            try {
                if (terminalManager.terminalState.value.sessions.isEmpty()) {
                    terminalManager.createNewSession("Ubuntu 1")
                    Log.d("MainActivity", "Initial session created successfully")
                } else {
                    Log.d("MainActivity", "Session already exists, skipping creation")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to create initial session", e)
            }
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                val context = LocalContext.current
                val terminalManagerInCompose = remember { TerminalManager.getInstance(context) }
                val terminalEnv = rememberTerminalEnv(terminalManagerInCompose)

                TerminalScreen(
                    env = terminalEnv
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (lastBackPressedTime > 0 && currentTime - lastBackPressedTime < 2000L) {
                    finish()
                } else {
                    lastBackPressedTime = currentTime
                    Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY

                if (statusBarShown) {
                    val insets = ViewCompat.getRootWindowInsets(window.decorView)
                    val statusBarHeight = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
                        ?: getStatusBarHeightFallback()
                    if (downY > statusBarHeight) {
                        statusBarShown = false
                        lastBackPressedTime = 0L
                        handler.removeCallbacks(hideStatusRunnable)
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    }
                } else if (longPressRunnable == null) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    if (downX < edgeWidth || downX > screenWidth - edgeWidth) {
                        if (downY < exclusionHeight) {
                            longPressRunnable = showStatusRunnable
                            handler.postDelayed(showStatusRunnable, longPressTimeout)
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressRunnable != null) {
                    val dx = Math.abs(ev.rawX - downX)
                    val dy = Math.abs(ev.rawY - downY)
                    if (dx > touchSlop || dy > touchSlop) {
                        handler.removeCallbacks(longPressRunnable!!)
                        longPressRunnable = null
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (longPressRunnable != null) {
                    handler.removeCallbacks(longPressRunnable!!)
                    longPressRunnable = null
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun getStatusBarHeightFallback(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideStatusRunnable)
        handler.removeCallbacks(showStatusRunnable)
        super.onDestroy()
    }

    private var blackOverlay: View? = null

    private fun showBlackOverlay() {
        if (blackOverlay != null) return
        val root = window.decorView as? ViewGroup ?: return
        val overlay = View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            isClickable = false
            isFocusable = false
        }
        root.addView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        blackOverlay = overlay
    }

    private fun hideBlackOverlay() {
        blackOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        blackOverlay = null
    }

    private fun dismissKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        window.decorView.requestLayout()
        window.decorView.findViewById<View>(android.R.id.content)?.requestLayout()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        showBlackOverlay()
        dismissKeyboard()
    }

    override fun onPause() {
        super.onPause()
        dismissKeyboard()
    }

    override fun onResume() {
        super.onResume()
        hideBlackOverlay()
    }
}
