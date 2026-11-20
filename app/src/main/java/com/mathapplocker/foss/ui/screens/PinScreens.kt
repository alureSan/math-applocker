package com.mathapplocker.foss.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mathapplocker.foss.R
import com.mathapplocker.foss.data.PinHasher
import com.mathapplocker.foss.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PinSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()

    var firstPin by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }

    val isConfirmStep = firstPin != null
    val title = if (isConfirmStep) {
        stringResource(R.string.set_pin_confirm)
    } else {
        stringResource(R.string.set_pin_title)
    }

    fun finish(pin: String) {
        saving = true
        scope.launch {
            val stored = withContext(Dispatchers.Default) { PinHasher.create(pin) }
            prefs.setPin(stored.saltBase64, stored.hashBase64, stored.iterations)
            saving = false
            onDone()
        }
    }

    fun handleDigit(digit: Char) {
        if (saving || input.length >= 4) return
        errorRes = null
        input += digit
        if (input.length == 4) {
            val pin = input
            if (!isConfirmStep) {
                firstPin = pin
                input = ""
            } else if (pin == firstPin) {
                finish(pin)
            } else {
                errorRes = R.string.pin_mismatch
                firstPin = null
                input = ""
            }
        }
    }

    PinScaffold(
        title = title,
        subtitle = stringResource(R.string.set_pin_subtitle),
        inputLength = input.length,
        errorText = errorRes?.let { stringResource(it) },
        busy = saving,
        onDigit = ::handleDigit,
        onBackspace = { input = input.dropLast(1) },
    )
}

@Composable
fun PinAuthScreen(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var checking by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(prefs.pinFailedAttempts()) }
    var lockUntil by remember { mutableLongStateOf(prefs.pinLockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockUntil) {
        now = System.currentTimeMillis()
        while (System.currentTimeMillis() < lockUntil) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }

    val remainingSeconds = (((lockUntil - now) + 999) / 1000).coerceAtLeast(0).toInt()
    val lockedOut = remainingSeconds > 0

    fun handleDigit(digit: Char) {
        if (checking || lockedOut || input.length >= 4) return
        errorRes = null
        input += digit
        if (input.length == 4) {
            val pin = input
            checking = true
            scope.launch {
                val ok = withContext(Dispatchers.Default) {
                    PinHasher.verify(
                        pin = pin,
                        saltBase64 = prefs.pinSalt(),
                        hashBase64 = prefs.pinHash(),
                        iterations = prefs.pinIterations(),
                    )
                }
                checking = false
                if (ok) {
                    prefs.resetPinFailures()
                    prefs.setAuthed(true)
                    onSuccess()
                } else {
                    attempts += 1
                    var newLockUntil = lockUntil
                    if (attempts >= MAX_ATTEMPTS) {
                        attempts = 0
                        newLockUntil = System.currentTimeMillis() + LOCKOUT_MS
                        lockUntil = newLockUntil
                    }
                    prefs.setPinFailedAttempts(attempts)
                    prefs.setPinLockedUntil(newLockUntil)
                    errorRes = R.string.wrong_pin
                    input = ""
                }
            }
        }
    }

    PinScaffold(
        title = stringResource(R.string.enter_pin_title),
        subtitle = stringResource(R.string.enter_pin_subtitle),
        inputLength = input.length,
        errorText = when {
            lockedOut -> stringResource(R.string.too_many_attempts, remainingSeconds)
            errorRes != null -> stringResource(errorRes!!)
            else -> null
        },
        busy = checking,
        enabled = !lockedOut,
        onDigit = ::handleDigit,
        onBackspace = { input = input.dropLast(1) },
    )
}

@Composable
private fun PinScaffold(
    title: String,
    subtitle: String,
    inputLength: Int,
    errorText: String?,
    busy: Boolean,
    enabled: Boolean = true,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            val landscape = maxWidth > maxHeight

            if (landscape) {
                // Landscape phones are short: put the text on the left and the
                // keypad on the right, and shrink the keys to fit the height.
                val availableHeight = maxHeight
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PinHeader(
                            title = title,
                            subtitle = subtitle,
                            inputLength = inputLength,
                            errorText = errorText,
                            compact = true,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val busyReserve = if (busy) 36.dp else 0.dp
                        val keyHeight = ((availableHeight - 24.dp - 30.dp - busyReserve) / 4)
                            .coerceIn(38.dp, 64.dp)
                        PinKeypad(
                            onDigit = onDigit,
                            onBackspace = onBackspace,
                            enabled = enabled && !busy,
                            keyHeight = keyHeight,
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .fillMaxWidth(),
                        )
                        if (busy) {
                            Spacer(Modifier.height(10.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    PinHeader(
                        title = title,
                        subtitle = subtitle,
                        inputLength = inputLength,
                        errorText = errorText,
                    )

                    Spacer(Modifier.height(28.dp))
                    PinKeypad(
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                        enabled = enabled && !busy,
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                    )

                    if (busy) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinHeader(
    title: String,
    subtitle: String,
    inputLength: Int,
    errorText: String?,
    compact: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(if (compact) 52.dp else 64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
            )
        }
    }

    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
    val digitsDescription = stringResource(R.string.digits_entered, inputLength)
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.semantics { contentDescription = digitsDescription },
    ) {
        repeat(4) { index ->
            val filled = index < inputLength
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }

    Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
    Text(
        text = errorText ?: " ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.height(22.dp),
    )
}

@Composable
private fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 64.dp,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    val backspaceDescription = stringResource(R.string.delete_digit)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit ->
                    PinKey(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        keyHeight = keyHeight,
                        onClick = { onDigit(digit) },
                    ) { digit.toString() }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f).height(keyHeight))
            PinKey(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                keyHeight = keyHeight,
                onClick = { onDigit('0') },
            ) { "0" }
            PinKey(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = backspaceDescription },
                enabled = enabled,
                keyHeight = keyHeight,
                onClick = onBackspace,
            ) { "⌫" }
        }
    }
}

@Composable
private fun PinKey(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    keyHeight: Dp,
    onClick: () -> Unit,
    content: @Composable () -> String,
) {
    Box(
        modifier = modifier
            .height(keyHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = content(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_MS = 30_000L
