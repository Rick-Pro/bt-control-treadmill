package io.github.rickpro.stride

import android.app.Application
import android.content.pm.PackageManager
import io.github.rickpro.stride.history.HistoryStore
import io.github.rickpro.stride.session.DiagnosticsLog
import io.github.rickpro.stride.session.Haptics
import io.github.rickpro.stride.session.Settings
import io.github.rickpro.stride.session.TreadmillManager

/**
 * Builds the few long-lived objects once. The treadmill link lives here, not in the activity, so a rotation or a
 * trip to Settings never drops it. MainActivity reads [manager], [history] and [settings] through
 * `application as StrideApp`.
 */
class StrideApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var manager: TreadmillManager
        private set

    val versionName: String by lazy {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "dev"
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        history = HistoryStore(this)
        manager = TreadmillManager(
            context = this,
            settings = settings,
            log = DiagnosticsLog(),
            haptics = Haptics(this),
            historyStore = history,
            appVersion = versionName,
        )
    }
}
