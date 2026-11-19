package com.mathapplocker.foss.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

data class AppEntry(
    val packageName: String,
    val label: String,
)

/**
 * Loads launchable apps off the main thread. Labels come first (fast); icons are
 * loaded lazily per visible row and cached, so the picker opens instantly even on
 * devices with hundreds of apps.
 */
object AppsRepository {

    @Volatile
    private var cachedApps: List<AppEntry>? = null
    private val iconCache: MutableMap<String, ImageBitmap?> =
        Collections.synchronizedMap(HashMap())

    suspend fun loadLaunchableApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        cachedApps ?: buildList(context).also { cachedApps = it }
    }

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (iconCache.containsKey(packageName)) return@withContext iconCache[packageName]
            val drawable = runCatching {
                context.packageManager.getApplicationIcon(packageName)
            }.getOrNull()
            val bitmap = toImageBitmap(context, drawable)
            iconCache[packageName] = bitmap
            bitmap
        }

    fun invalidate() {
        cachedApps = null
        iconCache.clear()
    }

    private fun buildList(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        val seen = HashSet<String>()
        val entries = ArrayList<AppEntry>(resolved.size)
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || pkg.startsWith("com.mathapplocker.foss")) continue
            if (!seen.add(pkg)) continue
            val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            entries += AppEntry(pkg, label)
        }
        entries.sortBy { it.label.lowercase() }
        return entries
    }

    private fun toImageBitmap(context: Context, drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        return runCatching {
            val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            drawable.toBitmap(size, size).asImageBitmap()
        }.getOrNull()
    }
}
