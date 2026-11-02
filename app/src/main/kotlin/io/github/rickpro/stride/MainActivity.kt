package io.github.rickpro.stride

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.rickpro.stride.ui.AppRoot
import io.github.rickpro.stride.ui.theme.StrideTheme

/**
 * The only activity. Draws edge to edge with light bar icons on the dark background, whatever the system
 * theme is. The treadmill link lives in [StrideApp.manager] and outlives this activity; the activity only
 * tells it when the app is in front.
 */
class MainActivity : ComponentActivity() {
    private val app: StrideApp get() = application as StrideApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            StrideTheme {
                AppRoot(app = app, keepScreenOn = ::keepScreenOn)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.manager.onForeground()
    }

    override fun onStop() {
        super.onStop()
        app.manager.onBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) app.manager.onAppClosed()
    }

    /** Keeps the screen on while the belt moves. A window flag, so no permission is needed. */
    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
