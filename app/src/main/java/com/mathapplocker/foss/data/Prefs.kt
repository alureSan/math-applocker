package com.mathapplocker.foss.data

import android.content.Context
import android.content.SharedPreferences
import com.mathapplocker.foss.math.Difficulty
import com.mathapplocker.foss.math.Operation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 5,
    val numQuestions: Int = 3,
    val difficulty: Difficulty = Difficulty.AGE_5_7,
    val categories: Set<Operation> = Operation.entries.toSet(),
    val blockedApps: Set<String> = emptySet(),
    val hasPin: Boolean = false,
    val authed: Boolean = false,
    val language: String = "",
    val lastPromptAt: Long = 0L,
) {
    /** True while selected apps are unlocked after a solved challenge. */
    fun isWithinUnlockWindow(now: Long = System.currentTimeMillis()): Boolean {
        if (lastPromptAt <= 0L) return false
        return now < lastPromptAt + intervalMinutes.coerceAtLeast(1) * 60_000L
    }

    fun unlockWindowRemainingMs(now: Long = System.currentTimeMillis()): Long {
        if (lastPromptAt <= 0L) return 0L
        return (lastPromptAt + intervalMinutes.coerceAtLeast(1) * 60_000L - now).coerceAtLeast(0L)
    }
}

/**
 * Single source of truth for user settings. Backed by SharedPreferences so that the
 * foreground service can read values cheaply from any thread.
 */
class Prefs private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read(): Settings = Settings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 5).coerceIn(MIN_INTERVAL, MAX_INTERVAL),
        numQuestions = prefs.getInt(KEY_NUM_QUESTIONS, 3).coerceIn(MIN_QUESTIONS, MAX_QUESTIONS),
        difficulty = Difficulty.fromKey(prefs.getString(KEY_DIFFICULTY, null)),
        categories = readOperations(),
        blockedApps = prefs.getStringSet(KEY_BLOCKED_APPS, emptySet())?.toSet() ?: emptySet(),
        hasPin = !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty(),
        authed = prefs.getBoolean(KEY_AUTHED, false),
        language = prefs.getString(KEY_LANGUAGE, "") ?: "",
        lastPromptAt = prefs.getLong(KEY_LAST_PROMPT, 0L),
    )

    private fun readOperations(): Set<Operation> {
        val stored = prefs.getStringSet(KEY_CATEGORIES, null)
            ?: return Operation.entries.toSet()
        val parsed = stored.mapNotNull { Operation.fromKey(it) }.toSet()
        return parsed.ifEmpty { Operation.entries.toSet() }
    }

    fun setEnabled(enabled: Boolean) = edit { putBoolean(KEY_ENABLED, enabled) }

    fun setIntervalMinutes(minutes: Int) =
        edit { putInt(KEY_INTERVAL, minutes.coerceIn(MIN_INTERVAL, MAX_INTERVAL)) }

    fun setNumQuestions(count: Int) =
        edit { putInt(KEY_NUM_QUESTIONS, count.coerceIn(MIN_QUESTIONS, MAX_QUESTIONS)) }

    fun setDifficulty(difficulty: Difficulty) =
        edit { putString(KEY_DIFFICULTY, difficulty.key) }

    fun setCategories(operations: Set<Operation>) =
        edit { putStringSet(KEY_CATEGORIES, operations.map { it.key }.toSet()) }

    fun setBlockedApps(packages: Set<String>) =
        edit { putStringSet(KEY_BLOCKED_APPS, packages.toSet()) }

    fun setLanguage(tag: String) = edit { putString(KEY_LANGUAGE, tag) }

    fun setAuthed(authed: Boolean) = edit { putBoolean(KEY_AUTHED, authed) }

    fun markChallengeSolved(now: Long = System.currentTimeMillis()) =
        edit { putLong(KEY_LAST_PROMPT, now) }

    fun clearLastPrompt() = edit { putLong(KEY_LAST_PROMPT, 0L) }

    /** Stores a salted PBKDF2 hash. Never stores the PIN itself. */
    fun setPin(saltBase64: String, hashBase64: String, iterations: Int) = edit {
        putString(KEY_PIN_SALT, saltBase64)
        putString(KEY_PIN_HASH, hashBase64)
        putInt(KEY_PIN_ITERATIONS, iterations)
        putBoolean(KEY_AUTHED, true)
    }

    fun pinSalt(): String? = prefs.getString(KEY_PIN_SALT, null)

    fun pinHash(): String? = prefs.getString(KEY_PIN_HASH, null)

    fun pinIterations(): Int = prefs.getInt(KEY_PIN_ITERATIONS, DEFAULT_ITERATIONS)

    fun pinFailedAttempts(): Int = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)

    fun setPinFailedAttempts(value: Int) = edit { putInt(KEY_PIN_FAILED_ATTEMPTS, value) }

    fun pinLockedUntil(): Long = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0L)

    fun setPinLockedUntil(value: Long) = edit { putLong(KEY_PIN_LOCKED_UNTIL, value) }

    fun resetPinFailures() = edit {
        putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
        putLong(KEY_PIN_LOCKED_UNTIL, 0L)
    }

    /** Debug/support escape hatch only; not exposed in the UI. */
    fun clearPin() = edit {
        remove(KEY_PIN_SALT)
        remove(KEY_PIN_HASH)
        remove(KEY_PIN_ITERATIONS)
        putBoolean(KEY_AUTHED, false)
    }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        // SharedPreferences does not notify the listener on the same thread in all cases;
        // refresh eagerly so the UI reacts immediately.
        _settings.value = read()
    }

    companion object {
        const val NAME = "math_applocker_prefs"
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 720
        const val MIN_QUESTIONS = 1
        const val MAX_QUESTIONS = 20
        const val DEFAULT_ITERATIONS = 30_000

        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_NUM_QUESTIONS = "num_questions"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_CATEGORIES = "cats"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_AUTHED = "authed"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LAST_PROMPT = "last_prompt_ts"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_ITERATIONS = "pin_iterations"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
