package com.ai.assistance.onecode.terminal

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
        lastBackPressedTime = 0L
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))

        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        val terminalManager = TerminalManager.getInstance(this)
        
        lifecycleScope.launch {
            try {
                if (terminalManager.terminalState.value.sessions.isEmpty()) {
                    terminalManager.createNewSession("Default Session")
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

        // 双重侧滑退出：第一次侧滑拉出状态栏+提示，2秒内再侧滑退出 app
        // 子页面(Settings/Setup)的 NavHost BackHandler 优先级更高，不会被这里拦截
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (lastBackPressedTime > 0 && currentTime - lastBackPressedTime < 2000L) {
                    handler.removeCallbacks(hideStatusRunnable)
                    finish()
                } else {
                    statusBarShown = true
                    windowInsetsController.show(WindowInsetsCompat.Type.statusBars())
                    Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                    lastBackPressedTime = currentTime
                    handler.removeCallbacks(hideStatusRunnable)
                    handler.postDelayed(hideStatusRunnable, 3000L)
                }
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (statusBarShown && ev.action == MotionEvent.ACTION_DOWN) {
            val insets = ViewCompat.getRootWindowInsets(window.decorView)
            val statusBarHeight = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
                ?: getStatusBarHeightFallback()
            if (ev.rawY > statusBarHeight) {
                statusBarShown = false
                lastBackPressedTime = 0L
                handler.removeCallbacks(hideStatusRunnable)
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
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
