package com.mathapplocker.foss

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class App : Application(), DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var relockJob: Job? = null

    override fun onCreate() {
        super<Application>.onCreate()
        Notifications.ensureChannel(this)
        applySavedLanguage()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        relockJob?.cancel()
        relockJob = null
    }

    override fun onStop(owner: LifecycleOwner) {
        val prefs = Prefs.get(this)
        if (!prefs.settings.value.hasPin) return
        // Short grace period so opening a system permission screen does not
        // immediately re-lock the app. Anything longer requires the PIN again.
        relockJob?.cancel()
        relockJob = scope.launch {
            delay(RELOCK_GRACE_MS)
            prefs.setAuthed(false)
        }
    }

    private fun applySavedLanguage() {
        val tag = Prefs.get(this).settings.value.language
        if (tag.isNotBlank()) {
            runCatching {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }
    }

    companion object {
        private const val RELOCK_GRACE_MS = 20_000L
    }
}
