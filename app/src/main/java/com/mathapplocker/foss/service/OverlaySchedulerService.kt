package com.mathapplocker.foss.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.data.Settings
import com.mathapplocker.foss.data.db.AppDatabase
import com.mathapplocker.foss.math.QuestionGenerator
import com.mathapplocker.foss.math.QuizQuestion
import com.mathapplocker.foss.media.MediaPauseController
import com.mathapplocker.foss.overlay.OverlayController
import com.mathapplocker.foss.util.AppLocale
import com.mathapplocker.foss.util.AppPermissions
import com.mathapplocker.foss.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the protection alive and decides when to interrupt the user.
 *
 * Option A timing: entering a selected app shows a challenge immediately unless a
 * challenge was solved within the configured interval. After solving, all selected
 * apps stay unlocked for that interval (wall-clock time).
 */
class OverlaySchedulerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var prefs: Prefs
    private lateinit var detector: ForegroundAppDetector
    private lateinit var media: MediaPauseController

    private var overlay: OverlayController? = null
    private var overlayPackage: String? = null
    private var loopJob: Job? = null
    private var challengeJob: Job? = null
    private var notificationSignature: Triple<Boolean, Int, Boolean>? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        detector = ForegroundAppDetector(this)
        media = MediaPauseController(this)
        Notifications.ensureChannel(AppLocale.localizedContext(this))
        startForegroundCompat()
        loopJob = serviceScope.launch { runProtectionLoop() }
        serviceScope.launch {
            prefs.settings.collect {
                Notifications.ensureChannel(AppLocale.localizedContext(this@OverlaySchedulerService))
                notificationSignature = null
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.setEnabled(false)
                stopProtection(resumeMedia = true)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REFRESH -> {
                notificationSignature = null
                updateNotification()
            }
        }

        if (!prefs.settings.value.enabled) {
            stopProtection(resumeMedia = true)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopProtection(resumeMedia = true)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val settings = prefs.settings.value
        val notification = Notifications.buildServiceNotification(
            context = AppLocale.localizedContext(this),
            enabled = settings.enabled,
            blockedCount = settings.blockedApps.size,
            missingPermissions = AppPermissions.missingCorePermissions(this).isNotEmpty(),
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, Notifications.SERVICE_NOTIFICATION_ID, notification, type)
        }
        notificationSignature = Triple(
            settings.enabled,
            settings.blockedApps.size,
            AppPermissions.missingCorePermissions(this).isNotEmpty(),
        )
    }

    private suspend fun runProtectionLoop() {
        while (currentCoroutineContext().isActive) {
            val settings = prefs.settings.value
            if (!settings.enabled) {
                stopProtection(resumeMedia = true)
                stopSelf()
                return
            }

            val missing = AppPermissions.missingCorePermissions(this)
            if (missing.isNotEmpty()) {
                updateNotification()
                delay(2_000)
                continue
            }

            if (overlay?.isShown == true) {
                val shown = overlay
                val locked = overlayPackage
                val current = if (detector.isScreenOn) detector.currentForeground() else null
                if (shown != null && locked != null && current != null && current != locked) {
                    // The user navigated away (Home, recents, notification, our app).
                    // Dismiss so the overlay does not float above the launcher, and
                    // keep media paused because the challenge was not solved.
                    shown.hide()
                    overlay = null
                    overlayPackage = null
                    media.stop(serviceScope, resumePlayback = false)
                    notificationSignature = null
                    updateNotification()
                }
            } else {
                if (!detector.isScreenOn) {
                    // No need to poll while the screen is off.
                    delay(1_200)
                    continue
                }
                evaluateForegroundApp(settings)
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun evaluateForegroundApp(settings: Settings) {
        if (settings.blockedApps.isEmpty()) return
        val foreground = detector.currentForeground() ?: return
        if (foreground == packageName) return
        if (foreground !in settings.blockedApps) return
        if (settings.isWithinUnlockWindow()) return
        showChallenge(foreground, settings)
    }

    private fun showChallenge(foregroundPackage: String, settings: Settings) {
        if (challengeJob?.isActive == true) return
        challengeJob = serviceScope.launch {
            // Let freshly opened media apps finish buffering so playback state is
            // known and can be resumed after the challenge.
            delay(SETTLE_DELAY_MS)
            if (detector.currentForeground() != foregroundPackage) return@launch
            if (prefs.settings.value.isWithinUnlockWindow()) return@launch

            val customQuestions = if (settings.advancedMode) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val database = AppDatabase.get(this@OverlaySchedulerService)
                        val enabledSets = database.questionSets().enabledIds()
                        if (enabledSets.isEmpty()) {
                            emptyList()
                        } else {
                            database.customQuestions()
                                .getForSets(enabledSets)
                                .map {
                                    QuizQuestion(
                                        text = it.text,
                                        answer = it.answer,
                                        isCustom = true,
                                        textAnswer = it.textAnswer,
                                    )
                                }
                        }
                    }.getOrDefault(emptyList())
                }
            } else {
                emptyList()
            }

            val quiz = withContext(Dispatchers.Default) {
                if (settings.advancedMode) {
                    val advanced = QuestionGenerator.generateAdvanced(
                        count = settings.numQuestions,
                        customQuestions = customQuestions,
                        timesTables = settings.timesTables,
                    )
                    // Never show an empty challenge (it would auto-solve). Fall back
                    // to generated questions until the parent adds content.
                    advanced.ifEmpty {
                        QuestionGenerator.generate(
                            count = settings.numQuestions,
                            difficulty = settings.difficulty,
                            operations = settings.categories,
                            digits = settings.operandDigits,
                        )
                    }
                } else {
                    QuestionGenerator.generate(
                        count = settings.numQuestions,
                        difficulty = settings.difficulty,
                        operations = settings.categories,
                        digits = settings.operandDigits,
                    )
                }
            }

            // The user may have left while questions were being built.
            if (detector.currentForeground() != foregroundPackage) return@launch
            if (prefs.settings.value.isWithinUnlockWindow()) return@launch
            if (overlay?.isShown == true) return@launch

            media.start(serviceScope)
            val controller = OverlayController(
                context = AppLocale.localizedContext(this@OverlaySchedulerService),
                questions = quiz,
                // Custom questions may have negative answers even on kid presets.
                allowNegative = settings.difficulty.allowNegative || quiz.any { it.answer < 0 },
                onSolved = { onChallengeSolved() },
                onExitHome = { onExitToHome() },
            )

            if (controller.show()) {
                overlay = controller
                overlayPackage = foregroundPackage
                notificationSignature = null
                updateNotification()
            } else {
                media.stop(serviceScope, resumePlayback = true)
                // Likely the overlay permission was revoked mid-flight; back off
                // instead of retrying on every poll tick.
                delay(5_000)
            }
        }
    }

    private fun onChallengeSolved() {
        overlay = null
        overlayPackage = null
        prefs.markChallengeSolved()
        media.stop(serviceScope, resumePlayback = true)
        notificationSignature = null
        updateNotification()
    }

    private fun onExitToHome() {
        overlay = null
        overlayPackage = null
        // Keep playback paused: the challenge was not solved.
        media.stop(serviceScope, resumePlayback = false)
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        notificationSignature = null
        updateNotification()
    }

    private fun updateNotification() {
        val settings = prefs.settings.value
        val missing = AppPermissions.missingCorePermissions(this).isNotEmpty()
        val signature = Triple(settings.enabled, settings.blockedApps.size, missing)
        if (signature == notificationSignature) return
        notificationSignature = signature
        Notifications.updateServiceNotification(
            context = AppLocale.localizedContext(this),
            enabled = settings.enabled,
            blockedCount = settings.blockedApps.size,
        )
    }

    private fun stopProtection(resumeMedia: Boolean) {
        challengeJob?.cancel()
        challengeJob = null
        loopJob?.cancel()
        loopJob = null
        overlay?.hide()
        overlay = null
        overlayPackage = null
        media.stop(serviceScope, resumePlayback = resumeMedia)
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    companion object {
        const val ACTION_START = "com.mathapplocker.foss.action.START"
        const val ACTION_STOP = "com.mathapplocker.foss.action.STOP"
        const val ACTION_REFRESH = "com.mathapplocker.foss.action.REFRESH"

        private const val POLL_INTERVAL_MS = 700L
        private const val SETTLE_DELAY_MS = 1_500L

        fun start(context: Context) {
            val intent = Intent(context, OverlaySchedulerService::class.java)
                .setAction(ACTION_START)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlaySchedulerService::class.java))
        }
    }
}
