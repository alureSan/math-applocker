package com.mathapplocker.foss.util

import android.content.Context
import android.content.res.Configuration
import com.mathapplocker.foss.data.Prefs
import java.util.Locale

/**
 * AppCompat applies the in-app language to Activities. Services (the overlay and
 * its notification) have their own context, so they need a configuration context
 * with the saved language to avoid falling back to the system language.
 */
object AppLocale {

    fun localizedContext(context: Context): Context {
        val tag = Prefs.get(context).settings.value.language
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
