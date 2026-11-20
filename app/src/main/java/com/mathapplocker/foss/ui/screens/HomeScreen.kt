package com.mathapplocker.foss.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathapplocker.foss.R
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.data.Settings
import com.mathapplocker.foss.data.db.AppDatabase
import com.mathapplocker.foss.math.QuestionGenerator
import com.mathapplocker.foss.service.OverlaySchedulerService
import com.mathapplocker.foss.ui.Screen
import com.mathapplocker.foss.ui.common.NavigationRow
import com.mathapplocker.foss.ui.common.PermissionRow
import com.mathapplocker.foss.ui.common.SectionLabel
import com.mathapplocker.foss.util.AppLocale
import com.mathapplocker.foss.util.AppPermissions
import com.mathapplocker.foss.util.Notifications
import kotlinx.coroutines.delay
import java.util.Locale

private data class PermissionSnapshot(
    val overlay: Boolean,
    val usage: Boolean,
    val notifications: Boolean,
    val ignoringBatteryOptimizations: Boolean,
) {
    val coreGranted: Boolean get() = overlay && usage && notifications
}

private enum class PermissionKind(val titleRes: Int, val explainRes: Int) {
    OVERLAY(R.string.perm_overlay_title, R.string.perm_overlay_explain),
    USAGE(R.string.perm_usage_title, R.string.perm_usage_explain),
    NOTIFICATIONS(R.string.perm_notifications_title, R.string.perm_notifications_explain),
    BATTERY(R.string.perm_battery_title, R.string.perm_battery_explain),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: Settings,
    onNavigate: (Screen) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val database = remember { AppDatabase.get(context) }
    val sets by remember { database.questionSets().observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var permissions by remember { mutableStateOf(readPermissions(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissions = readPermissions(context)
    }

    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    var dialogPermission by remember { mutableStateOf<PermissionKind?>(null) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissions = readPermissions(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissions = readPermissions(context)
    }

    fun openPermission(kind: PermissionKind) {
        try {
            when (kind) {
                PermissionKind.OVERLAY -> settingsLauncher.launch(
                    AppPermissions.overlaySettingsIntent(context),
                )

                PermissionKind.USAGE -> settingsLauncher.launch(
                    AppPermissions.usageAccessIntent(),
                )

                PermissionKind.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        settingsLauncher.launch(notificationSettingsIntent(context))
                    }
                }

                PermissionKind.BATTERY -> {
                    val intent = AppPermissions.batteryOptimizationIntent(context)
                    settingsLauncher.launch(intent)
                }
            }
        } catch (_: Throwable) {
            runCatching { settingsLauncher.launch(AppPermissions.appDetailsIntent(context)) }
        }
    }

    fun firstMissingPermission(): PermissionKind? = when {
        !permissions.overlay -> PermissionKind.OVERLAY
        !permissions.usage -> PermissionKind.USAGE
        !permissions.notifications -> PermissionKind.NOTIFICATIONS
        else -> null
    }

    fun enableProtection() {
        val missing = firstMissingPermission()
        if (missing != null) {
            pendingEnable = true
            dialogPermission = missing
            return
        }
        prefs.setEnabled(true)
        OverlaySchedulerService.start(context)
    }

    fun disableProtection() {
        pendingEnable = false
        prefs.setEnabled(false)
        OverlaySchedulerService.stop(context)
    }

    // Once the missing permission is granted, continue the pending enable action.
    LaunchedEffect(pendingEnable, permissions.coreGranted) {
        if (pendingEnable && permissions.coreGranted) {
            pendingEnable = false
            prefs.setEnabled(true)
            OverlaySchedulerService.start(context)
        }
    }

    // Restart the service if it is not running but protection is enabled.
    LaunchedEffect(settings.enabled, permissions.coreGranted) {
        if (settings.enabled && permissions.coreGranted) {
            OverlaySchedulerService.start(context)
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settings.lastPromptAt, settings.intervalMinutes) {
        now = System.currentTimeMillis()
        while (settings.unlockWindowRemainingMs(now) > 0L) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val unlockRemainingMs = settings.unlockWindowRemainingMs(now)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusCard(
                    enabled = settings.enabled,
                    active = settings.enabled && permissions.coreGranted,
                    blockedCount = settings.blockedApps.size,
                    onToggle = { on -> if (on) enableProtection() else disableProtection() },
                )
            }

            if (unlockRemainingMs > 0L) {
                item {
                    UnlockWindowChip(remainingMs = unlockRemainingMs)
                }
            }

            item {
                PermissionsCard(
                    permissions = permissions,
                    onGrant = { openPermission(it) },
                )
            }

            item { SectionLabel(stringResource(R.string.configuration)) }

            item {
                ChallengeModeCard(
                    advanced = settings.advancedMode,
                    onChange = { prefs.setAdvancedMode(it) },
                )
            }

            if (settings.advancedMode) {
                item {
                    val setsLabel = pluralStringResource(
                        R.plurals.set_count,
                        sets.size,
                        sets.size,
                    )
                    val tablesLabel = if (settings.timesTables.isEmpty()) {
                        stringResource(R.string.no_tables)
                    } else {
                        pluralStringResource(
                            R.plurals.table_count,
                            settings.timesTables.size,
                            settings.timesTables.size,
                        )
                    }
                    NavigationRow(
                        title = stringResource(R.string.manage_questions),
                        subtitle = pluralStringResource(
                            R.plurals.rules_summary,
                            settings.numQuestions,
                            settings.intervalMinutes,
                            settings.numQuestions,
                            "$setsLabel · $tablesLabel",
                        ),
                        icon = Icons.Filled.Edit,
                        onClick = { onNavigate(Screen.Questions) },
                    )
                }
            } else {
                item {
                    NavigationRow(
                        title = stringResource(R.string.configure_math_rules),
                        subtitle = pluralStringResource(
                            R.plurals.rules_summary,
                            settings.numQuestions,
                            settings.intervalMinutes,
                            settings.numQuestions,
                            if (settings.operandDigits == QuestionGenerator.AUTO_DIGITS) {
                                stringResource(R.string.digits_auto)
                            } else {
                                pluralStringResource(
                                    R.plurals.digit_count,
                                    settings.operandDigits,
                                    settings.operandDigits,
                                )
                            },
                        ),
                        icon = Icons.Filled.Add,
                        onClick = { onNavigate(Screen.Math) },
                    )
                }
            }

            item {
                NavigationRow(
                    title = stringResource(R.string.choose_apps),
                    subtitle = if (settings.blockedApps.isEmpty()) {
                        stringResource(R.string.no_apps_selected)
                    } else {
                        pluralStringResource(
                            R.plurals.choose_apps_summary,
                            settings.blockedApps.size,
                            settings.blockedApps.size,
                        )
                    },
                    icon = Icons.Filled.Lock,
                    onClick = { onNavigate(Screen.Apps) },
                )
            }

            item {
                NavigationRow(
                    title = stringResource(R.string.choose_language_title),
                    subtitle = if (currentLanguage(settings).startsWith("es")) {
                        stringResource(R.string.language_spanish)
                    } else {
                        stringResource(R.string.language_english)
                    },
                    avatarText = "Aa",
                    onClick = { showLanguageDialog = true },
                )
            }

            item {
                NavigationRow(
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.about_summary),
                    icon = Icons.Filled.Info,
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    dialogPermission?.let { kind ->
        AlertDialog(
            onDismissRequest = {
                dialogPermission = null
                pendingEnable = false
            },
            title = { Text(stringResource(R.string.permission_needed)) },
            text = {
                Text(
                    stringResource(
                        R.string.permission_dialog_message,
                        stringResource(kind.titleRes),
                        stringResource(kind.explainRes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogPermission = null
                        openPermission(kind)
                    },
                ) { Text(stringResource(R.string.grant)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogPermission = null
                        pendingEnable = false
                    },
                ) { Text(stringResource(R.string.not_now)) }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
        )
    }

    if (showLanguageDialog) {
        val current = currentLanguage(settings)
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.choose_language_title)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.language_english),
                        selected = !current.startsWith("es"),
                        onClick = {
                            prefs.setLanguage("en")
                            Notifications.ensureChannel(AppLocale.localizedContext(context))
                            applyLocale("en")
                            showLanguageDialog = false
                        },
                    )
                    LanguageOption(
                        label = stringResource(R.string.language_spanish),
                        selected = current.startsWith("es"),
                        onClick = {
                            prefs.setLanguage("es")
                            Notifications.ensureChannel(AppLocale.localizedContext(context))
                            applyLocale("es")
                            showLanguageDialog = false
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAboutDialog) {
        val version = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        }
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.about)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_summary))
                    Text(
                        text = stringResource(R.string.version_label, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionSnapshot,
    onGrant: (PermissionKind) -> Unit,
) {
    val allGranted = permissions.overlay &&
        permissions.usage &&
        permissions.notifications &&
        permissions.ignoringBatteryOptimizations
    val container = if (allGranted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = if (allGranted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val accent = if (allGranted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (allGranted) Icons.Filled.Check else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = accent,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (allGranted) {
                                R.string.all_permissions_granted
                            } else {
                                R.string.missing_permissions_title
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            if (allGranted) {
                                R.string.all_permissions_summary
                            } else {
                                R.string.missing_permissions_summary
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.8f),
                    )
                }
            }

            if (!allGranted) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        if (!permissions.overlay) {
                            PermissionRow(
                                title = stringResource(R.string.perm_overlay_title),
                                description = stringResource(R.string.perm_overlay_explain),
                                granted = false,
                                grantLabel = stringResource(R.string.grant),
                                onGrant = { onGrant(PermissionKind.OVERLAY) },
                            )
                        }
                        if (!permissions.usage) {
                            PermissionRow(
                                title = stringResource(R.string.perm_usage_title),
                                description = stringResource(R.string.perm_usage_explain),
                                granted = false,
                                grantLabel = stringResource(R.string.grant),
                                onGrant = { onGrant(PermissionKind.USAGE) },
                            )
                        }
                        if (!permissions.notifications) {
                            PermissionRow(
                                title = stringResource(R.string.perm_notifications_title),
                                description = stringResource(R.string.perm_notifications_explain),
                                granted = false,
                                grantLabel = stringResource(R.string.grant),
                                onGrant = { onGrant(PermissionKind.NOTIFICATIONS) },
                            )
                        }
                        if (!permissions.ignoringBatteryOptimizations) {
                            PermissionRow(
                                title = stringResource(R.string.perm_battery_title),
                                description = stringResource(R.string.perm_battery_explain),
                                granted = false,
                                grantLabel = stringResource(R.string.grant),
                                onGrant = { onGrant(PermissionKind.BATTERY) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeModeCard(
    advanced: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.challenge_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !advanced,
                    onClick = { onChange(false) },
                    label = { Text(stringResource(R.string.mode_basic)) },
                )
                FilterChip(
                    selected = advanced,
                    onClick = { onChange(true) },
                    label = { Text(stringResource(R.string.mode_advanced)) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (advanced) {
                        R.string.mode_advanced_summary
                    } else {
                        R.string.mode_basic_summary
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    enabled: Boolean,
    active: Boolean,
    blockedCount: Int,
    onToggle: (Boolean) -> Unit,
) {
    val container = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val dotColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val title = if (active) {
        stringResource(R.string.service_active)
    } else {
        stringResource(R.string.service_inactive)
    }
    val subtitle = when {
        active && blockedCount > 0 -> pluralStringResource(
            R.plurals.protecting_apps,
            blockedCount,
            blockedCount,
        )
        active -> stringResource(R.string.notif_no_apps)
        enabled -> stringResource(R.string.permission_required_to_enable)
        else -> stringResource(R.string.service_disabled_msg)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun UnlockWindowChip(remainingMs: Long) {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val text = String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60,
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.unlock_remaining, text),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun readPermissions(context: Context): PermissionSnapshot = PermissionSnapshot(
    overlay = AppPermissions.canDrawOverlays(context),
    usage = AppPermissions.hasUsageAccess(context),
    notifications = Notifications.canPostNotifications(context),
    ignoringBatteryOptimizations = AppPermissions.ignoresBatteryOptimizations(context),
)

private fun currentLanguage(settings: Settings): String =
    settings.language.ifBlank { Locale.getDefault().language }

private fun applyLocale(tag: String) {
    runCatching {
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(tag),
        )
    }
}

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
