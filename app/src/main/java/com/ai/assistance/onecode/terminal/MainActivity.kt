package com.ai.assistance.onecode.terminal

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ai.assistance.onecode.terminal.main.TerminalScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log


class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

        // 双重返回退出：根页面侧滑/返回第一次提示，2秒内再按一次退出 app
        // 子页面(Settings/Setup)的 NavHost BackHandler 优先级更高，不会被这里拦截
        var lastBackPressedTime = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressedTime < 2000L) {
                    finish()
                } else {
                    lastBackPressedTime = currentTime
                    Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        })
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
